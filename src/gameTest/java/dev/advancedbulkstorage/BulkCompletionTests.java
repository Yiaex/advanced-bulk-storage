package dev.advancedbulkstorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.api.storage.StorageCells;
import com.mojang.authlib.GameProfile;
import com.google.gson.Gson;
import dev.advancedbulkstorage.device.BulkDriveEntity;
import dev.advancedbulkstorage.device.BulkInterfaceEntity;
import dev.advancedbulkstorage.device.BulkMigratorEntity;
import dev.advancedbulkstorage.device.BulkManagerEntity;
import dev.advancedbulkstorage.device.BulkMonitorEntity;
import dev.advancedbulkstorage.device.MigratorTier;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.storage.AutoSortTier;
import dev.advancedbulkstorage.storage.BulkCellIndex;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.storage.BulkCellItem;
import dev.advancedbulkstorage.storage.CellTransfer;
import dev.advancedbulkstorage.menu.BulkManagerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BulkStorageMod.ID)
@PrefixGameTestTemplate(false)
public final class BulkCompletionTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey NUGGET = AEItemKey.of(Items.IRON_NUGGET);
    private static final AEItemKey IRON_BLOCK = AEItemKey.of(Items.IRON_BLOCK);
    private static final AEItemKey DIAMOND = AEItemKey.of(Items.DIAMOND);
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final Actionable REAL = Actionable.MODULATE;
    private static final Actionable SIM = Actionable.SIMULATE;

    @GameTest(template = "empty", batch = "abs_migrator", timeoutTicks = 300)
    public static void selectedMountedRouteConservesCompressedUnits(GameTestHelper test) {
        var center = new BlockPos(2, 2, 2);
        var direction = inChunk(test, center);
        var from = center.relative(direction);
        var to = center.relative(direction, 2);
        test.setBlock(center, BulkStorageMod.MIGRATORS.get(MigratorTier.QUANTUM).get());
        test.setBlock(center.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(from, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(to, BulkStorageMod.BULK_DRIVE.get());
        BulkMigratorEntity device = test.getBlockEntity(center);
        BulkDriveEntity source = test.getBlockEntity(from);
        BulkDriveEntity target = test.getBlockEntity(to);
        source.getInternalInventory().setItemDirect(0, disk("Origin"));
        target.getInternalInventory().setItemDirect(0, disk("Destination", BulkStorageMod.MULTI_COMPRESSION_CARD.get().getDefaultInstance()));
        long initial = 16_777_217L;
        test.startSequence().thenWaitUntil(() -> {
            test.assertTrue(device.getMainNode().isOnline() && source.isPowered() && target.isPowered(), "Migration grid not ready");
        }).thenExecute(() -> {
            test.assertTrue(actual(source, 0).insert(IRON, initial, REAL, SOURCE) == initial, "Seed rejected");
            for (int i = 0; i < 3; ++i) device.cycleScope();
            select(device, true, "Origin");
            select(device, false, "Destination");
            test.assertTrue(device.scope() == BulkMigratorEntity.Scope.GRID_PAIR
                    && device.endpointLabel(true).startsWith("Origin") && device.endpointLabel(false).startsWith("Destination"),
                    "The migrator did not resolve the named mounted disks");
            device.toggle();
        }).thenWaitUntil(() -> {
            test.assertTrue(device.totalMoved() == initial && actual(source, 0).amount(IRON) == 0
                            && actual(target, 0).amount(NUGGET) == initial * 9,
                    "Mounted cross-drive conversion lost or duplicated resources");
        }).thenExecute(() -> {
            var level = test.getLevel();
            var inventory = target.getInternalInventory().getStackInSlot(0);
            var restored = ItemStack.parse(level.registryAccess(), inventory.save(level.registryAccess())).orElseThrow();
            var reloaded = (BulkCellInventory) appeng.api.storage.StorageCells.getCellInventory(restored, null);
            test.assertTrue(reloaded.amount(NUGGET) == initial * 9 && reloaded.id().equals(actual(target, 0).id()),
                    "Mounted transaction stock or identity failed after serialization");
            test.assertTrue(!BulkCellIndex.sameSaveChunk(level, test.absolutePos(from), level, test.absolutePos(to).east(16)),
                    "Independent chunk snapshots were considered atomic");
            device.cycleScope();
            test.assertTrue(!device.configured() && device.status() == BulkMigratorEntity.Status.PAUSED,
                    "Switching scope started an unconfirmed route");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "abs_migrator", timeoutTicks = 300)
    public static void autoSortingRespectsPriorityAndSharesCompressedInventory(GameTestHelper test) {
        var first = new BlockPos(2, 2, 2);
        var direction = inChunk(test, first);
        var second = first.relative(direction);
        test.setBlock(first, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(second, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(first.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        BulkDriveEntity origin = test.getBlockEntity(first);
        BulkDriveEntity target = test.getBlockEntity(second);
        var source = disk("Lower priority");
        var receiving = disk("Higher priority", BulkStorageMod.SORTING_CARDS.get(AutoSortTier.QUANTUM).get().getDefaultInstance(),
                BulkStorageMod.MULTI_COMPRESSION_CARD.get().getDefaultInstance());
        origin.getInternalInventory().setItemDirect(0, source);
        target.getInternalInventory().setItemDirect(0, receiving);
        test.startSequence().thenWaitUntil(() -> {
            test.assertTrue(origin.isPowered() && target.isPowered(), "Sorting grid not ready");
        }).thenExecute(() -> {
            target.setPriority(20);
            test.assertTrue(actual(origin, 0).insert(IRON, 9000, REAL, SOURCE) == 9000, "Could not seed the origin");
            var mounted = target.getMainNode().getGrid().getService(BulkCellIndex.class).find(actual(target, 0).id());
            test.assertTrue(mounted != null && !mounted.inventory().configure("filter", "*")
                    && mounted.inventory().configure("enable_sort", ""), "Managed configuration did not honor whitelist/card rules");
            target.getMainNode().getGrid().getService(BulkCellIndex.class).configurationChanged(mounted);
        }).thenWaitUntil(() -> {
            test.assertTrue(actual(origin, 0).amount(IRON) == 0 && actual(target, 0).amount(NUGGET) == 81_000
                    && actual(target, 0).recentSortRate(test.getLevel().getGameTime()) == 9000,
                    "Cross-drive sorting or its measured speed is incorrect");
        }).thenIdle(6).thenExecute(() -> {
            test.assertTrue(actual(origin, 0).amount(IRON) == 0 && actual(target, 0).amount(NUGGET) == 81_000,
                    "Compression stock bounced between drives");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "abs_migrator", timeoutTicks = 300)
    public static void sleepingSorterWakesForNewCompressedForms(GameTestHelper test) {
        var pos = new BlockPos(2, 2, 2);
        test.setBlock(pos, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(pos.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        BulkDriveEntity drive = test.getBlockEntity(pos);
        var source = disk("Pool", BulkStorageMod.MULTI_COMPRESSION_CARD.get().getDefaultInstance());
        var target = disk("Blocks only", BulkStorageMod.SORTING_CARDS.get(AutoSortTier.QUANTUM).get().getDefaultInstance());
        target.set(BulkStorageMod.FILTER, new FilterConfig("minecraft:iron_block", false));
        target.set(BulkStorageMod.AUTO_SORT_ENABLED, true);
        drive.getInternalInventory().setItemDirect(0, source);
        drive.getInternalInventory().setItemDirect(1, target);
        test.startSequence().thenWaitUntil(() -> test.assertTrue(drive.isPowered(), "Drive not ready")).thenIdle(16)
                .thenExecute(() -> test.assertTrue(actual(drive, 0).insert(IRON, 18, REAL, SOURCE) == 18,
                        "Could not seed a new compression pool"))
                .thenWaitUntil(() -> test.assertTrue(actual(drive, 1).amount(IRON_BLOCK) == 2
                        && actual(drive, 0).amount(NUGGET) == 0, "A sleeping sorter ignored the new block representation"))
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void compressionObserversSeeBothEndsOfCommittedStock(GameTestHelper test) {
        var sourceStack = disk("Source", BulkStorageMod.MULTI_COMPRESSION_CARD.get().getDefaultInstance());
        var targetStack = disk("Target", BulkStorageMod.MULTI_COMPRESSION_CARD.get().getDefaultInstance());
        var source = (BulkCellInventory) StorageCells.getCellInventory(sourceStack, null);
        var target = (BulkCellInventory) StorageCells.getCellInventory(targetStack, null);
        test.assertTrue(source.insert(IRON, 36, REAL, SOURCE) == 36, "Could not seed source");
        class Observer implements BulkCellInventory.StockListener {
            long observed;

            @Override public void changed(AEItemKey key, long delta) {
                observed = target.amount(NUGGET);
                targetStack.set(BulkStorageMod.FILTER, new FilterConfig("minecraft:diamond", false));
                source.removeStockMonitor(this);
            }
        }
        var observer = new Observer();
        source.addStockMonitor(observer);
        var transfer = CellTransfer.beginOwned(source, target);
        test.assertTrue(transfer != null && transfer.move(IRON_BLOCK, 2) == 2 && transfer.commit((a, b) -> {}),
                "Couldn't commit compressed move");
        test.assertTrue(observer.observed == 162 && source.amount(NUGGET) == 162 && target.amount(NUGGET) == 162
                && target.insert(IRON_BLOCK, 1, SIM, SOURCE) == 0, "An observer ran inside a partially applied move");
        test.succeed();
    }

    @GameTest(template = "empty", batch = "abs_duplicate_cross", timeoutTicks = 300)
    public static void removedDuplicateInAnotherChunkWakesTheOriginalSorter(GameTestHelper test) {
        var level = test.getLevel();
        var nearby = test.absolutePos(new BlockPos(2, 2, 2)).south(64);
        var originalPos = new BlockPos((nearby.getX() & ~15) + 14, nearby.getY(), nearby.getZ());
        var duplicatePos = originalPos.east(2);
        var nearChunk = new net.minecraft.world.level.ChunkPos(originalPos);
        var farChunk = new net.minecraft.world.level.ChunkPos(duplicatePos);
        level.setChunkForced(nearChunk.x, nearChunk.z, true);
        level.setChunkForced(farChunk.x, farChunk.z, true);
        level.getChunk(nearChunk.x, nearChunk.z);
        level.getChunk(farChunk.x, farChunk.z);
        var destination = disk("Primary", BulkStorageMod.SORTING_CARDS.get(AutoSortTier.QUANTUM).get().getDefaultInstance());
        destination.set(BulkStorageMod.AUTO_SORT_ENABLED, true);
        var id = destination.get(BulkStorageMod.CELL_ID);
        var drives = new BulkDriveEntity[2];
        test.startSequence().thenWaitUntil(() -> test.assertTrue(level.isPositionEntityTicking(originalPos)
                        && level.isPositionEntityTicking(duplicatePos), "Forced chunks are not ticking yet"))
                .thenExecute(() -> {
                    level.setBlockAndUpdate(originalPos, BulkStorageMod.BULK_DRIVE.get().defaultBlockState());
                    level.setBlockAndUpdate(originalPos.east(), BulkStorageMod.BULK_DRIVE.get().defaultBlockState());
                    level.setBlockAndUpdate(duplicatePos, BulkStorageMod.BULK_DRIVE.get().defaultBlockState());
                    level.setBlockAndUpdate(originalPos.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    drives[0] = (BulkDriveEntity) level.getBlockEntity(originalPos);
                    drives[1] = (BulkDriveEntity) level.getBlockEntity(duplicatePos);
                    drives[0].getInternalInventory().setItemDirect(0, disk("Source"));
                    drives[0].getInternalInventory().setItemDirect(1, destination);
                    drives[1].getInternalInventory().setItemDirect(0, destination.copy());
                })
                .thenWaitUntil(() -> test.assertTrue(drives[0].isPowered() && drives[1].isPowered()
                        && drives[0].getMainNode().getGrid() == drives[1].getMainNode().getGrid(), "Cross-chunk grid not ready"))
                .thenExecute(() -> test.assertTrue(actual(drives[0], 0).insert(IRON, 42, REAL, SOURCE) == 42,
                        "Could not seed stock"))
                .thenIdle(12).thenExecute(() -> {
                    var original = drives[0];
                    test.assertTrue(original.getMainNode().getGrid().getService(BulkCellIndex.class).find(id) == null
                            && actual(original, 0).amount(IRON) == 42 && actual(original, 1).amount(IRON) == 0,
                            "Duplicate UUID did not suspend sorting");
                    drives[1].getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
                }).thenWaitUntil(() -> test.assertTrue(actual(drives[0], 0).amount(IRON) == 0
                        && actual(drives[0], 1).amount(IRON) == 42, "Removing the duplicate did not wake the original sorter"))
                .thenExecute(() -> {
                    level.setChunkForced(nearChunk.x, nearChunk.z, false);
                    level.setChunkForced(farChunk.x, farChunk.z, false);
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "abs_migrator", timeoutTicks = 300)
    public static void gridImportMovesFromARealAe2Drive(GameTestHelper test) {
        var center = new BlockPos(2, 2, 2);
        var adjacent = center.relative(inChunk(test, center));
        test.setBlock(center, BulkStorageMod.MIGRATORS.get(MigratorTier.STANDARD).get());
        test.setBlock(center.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(adjacent, AEBlocks.DRIVE.block());
        BulkMigratorEntity device = test.getBlockEntity(center);
        DriveBlockEntity drive = test.getBlockEntity(adjacent);
        var nativeDisk = AEItems.ITEM_CELL_1K.stack();
        var nativeStock = StorageCells.getCellInventory(nativeDisk, () -> {});
        test.assertTrue(nativeStock.insert(IRON, 240, REAL, SOURCE) == 240, "Native stock could not be seeded");
        nativeStock.persist();
        drive.getInternalInventory().setItemDirect(0, nativeDisk);
        device.getInternalInventory().setItemDirect(1, disk("Local target"));
        test.startSequence().thenWaitUntil(() -> {
            test.assertTrue(device.getMainNode().isOnline() && drive.getMainNode().isOnline(), "Native drive not ready");
        }).thenExecute(() -> {
            device.cycleScope();
            device.cycleScope();
            device.toggle();
        }).thenWaitUntil(() -> {
            var local = (BulkCellInventory) StorageCells.getCellInventory(device.getInternalInventory().getStackInSlot(1), null);
            test.assertTrue(device.totalMoved() == 240 && local.amount(IRON) == 240
                    && drive.getOriginalCellInventory(0).extract(IRON, Long.MAX_VALUE, SIM, SOURCE) == 0,
                    "Grid import did not commit both physical endpoints");
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void limitOverflowAccountsForStoredDiscardedAndRejectedStock(GameTestHelper test) {
        var center = new BlockPos(2, 2, 2);
        test.setBlock(center, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(center.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(center.east(), BulkStorageMod.ADVANCED_INTERFACE.get());
        BulkDriveEntity drive = test.getBlockEntity(center);
        BulkInterfaceEntity host = test.getBlockEntity(center.east());
        var first = disk("Overflow", BulkStorageMod.STORAGE_LIMIT_CARD.get().getDefaultInstance());
        var second = disk("Reserve", BulkStorageMod.STORAGE_LIMIT_CARD.get().getDefaultInstance());
        first.set(BulkStorageMod.LIMITS, "100 each !void");
        second.set(BulkStorageMod.LIMITS, "2 each");
        drive.getInternalInventory().setItemDirect(0, first);
        drive.getInternalInventory().setItemDirect(1, second);
        test.startSequence().thenWaitUntil(() -> {
            test.assertTrue(host.index() != null && drive.isPowered(), "Interface grid not ready");
        }).thenExecute(() -> {
            var one = actual(drive, 0);
            var two = actual(drive, 1);
            test.assertTrue(host.bind(one.id()) && host.bind(two.id()), "Could not bind overflow pair");
            var port = host.bulkHandler(Direction.EAST);
            var handler = host.itemHandler(Direction.EAST);
            var stack = IRON.toStack(64);
            test.assertTrue(handler.insertItem(0, stack, true).isEmpty() && one.amount(IRON) == 0
                    && handler.insertItem(-1, stack, false) == stack, "Item handler changed stock during validation");
            test.assertTrue(handler.insertItem(0, stack, false).isEmpty() && one.amount(IRON) == 64
                    && two.amount(IRON) == 0 && one.discardedItems() == 0
                    && port.extract(IRON, 64, REAL) == 64, "Item handler did not store the entire stack in the first binding");
            var simulation = port.insertWithOverflow(IRON, 110, SIM);
            test.assertTrue(simulation.stored() == 102 && simulation.discarded() == 8 && one.discardedItems() == 0
                    && one.amount(IRON) == 0 && two.amount(IRON) == 0, "Simulation changed stock or disposal telemetry");
            var inserted = port.insertWithOverflow(IRON, 110, REAL);
            test.assertTrue(inserted.equals(simulation) && one.amount(IRON) == 100 && two.amount(IRON) == 2
                    && one.discardedItems() == 8 && two.discardedItems() == 0, "Overflow ran before every available real store");
            test.assertTrue(one.insert(IRON, 1, REAL, SOURCE) == 0 && port.insert(IRON, 1, REAL) == 0
                    && one.discardedItems() == 8, "Ordinary storage routing disposed of stock");
            first.set(BulkStorageMod.LIMITS, "100 each !void; 100 total");
            test.assertTrue(port.insertWithOverflow(IRON, 10, REAL).accepted() == 0 && one.atConfiguredLimit(),
                    "A conflicting reject-only limit did not prevent disposal");
            first.set(BulkStorageMod.FILTER, new FilterConfig("minecraft:iron_ingot", false));
            test.assertTrue(port.insertWithOverflow(DIAMOND, 10, REAL).discarded() == 0, "Whitelist was bypassed by disposal");
            var level = test.getLevel();
            var restored = ItemStack.parse(level.registryAccess(), first.save(level.registryAccess())).orElseThrow();
            test.assertTrue(restored.getOrDefault(BulkStorageMod.DISCARDED_ITEMS, 0L) == 8L,
                    "Disposal audit counter did not survive serialization");
            first.set(BulkStorageMod.LIMITS, "100 each !void");
            first.set(BulkStorageMod.DISCARDED_ITEMS, Long.MAX_VALUE - 2);
            test.assertTrue(!one.configure("filter", "@minecraft"), "A manager expanded a destructive filter without confirmation");
            test.assertTrue(port.insertWithOverflow(IRON, 10, SIM).discarded() == 2
                    && port.insertWithOverflow(IRON, 10, REAL).discarded() == 2
                    && one.discardedItems() == Long.MAX_VALUE
                    && port.insertWithOverflow(IRON, 10, REAL).accepted() == 0,
                    "Overflow consumed items that it could no longer log");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "abs_migrator", timeoutTicks = 300)
    public static void queuedManagerChangesSkipAReplacedPhysicalDisk(GameTestHelper test) {
        var pos = new BlockPos(2, 2, 2);
        test.setBlock(pos, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(pos.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(pos.east(), BulkStorageMod.MANAGER.get());
        BulkDriveEntity drive = test.getBlockEntity(pos);
        BulkManagerEntity manager = test.getBlockEntity(pos.east());
        var original = disk("Shown disk");
        var replacement = original.copy();
        drive.getInternalInventory().setItemDirect(0, original);
        var player = new ServerPlayer(test.getLevel().getServer(), test.getLevel(),
                new GameProfile(java.util.UUID.randomUUID(), "abs-menu-test"), ClientInformation.createDefault());
        var absolute = test.absolutePos(pos.east()).getCenter();
        player.moveTo(absolute.x, absolute.y + 1, absolute.z, 0, 0);
        test.startSequence().thenWaitUntil(() -> test.assertTrue(manager.index() != null && drive.isPowered(),
                "Manager grid not ready")).thenExecute(() -> {
            var menu = managerMenu(player, manager);
            player.containerMenu = menu;
            test.assertTrue(menu.view().rows().size() == 1, "Expected one target in the manager");
            var request = new BulkManagerMenu.Change("filter", "minecraft:iron_ingot", true, menu.viewToken);
            menu.receiveClientAction("change", new Gson().toJson(request));
            test.assertTrue(menu.operationStatus.equals("working"), "Manager did not queue the change");
            drive.getInternalInventory().setItemDirect(0, replacement);
        }).thenWaitUntil(() -> {
            test.assertTrue(player.containerMenu instanceof BulkManagerMenu menu && menu.operationStatus.equals("0/1"),
                    "Manager did not skip the replaced target");
        }).thenExecute(() -> {
            test.assertTrue(replacement.get(BulkStorageMod.FILTER).expression().equals("@minecraft"),
                    "A replacement with the same UUID received the queued change");
            var menu = (BulkManagerMenu) player.containerMenu;
            player.tickCount += 10;
            var request = new BulkManagerMenu.Change("filter", "minecraft:iron_ingot", true, menu.viewToken);
            menu.receiveClientAction("change", new Gson().toJson(request));
            test.assertTrue(menu.operationStatus.equals("working")
                    && actual(drive, 0).configure("filter", "minecraft:gold_ingot"), "Could not stage competing edit");
        }).thenWaitUntil(() -> {
            test.assertTrue(player.containerMenu instanceof BulkManagerMenu menu && menu.operationStatus.equals("0/1"),
                    "A queued batch overwrote a competing edit");
        }).thenExecute(() -> {
            test.assertTrue(replacement.get(BulkStorageMod.FILTER).expression().equals("minecraft:gold_ingot"),
                    "A changed configuration was overwritten");
            player.containerMenu.removed(player);
            player.containerMenu = player.inventoryMenu;
        }).thenSucceed();
    }

    private static BulkManagerMenu managerMenu(ServerPlayer player, BulkManagerEntity manager) {
        // Run the real menu handler without connecting a mock client that lacks other mods' channels.
        try {
            var factory = BulkManagerMenu.class.getDeclaredConstructor(MenuType.class, int.class, Inventory.class,
                    BulkManagerEntity.class);
            factory.setAccessible(true);
            return factory.newInstance(BulkStorageMod.MANAGER_MENU.get(), 1, player.getInventory(), manager);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not create a server-side manager menu for test", failure);
        }
    }

    @GameTest(template = "empty")
    public static void diskMonitorTracksPhysicalStockAndLimitAfterRemoval(GameTestHelper test) {
        var center = new BlockPos(2, 2, 2);
        test.setBlock(center, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(center.east(), BulkStorageMod.MONITOR.get());
        test.setBlock(center.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        BulkDriveEntity drive = test.getBlockEntity(center);
        BulkMonitorEntity monitor = test.getBlockEntity(center.east());
        var disk = disk("Monitored", BulkStorageMod.STORAGE_LIMIT_CARD.get().getDefaultInstance());
        disk.set(BulkStorageMod.LIMITS, "100 each");
        drive.getInternalInventory().setItemDirect(0, disk);
        test.startSequence().thenWaitUntil(() -> {
            test.assertTrue(drive.isPowered() && monitor.getMainNode().isOnline(), "Monitor grid not ready");
        }).thenExecute(() -> {
            monitor.cycleMetric();
            monitor.cycleTarget();
            monitor.setThreshold(50);
            test.assertTrue(monitor.reading() && monitor.count() == 0 && !monitor.signal(), "Initial disk reading was invalid");
            test.assertTrue(actual(drive, 0).insert(IRON, 100, REAL, SOURCE) == 100, "Could not seed monitored stock");
        }).thenWaitUntil(() -> {
            test.assertTrue(monitor.count() == 100 && monitor.signal(), "Incremental disk reading or output is incorrect");
        }).thenExecute(() -> {
            for (int i = 0; i < 4; ++i) monitor.cycleMetric();
            monitor.setThreshold(1);
            test.assertTrue(monitor.metric() == BulkMonitorEntity.Metric.LIMIT_REACHED && monitor.count() == 1 && monitor.signal(),
                    "Monitor did not report an active limit");
            drive.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
        }).thenWaitUntil(() -> {
            test.assertTrue(!monitor.reading() && !monitor.signal(), "An unmounted disk retained a redstone output");
        }).thenSucceed();
    }

    private static Direction inChunk(GameTestHelper test, BlockPos pos) {
        return (test.absolutePos(pos).getX() & 15) >= 14 ? Direction.WEST : Direction.EAST;
    }

    private static void select(BulkMigratorEntity device, boolean source, String name) {
        for (int i = 0; i < 2 && !device.endpointLabel(source).startsWith(name); ++i) device.cycleEndpoint(source);
    }

    private static BulkCellInventory actual(BulkDriveEntity drive, int slot) {
        return (BulkCellInventory) drive.getOriginalCellInventory(slot);
    }

    private static ItemStack disk(String name, ItemStack... cards) {
        var stack = BulkStorageMod.CELLS.get(CellTier.QUANTUM).get().getDefaultInstance();
        stack.set(BulkStorageMod.FILTER, new FilterConfig("@minecraft", false));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        var upgrades = ((BulkCellItem) stack.getItem()).getUpgrades(stack);
        for (int i = 0; i < cards.length; ++i) upgrades.setItemDirect(i, cards[i]);
        BulkCellItem.ensureId(stack);
        return stack;
    }
}
