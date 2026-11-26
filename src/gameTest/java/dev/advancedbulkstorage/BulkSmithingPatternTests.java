package dev.advancedbulkstorage;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.EncodedSmithingTablePattern;
import com.mojang.logging.LogUtils;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.storage.BulkCellItem;
import gripe._90.megacells.definition.MEGAItems;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BulkStorageMod.ID)
@PrefixGameTestTemplate(false)
public final class BulkSmithingPatternTests {
    private BulkSmithingPatternTests() {}

    @GameTest(template = "empty")
    public static void onlyUuidOnlyBlankPatternsAreNormalized(GameTestHelper test) {
        var level = test.getLevel();
        var base = cell(CellTier.MOD);
        var id = BulkCellItem.ensureId(base);
        var template = MEGAItems.CELL_COMPONENT_256M.stack();
        var addition = MEGAItems.CELL_COMPONENT_256M.stack();
        var recipe = smithing(level, template, base, addition);
        var output = recipe.value().assemble(new SmithingRecipeInput(template, base, addition), level.registryAccess());
        var encoded = PatternDetailsHelper.encodeSmithingTablePattern(recipe, AEItemKey.of(template),
                AEItemKey.of(base), AEItemKey.of(addition), AEItemKey.of(output), false);
        var pattern = encoded.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        test.assertTrue(ItemStack.matches(pattern.base(), cell(CellTier.MOD))
                && ItemStack.matches(pattern.resultItem(), cell(CellTier.QUANTUM)),
                "A new blank pattern still copied the example disk UUID");
        test.assertTrue(id.equals(base.get(BulkStorageMod.CELL_ID)) && id.equals(output.get(BulkStorageMod.CELL_ID)),
                "Encoding touched the example disk or its manual smithing result");

        base.set(BulkStorageMod.FILTER, new FilterConfig("@minecraft", false));
        output = recipe.value().assemble(new SmithingRecipeInput(template, base, addition), level.registryAccess());
        var configured = encodedSmithingFixture(level, recipe, template, base, addition);
        var decoded = (AESmithingTablePattern) PatternDetailsHelper.decodePattern(configured, level);
        test.assertTrue(decoded != null && decoded.getBase().equals(AEItemKey.of(base))
                && decoded.getOutputs().getFirst().what().equals(AEItemKey.of(output)),
                "A configured pattern lost its exact input or output data");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void savedUuidPatternsCanSubmitAndCraftAQuantumCell(GameTestHelper test) {
        var center = new BlockPos(2, 2, 2);
        test.setBlock(center, AEBlocks.DRIVE.block());
        test.setBlock(center.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(center.west(), AEBlocks.CRAFTING_STORAGE_1K.block());
        test.setBlock(center.east(), AEBlocks.PATTERN_PROVIDER.block());
        test.setBlock(center.east().above(), AEBlocks.MOLECULAR_ASSEMBLER.block());
        DriveBlockEntity drive = test.getBlockEntity(center);
        PatternProviderBlockEntity provider = test.getBlockEntity(center.east());
        MolecularAssemblerBlockEntity assembler = test.getBlockEntity(center.east().above());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
        assembler.getUpgrades().addItems(AEItems.SPEED_CARD.stack(5));
        var level = test.getLevel();
        var blankMega = MEGAItems.BULK_ITEM_CELL.stack();
        var logic = AEItems.LOGIC_PROCESSOR.stack();
        var engineering = AEItems.ENGINEERING_PROCESSOR.stack();
        var part64m = MEGAItems.CELL_COMPONENT_64M.stack();
        var part256m = MEGAItems.CELL_COMPONENT_256M.stack();
        var output = AEItemKey.of(cell(CellTier.QUANTUM));
        var source = IActionSource.ofMachine(drive);
        var calculation = new AtomicReference<Future<ICraftingPlan>>();

        test.startSequence().thenWaitUntil(() -> {
            var grid = drive.getMainNode().getGrid();
            test.assertTrue(grid != null && drive.isPowered() && provider.getMainNode().isOnline()
                    && !grid.getCraftingService().getCpus().isEmpty(), "Crafting network is not ready");
        }).thenExecute(() -> {
            var input = CraftingInput.of(2, 2, List.of(blankMega, logic, engineering, part64m));
            var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            var patterns = provider.getLogic().getPatternInv();
            patterns.setItemDirect(0, PatternDetailsHelper.encodeCraftingPattern(recipe,
                    new ItemStack[] {blankMega, logic, ItemStack.EMPTY, engineering, part64m, ItemStack.EMPTY,
                            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY},
                    cell(CellTier.TAG), false, false));
            var tiers = CellTier.values();
            var templates = List.of(engineering, part64m, part256m);
            for (int i = 0; i < templates.size(); i++) {
                var base = cell(tiers[i]);
                if (i < 2) {
                    // The saved network had unique IDs on the two middle-tier patterns.
                    BulkCellItem.ensureId(base);
                }
                var template = templates.get(i);
                var legacy = encodedSmithingFixture(level, smithing(level, template, base, part256m),
                        template, base, part256m);
                test.assertTrue(ItemStack.matches(legacy.get(AEComponents.ENCODED_SMITHING_TABLE_PATTERN).base(), base),
                        "The legacy pattern fixture did not keep its original example disk");
                patterns.setItemDirect(i + 1, legacy);
            }

            var network = drive.getMainNode().getGrid().getStorageService().getInventory();
            test.assertTrue(network.insert(AEItemKey.of(blankMega), 1, Actionable.MODULATE, source) == 1
                    && network.insert(AEItemKey.of(logic), 1, Actionable.MODULATE, source) == 1
                    && network.insert(AEItemKey.of(engineering), 2, Actionable.MODULATE, source) == 2
                    && network.insert(AEItemKey.of(part64m), 2, Actionable.MODULATE, source) == 2
                    && network.insert(AEItemKey.of(part256m), 4, Actionable.MODULATE, source) == 4,
                    "Could not supply the exact raw cost of one crafted quantum cell");
        }).thenWaitUntil(() -> {
            var crafting = drive.getMainNode().getGrid().getCraftingService();
            for (var tier : CellTier.values()) {
                test.assertTrue(crafting.isCraftable(AEItemKey.of(cell(tier))), "The blank result is not advertised for " + tier);
            }
        }).thenExecute(() -> {
            var crafting = drive.getMainNode().getGrid().getCraftingService();
            calculation.set(crafting.beginCraftingCalculation(level, () -> source, output, 1,
                    CalculationStrategy.REPORT_MISSING_ITEMS));
        }).thenWaitUntil(() -> test.assertTrue(calculation.get().isDone(), "Waiting for the crafting plan"))
                .thenExecute(() -> {
                    var plan = calculation.get().resultNow();
                    test.assertTrue(!plan.simulation() && plan.patternTimes().size() == 4,
                            "The quantum order did not plan all four recipe stages");
                    var crafting = drive.getMainNode().getGrid().getCraftingService();
                    var submission = crafting.submitJob(plan, null, null, false, source);
                    test.assertTrue(submission.successful(), "Crafting CPU rejected order: " + submission.errorCode());
                }).thenWaitUntil(() -> {
                    var storage = drive.getMainNode().getGrid().getStorageService().getInventory();
                    test.assertTrue(storage.extract(output, 1, Actionable.SIMULATE, source) == 1,
                            "The crafted blank quantum disk has not arrived in storage");
                }).thenExecute(() -> {
                    var storage = drive.getMainNode().getGrid().getStorageService().getInventory();
                    for (var input : List.of(blankMega, logic, engineering, part64m, part256m)) {
                        test.assertTrue(storage.extract(AEItemKey.of(input), 1, Actionable.SIMULATE, source) == 0,
                                "A raw ingredient was left unused by the quantum craft");
                    }
                    LogUtils.getLogger().info("ABS legacy smithing patterns: advertised four tiers, submitted one order, stored one blank quantum cell");
                }).thenSucceed();
    }

    private static ItemStack cell(CellTier tier) {
        return BulkStorageMod.CELLS.get(tier).get().getDefaultInstance();
    }

    private static RecipeHolder<SmithingRecipe> smithing(ServerLevel level, ItemStack template, ItemStack base,
                                                         ItemStack addition) {
        return level.getRecipeManager().getRecipeFor(RecipeType.SMITHING,
                new SmithingRecipeInput(template, base, addition), level).orElseThrow();
    }

    private static ItemStack encodedSmithingFixture(ServerLevel level, RecipeHolder<SmithingRecipe> recipe,
                                                    ItemStack template, ItemStack base, ItemStack addition) {
        var output = recipe.value().assemble(new SmithingRecipeInput(template, base, addition), level.registryAccess());
        var pattern = AEItems.SMITHING_TABLE_PATTERN.stack();
        pattern.set(AEComponents.ENCODED_SMITHING_TABLE_PATTERN,
                new EncodedSmithingTablePattern(template, base, addition, output, false, recipe.id()));
        return ItemStack.parse(level.registryAccess(), pattern.save(level.registryAccess())).orElseThrow();
    }
}
