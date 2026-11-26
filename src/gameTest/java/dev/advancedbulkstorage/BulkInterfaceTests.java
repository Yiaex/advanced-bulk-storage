package dev.advancedbulkstorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import dev.advancedbulkstorage.device.BulkInterfaceEntity;
import dev.advancedbulkstorage.device.BulkDriveEntity;
import dev.advancedbulkstorage.device.BulkItemAccess;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.storage.BulkCellItem;
import dev.advancedbulkstorage.storage.BulkInventoryPreview;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BulkStorageMod.ID)
@PrefixGameTestTemplate(false)
public final class BulkInterfaceTests {
    private static final Actionable REAL = Actionable.MODULATE;
    private static final Actionable SIM = Actionable.SIMULATE;
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey GOLD = AEItemKey.of(Items.GOLD_INGOT);

    @GameTest(template = "empty")
    public static void interfacesBindActualCellsWithoutDuplication(GameTestHelper test) {
        var positions = new BlockPos(2, 2, 2);
        var basicPos = positions.west();
        var advancedPos = positions.south(); // The native drive does not expose a node on its north-facing front.
        test.setBlock(positions, AEBlocks.DRIVE.block());
        test.setBlock(positions.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(basicPos, BulkStorageMod.BASIC_INTERFACE.get());
        test.setBlock(advancedPos, BulkStorageMod.ADVANCED_INTERFACE.get());
        DriveBlockEntity drive = test.getBlockEntity(positions);
        BulkInterfaceEntity basic = test.getBlockEntity(basicPos);
        BulkInterfaceEntity advanced = test.getBlockEntity(advancedPos);
        var primary = cell(Long.MAX_VALUE - 5, 2);
        var secondary = cell(10, 4);
        drive.getInternalInventory().setItemDirect(0, primary);
        drive.getInternalInventory().setItemDirect(1, secondary);
        var firstId = primary.get(BulkStorageMod.CELL_ID);
        var secondId = secondary.get(BulkStorageMod.CELL_ID);

        test.startSequence().thenWaitUntil(() -> {
            test.assertTrue(basic.index() != null && advanced.index() != null, "Interface channel not ready");
            test.assertTrue(basic.index().find(firstId) != null && basic.index().find(secondId) != null,
                    "The native AE2 drive did not index both bulk cells");
        }).thenExecute(() -> {
            test.assertTrue(basic.bind(firstId) && !basic.bind(secondId) && advanced.bind(firstId)
                    && advanced.bind(secondId) && !advanced.bind(firstId), "Binding limits or duplicate protection failed");
            var level = test.getLevel();
            var basicPort = level.getCapability(BulkItemAccess.CAPABILITY, test.absolutePos(basicPos), Direction.WEST);
            var advancedPort = level.getCapability(BulkItemAccess.CAPABILITY, test.absolutePos(advancedPos), Direction.NORTH);
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, test.absolutePos(basicPos), Direction.WEST);
            test.assertTrue(basicPort != null && advancedPort != null && handler != null, "Capabilities not registered");
            test.assertTrue(advancedPort.count(IRON) == Integer.MAX_VALUE && basicPort.count(IRON) == Integer.MAX_VALUE
                    && advancedPort.count(GOLD) == 6, "Per-key INT saturation or aggregate count failed");
            test.assertTrue(basicPort.extract(IRON, Integer.MAX_VALUE, SIM) == Integer.MAX_VALUE
                    && actual(drive, 0).amount(IRON) == Long.MAX_VALUE - 5, "Simulation changed real stock");
            int visible = 0;
            for (int slot = 1; slot < handler.getSlots(); slot++) {
                var item = handler.getStackInSlot(slot);
                if (item.is(Items.IRON_INGOT)) {
                    test.assertTrue(item.getCount() == 64 && handler.extractItem(slot, 200, true).getCount() == 64,
                            "Forge item access used an oversized stack");
                    visible++;
                }
            }
            test.assertTrue(visible == 1, "Item handler projected the same key more than once");
            test.assertTrue(advanced.prioritize(secondId) && advancedPort.extract(IRON, 12, REAL) == 12
                    && actual(drive, 0).amount(IRON) == Long.MAX_VALUE - 7 && actual(drive, 1).amount(IRON) == 0,
                    "Prioritized batch withdrawal lost or duplicated stock");
            advanced.cycleMode(Direction.NORTH);
            test.assertTrue(advancedPort.extract(GOLD, 1, REAL) == 0, "Side insert-only mode allowed extraction");
            var otherSide = advanced.bulkHandler(Direction.EAST);
            test.assertTrue(otherSide.extract(GOLD, 1, REAL) == 1, "Side mode incorrectly applied to all directions");
            test.assertTrue(advanced.setFilter("*") != null && advanced.setFilter("minecraft:iron_ingot") == null
                    && advancedPort.insert(GOLD, 1, SIM) == 0 && advancedPort.count(GOLD) == 0,
                    "Secondary whitelist accepted a disallowed operation");
            advanced.cycleRedstone();
            test.assertTrue(advancedPort.count(IRON) == 0, "Redstone-gated port exposed inventory without a signal");
            var saved = advanced.saveWithoutMetadata(level.registryAccess());
            var reloaded = new BulkInterfaceEntity(advanced.getBlockPos(), advanced.getBlockState());
            reloaded.loadWithComponents(saved, level.registryAccess());
            test.assertTrue(reloaded.bindings().equals(java.util.List.of(secondId, firstId))
                    && reloaded.mode(Direction.NORTH) == BulkInterfaceEntity.Access.INSERT_ONLY
                    && reloaded.redstone() == BulkInterfaceEntity.Redstone.REQUIRE_SIGNAL
                    && reloaded.filter().expression().equals("minecraft:iron_ingot"),
                    "Binding priority, face, redstone or whitelist was lost on load");
            drive.getInternalInventory().setItemDirect(2, primary.copy());
            test.assertTrue(basic.index().find(firstId) == null && basicPort.count(IRON) == 0
                            && handler.getSlots() == 1 && basicPort.extract(IRON, 1, REAL) == 0,
                    "Duplicate UUID exposed real stock");
            drive.getInternalInventory().setItemDirect(2, ItemStack.EMPTY);
            test.assertTrue(basicPort.count(IRON) == Integer.MAX_VALUE, "Removing the duplicate did not resume access");
            drive.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
            test.assertTrue(basicPort.count(IRON) == 0 && basic.bindings().contains(firstId),
                    "Unmounting a disk did not suspend its binding");
            drive.getInternalInventory().setItemDirect(3, primary);
            test.assertTrue(basicPort.count(IRON) == Integer.MAX_VALUE
                    && actual(drive, 3).amount(IRON) == Long.MAX_VALUE - 7,
                    "A moved disk lost its UUID or real inventory");
            for (var id : java.util.List.of("bulk_storage_interface", "advanced_bulk_storage_interface")) {
                test.assertTrue(level.getRecipeManager().byKey(BulkStorageMod.id(id)).isPresent(), "Missing recipe " + id);
            }
            test.setBlock(positions.east(), net.minecraft.world.level.block.Blocks.AIR);
        }).thenWaitUntil(() -> {
            test.assertTrue(basic.index() == null && basic.bulkHandler(Direction.WEST).count(IRON) == 0,
                    "Interface retained access after the grid lost power");
            test.assertTrue(!basic.getBlockState().getValue(BlockStateProperties.LIT), "The status model did not turn off");
            test.assertTrue(actual(drive, 3).amount(IRON) == Long.MAX_VALUE - 7,
                    "Losing power changed the physical inventory");
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void interfaceSlotTracksStockChangesInSixteenthBinding(GameTestHelper test) {
        var drivePos = new BlockPos(2, 2, 2);
        var portPos = drivePos.south();
        test.setBlock(drivePos, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(drivePos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(portPos, BulkStorageMod.ADVANCED_INTERFACE.get());
        BulkDriveEntity drive = test.getBlockEntity(drivePos);
        BulkInterfaceEntity port = test.getBlockEntity(portPos);
        for (int slot = 0; slot < 16; slot++) {
            drive.getInternalInventory().setItemDirect(slot, cell(slot == 0 ? 3 : slot == 15 ? 5 : 0, 0));
        }

        test.startSequence().thenWaitUntil(() -> test.assertTrue(port.index() != null && drive.isPowered(),
                "Interface fixture is offline")).thenExecute(() -> {
            for (int slot = 0; slot < 16; slot++) {
                var stack = drive.getInternalInventory().getStackInSlot(slot);
                test.assertTrue(port.bind(stack.get(BulkStorageMod.CELL_ID)), "Could not bind slot " + slot);
            }
            var handler = port.itemHandler(Direction.SOUTH);
            var bulk = port.bulkHandler(Direction.SOUTH);
            test.assertTrue(handler.getSlots() == 2 && handler.getStackInSlot(1).getCount() == 8,
                    "Both ends of a 16-binding interface should contribute to the same slot");
            var first = (BulkCellInventory) drive.getOriginalCellInventory(0);
            var last = (BulkCellInventory) drive.getOriginalCellInventory(15);
            var source = IActionSource.empty();
            long moved = first.extract(IRON, 3, REAL, source);
            test.assertTrue(moved == 3 && last.insert(IRON, moved, REAL, source) == moved,
                    "Could not move stock between the bound cells");
            test.assertTrue(handler.getStackInSlot(1).getCount() == 8 && bulk.count(IRON) == 8,
                    "A previously visible slot lost stock after same-tick changes");
            moved = last.extract(IRON, 8, REAL, source);
            test.assertTrue(moved == 8 && first.insert(IRON, moved, REAL, source) == moved
                    && handler.getStackInSlot(1).getCount() == 8
                    && first.amount(IRON) == 8 && last.amount(IRON) == 0,
                    "A new slot contributor was missed or the move failed to conserve stock");
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void cachedInterfaceFollowsGridSplitAndMerge(GameTestHelper test) {
        var leftPos = new BlockPos(2, 2, 2);
        var bridge = leftPos.east();
        var rightPos = bridge.east();
        var portPos = leftPos.south();
        test.setBlock(leftPos, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(rightPos, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(leftPos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(bridge, AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(rightPos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(portPos, BulkStorageMod.ADVANCED_INTERFACE.get());
        BulkDriveEntity left = test.getBlockEntity(leftPos);
        BulkDriveEntity right = test.getBlockEntity(rightPos);
        BulkInterfaceEntity port = test.getBlockEntity(portPos);
        left.getInternalInventory().setItemDirect(0, cell(3, 0));
        right.getInternalInventory().setItemDirect(0, cell(5, 0));
        var id = right.getInternalInventory().getStackInSlot(0).get(BulkStorageMod.CELL_ID);

        test.startSequence().thenWaitUntil(() -> test.assertTrue(port.index() != null
                && port.index().find(id) != null, "Joined grid is not ready")).thenExecute(() -> {
            test.assertTrue(port.bind(id) && port.bulkHandler(Direction.SOUTH).count(IRON) == 5,
                    "Could not prime the interface from the remote cell");
            test.setBlock(bridge, Blocks.AIR);
        }).thenWaitUntil(() -> test.assertTrue(left.isPowered() && right.isPowered() && port.index() != null
                && port.index().find(id) == null, "Expected two independently powered grids")).thenExecute(() -> {
            var handler = port.itemHandler(Direction.SOUTH);
            var stack = IRON.toStack(1);
            test.assertTrue(handler.getSlots() == 1 && port.bulkHandler(Direction.SOUTH).count(IRON) == 0
                    && handler.insertItem(0, stack, false) == stack,
                    "Cached interface retained access to a cell on the other grid");
            test.setBlock(bridge, AEBlocks.CREATIVE_ENERGY_CELL.block());
        }).thenWaitUntil(() -> test.assertTrue(port.index() != null && port.index().find(id) != null,
                "Grid did not rejoin")).thenExecute(() -> {
            var handler = port.itemHandler(Direction.SOUTH);
            test.assertTrue(handler.getSlots() == 2 && handler.getStackInSlot(1).getCount() == 5
                    && handler.insertItem(0, IRON.toStack(1), false).isEmpty()
                    && handler.getStackInSlot(1).getCount() == 6,
                    "The cached interface failed to rediscover the rejoined cell");
        }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void interfacesSelectSixteenAndShareCompressedStock(GameTestHelper test) {
        var drivePos = new BlockPos(2, 2, 2);
        test.setBlock(drivePos, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(drivePos.west(), BulkStorageMod.BASIC_INTERFACE.get());
        test.setBlock(drivePos.south(), BulkStorageMod.ADVANCED_INTERFACE.get());
        test.setBlock(drivePos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        BulkDriveEntity drive = test.getBlockEntity(drivePos);
        BulkInterfaceEntity basic = test.getBlockEntity(drivePos.west());
        BulkInterfaceEntity advanced = test.getBlockEntity(drivePos.south());
        var ironBlock = AEItemKey.of(Items.IRON_BLOCK);
        var ironNugget = AEItemKey.of(Items.IRON_NUGGET);
        var cells = new java.util.ArrayList<ItemStack>();
        for (int slot = 0; slot < 17; ++slot) {
            var stack = BulkStorageMod.CELLS.get(CellTier.QUANTUM).get().getDefaultInstance();
            stack.set(BulkStorageMod.FILTER, new FilterConfig("@minecraft", false));
            if (slot < 2) {
                ((BulkCellItem) stack.getItem()).getUpgrades(stack).setItemDirect(0,
                        BulkStorageMod.MULTI_COMPRESSION_CARD.get().getDefaultInstance());
            }
            BulkCellItem.ensureId(stack);
            cells.add(stack);
            drive.getInternalInventory().setItemDirect(slot, stack);
        }
        var first = cells.get(0).get(BulkStorageMod.CELL_ID);
        var second = cells.get(1).get(BulkStorageMod.CELL_ID);
        test.startSequence().thenWaitUntil(() -> {
            test.assertTrue(advanced.index() != null && basic.index() != null, "Interface channel not ready");
            test.assertTrue(basic.index().mountedCells().size() == 17, "The catalog omitted physical bulk cells");
        }).thenExecute(() -> {
            test.assertTrue(basic.getBlockState().getValue(BlockStateProperties.LIT), "The online model was not enabled");
            test.assertTrue(basic.bind(second) && basic.replaceBinding(first) && basic.bindings().equals(java.util.List.of(first)),
                    "Replacing the one basic binding failed");
            for (int slot = 0; slot < 16; ++slot) {
                test.assertTrue(advanced.bind(cells.get(slot).get(BulkStorageMod.CELL_ID)), "Valid cell rejected at " + slot);
            }
            test.assertTrue(!advanced.bind(cells.get(16).get(BulkStorageMod.CELL_ID)), "A seventeenth binding was accepted");
            var a = (BulkCellInventory) drive.getOriginalCellInventory(0);
            var b = (BulkCellInventory) drive.getOriginalCellInventory(1);
            var source = IActionSource.empty();
            long amount = 3_000_000_011L;
            test.assertTrue(a.insert(ironBlock, amount, REAL, source) == amount && b.insert(IRON, 100, REAL, source) == 100,
                    "Could not seed shared pools");
            var ops = RegistryOps.create(NbtOps.INSTANCE, test.getLevel().registryAccess());
            var summary = BulkInventoryPreview.decodeRemote(BulkStorageTests.collectRemotePreview(a), ops);
            test.assertTrue(summary.total() == 1 && summary.entries().getFirst().pool()
                    && summary.entries().getFirst().key().equals(ironNugget)
                    && summary.entries().getFirst().amount() == amount * 81, "Pool summary counted virtual forms as real stock");
            var basicPort = basic.bulkHandler(Direction.WEST);
            var advancedPort = advanced.bulkHandler(Direction.SOUTH);
            test.assertTrue(basicPort.count(ironBlock) == Integer.MAX_VALUE && advancedPort.count(ironBlock) == Integer.MAX_VALUE,
                    "Compressed aliases exceeded external INT limits");
            var handler = advanced.itemHandler(Direction.SOUTH);
            int ironBlockSlot = -1;
            for (int slot = 1; slot < handler.getSlots(); slot++) {
                if (handler.getStackInSlot(slot).is(Items.IRON_BLOCK)) {
                    test.assertTrue(ironBlockSlot == -1, "Multiple pools exposed duplicate alias slots");
                    ironBlockSlot = slot;
                }
            }
            test.assertTrue(ironBlockSlot > 0 && handler.extractItem(ironBlockSlot, 2, true).getCount() == 2,
                    "Automation did not discover the compressed alias");
            test.assertTrue(basicPort.extract(ironBlock, Integer.MAX_VALUE, REAL) == Integer.MAX_VALUE
                    && advancedPort.extract(IRON, 100, REAL) == 100
                    && handler.extractItem(ironBlockSlot, 2, false).getCount() == 2, "Shared pool extraction failed");
            long remainder = a.amount(ironNugget) + b.amount(ironNugget);
            test.assertTrue(remainder + ((long) Integer.MAX_VALUE + 2) * 81 + 100L * 9 == amount * 81 + 900,
                    "Interleaved extraction through two ports did not conserve base units");
            test.assertTrue(advanced.setFilter("minecraft:iron_ingot") == null
                    && advancedPort.count(ironBlock) == 0 && advancedPort.count(IRON) > 0,
                    "The secondary filter was not enforced per requested form");
            var basicHandler = basic.itemHandler(Direction.WEST);
            test.assertTrue(basicHandler.getSlots() > 1, "Basic handler did not discover bound stock");
            test.assertTrue(basic.unbind(first) && basic.bindings().isEmpty() && basicHandler.getSlots() == 1
                    && basicPort.count(IRON) == 0, "A held interface capability retained an unbound cell");
            test.assertTrue(basic.bind(first) && basicHandler.getSlots() > 1
                    && basicPort.count(IRON) == Math.min(a.amount(IRON), Integer.MAX_VALUE),
                    "A held interface capability did not see the rebound cell");
        }).thenSucceed();
    }

    private static BulkCellInventory actual(DriveBlockEntity drive, int slot) {
        return (BulkCellInventory) drive.getOriginalCellInventory(slot);
    }

    private static ItemStack cell(long iron, long gold) {
        var stack = BulkStorageMod.CELLS.get(CellTier.TAG).get().getDefaultInstance();
        stack.set(BulkStorageMod.FILTER, new FilterConfig("#c:ingots", false));
        var storage = StorageCells.getCellInventory(stack, () -> {});
        storage.insert(IRON, iron, REAL, IActionSource.empty());
        storage.insert(GOLD, gold, REAL, IActionSource.empty());
        storage.persist();
        return stack;
    }
}
