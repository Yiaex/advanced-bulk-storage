package dev.advancedbulkstorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.DriveMenu;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.storage.BulkCellItem;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.CompressionDecoder;
import net.minecraft.network.CompressionEncoder;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.Varint21FrameDecoder;
import net.minecraft.network.Varint21LengthFieldPrepender;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import snownee.jade.addon.universal.ItemStorageProvider;
import snownee.jade.api.view.ViewGroup;
import snownee.jade.impl.BlockAccessorImpl;

@GameTestHolder(BulkStorageMod.ID)
@PrefixGameTestTemplate(false)
public final class BulkCellNetworkTests {
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final long OVER_INT = (long) Integer.MAX_VALUE + 1;

    private BulkCellNetworkTests() {}

    @GameTest(template = "empty")
    public static void itemAndContainerPacketsPreserveAllTiers(GameTestHelper test) {
        var slots = NonNullList.<ItemStack>create();
        for (var tier : CellTier.values()) {
            var empty = BulkStorageMod.CELLS.get(tier).get().getDefaultInstance();
            assertSameCell(test, empty, roundTrip(test, ItemStack.STREAM_CODEC, empty));
            slots.add(populated(tier));
        }
        var menu = new ClientboundContainerSetContentPacket(1, 100, slots, slots.getLast());
        var received = roundTrip(test, ClientboundContainerSetContentPacket.STREAM_CODEC, menu);
        var creativePlayer = playerNear(test, new BlockPos(2, 2, 2), GameType.CREATIVE);
        for (int index = 0; index < slots.size(); index++) {
            assertSameCell(test, slots.get(index), received.getItems().get(index));
            var creative = new ServerboundSetCreativeModeSlotPacket(36 + index, received.getItems().get(index).copy());
            var returned = roundTrip(test, ServerboundSetCreativeModeSlotPacket.STREAM_CODEC, creative);
            assertSameCell(test, slots.get(index), returned.itemStack());
            creativePlayer.connection.handleSetCreativeModeSlot(returned);
            assertSameCell(test, slots.get(index), creativePlayer.getInventory().getItem(index));
        }
        assertSameCell(test, slots.getLast(), received.getCarriedItem());
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalDriveClicksWithTwoViewers(GameTestHelper test) {
        var pos = new BlockPos(2, 2, 2);
        var drive = drive(test, pos);
        var first = playerNear(test, pos, GameType.SURVIVAL);
        var second = playerNear(test, pos, GameType.SURVIVAL);
        var firstMenu = new DriveMenu(1, first.getInventory(), drive);
        var secondMenu = new DriveMenu(2, second.getInventory(), drive);
        first.containerMenu = firstMenu;
        second.containerMenu = secondMenu;
        for (var tier : CellTier.values()) {
            var cell = populated(tier);
            var original = cell.copy();
            int slot = tier.ordinal();
            first.getInventory().setItem(slot, cell);
            click(test, first, firstMenu.getSlots(SlotSemantics.PLAYER_HOTBAR).get(slot).index,
                    ClickType.QUICK_MOVE, new Int2ObjectOpenHashMap<>());
            assertSameCell(test, original, drive.getInternalInventory().getStackInSlot(slot));
            test.assertTrue(first.getInventory().getItem(slot).isEmpty(), "Shift-in left another cell in the hotbar");
        }
        var expected = drive.getInternalInventory().getStackInSlot(0).copy();
        assertSameCell(test, expected, snapshot(test, firstMenu).getItems().getFirst());
        assertSameCell(test, expected, snapshot(test, secondMenu).getItems().getFirst());

        var prediction = new Int2ObjectOpenHashMap<ItemStack>();
        var incomplete = expected.copy();
        incomplete.remove(BulkStorageMod.CONTENTS);
        prediction.put(firstMenu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst().index, incomplete);
        click(test, first, 0, ClickType.QUICK_MOVE, prediction);
        test.assertTrue(snapshot(test, secondMenu).getItems().getFirst().isEmpty(), "Second viewer still sees the removed cell");
        test.assertTrue(drive.getInternalInventory().getStackInSlot(0).isEmpty(), "Shift-out left a copy in the drive");
        var id = expected.get(BulkStorageMod.CELL_ID);
        var moved = inventorySlot(first, id);
        assertSameCell(test, expected, first.getInventory().getItem(moved));

        first.containerMenu = first.inventoryMenu;
        int menuSlot = moved < 9 ? 36 + moved : moved;
        int destination = moved == 0 ? 37 : 36;
        click(test, first, menuSlot, ClickType.PICKUP, new Int2ObjectOpenHashMap<>());
        test.assertTrue(first.getInventory().getItem(moved).isEmpty(), "Picked-up cell was left in its inventory slot");
        click(test, first, destination, ClickType.PICKUP, new Int2ObjectOpenHashMap<>());
        assertSameCell(test, expected, first.getInventory().getItem(destination - 36));
        test.assertTrue(first.containerMenu.getCarried().isEmpty(), "Moving a cell left a carried copy");
        second.containerMenu = second.inventoryMenu;
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void brokenDriveItemsCanSyncSaveAndBePickedUp(GameTestHelper test) {
        var pos = new BlockPos(2, 2, 2);
        var drive = drive(test, pos);
        var player = playerNear(test, pos, GameType.SURVIVAL);
        var expected = new java.util.HashMap<UUID, ItemStack>();
        for (var tier : CellTier.values()) {
            var cell = populated(tier);
            drive.getInternalInventory().setItemDirect(tier.ordinal(), cell);
            expected.put(cell.get(BulkStorageMod.CELL_ID), cell.copy());
        }
        var level = test.getLevel();
        var blockTag = drive.saveWithFullMetadata(level.registryAccess());
        var restoredDrive = (DriveBlockEntity) BlockEntity.loadStatic(drive.getBlockPos(), drive.getBlockState(),
                blockTag, level.registryAccess());
        test.assertTrue(restoredDrive != null, "Persisted drive could not be reloaded");
        for (var cell : expected.values()) {
            int slot = ((BulkCellItem) cell.getItem()).tier().ordinal();
            assertSameCell(test, cell, restoredDrive.getInternalInventory().getStackInSlot(slot));
        }
        test.assertTrue(level.destroyBlock(drive.getBlockPos(), true, player), "Drive did not break");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(drive.getBlockPos()).inflate(3),
                entity -> entity.getItem().getItem() instanceof BulkCellItem);
        test.assertTrue(drops.size() == expected.size(), "Breaking the drive did not drop exactly one copy of each cell");
        for (var drop : drops) {
            var id = drop.getItem().get(BulkStorageMod.CELL_ID);
            var original = expected.remove(id);
            test.assertTrue(original != null, "Dropped cell has a duplicate or unexpected UUID");
            var entityPacket = new ClientboundSetEntityDataPacket(drop.getId(), drop.getEntityData().getNonDefaultValues());
            var received = roundTrip(test, ClientboundSetEntityDataPacket.STREAM_CODEC, entityPacket);
            var rendered = new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(), new ItemStack(Items.STICK));
            rendered.getEntityData().assignValues(received.packedItems());
            assertSameCell(test, original, rendered.getItem());

            CompoundTag persisted = drop.saveWithoutId(new CompoundTag());
            rendered.load(persisted);
            assertSameCell(test, original, rendered.getItem());
            drop.setNoPickUpDelay();
            drop.playerTouch(player);
            test.assertTrue(drop.isRemoved(), "The picked-up item entity still exists");
            assertSameCell(test, original, player.getInventory().getItem(inventorySlot(player, id)));
        }
        test.assertTrue(expected.isEmpty(), "A drive cell was lost");
        var hand = player.getInventory().removeItem(0, 1);
        var original = hand.copy();
        var droppedAgain = player.drop(hand, false);
        test.assertTrue(droppedAgain != null && player.getInventory().getItem(0).isEmpty(), "Dropping from inventory failed");
        droppedAgain.setNoPickUpDelay();
        droppedAgain.playerTouch(player);
        test.assertTrue(droppedAgain.isRemoved(), "Inventory drop still exists after pickup");
        assertSameCell(test, original, player.getInventory().getItem(inventorySlot(player, original.get(BulkStorageMod.CELL_ID))));
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void oldDiskCellLoadsWithoutMigration(GameTestHelper test) throws Exception {
        var name = "/advanced_bulk_storage/legacy/p0-0.1.0-dev-cell.snbt";
        try (var stream = BulkCellNetworkTests.class.getResourceAsStream(name)) {
            test.assertTrue(stream != null, "Missing pre-fix cell fixture");
            var raw = TagParser.parseTag(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            var registries = test.getLevel().registryAccess();
            var stack = ItemStack.parse(registries, raw).orElseThrow();
            var original = inventory(stack);
            var named = new ItemStack(Items.IRON_INGOT);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Distinct stock"));
            test.assertTrue(original.amount(AEItemKey.of(Items.IRON_INGOT)) == Long.MAX_VALUE
                    && original.amount(AEItemKey.of(named)) == OVER_INT, "Legacy quantities or item components changed");
            var decoded = roundTrip(test, ItemStack.STREAM_CODEC, stack);
            assertSameCell(test, stack, decoded);
            test.assertTrue(raw.equals(decoded.save(registries)), "Legacy disk representation changed after the wire round trip");
        }
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void jadeReadsTheActualDriveInventory(GameTestHelper test) {
        var pos = new BlockPos(2, 2, 2);
        var drive = drive(test, pos);
        var player = playerNear(test, pos, GameType.SURVIVAL);
        var expected = new java.util.HashMap<UUID, ItemStack>();
        for (var tier : CellTier.values()) {
            var cell = populated(tier);
            drive.getInternalInventory().setItemDirect(tier.ordinal(), cell);
            expected.put(cell.get(BulkStorageMod.CELL_ID), cell.copy());
        }
        var data = new CompoundTag();
        var absolute = drive.getBlockPos();
        var accessor = new BlockAccessorImpl.Builder().level(test.getLevel()).player(player).serverData(data)
                .serverConnected(true).showDetails(true).blockState(drive.getBlockState()).blockEntity(() -> drive)
                .hit(new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false)).build();
        var provider = ItemStorageProvider.Extension.INSTANCE;
        var groups = provider.getGroups(accessor);
        test.assertTrue(groups != null && !groups.isEmpty(), "Jade could not collect the actual drive inventory");
        var codec = ViewGroup.listCodec(ItemStack.OPTIONAL_STREAM_CODEC);
        var payload = accessor.encodeAsNbt(codec, Map.entry(provider.getUid(), groups));
        var views = accessor.decodeFromNbt(codec, payload);
        test.assertTrue(views.isPresent(), "Jade could not decode the inventory payload");
        for (var group : views.orElseThrow().getValue()) {
            for (var displayed : group.views) {
                var original = expected.remove(displayed.get(BulkStorageMod.CELL_ID));
                test.assertTrue(original != null, "Jade returned a duplicate or unexpected cell");
                assertSameCell(test, original, displayed);
            }
        }
        test.assertTrue(expected.isEmpty(), "Jade did not preserve all four populated cells");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void twoFullyPopulatedDriveSnapshotsFitNormalCompressedTransport(GameTestHelper test) {
        var quantum = denseQuantumCell(test);
        var menus = new java.util.ArrayList<DriveMenu>(2);
        for (int index = 0; index < 2; index++) {
            var pos = new BlockPos(2 + index * 5, 2, 2);
            var drive = drive(test, pos);
            var player = playerNear(test, pos, GameType.SURVIVAL);
            var menu = new DriveMenu(index + 1, player.getInventory(), drive);
            player.containerMenu = menu;
            menus.add(menu);
            for (int slot = 0; slot < drive.getCellCount(); slot++) {
                var cell = quantum.copy();
                cell.set(BulkStorageMod.CELL_ID, UUID.randomUUID());
                drive.getInternalInventory().setItemDirect(slot, cell);
            }
            for (int slot = 0; slot < 6; slot++) {
                var carried = quantum.copy();
                carried.set(BulkStorageMod.CELL_ID, UUID.randomUUID());
                player.getInventory().setItem(slot, carried);
            }
        }
        for (var menu : menus) {
            var drive = menu.getBlockEntity();
            test.assertTrue(drive instanceof DriveBlockEntity, "Viewer lost its drive");
            var packet = new ClientboundContainerSetContentPacket(menu.containerId, menu.getStateId(),
                    menu.getItems(), ItemStack.EMPTY);
            var received = compressedDriveSnapshot(test, packet, 10);
            for (var slot : menu.getSlots(SlotSemantics.STORAGE_CELL)) {
                assertSameCell(test, slot.getItem(), received.getItems().get(slot.index));
            }
        }
        for (var menu : menus) {
            var player = menu.getPlayer();
            player.containerMenu = player.inventoryMenu;
        }
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void denseDriveMenuRoundTrips32FullQuantumCells(GameTestHelper test) {
        var quantum = denseQuantumCell(test);
        var pos = new BlockPos(2, 2, 2);
        test.setBlock(pos, BulkStorageMod.BULK_DRIVE.get());
        var drive = (dev.advancedbulkstorage.device.BulkDriveEntity) test.getBlockEntity(pos);
        for (int slot = 0; slot < drive.getCellCount(); slot++) {
            var cell = quantum.copy();
            cell.set(BulkStorageMod.CELL_ID, UUID.randomUUID());
            drive.getInternalInventory().setItemDirect(slot, cell);
        }
        var player = playerNear(test, pos, GameType.SURVIVAL);
        var menu = new dev.advancedbulkstorage.menu.BulkDriveMenu(BulkStorageMod.BULK_DRIVE_MENU.get(), 42,
                player.getInventory(), drive);
        player.containerMenu = menu;
        var packet = new ClientboundContainerSetContentPacket(menu.containerId, menu.getStateId(),
                menu.getItems(), ItemStack.EMPTY);
        var received = compressedDriveSnapshot(test, packet, drive.getCellCount());
        var slots = menu.getSlots(SlotSemantics.MACHINE_INPUT);
        test.assertTrue(slots.size() == 32, "Dense drive menu omitted a real slot");
        for (var slot : slots) {
            test.assertTrue(ItemStack.matches(slot.getItem(), received.getItems().get(slot.index)),
                    "Dense drive changed the compressed inventory in slot " + slot.index);
        }
        player.containerMenu = player.inventoryMenu;
        test.succeed();
    }

    private static ItemStack denseQuantumCell(GameTestHelper test) {
        var quantum = BulkStorageMod.CELLS.get(CellTier.QUANTUM).get().getDefaultInstance();
        quantum.set(BulkStorageMod.FILTER, new FilterConfig("minecraft:diamond_pickaxe", false));
        var inventory = (BulkCellInventory) StorageCells.getCellInventory(quantum, () -> {});
        for (int i = 0; i < 4096; i++) {
            var stack = new ItemStack(Items.DIAMOND_PICKAXE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Configured tool " + i));
            stack.set(DataComponents.DAMAGE, i % 1561);
            long amount = Long.MAX_VALUE - i;
            if (inventory.insert(AEItemKey.of(stack), amount, Actionable.MODULATE, SOURCE) != amount) {
                test.fail("Could not seed the 4096-key Quantum cell at row " + i);
            }
            if (i % 2 == 0) {
                test.assertTrue(inventory.insert(AEItemKey.of(stack), Long.MAX_VALUE, Actionable.MODULATE, SOURCE) == Long.MAX_VALUE,
                        "Could not seed a wide quantity in the dense cell");
            }
        }
        inventory.persist();
        return quantum;
    }

    private static ClientboundContainerSetContentPacket compressedDriveSnapshot(
            GameTestHelper test, ClientboundContainerSetContentPacket packet, int cells) {
        var codec = ClientboundContainerSetContentPacket.STREAM_CODEC;
        var encoded = new RegistryFriendlyByteBuf(Unpooled.buffer(), test.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        var outbound = new EmbeddedChannel(new Varint21LengthFieldPrepender(), new CompressionEncoder(256));
        var inbound = new EmbeddedChannel(new Varint21FrameDecoder(null), new CompressionDecoder(256, true));
        try {
            codec.encode(encoded, packet);
            int uncompressed = encoded.readableBytes();
            outbound.writeOutbound(encoded.retain());
            ByteBuf framed = outbound.readOutbound();
            int wireSize = framed.readableBytes();
            inbound.writeInbound(framed);
            ByteBuf payload = inbound.readInbound();
            try {
                var decoded = codec.decode(new RegistryFriendlyByteBuf(payload, test.getLevel().registryAccess(), ConnectionType.NEOFORGE));
                test.assertTrue(!payload.isReadable(), "Drive packet has trailing payload");
                LogUtils.getLogger().info("ABS P0 drive snapshot: {} bytes before compression, {} framed bytes, {} cells",
                        uncompressed, wireSize, cells);
                return decoded;
            } finally {
                payload.release();
            }
        } finally {
            encoded.release();
            outbound.finishAndReleaseAll();
            inbound.finishAndReleaseAll();
        }
    }

    private static DriveBlockEntity drive(GameTestHelper test, BlockPos pos) {
        test.setBlock(pos, AEBlocks.DRIVE.block());
        test.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        return test.getBlockEntity(pos);
    }

    private static ServerPlayer playerNear(GameTestHelper test, BlockPos pos, GameType mode) {
        var level = test.getLevel();
        var profile = new GameProfile(UUID.randomUUID(), "CellRegression");
        var cookie = CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
        // Exercise the real packet handlers without running a login on the embedded peer.
        player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                player, cookie) {
            @Override
            public void send(Packet<?> packet, PacketSendListener listener) {
                if (packet instanceof ClientboundContainerSetContentPacket content) {
                    roundTrip(test, ClientboundContainerSetContentPacket.STREAM_CODEC, content);
                }
            }
        };
        var absolute = test.absolutePos(pos);
        player.setPos(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5);
        player.setGameMode(mode);
        return player;
    }

    private static ClientboundContainerSetContentPacket snapshot(GameTestHelper test, DriveMenu menu) {
        return roundTrip(test, ClientboundContainerSetContentPacket.STREAM_CODEC,
                new ClientboundContainerSetContentPacket(menu.containerId, menu.getStateId(),
                        menu.getItems(), menu.getCarried()));
    }

    private static void click(GameTestHelper test, ServerPlayer player, int slot, ClickType click,
                              Int2ObjectOpenHashMap<ItemStack> changed) {
        var menu = player.containerMenu;
        var request = new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), slot, 0,
                click, ItemStack.EMPTY, changed);
        player.connection.handleContainerClick(roundTrip(test, ServerboundContainerClickPacket.STREAM_CODEC, request));
    }

    private static int inventorySlot(ServerPlayer player, UUID id) {
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            if (id.equals(player.getInventory().getItem(slot).get(BulkStorageMod.CELL_ID))) {
                return slot;
            }
        }
        throw new IllegalStateException("Cell " + id + " is missing from player inventory");
    }

    static ItemStack populated(CellTier tier) {
        BulkCellItem item = BulkStorageMod.CELLS.get(tier).get();
        var stack = item.getDefaultInstance();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Regression " + tier.name()));
        stack.set(BulkStorageMod.FILTER, new FilterConfig(tier == CellTier.MOD ? "@minecraft" : "#c:ingots", false));
        item.getUpgrades(stack).addItems(BulkStorageMod.EXTENDED_TAG_CARD.get().getDefaultInstance());
        var cell = inventory(stack);
        var plain = AEItemKey.of(Items.IRON_INGOT);
        var named = new ItemStack(Items.IRON_INGOT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Distinct stock"));
        if (cell.insert(plain, Long.MAX_VALUE, Actionable.MODULATE, SOURCE) != Long.MAX_VALUE
                || cell.insert(plain, OVER_INT, Actionable.MODULATE, SOURCE) != OVER_INT
                || cell.insert(AEItemKey.of(named), OVER_INT, Actionable.MODULATE, SOURCE) != OVER_INT) {
            throw new IllegalStateException("Could not seed non-empty " + tier + " test cell");
        }
        return stack;
    }

    static BulkCellInventory inventory(ItemStack stack) {
        return (BulkCellInventory) StorageCells.getCellInventory(stack, null);
    }

    static void assertSameCell(GameTestHelper test, ItemStack original, ItemStack copy) {
        test.assertTrue(ItemStack.matches(original, copy),
                "Item ID, count, storage contents, UUID, filter, name or upgrades changed in transit");
    }

    static <T> T roundTrip(GameTestHelper test, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T value) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), test.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            codec.encode(buffer, value);
            var result = codec.decode(buffer);
            test.assertTrue(!buffer.isReadable(), "Stream codec left bytes unread");
            return result;
        } finally {
            buffer.release();
        }
    }
}
