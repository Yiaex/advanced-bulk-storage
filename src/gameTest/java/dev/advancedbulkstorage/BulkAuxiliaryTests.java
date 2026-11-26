package dev.advancedbulkstorage;

import appeng.api.config.Actionable;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEBlocks;
import com.mojang.logging.LogUtils;
import dev.advancedbulkstorage.device.BulkDriveEntity;
import dev.advancedbulkstorage.device.BulkInterfaceEntity;
import dev.advancedbulkstorage.device.BulkItemAccess;
import dev.advancedbulkstorage.device.BulkManagerEntity;
import dev.advancedbulkstorage.device.BulkMonitorEntity;
import dev.advancedbulkstorage.device.BulkWorkbenchEntity;
import dev.advancedbulkstorage.filter.FilterConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BulkStorageMod.ID)
@PrefixGameTestTemplate(false)
public final class BulkAuxiliaryTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final IActionSource SOURCE = IActionSource.empty();

    @GameTest(template = "empty", batch = "abs_migrator", timeoutTicks = 200)
    public static void denseDriveManagerInterfaceAndWatcherShareActualStock(GameTestHelper test) {
        var pos = new BlockPos(2, 2, 2);
        test.setBlock(pos, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(pos.west(), BulkStorageMod.MONITOR.get());
        test.setBlock(pos.north(), BulkStorageMod.MANAGER.get());
        test.setBlock(pos.south(), BulkStorageMod.BASIC_INTERFACE.get());
        BulkDriveEntity drive = test.getBlockEntity(pos);
        BulkManagerEntity manager = test.getBlockEntity(pos.north());
        BulkMonitorEntity monitor = test.getBlockEntity(pos.west());
        BulkInterfaceEntity access = test.getBlockEntity(pos.south());
        for (int slot = 0; slot < BulkDriveEntity.SLOTS; slot++) {
            drive.getInternalInventory().setItemDirect(slot, stock(slot + 1));
        }
        drive.setPriority(130);
        var last = drive.getInternalInventory().getStackInSlot(BulkDriveEntity.SLOTS - 1);
        test.startSequence()
                .thenWaitUntil(() -> test.assertTrue(manager.index() != null
                        && manager.index().available().size() == 32 && access.index() == manager.index()
                        && monitor.getMainNode().isOnline(), "Indexed 32-cell grid failed to come online"))
                .thenExecute(() -> {
                    var id = last.get(BulkStorageMod.CELL_ID);
                    test.assertTrue(id != null && access.bind(id), "Interface did not bind a slot on the new drive");
                    monitor.applyFilter(new FilterConfig("#c:ingots", false), null);
                    monitor.setThreshold(528);
                })
                .thenWaitUntil(() -> test.assertTrue(monitor.count() == 528 && monitor.signal(),
                        "Monitor failed to observe actual drive stock or threshold: " + monitor.count() + ", powered=" + monitor.signal()))
                .thenExecute(() -> {
                    var port = test.getLevel().getCapability(BulkItemAccess.CAPABILITY, test.absolutePos(pos.south()),
                            Direction.SOUTH);
                    test.assertTrue(port != null && port.count(IRON) == 32 && port.extract(IRON, 1, Actionable.MODULATE) == 1,
                            "Interface extraction from slot 32 did not reach the real inventory");
                    LogUtils.getLogger().info("ABS auxiliary integration: indexed 32 live cells, initial=528, extracted=1");
                    var data = drive.saveWithoutMetadata(test.getLevel().registryAccess());
                    var rebuilt = new BulkDriveEntity(drive.getBlockPos(), drive.getBlockState());
                    rebuilt.loadWithComponents(data, test.getLevel().registryAccess());
                    var savedLast = rebuilt.getInternalInventory().getStackInSlot(31);
                    test.assertTrue(rebuilt.priority() == 130 && savedLast.get(BulkStorageMod.CELL_ID).equals(last.get(BulkStorageMod.CELL_ID))
                            && StorageCells.getCellInventory(savedLast, null).getAvailableStacks().get(IRON) == 31,
                            "Dense drive save failed to retain slot 32, its UUID or priority");
                })
                .thenWaitUntil(() -> test.assertTrue(monitor.count() == 527 && !monitor.signal(),
                        "Storage watcher did not update quantity and redstone after withdrawal: " + monitor.count()))
                .thenExecute(() -> {
                    var absolute = test.absolutePos(pos);
                    test.getLevel().destroyBlock(absolute, true);
                    var dropped = test.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                            new net.minecraft.world.phys.AABB(absolute).inflate(2));
                    var byId = new java.util.HashSet<java.util.UUID>();
                    long actual = 0;
                    for (var entity : dropped) {
                        var stack = entity.getItem();
                        var id = stack.get(BulkStorageMod.CELL_ID);
                        if (id != null && stack.getItem() instanceof dev.advancedbulkstorage.storage.BulkCellItem) {
                            test.assertTrue(byId.add(id), "Drive dropped duplicate cell UUID");
                            actual += StorageCells.getCellInventory(stack, null).getAvailableStacks().get(IRON);
                        }
                    }
                    test.assertTrue(byId.size() == 32 && actual == 527, "Breaking the drive lost a cell or its stock");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void advancedWorkbenchKeepsUuidStockAndSharedFilter(GameTestHelper test) {
        var pos = new BlockPos(2, 2, 2);
        test.setBlock(pos, BulkStorageMod.WORKBENCH.get());
        BulkWorkbenchEntity workbench = test.getBlockEntity(pos);
        var cell = stock(64_000_000_000L);
        workbench.getSubInventory(ISegmentedInventory.CELLS).setItemDirect(0, cell);
        var id = cell.get(BulkStorageMod.CELL_ID);
        workbench.applyFilter(new FilterConfig("minecraft:gold_ingot", false), null);
        test.assertTrue(workbench.filterStack() == cell && id.equals(cell.get(BulkStorageMod.CELL_ID))
                && workbench.filterConfig().expression().equals("minecraft:gold_ingot")
                && StorageCells.getCellInventory(cell, null).getAvailableStacks().get(IRON) == 64_000_000_000L,
                "Shared workbench filter modified the real inventory or UUID");
        for (var name : java.util.List.of("advanced_cell_workbench", "bulk_storage_drive", "bulk_storage_manager", "bulk_storage_monitor")) {
            test.assertTrue(test.getLevel().getRecipeManager().byKey(BulkStorageMod.id(name)).isPresent(),
                    "Auxiliary recipe missing " + name);
        }
        test.succeed();
    }

    private static ItemStack stock(long amount) {
        var stack = BulkStorageMod.CELLS.get(CellTier.QUANTUM).get().getDefaultInstance();
        stack.set(BulkStorageMod.FILTER, new FilterConfig("#c:ingots", false));
        var inventory = StorageCells.getCellInventory(stack, () -> {});
        inventory.insert(IRON, amount, Actionable.MODULATE, SOURCE);
        inventory.persist();
        return stack;
    }
}
