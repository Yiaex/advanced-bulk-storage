package dev.advancedbulkstorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.mojang.logging.LogUtils;
import dev.advancedbulkstorage.device.BulkMigratorEntity;
import dev.advancedbulkstorage.device.MigratorTier;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.migration.BulkScheduler;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.storage.CellTransfer;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BulkStorageMod.ID)
@PrefixGameTestTemplate(false)
public final class BulkMigratorTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey GOLD = AEItemKey.of(Items.GOLD_INGOT);
    private static final IActionSource SOURCE = IActionSource.empty();

    @GameTest(template = "empty")
    public static void schedulerSharesBudgetSleepsAndRecovers(GameTestHelper test) {
        var scheduler = new BulkScheduler();
        var highRuns = new AtomicInteger();
        var regularRuns = new AtomicInteger();
        BulkScheduler.Job priority = allowance -> {
            if (allowance.takeKey()) {
                highRuns.incrementAndGet();
            }
            return true;
        };
        BulkScheduler.Job regular = allowance -> {
            if (allowance.takeKey()) {
                regularRuns.incrementAndGet();
            }
            return false;
        };
        scheduler.wake(priority, true);
        scheduler.wake(priority, true);
        scheduler.wake(regular, false);
        test.assertTrue(scheduler.status().queued() == 2, "Repeated wakeups duplicated a job");
        scheduler.advance(55, 100_000_000, 1, true);
        test.assertTrue(scheduler.status().bandPercent() == 0 && highRuns.get() == 0 && regularRuns.get() == 0,
                "Scheduler ran nonessential work past the MSPT cutoff");
        for (int i = 0; i < 50; i++) {
            scheduler.advance(20, 100_000_000, 1, true);
            test.assertTrue(scheduler.status().keys() <= 1, "Jobs did not share the same key ceiling");
        }
        test.assertTrue(scheduler.status().bandPercent() == 100 && highRuns.get() > 1 && regularRuns.get() == 1,
                "Fair scheduling, sleep or automatic recovery failed");
        scheduler.cancel(priority);
        test.assertTrue(scheduler.status().queued() == 0, "Cancelled job was still queued");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void localTransactionsRespectLimitsAndPreserveLegacyStock(GameTestHelper test) {
        for (long count : new long[]{1, 64, 1_000_000, 1_000_000_000, Integer.MAX_VALUE}) {
            var source = cell(CellTier.QUANTUM, count + 2, 0);
            var target = cell(CellTier.QUANTUM, 0, 0);
            var from = StorageCells.getCellInventory(source, null);
            var to = StorageCells.getCellInventory(target, null);
            source.set(BulkStorageMod.FILTER, new FilterConfig("*", false));
            var pair = CellTransfer.begin(source, from, target, to);
            test.assertTrue(pair != null && pair.move(IRON, count) == count && pair.commit((left, right) -> {}),
                    "Direct transaction failed for " + count);
            test.assertTrue(from.getAvailableStacks().get(IRON) == 2 && to.getAvailableStacks().get(IRON) == count,
                    "Transaction did not conserve " + count);
            test.assertTrue(CellTransfer.begin(source, from, source, from) == null, "Self transfer did not fail closed");
        }
        var source = cell(CellTier.QUANTUM, 64, 0);
        var nearFull = cell(CellTier.QUANTUM, Long.MAX_VALUE - 5, 0);
        var from = StorageCells.getCellInventory(source, null);
        var to = (BulkCellInventory) StorageCells.getCellInventory(nearFull, null);
        var pair = CellTransfer.begin(source, from, nearFull, to);
        test.assertTrue(pair.move(IRON, 1_000_000_000L) == 64 && pair.move(IRON, 100) == 0, "Transfer truncated at LONG");
        test.assertTrue(pair.commit((left, right) -> {}) && from.getAvailableStacks().get(IRON) == 0
                && to.getAvailableStacks().get(IRON) == Long.MAX_VALUE
                && to.amountExact(IRON).equals(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(59))),
                "Direct transfer lost the units above LONG");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void ae2AndMegaUseStagedOwnedCells(GameTestHelper test) {
        var fromStack = AEItems.ITEM_CELL_1K.stack();
        var from = StorageCells.getCellInventory(fromStack, null);
        from.insert(IRON, 125, Actionable.MODULATE, SOURCE);
        from.insert(GOLD, 64, Actionable.MODULATE, SOURCE);
        var mega = gripe._90.megacells.definition.MEGAItems.BULK_ITEM_CELL.stack();
        ((gripe._90.megacells.item.cell.BulkCellItem) mega.getItem()).getConfigInventory(mega)
                .setStack(0, new appeng.api.stacks.GenericStack(IRON, 1));
        var to = StorageCells.getCellInventory(mega, null);
        var staging = CellTransfer.begin(fromStack, from, mega, to);
        test.assertTrue(staging != null && staging.move(IRON, 100) == 100 && staging.move(GOLD, 64) == 0,
                "Native or MEGA target rules did not work in the staged pair");
        test.assertTrue(from.getAvailableStacks().get(IRON) == 125 && to.getAvailableStacks().get(IRON) == 0,
                "Staging mutated an attached original");
        test.assertTrue(staging.commit((source, target) -> {
            var sourceCell = StorageCells.getCellInventory(source, null);
            var targetCell = StorageCells.getCellInventory(target, null);
            test.assertTrue(sourceCell.getAvailableStacks().get(IRON) == 25 && sourceCell.getAvailableStacks().get(GOLD) == 64
                    && targetCell.getAvailableStacks().get(IRON) == 100, "Persisted pair lost its stock");
        }), "Staged pair was never installed");
        test.succeed();
    }

    @GameTest(template = "empty", batch = "abs_migrator", timeoutTicks = 300)
    public static void quantumMachineMovesIntMaxInOneScheduledTick(GameTestHelper test) {
        var p = new BlockPos(2, 2, 2);
        test.setBlock(p, BulkStorageMod.MIGRATORS.get(MigratorTier.QUANTUM).get());
        test.setBlock(p.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        BulkMigratorEntity entity = test.getBlockEntity(p);
        var source = cell(CellTier.QUANTUM, 3_000_000_000L, 64);
        var target = cell(CellTier.QUANTUM, 0, 0);
        entity.getInternalInventory().setItemDirect(0, source);
        entity.getInternalInventory().setItemDirect(1, target);
        for (int slot = 0; slot < 7; slot++) {
            entity.upgrades().setItemDirect(slot, AEItems.SPEED_CARD.stack());
        }
        test.assertTrue(MigratorTier.STANDARD.rate(4) == 1_048_576 && MigratorTier.OVERCLOCKED.rate(4) == 16_777_216
                && entity.rate() == Integer.MAX_VALUE
                && entity.setFilter("*") != null && entity.setFilter("minecraft:iron_ingot") == null,
                "A tier rate, accelerator or whitelist was wrong");
        for (var tier : MigratorTier.values()) {
            test.assertTrue(test.getLevel().getRecipeManager().byKey(BulkStorageMod.id(tier.id)).isPresent(),
                    "Migrator recipe missing");
        }
        test.startSequence().thenWaitUntil(() -> test.assertTrue(entity.getMainNode().isOnline(), "Migrator is offline"))
                .thenExecute(entity::toggle)
                .thenWaitUntil(() -> test.assertTrue(entity.totalMoved() > 0, "Scheduler has not run a migration"))
                .thenExecute(() -> {
                    long moved = entity.totalMoved();
                    var result = entity.getInternalInventory().getStackInSlot(1);
                    var left = entity.getInternalInventory().getStackInSlot(0);
                    test.assertTrue(moved == Integer.MAX_VALUE && itemAmount(result, IRON) == moved
                            && itemAmount(left, IRON) == 3_000_000_000L - moved && itemAmount(left, GOLD) == 64,
                            "Quantum job did not move exactly INT_MAX in its first tick");
                    LogUtils.getLogger().info("ABS quantum scheduled first tick: moved={}, source remaining={}",
                            moved, itemAmount(left, IRON));
                    entity.toggle();
                    test.setBlock(p.east(), Blocks.AIR);
                })
                .thenIdle(4)
                .thenExecute(() -> test.assertTrue(entity.totalMoved() == Integer.MAX_VALUE,
                        "Paused or unpowered unit continued to move inventory"))
                .thenSucceed();
    }

    private static long itemAmount(ItemStack stack, AEItemKey key) {
        return StorageCells.getCellInventory(stack, null).getAvailableStacks().get(key);
    }

    private static ItemStack cell(CellTier tier, long iron, long gold) {
        var stack = BulkStorageMod.CELLS.get(tier).get().getDefaultInstance();
        stack.set(BulkStorageMod.FILTER, new FilterConfig("#c:ingots", false));
        var inventory = StorageCells.getCellInventory(stack, () -> {});
        inventory.insert(IRON, iron, Actionable.MODULATE, SOURCE);
        inventory.insert(GOLD, gold, Actionable.MODULATE, SOURCE);
        inventory.persist();
        return stack;
    }
}
