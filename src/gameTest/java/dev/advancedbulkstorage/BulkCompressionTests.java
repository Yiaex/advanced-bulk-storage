package dev.advancedbulkstorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.core.definitions.AEBlocks;
import dev.advancedbulkstorage.device.BulkDriveEntity;
import dev.advancedbulkstorage.device.BulkInterfaceEntity;
import dev.advancedbulkstorage.device.BulkItemAccess;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.storage.BulkCellItem;
import dev.advancedbulkstorage.storage.BulkInventoryPreview;
import dev.advancedbulkstorage.storage.CellTransfer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(BulkStorageMod.ID)
@PrefixGameTestTemplate(false)
public final class BulkCompressionTests {
    private static final AEItemKey NUGGET = AEItemKey.of(Items.IRON_NUGGET);
    private static final AEItemKey INGOT = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey BLOCK = AEItemKey.of(Items.IRON_BLOCK);
    private static final AEItemKey DIAMOND = AEItemKey.of(Items.DIAMOND);
    private static final AEItemKey DIAMOND_BLOCK = AEItemKey.of(Items.DIAMOND_BLOCK);
    private static final Actionable REAL = Actionable.MODULATE;
    private static final Actionable SIM = Actionable.SIMULATE;
    private static final IActionSource SOURCE = IActionSource.empty();

    @GameTest(template = "empty")
    public static void separatePoolsPreserveUnitsAfterSaveAndUpgradeRemoval(GameTestHelper test) {
        var inventory = make("#c:ingots | minecraft:diamond");
        var stack = inventory;
        var cell = open(stack);
        test.assertTrue(cell.insert(INGOT, 19, REAL, SOURCE) == 19 && cell.insert(DIAMOND, 12, REAL, SOURCE) == 12,
                "Compression recipe families or filter failed");
        test.assertTrue(cell.types() == 2 && cell.amount(NUGGET) == 171 && cell.amount(BLOCK) == 2
                && cell.amount(DIAMOND_BLOCK) == 1 && cell.insert(BLOCK, 1, SIM, SOURCE) == 0,
                "Pool count, conversion or actual input whitelist was wrong");
        var output = cell.getAvailableStacks();
        test.assertTrue(output.get(NUGGET) == 171 && output.get(DIAMOND) == 12
                && output.get(INGOT) == 0 && output.get(BLOCK) == 0, "Virtual forms were double counted");
        test.assertTrue(cell.extract(BLOCK, 2, SIM, SOURCE) == 2 && cell.amount(NUGGET) == 171, "Simulation consumed a pool");
        test.assertTrue(cell.extract(BLOCK, 2, REAL, SOURCE) == 2 && cell.amount(NUGGET) == 9
                && cell.extract(DIAMOND_BLOCK, 1, REAL, SOURCE) == 1 && cell.amount(DIAMOND) == 3,
                "Real extraction did not leave the correct residues");
        var tag = stack.save(test.getLevel().registryAccess());
        var copy = ItemStack.parse(test.getLevel().registryAccess(), tag).orElseThrow();
        var copyCell = open(copy);
        test.assertTrue(copyCell.amount(NUGGET) == 9 && copyCell.amount(INGOT) == 1 && copyCell.amount(DIAMOND) == 3,
                "Pool ledger did not survive ItemStack persistence");
        var preview = BulkInventoryPreview.read(copy.get(BulkStorageMod.CONTENTS),
                RegistryOps.create(NbtOps.INSTANCE, test.getLevel().registryAccess()));
        test.assertTrue(!preview.invalid() && preview.total() == 2 && preview.entries().getFirst().key().equals(NUGGET)
                && preview.entries().getFirst().amount() == 9, "Tooltip did not show each physical pool once");
        var network = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), test.getLevel().registryAccess());
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(network, copy);
            var decoded = ItemStack.OPTIONAL_STREAM_CODEC.decode(network);
            test.assertTrue(ItemStack.matches(copy, decoded) && open(decoded).amount(NUGGET) == 9,
                    "Pooled ItemStack contents changed in network round trip");
        } finally {
            network.release();
        }
        var changedRatio = copy.copy();
        var changedTag = copy.get(BulkStorageMod.CONTENTS).copyTag();
        for (var entry : changedTag.getList("pools", Tag.TAG_COMPOUND)) {
            var fields = (net.minecraft.nbt.CompoundTag) entry;
            for (var form : fields.getList("forms", Tag.TAG_COMPOUND)) {
                var variant = (net.minecraft.nbt.CompoundTag) form;
                if (variant.getLong("factor") == 81) {
                    variant.putLong("factor", 80);
                }
            }
        }
        changedRatio.set(BulkStorageMod.CONTENTS, CustomData.of(changedTag));
        var changedCell = open(changedRatio);
        test.assertTrue(changedCell.amount(INGOT) == 0 && changedCell.extract(NUGGET, 9, REAL, SOURCE) == 9,
                "A saved ratio different from current recipes reinterpreted the physical units");
        ((BulkCellItem) copy.getItem()).getUpgrades(copy).setItemDirect(0, ItemStack.EMPTY);
        test.assertTrue(copyCell.extract(INGOT, 1, REAL, SOURCE) == 0 && copyCell.extract(NUGGET, 9, REAL, SOURCE) == 9
                && copyCell.extract(DIAMOND, 3, REAL, SOURCE) == 3 && copyCell.types() == 0,
                "Removing the upgrade stranded or duplicated its physical units");
        test.assertTrue(test.getLevel().getRecipeManager().byKey(BulkStorageMod.id("multi_compression_card")).isPresent(),
                "Multi-compression card recipe was missing");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void twoInterfacesShareOneCompressedPool(GameTestHelper test) {
        var pos = new BlockPos(2, 2, 2);
        test.setBlock(pos, BulkStorageMod.BULK_DRIVE.get());
        test.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        test.setBlock(pos.west(), BulkStorageMod.BASIC_INTERFACE.get());
        test.setBlock(pos.south(), BulkStorageMod.ADVANCED_INTERFACE.get());
        BulkDriveEntity drive = test.getBlockEntity(pos);
        BulkInterfaceEntity basic = test.getBlockEntity(pos.west());
        BulkInterfaceEntity advanced = test.getBlockEntity(pos.south());
        var stack = make("#c:ingots");
        open(stack).insert(INGOT, 19, REAL, SOURCE);
        drive.getInternalInventory().setItemDirect(0, stack);
        var id = stack.get(BulkStorageMod.CELL_ID);
        test.startSequence().thenWaitUntil(() -> test.assertTrue(basic.index() != null && advanced.index() != null
                && basic.index().find(id) != null, "Shared pool drive is not accessible"))
                .thenExecute(() -> {
                    test.assertTrue(basic.bind(id) && advanced.bind(id), "Could not bind the common cell");
                    var from = test.getLevel().getCapability(BulkItemAccess.CAPABILITY, test.absolutePos(pos.west()), Direction.WEST);
                    var other = test.getLevel().getCapability(BulkItemAccess.CAPABILITY, test.absolutePos(pos.south()), Direction.SOUTH);
                    test.assertTrue(from.count(BLOCK) == 2 && other.count(INGOT) == 19
                            && from.extract(BLOCK, 2, REAL) == 2 && other.extract(INGOT, 9, REAL) == 1
                            && from.count(NUGGET) == 0 && other.count(INGOT) == 0,
                            "Interfaces withdrew the same physical compressed resource twice");
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void compressedTransferConservesIntScaleAndAvoidsOverflow(GameTestHelper test) {
        var fromStack = make("minecraft:iron_ingot");
        var toStack = make("minecraft:iron_ingot");
        var from = open(fromStack);
        var to = open(toStack);
        test.assertTrue(from.insert(INGOT, Long.MAX_VALUE, REAL, SOURCE) == Long.MAX_VALUE / 9
                && from.insert(INGOT, 1, REAL, SOURCE) == 0, "LONG conversion silently truncated or overflowed");
        long before = from.amount(NUGGET);
        var batch = CellTransfer.begin(fromStack, from, toStack, to);
        test.assertTrue(batch != null && batch.move(INGOT, Integer.MAX_VALUE) == Integer.MAX_VALUE,
                "INT-scale staged migration with compressed cells failed");
        test.assertTrue(from.amount(NUGGET) == before && to.amount(NUGGET) == 0, "Staged mutations reached the original pools");
        test.assertTrue(batch.commit((source, target) -> {
            var afterSource = open(source);
            var afterTarget = open(target);
            long moved = (long) Integer.MAX_VALUE * 9;
            test.assertTrue(afterSource.amount(NUGGET) == before - moved && afterTarget.amount(NUGGET) == moved,
                    "Compressed migration lost base units");
            test.assertTrue(afterTarget.extract(BLOCK, 1, REAL, SOURCE) == 1 && afterTarget.amount(NUGGET) == moved - 81,
                    "Alternative withdrawal after migration debited the wrong pool");
        }), "Compressed staged pair never installed");
        test.succeed();
    }

    private static ItemStack make(String expression) {
        var stack = BulkStorageMod.CELLS.get(CellTier.QUANTUM).get().getDefaultInstance();
        stack.set(BulkStorageMod.FILTER, new FilterConfig(expression, false));
        ((BulkCellItem) stack.getItem()).getUpgrades(stack).setItemDirect(0, new ItemStack(BulkStorageMod.MULTI_COMPRESSION_CARD.get()));
        return stack;
    }

    private static BulkCellInventory open(ItemStack stack) {
        return (BulkCellInventory) StorageCells.getCellInventory(stack, null);
    }
}
