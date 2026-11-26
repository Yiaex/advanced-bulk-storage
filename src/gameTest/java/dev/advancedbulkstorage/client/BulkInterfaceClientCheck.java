package dev.advancedbulkstorage.client;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.client.gui.style.StyleManager;
import appeng.core.definitions.AEBlocks;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import com.mojang.logging.LogUtils;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.CellTier;
import dev.advancedbulkstorage.device.BulkDriveEntity;
import dev.advancedbulkstorage.device.BulkInterfaceEntity;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.storage.BulkCellItem;
import dev.advancedbulkstorage.storage.BulkInventoryPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Integrated-server fixture for a disposable singleplayer world. Excluded from the release JAR. */
@EventBusSubscriber(modid = BulkStorageMod.ID, value = Dist.CLIENT)
public final class BulkInterfaceClientCheck {
    private static final boolean ENABLED = Boolean.getBoolean("advanced_bulk_storage.checkInterface");
    private static final BlockPos DRIVE = new BlockPos(8, -60, 8);
    private static final BlockPos BASIC = DRIVE.west();
    private static final BlockPos ADVANCED = DRIVE.east();
    private static final String STYLE = "/screens/advanced_bulk_storage/bulk_storage_interface.json";
    private enum Phase { START, BASIC, ADVANCED, DRIVE, DONE }
    private static Phase phase = Phase.START;
    private static int ticks;
    private static int firstHoverTick = -1;
    private static BulkInterfaceScreen instrumented;
    private static boolean captured;

    private BulkInterfaceClientCheck() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (!ENABLED || client.level == null || client.getSingleplayerServer() == null || phase == Phase.DONE) {
            return;
        }
        if (++ticks > 1000) {
            throw new IllegalStateException("ABS interface client check timed out in " + phase);
        }
        if (phase == Phase.START) {
            if (ticks >= 220) {
                phase = Phase.BASIC;
                seed(client);
            }
            return;
        }
        if (captured) {
            captured = false;
            if (phase == Phase.DRIVE) {
                phase = Phase.DONE;
                LogUtils.getLogger().info("ABS screen check completed at GUI scales 2 and 3");
                return;
            }
            if (phase == Phase.ADVANCED) {
                phase = Phase.DRIVE;
                openDrive(client);
                return;
            }
            phase = Phase.ADVANCED;
            instrumented = null;
            firstHoverTick = -1;
            open(client, ADVANCED);
        }
        if (phase != Phase.DRIVE && client.screen instanceof BulkInterfaceScreen screen && screen != instrumented
                && screen.getMenu().advanced() == (phase == Phase.ADVANCED)) {
            client.options.guiScale().set(phase == Phase.BASIC ? 2 : 3);
            client.resizeDisplay();
            instrumented = new BulkInterfaceScreen(screen.getMenu(), client.player.getInventory(),
                    screen.getTitle(), StyleManager.loadStyleDoc(STYLE), () -> true);
            client.setScreen(instrumented);
        }
    }

    private static void seed(Minecraft client) {
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.serverLevel();
            player.setGameMode(GameType.CREATIVE);
            for (int x = 5; x <= 11; x++) {
                for (int z = 4; z <= 10; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, -61, z), Blocks.SMOOTH_STONE.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(DRIVE, BulkStorageMod.BULK_DRIVE.get().defaultBlockState());
            level.setBlockAndUpdate(DRIVE.south(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            level.setBlockAndUpdate(BASIC, BulkStorageMod.BASIC_INTERFACE.get().defaultBlockState());
            level.setBlockAndUpdate(ADVANCED, BulkStorageMod.ADVANCED_INTERFACE.get().defaultBlockState());
            var drive = (BulkDriveEntity) level.getBlockEntity(DRIVE);
            for (int slot = 0; slot < 18; ++slot) {
                CellTier tier = slot < 2 ? CellTier.QUANTUM : CellTier.values()[slot % CellTier.values().length];
                var stack = BulkStorageMod.CELLS.get(tier).get().getDefaultInstance();
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(slot == 0 ? "Quantum LONG preview" : "Drive cell " + (slot + 1)));
                stack.set(BulkStorageMod.FILTER, new FilterConfig(tier == CellTier.QUANTUM || tier == CellTier.MOD
                        ? "@minecraft" : "#c:ingots", false));
                if (slot == 0) {
                    var inventory = StorageCells.getCellInventory(stack, () -> {});
                    inventory.insert(AEItemKey.of(Items.IRON_INGOT), 64_000_000_000L,
                            Actionable.MODULATE, IActionSource.empty());
                    inventory.persist();
                } else if (slot == 1) {
                    var inventory = StorageCells.getCellInventory(stack, () -> {});
                    inventory.insert(AEItemKey.of(Items.IRON_INGOT), Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
                    int seeded = 1;
                    for (var item : BuiltInRegistries.ITEM) {
                        if (item != Items.IRON_INGOT && inventory.insert(AEItemKey.of(item),
                                2_200_000_000L + seeded * 11_823L, Actionable.MODULATE, IActionSource.empty()) > 0
                                && ++seeded == 50) {
                            break;
                        }
                    }
                    inventory.persist();
                }
                BulkCellItem.ensureId(stack);
                drive.getInternalInventory().setItemDirect(slot, stack);
            }
        });
    }

    @SubscribeEvent
    public static void allowGridToConnect(ClientTickEvent.Pre event) {
        if (ENABLED && phase == Phase.BASIC && ticks == 260) {
            open(Minecraft.getInstance(), BASIC);
        }
    }

    private static void open(Minecraft client, BlockPos pos) {
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            var level = player.serverLevel();
            var drive = (BulkDriveEntity) level.getBlockEntity(DRIVE);
            var host = (BulkInterfaceEntity) level.getBlockEntity(pos);
            int bindings = host.advanced() ? 16 : 1;
            for (int slot = 0; slot < bindings; ++slot) {
                if (!host.bind(drive.getInternalInventory().getStackInSlot(slot).get(BulkStorageMod.CELL_ID))) {
                    throw new IllegalStateException("ABS client test drive is offline or binding failed in slot " + slot);
                }
            }
            player.closeContainer();
            player.teleportTo(pos.getX() + 0.5, -60, pos.getZ() - 2.5);
            MenuOpener.open(BulkStorageMod.INTERFACE_MENU.get(), player, MenuLocators.forBlockEntity(host));
        });
    }

    private static void openDrive(Minecraft client) {
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.closeContainer();
            player.getInventory().setItem(33, new net.minecraft.world.item.ItemStack(Items.DIAMOND, 64));
            player.getInventory().setItem(6, BulkStorageMod.CELLS.get(CellTier.QUANTUM).get().getDefaultInstance());
            MenuOpener.open(BulkStorageMod.BULK_DRIVE_MENU.get(), player,
                    MenuLocators.forBlockEntity(player.serverLevel().getBlockEntity(DRIVE)));
        });
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Pre event) {
        if (ENABLED && phase == Phase.DRIVE && !captured && event.getScreen() instanceof BulkDriveScreen drive) {
            var menu = drive.getMenu();
            var inventory = menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
            var hotbar = menu.getSlots(SlotSemantics.PLAYER_HOTBAR);
            if (inventory.size() != 27 || hotbar.size() != 9
                    || hotbar.getFirst().y - inventory.getLast().y != 22) {
                throw new IllegalStateException("ABS drive inventory slots are overlapping or out of order");
            }
            if (!inventory.get(24).getItem().is(Items.DIAMOND)
                    || !hotbar.get(6).getItem().is(BulkStorageMod.CELLS.get(CellTier.QUANTUM).get())) {
                return;
            }
            var client = Minecraft.getInstance();
            event.setCanceled(true);
            drive.render(event.getGuiGraphics(), client.getWindow().getGuiScaledWidth() - 1,
                    client.getWindow().getGuiScaledHeight() - 1, event.getPartialTick());
            event.getGuiGraphics().flush();
            Screenshot.grab(client.gameDirectory, "drive-inventory.png", client.getMainRenderTarget(),
                    component -> LogUtils.getLogger().info("ABS {}", component.getString()));
            captured = true;
            return;
        }
        if (!ENABLED || instrumented == null || captured || phase == Phase.DRIVE || phase == Phase.DONE
                || event.getScreen() != instrumented) {
            return;
        }
        var client = Minecraft.getInstance();
        int x = (client.getWindow().getGuiScaledWidth() - 280) / 2 + 22;
        boolean advanced = phase == Phase.ADVANCED;
        int y = (client.getWindow().getGuiScaledHeight() - 249) / 2 + 46 + (advanced ? 20 : 0);
        // Render the normal screen with a reproducible pointer. The supplier simulates a held Shift.
        event.setCanceled(true);
        instrumented.render(event.getGuiGraphics(), x, y, event.getPartialTick());
        if (firstHoverTick < 0 && !instrumented.getMenu().response().rows().isEmpty()) {
            firstHoverTick = ticks;
        }
        var menu = instrumented.getMenu();
        if (menu.previewData.isEmpty()) {
            return;
        }
        var page = menu.response();
        if (menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst().y
                - menu.getSlots(SlotSemantics.PLAYER_INVENTORY).getLast().y != 22) {
            throw new IllegalStateException("ABS interface player inventory overlaps the hotbar");
        }
        var preview = BulkInventoryPreview.decodeRemote(menu.previewData,
                RegistryOps.create(NbtOps.INSTANCE, client.level.registryAccess()));
        if (!page.online() || page.bindings() != (advanced ? 16 : 1) || page.total() != 18
                || !page.rows().get(advanced ? 1 : 0).id().equals(menu.previewId)
                || preview.total() != (advanced ? 50 : 1)
                || preview.entries().getFirst().amount() != (advanced ? Long.MAX_VALUE : 64_000_000_000L)
                || preview.visibleCount(true, 9, 36, 9, 4) != (advanced ? 36 : 1)) {
            throw new IllegalStateException("ABS client received incorrect interface rows or inventory");
        }
        int latency = ticks - firstHoverTick;
        if (firstHoverTick < 0 || latency > 16) {
            throw new IllegalStateException("ABS interface hover took " + latency + " client ticks");
        }
        LogUtils.getLogger().info("ABS hover preview mode={} keys={} clientTicks={}",
                advanced ? "advanced" : "basic", preview.total(), latency);
        event.getGuiGraphics().flush();
        String screenshot = advanced ? "advanced-interface-shift.png" : "basic-interface-shift.png";
        Screenshot.grab(client.gameDirectory, screenshot, client.getMainRenderTarget(),
                component -> LogUtils.getLogger().info("ABS {}", component.getString()));
        captured = true;
    }
}
