package dev.advancedbulkstorage;

import appeng.api.config.Actionable;
import appeng.api.config.OperationMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.storage.IOPortBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.mojang.logging.LogUtils;
import dev.advancedbulkstorage.filter.FilterConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BulkStorageMod.ID)
@PrefixGameTestTemplate(false)
public final class BulkCellSafetyTests {
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final Actionable REAL = Actionable.MODULATE;

    @GameTest(template = "empty")
    public static void mixedCellNetworkCountsSaturate(GameTestHelper test) {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var pos = new BlockPos(2, 2, 2);
        var first = placeDrive(test, pos);
        var second = placeDrive(test, pos.east());
        test.setBlock(pos.east(2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var bulk = makeCell();
        var bulkStorage = StorageCells.getCellInventory(bulk, null);
        test.assertTrue(bulkStorage.insert(key, Long.MAX_VALUE, REAL, SOURCE) == Long.MAX_VALUE, "Bulk fixture insert failed");
        var vanilla = AEItems.ITEM_CELL_1K.stack();
        var aeStorage = StorageCells.getCellInventory(vanilla, null);
        test.assertTrue(aeStorage.insert(key, 1, REAL, SOURCE) == 1, "Native fixture insert failed");
        first.getInternalInventory().setItemDirect(0, bulk);
        second.getInternalInventory().setItemDirect(0, vanilla);
        test.startSequence().thenWaitUntil(() ->
                test.assertTrue(first.isPowered() && second.isPowered() && first.getMainNode().getGrid() == second.getMainNode().getGrid(), "Drives not on the same active grid"))
                .thenExecute(() -> {
                    var network = first.getMainNode().getGrid().getStorageService().getInventory();
                    first.setPriority(100);
                    second.setPriority(0);
                    long high = network.getAvailableStacks().get(key);
                    first.setPriority(0);
                    second.setPriority(100);
                    long low = network.getAvailableStacks().get(key);
                    long bulkAfter = first.getOriginalCellInventory(0).getAvailableStacks().get(key);
                    long aeAfter = second.getOriginalCellInventory(0).getAvailableStacks().get(key);
                    LogUtils.getLogger().info("ABS AUDIT mixed: bulkPriorityHigh={}, bulkPriorityLow={}, actualBulk={}, actualAE={}",
                            high, low, bulkAfter, aeAfter);
                    test.assertTrue(high == Long.MAX_VALUE && low == Long.MAX_VALUE && bulkAfter == Long.MAX_VALUE && aeAfter == 1,
                            "Mixed AE2 counts wrapped or changed physical stock");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void ioPortRefundPreservesOldStock(GameTestHelper test) {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var pos = new BlockPos(2, 2, 2);
        var drive = placeDrive(test, pos);
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        test.setBlock(pos.east(), AEBlocks.IO_PORT.block());
        IOPortBlockEntity port = test.getBlockEntity(pos.east());
        test.setBlock(pos.east(2), AEBlocks.ENERGY_CELL.block());
        EnergyCellBlockEntity energy = test.getBlockEntity(pos.east(2));
        energy.injectAEPower(1024, REAL);
        var source = makeCell();
        var inventory = StorageCells.getCellInventory(source, null);
        test.assertTrue(inventory.insert(key, 100, REAL, SOURCE) == 100, "Could not seed source cell");
        source.set(BulkStorageMod.FILTER, FilterConfig.EMPTY);
        test.startSequence().thenWaitUntil(() ->
                test.assertTrue(drive.isPowered() && port.getMainNode().isActive(), "I/O port grid not active"))
                .thenExecute(() -> {
                    energy.extractAEPower(1024, REAL, PowerMultiplier.ONE);
                    energy.injectAEPower(12, REAL);
                    port.getInternalInventory().setItemDirect(0, source);
                    port.tickingRequest(port.getMainNode().getNode(), 1);
                    long destination = drive.getOriginalCellInventory(0).getAvailableStacks().get(key);
                    long remaining = 0;
                    for (var stack : port.getInternalInventory()) {
                        var stored = StorageCells.getCellInventory(stack, null);
                        if (stored != null) {
                            remaining += stored.getAvailableStacks().get(key);
                        }
                    }
                    LogUtils.getLogger().info("ABS AUDIT I/O: start=100, destination={}, source={}, unaccounted={}",
                            destination, remaining, 100 - destination - remaining);
                    test.assertTrue(destination > 0 && destination < 100 && destination + remaining == 100,
                            "Stock missing after an energy-bounded partial transfer");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void ioPortFillPreservesNetworkStock(GameTestHelper test) {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var pos = new BlockPos(2, 2, 2);
        var drive = placeDrive(test, pos);
        var source = makeCell();
        var inventory = StorageCells.getCellInventory(source, null);
        test.assertTrue(inventory.insert(key, 100, REAL, SOURCE) == 100, "Could not seed source cell");
        source.set(BulkStorageMod.FILTER, FilterConfig.EMPTY);
        drive.getInternalInventory().setItemDirect(0, source);
        test.setBlock(pos.east(), AEBlocks.IO_PORT.block());
        IOPortBlockEntity port = test.getBlockEntity(pos.east());
        port.getConfigManager().putSetting(Settings.OPERATION_MODE, OperationMode.FILL);
        test.setBlock(pos.east(2), AEBlocks.ENERGY_CELL.block());
        EnergyCellBlockEntity energy = test.getBlockEntity(pos.east(2));
        energy.injectAEPower(1024, REAL);
        var destination = makeCell();
        test.startSequence().thenWaitUntil(() ->
                test.assertTrue(drive.isPowered() && port.getMainNode().isActive(), "I/O port grid not active"))
                .thenExecute(() -> {
                    energy.extractAEPower(1024, REAL, PowerMultiplier.ONE);
                    energy.injectAEPower(12, REAL);
                    port.getInternalInventory().setItemDirect(0, destination);
                    port.tickingRequest(port.getMainNode().getNode(), 1);
                    long remaining = drive.getOriginalCellInventory(0).getAvailableStacks().get(key);
                    long inserted = 0;
                    for (var stack : port.getInternalInventory()) {
                        var stored = StorageCells.getCellInventory(stack, null);
                        if (stored != null) {
                            inserted += stored.getAvailableStacks().get(key);
                        }
                    }
                    LogUtils.getLogger().info("ABS AUDIT I/O fill: start=100, destination={}, source={}, unaccounted={}",
                            inserted, remaining, 100 - inserted - remaining);
                    test.assertTrue(inserted > 0 && inserted < 100 && inserted + remaining == 100,
                            "Stock missing after filling a cell from an energy-limited network");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void independentHandlesShareInventory(GameTestHelper test) {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var stack = makeCell();
        var creator = StorageCells.getCellInventory(stack, null);
        test.assertTrue(creator.insert(key, 10, REAL, SOURCE) == 10, "Could not seed detached cell");
        var first = StorageCells.getCellInventory(stack, null);
        var second = StorageCells.getCellInventory(stack, null);
        long fromFirst = first.extract(key, 10, REAL, SOURCE);
        long fromSecond = second.extract(key, 10, REAL, SOURCE);
        LogUtils.getLogger().info("ABS AUDIT handles: initial=10, first={}, second={}", fromFirst, fromSecond);
        test.assertTrue(fromFirst == 10 && fromSecond == 0, "Two handles extracted the same physical inventory");
        test.succeed();
    }

    private static ItemStack makeCell() {
        var stack = BulkStorageMod.CELLS.get(CellTier.QUANTUM).get().getDefaultInstance();
        stack.set(BulkStorageMod.FILTER, new FilterConfig("#c:ingots", false));
        return stack;
    }

    private static DriveBlockEntity placeDrive(GameTestHelper test, BlockPos pos) {
        test.setBlock(pos, AEBlocks.DRIVE.block());
        return test.getBlockEntity(pos);
    }
}
