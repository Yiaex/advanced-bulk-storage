package dev.advancedbulkstorage.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.locator.MenuLocators;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.device.BulkMonitorEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

public final class BulkMonitorMenu extends AEBaseMenu {
    private final BulkMonitorEntity host;
    private int nextAction;
    @GuiSync(1) public String expression = "";
    @GuiSync(2) public long count;
    @GuiSync(3) public long threshold = 1;
    @GuiSync(4) public String comparison = "at_least";
    @GuiSync(5) public boolean signal;
    @GuiSync(6) public String metric = "network_stock";
    @GuiSync(7) public String target = "";
    @GuiSync(8) public boolean reading;

    public record Edit(String action, String value) {}

    public static MenuType<BulkMonitorMenu> createType() {
        return MenuTypeBuilder.create(BulkMonitorMenu::new, BulkMonitorEntity.class)
                .withMenuTitle(BulkMonitorEntity::getName).buildUnregistered(BulkStorageMod.id("bulk_storage_monitor"));
    }

    private BulkMonitorMenu(MenuType<BulkMonitorMenu> type, int id, Inventory inventory, BulkMonitorEntity host) {
        super(type, id, inventory, host);
        this.host = host;
        registerClientAction("edit", Edit.class, this::edit);
    }

    public void request(String action, String value) { sendClientAction("edit", new Edit(action, value)); }

    @Override public boolean stillValid(Player player) { return super.stillValid(player) && host.filterAccessible(player); }

    private void edit(Edit edit) {
        if (edit == null || edit.action() == null || edit.value() == null || edit.value().length() > 19
                || !stillValid(getPlayer()) || getPlayer().tickCount < nextAction) {
            return;
        }
        nextAction = getPlayer().tickCount + 5;
        switch (edit.action()) {
            case "filter" -> MenuOpener.open(BulkStorageMod.FILTER_EDITOR.get(), getPlayer(), MenuLocators.forBlockEntity(host));
            case "comparison" -> host.nextComparison();
            case "metric" -> host.cycleMetric();
            case "target" -> host.cycleTarget();
            case "threshold" -> {
                try {
                    host.setThreshold(Long.parseLong(edit.value()));
                } catch (NumberFormatException malformed) {
                    // Do not change an existing threshold on incomplete input.
                }
            }
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            expression = host.filterConfig().expression();
            count = host.count();
            threshold = host.threshold();
            comparison = host.comparison().name().toLowerCase(java.util.Locale.ROOT);
            signal = host.signal();
            metric = host.metric().name().toLowerCase(java.util.Locale.ROOT);
            target = host.targetLabel();
            reading = host.reading();
        }
        super.broadcastChanges();
    }
}
