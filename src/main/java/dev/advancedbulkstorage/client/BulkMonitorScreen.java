package dev.advancedbulkstorage.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import dev.advancedbulkstorage.menu.BulkMonitorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class BulkMonitorScreen extends AEBaseScreen<BulkMonitorMenu> {
    private AETextField threshold;
    private AE2Button comparison;
    private AE2Button metric;
    private AE2Button target;
    private AE2Button editFilter;
    private long synced;

    public BulkMonitorScreen(BulkMonitorMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        setTextContent(TEXT_ID_DIALOG_TITLE, title);
    }

    @Override protected void init() {
        super.init();
        threshold = addRenderableWidget(new AETextField(style, font, leftPos + 8, topPos + 68, 110, 13));
        threshold.setMaxLength(19);
        synced = menu.threshold;
        threshold.setValue(Long.toString(synced));
        addRenderableWidget(new AE2Button(leftPos + 122, topPos + 68, 66, 13, text("save"),
                b -> menu.request("threshold", threshold.getValue())));
        comparison = addRenderableWidget(new AE2Button(leftPos + 8, topPos + 84, 89, 13, text(menu.comparison),
                b -> menu.request("comparison", "")));
        editFilter = addRenderableWidget(new AE2Button(leftPos + 101, topPos + 84, 87, 13, text("edit"),
                b -> menu.request("filter", "")));
        metric = addRenderableWidget(new AE2Button(leftPos + 8, topPos + 101, 106, 13, text(menu.metric),
                b -> menu.request("metric", "")));
        target = addRenderableWidget(new AE2Button(leftPos + 118, topPos + 101, 70, 13, text("target"),
                b -> menu.request("target", "")));
        refreshMode();
    }

    @Override public void containerTick() {
        super.containerTick();
        comparison.setMessage(text(menu.comparison));
        refreshMode();
        if (synced != menu.threshold) {
            synced = menu.threshold;
            threshold.setValue(Long.toString(synced));
        }
    }

    private void refreshMode() {
        var name = text(menu.metric);
        metric.setMessage(Component.literal(font.plainSubstrByWidth(name.getString(), 100)));
        metric.setTooltip(Tooltip.create(name));
        target.active = !menu.metric.equals("network_stock");
        target.setTooltip(Tooltip.create(menu.target.isEmpty() ? text("target_missing") : Component.literal(menu.target)));
        editFilter.active = menu.metric.equals("network_stock") || menu.metric.equals("disk_stock");
    }

    @Override public void drawFG(GuiGraphics g, int left, int top, int x, int y) {
        super.drawFG(g, left, top, x, y);
        int color = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        var expr = menu.target.isEmpty() ? menu.expression.isBlank() ? text("no_filter").getString() : menu.expression
                : menu.target;
        g.drawString(font, font.plainSubstrByWidth(expr, 180), 8, 23, color, false);
        g.drawString(font, menu.reading ? text(menu.metric.equals("type_permille") ? "permille" : "count", menu.count)
                : text("unavailable"), 8, 35, color, false);
        g.drawString(font, text(menu.signal ? "on" : "off"), 8, 47, color, false);
    }

    static Component text(String key, Object... args) {
        return Component.translatable("gui.advanced_bulk_storage.monitor." + key, args);
    }
}
