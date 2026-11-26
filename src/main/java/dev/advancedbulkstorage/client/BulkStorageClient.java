package dev.advancedbulkstorage.client;

import appeng.client.gui.style.StyleManager;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.menu.FilterEditorMenu;
import dev.advancedbulkstorage.menu.BulkInterfaceMenu;
import dev.advancedbulkstorage.menu.BulkMigratorMenu;
import dev.advancedbulkstorage.menu.BulkDriveMenu;
import dev.advancedbulkstorage.menu.BulkManagerMenu;
import dev.advancedbulkstorage.menu.BulkMonitorMenu;
import dev.advancedbulkstorage.storage.BulkInventoryTooltip;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

@EventBusSubscriber(modid = BulkStorageMod.ID, value = Dist.CLIENT)
public final class BulkStorageClient {
    private BulkStorageClient() {}

    @SubscribeEvent
    public static void registerTooltip(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(BulkInventoryTooltip.class, BulkInventoryTooltipRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.<FilterEditorMenu, FilterEditorScreen>register(BulkStorageMod.FILTER_EDITOR.get(), (menu, inventory, title) ->
                new FilterEditorScreen(menu, inventory, title,
                        StyleManager.loadStyleDoc("/screens/advanced_bulk_storage/filter_editor.json")));
        event.<BulkInterfaceMenu, BulkInterfaceScreen>register(BulkStorageMod.INTERFACE_MENU.get(), (menu, inventory, title) ->
                new BulkInterfaceScreen(menu, inventory, title,
                        StyleManager.loadStyleDoc("/screens/advanced_bulk_storage/bulk_storage_interface.json")));
        event.<BulkMigratorMenu, BulkMigratorScreen>register(BulkStorageMod.MIGRATOR_MENU.get(), (menu, inventory, title) ->
                new BulkMigratorScreen(menu, inventory, title,
                        StyleManager.loadStyleDoc("/screens/advanced_bulk_storage/bulk_storage_migrator.json")));
        event.<BulkDriveMenu, BulkDriveScreen>register(BulkStorageMod.BULK_DRIVE_MENU.get(), (menu, inventory, title) ->
                new BulkDriveScreen(menu, inventory, title,
                        StyleManager.loadStyleDoc("/screens/advanced_bulk_storage/bulk_storage_drive.json")));
        event.<BulkManagerMenu, BulkManagerScreen>register(BulkStorageMod.MANAGER_MENU.get(), (menu, inventory, title) ->
                new BulkManagerScreen(menu, inventory, title,
                        StyleManager.loadStyleDoc("/screens/advanced_bulk_storage/bulk_storage_manager.json")));
        event.<BulkMonitorMenu, BulkMonitorScreen>register(BulkStorageMod.MONITOR_MENU.get(), (menu, inventory, title) ->
                new BulkMonitorScreen(menu, inventory, title,
                        StyleManager.loadStyleDoc("/screens/advanced_bulk_storage/bulk_storage_monitor.json")));
    }
}
