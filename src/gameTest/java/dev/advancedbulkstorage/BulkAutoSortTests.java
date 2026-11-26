package dev.advancedbulkstorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.mojang.logging.LogUtils;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.storage.AutoSortTier;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.storage.BulkCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BulkStorageMod.ID)
@PrefixGameTestTemplate(false)
public final class BulkAutoSortTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey GOLD = AEItemKey.of(Items.GOLD_INGOT);
    private static final IActionSource SOURCE = IActionSource.empty();

    @GameTest(template = "empty", batch = "abs_migrator", timeoutTicks = 300)
    public static void quantumAutoSortConservesStockAndWakesAfterIdle(GameTestHelper test) {
        var pos = new BlockPos(2, 2, 2);
        test.setBlock(pos, AEBlocks.DRIVE.block());
        test.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        DriveBlockEntity drive = test.getBlockEntity(pos);
        var first = target(true);
        var next = target(true);
        var source = cell("#c:ingots");
        var stock = (BulkCellInventory) StorageCells.getCellInventory(source, () -> {});
        stock.insert(IRON, 3_000_000_000L, Actionable.MODULATE, SOURCE);
        stock.insert(GOLD, 7, Actionable.MODULATE, SOURCE);
        stock.persist();
        drive.getInternalInventory().setItemDirect(0, first);
        drive.getInternalInventory().setItemDirect(1, next);
        drive.getInternalInventory().setItemDirect(2, source);
        for (var tier : AutoSortTier.values()) {
            test.assertTrue(test.getLevel().getRecipeManager().byKey(BulkStorageMod.id(tier.id)).isPresent(),
                    "Missing auto-sorting card recipe");
        }
        var disabled = target(false);
        test.assertTrue(((BulkCellInventory) StorageCells.getCellInventory(disabled, null)).autoSortRate() == 0,
                "Newly installed card did not default to disabled");
        var cards = ((BulkCellItem) disabled.getItem()).getUpgrades(disabled);
        cards.setItemDirect(1, new ItemStack(BulkStorageMod.SORTING_CARDS.get(AutoSortTier.STANDARD).get()));
        disabled.set(BulkStorageMod.AUTO_SORT_ENABLED, true);
        test.assertTrue(((BulkCellInventory) StorageCells.getCellInventory(disabled, null)).autoSortRate() == 0,
                "Conflicting sorting cards were enabled together");
        test.startSequence()
                .thenWaitUntil(() -> test.assertTrue(drive.getMainNode().isOnline() && mounted(drive, 0).amount(IRON) > 0,
                        "Drive or scheduled sorting did not become active"))
                .thenExecute(() -> {
                    long moved = mounted(drive, 0).amount(IRON);
                    test.assertTrue(moved == Integer.MAX_VALUE && mounted(drive, 2).amount(IRON) == 3_000_000_000L - moved
                            && mounted(drive, 2).amount(GOLD) == 7 && mounted(drive, 1).amount(IRON) == 0,
                            "Quantum sorting did not preserve INT throughput, admission and equal-priority tie breaks");
                    LogUtils.getLogger().info("ABS quantum sorting first tick: moved={}", moved);
                })
                .thenWaitUntil(() -> test.assertTrue(mounted(drive, 0).amount(IRON) == 3_000_000_000L,
                        "Scheduler did not finish the remaining stock"))
                .thenIdle(6)
                .thenExecute(() -> test.assertTrue(mounted(drive, 2).insert(IRON, 194, Actionable.MODULATE, SOURCE) == 194,
                        "Could not seed a wakeup on the attached source"))
                .thenWaitUntil(() -> test.assertTrue(mounted(drive, 0).amount(IRON) == 3_000_000_194L
                        && mounted(drive, 1).amount(IRON) == 0 && mounted(drive, 2).amount(IRON) == 0,
                        "An event did not wake the idle task or caused a sorting loop"))
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "abs_migrator", timeoutTicks = 300)
    public static void autoSortingMakesProgressWhileTargetIsDrained(GameTestHelper test) {
        var pos = new BlockPos(2, 2, 2);
        test.setBlock(pos, AEBlocks.DRIVE.block());
        test.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        DriveBlockEntity drive = test.getBlockEntity(pos);
        var target = target(true);
        target.set(BulkStorageMod.FILTER, new FilterConfig("minecraft:iron_ingot | minecraft:gold_ingot", false));
        var targetStock = (BulkCellInventory) StorageCells.getCellInventory(target, () -> {});
        targetStock.insert(GOLD, 100, Actionable.MODULATE, SOURCE);
        targetStock.persist();
        var ballast = cell("minecraft:stick");
        var ballastStock = (BulkCellInventory) StorageCells.getCellInventory(ballast, () -> {});
        for (int i = 0; i < 320; i++) {
            ballastStock.insert(ballastKey(i), i + 1, Actionable.MODULATE, SOURCE);
        }
        ballastStock.persist();
        var source = cell("minecraft:iron_ingot");
        var sourceStock = (BulkCellInventory) StorageCells.getCellInventory(source, () -> {});
        sourceStock.insert(IRON, 42, Actionable.MODULATE, SOURCE);
        sourceStock.persist();
        drive.getInternalInventory().setItemDirect(0, target);
        drive.getInternalInventory().setItemDirect(1, ballast);
        drive.getInternalInventory().setItemDirect(2, source);

        int[] drained = {0};
        for (int tick = 1; tick <= 80; tick++) {
            test.runAtTickTime(tick, () -> {
                if (drive.getMainNode().isOnline()) {
                    drained[0] += mounted(drive, 0).extract(GOLD, 1, Actionable.MODULATE, SOURCE);
                }
            });
        }
        test.runAtTickTime(80, () -> {
            test.assertTrue(drained[0] > 20 && mounted(drive, 0).amount(GOLD) + drained[0] == 100,
                    "Target was not actively drained or gold was lost");
            test.assertTrue(mounted(drive, 0).amount(IRON) == 42 && mounted(drive, 2).amount(IRON) == 0,
                    "Repeated extraction starved the scan of a later source cell");
            var unchanged = mounted(drive, 1);
            test.assertTrue(unchanged.types() == 320, "Sorting changed the ballast cell's key count");
            for (int i = 0; i < 320; i++) {
                test.assertTrue(unchanged.amount(ballastKey(i)) == i + 1, "Sorting changed variant " + i);
            }
            test.succeed();
        });
    }

    private static AEItemKey ballastKey(int index) {
        var stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Unsorted variant " + index));
        return AEItemKey.of(stack);
    }

    private static BulkCellInventory mounted(DriveBlockEntity drive, int slot) {
        return (BulkCellInventory) drive.getOriginalCellInventory(slot);
    }

    private static ItemStack target(boolean enabled) {
        var stack = cell("minecraft:iron_ingot");
        ((BulkCellItem) stack.getItem()).getUpgrades(stack).setItemDirect(0,
                new ItemStack(BulkStorageMod.SORTING_CARDS.get(AutoSortTier.QUANTUM).get()));
        if (enabled) {
            stack.set(BulkStorageMod.AUTO_SORT_ENABLED, true);
        }
        return stack;
    }

    private static ItemStack cell(String expression) {
        var stack = BulkStorageMod.CELLS.get(CellTier.QUANTUM).get().getDefaultInstance();
        stack.set(BulkStorageMod.FILTER, new FilterConfig(expression, false));
        return stack;
    }
}
