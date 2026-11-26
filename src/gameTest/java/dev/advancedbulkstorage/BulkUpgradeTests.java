package dev.advancedbulkstorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.storage.BulkCellItem;
import dev.advancedbulkstorage.storage.LimitProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BulkStorageMod.ID)
@PrefixGameTestTemplate(false)
public final class BulkUpgradeTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey GOLD = AEItemKey.of(Items.GOLD_INGOT);
    private static final AEItemKey DIAMOND = AEItemKey.of(Items.DIAMOND);
    private static final Actionable REAL = Actionable.MODULATE;
    private static final IActionSource SOURCE = IActionSource.empty();

    @GameTest(template = "empty")
    public static void recordsOverlappingLimitsWithoutDestroyingExcess(GameTestHelper test) {
        var stack = BulkStorageMod.CELLS.get(CellTier.QUANTUM).get().getDefaultInstance();
        var cellItem = (BulkCellItem) stack.getItem();
        cellItem.getUpgrades(stack).setItemDirect(0, new ItemStack(BulkStorageMod.STORAGE_LIMIT_CARD.get()));
        stack.set(BulkStorageMod.FILTER, new FilterConfig("@minecraft", false));
        String limits = "50 each; 70 total; 40 #c:ingots; 60 @minecraft; 25 minecraft:iron_ingot";
        test.assertTrue(LimitProfile.parse(limits).valid() && !LimitProfile.parse("70 *").valid()
                && !LimitProfile.parse("10 each; -5 total").valid(), "Malformed or universal group was accepted");
        stack.set(BulkStorageMod.LIMITS, limits);
        var cell = (BulkCellInventory) StorageCells.getCellInventory(stack, null);
        test.assertTrue(cell.insert(IRON, 100, Actionable.SIMULATE, SOURCE) == 25 && cell.amount(IRON) == 0,
                "Simulation altered the integer ledger");
        test.assertTrue(cell.insert(IRON, 100, REAL, SOURCE) == 25 && cell.insert(GOLD, 100, REAL, SOURCE) == 15
                && cell.insert(DIAMOND, 100, REAL, SOURCE) == 20, "Overlapping per-key, tag and namespace caps failed");
        test.assertTrue(cell.extract(IRON, 10, REAL, SOURCE) == 10 && cell.insert(GOLD, 20, REAL, SOURCE) == 10,
                "Extraction did not free the matching ledger counters");
        stack.set(BulkStorageMod.LIMITS, "2 total; 2 each");
        test.assertTrue(cell.insert(GOLD, 1, REAL, SOURCE) == 0 && cell.amount(GOLD) == 25
                && cell.extract(GOLD, 20, REAL, SOURCE) == 20 && cell.amount(GOLD) == 5,
                "Lowering a limit altered existing stock");
        cellItem.getUpgrades(stack).setItemDirect(0, ItemStack.EMPTY);
        test.assertTrue(cell.insert(GOLD, 1, REAL, SOURCE) == 1 && cell.amount(GOLD) == 6,
                "Removed card continued to impose old limits");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void compressedLimitsCountBaseUnitsAndEnergyCardsStack(GameTestHelper test) {
        var stack = BulkStorageMod.CELLS.get(CellTier.QUANTUM).get().getDefaultInstance();
        var cellItem = (BulkCellItem) stack.getItem();
        var cards = cellItem.getUpgrades(stack);
        cards.setItemDirect(0, new ItemStack(BulkStorageMod.MULTI_COMPRESSION_CARD.get()));
        cards.setItemDirect(1, new ItemStack(BulkStorageMod.STORAGE_LIMIT_CARD.get()));
        cards.setItemDirect(2, new ItemStack(BulkStorageMod.ENERGY_SAVING_CARD.get()));
        cards.setItemDirect(3, new ItemStack(BulkStorageMod.ENERGY_SAVING_CARD.get()));
        stack.set(BulkStorageMod.FILTER, new FilterConfig("#c:ingots", false));
        stack.set(BulkStorageMod.LIMITS, "100 total; 90 minecraft:iron_nugget");
        var cell = (BulkCellInventory) StorageCells.getCellInventory(stack, null);
        var nugget = AEItemKey.of(Items.IRON_NUGGET);
        var block = AEItemKey.of(Items.IRON_BLOCK);
        test.assertTrue(cell.insert(IRON, 100, REAL, SOURCE) == 10 && cell.amount(nugget) == 90
                && cell.insert(GOLD, 100, REAL, SOURCE) == 1, "Limits double counted aliases or ignored base units");
        test.assertTrue(cell.extract(block, 1, REAL, SOURCE) == 1 && cell.insert(IRON, 900, REAL, SOURCE) == 9
                && cell.amount(nugget) == 90, "Compressed withdrawal failed to update the ledger");
        test.assertTrue(Math.abs(cell.getIdleDrain() - 40.96) < 0.001, "Energy card multiplier did not stack");
        cards.setItemDirect(3, ItemStack.EMPTY);
        test.assertTrue(Math.abs(cell.getIdleDrain() - 51.2) < 0.001, "Energy was not recalculated after card removal");
        for (var id : java.util.List.of("storage_limit_card", "energy_saving_card")) {
            test.assertTrue(test.getLevel().getRecipeManager().byKey(BulkStorageMod.id(id)).isPresent(), "Missing " + id);
        }
        test.succeed();
    }
}
