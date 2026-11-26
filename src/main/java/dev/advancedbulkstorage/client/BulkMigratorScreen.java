package dev.advancedbulkstorage.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import appeng.menu.SlotSemantics;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.menu.BulkMigratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public final class BulkMigratorScreen extends AEBaseScreen<BulkMigratorMenu> {
    private AE2Button toggle;
    private AE2Button direction;
    private AE2Button power;
    private AE2Button signal;
    private AE2Button scope;
    private AE2Button origin;
    private AE2Button destination;
    private AETextField field;
    private String lastExpression = "";

    public BulkMigratorScreen(BulkMigratorMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        setTextContent(TEXT_ID_DIALOG_TITLE, title);
    }

    @Override
    protected void init() {
        super.init();
        toggle = button(8, 60, 59, "start", "toggle");
        direction = button(69, 60, 48, "forward", "direction");
        power = button(119, 60, 65, "fixed", "power");
        signal = button(186, 60, 64, "ignore", "signal");
        field = addRenderableWidget(new AETextField(style, font, leftPos + 8, topPos + 81, 140, 14));
        field.setMaxLength(FilterConfig.MAX_INPUT_LENGTH);
        field.setPlaceholder(text("filter"));
        field.setValue(menu.expression);
        addRenderableWidget(new AE2Button(leftPos + 151, topPos + 81, 47, 13, text("save"),
                button -> menu.request("filter", field.getValue())));
        button(201, 81, 49, "editor", "editor");
        scope = button(8, 146, 94, "slots", "scope");
        origin = button(105, 146, 70, "choose_source", "origin");
        destination = button(178, 146, 72, "choose_target", "destination");
    }

    private AE2Button button(int x, int y, int width, String label, String action) {
        return addRenderableWidget(new AE2Button(leftPos + x, topPos + y, width, 13, text(label),
                button -> menu.request(action, "")));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        toggle.setMessage(text(menu.running ? "pause" : "start"));
        direction.setMessage(text(menu.reversed ? "reverse" : "forward"));
        power.setMessage(text(menu.power));
        signal.setMessage(text(menu.signal));
        scope.setMessage(text(menu.scope));
        boolean pair = menu.scope.equals("grid_pair");
        origin.active = pair || menu.scope.equals("grid_to_slot");
        destination.active = pair || menu.scope.equals("slot_to_grid");
        direction.active = !pair;
        origin.setTooltip(Tooltip.create(endpoint(menu.origin, pair, origin.active)));
        destination.setTooltip(Tooltip.create(endpoint(menu.destination, pair, destination.active)));
        if (!lastExpression.equals(menu.expression)) {
            lastExpression = menu.expression;
            field.setValue(lastExpression);
        }
    }

    private static Component endpoint(String name, boolean pair, boolean available) {
        if (!available) {
            return text("local_slot");
        }
        if (!name.isEmpty()) {
            return Component.literal(name);
        }
        return text(pair ? "select_endpoint" : "automatic_endpoint");
    }

    @Override
    public void drawBG(GuiGraphics graphics, int left, int top, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, left, top, mouseX, mouseY, partialTicks);
        var slots = Icon.SLOT_BACKGROUND.getBlitter();
        for (var slot : menu.slots) {
            slots.dest(left + slot.x - 1, top + slot.y - 1).blit(graphics);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        super.drawFG(graphics, left, top, mouseX, mouseY);
        int color = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        var cells = menu.getSlots(SlotSemantics.MACHINE_INPUT);
        drawCellLabel(graphics, cells.getFirst(), text("cell_a"), color);
        drawCellLabel(graphics, cells.get(1), text("cell_b"), color);
        graphics.drawString(font, text("acceleration"), 121, 20, color, false);
        graphics.drawString(font, text("max_rate", menu.rate), 8, 101, color, false);
        graphics.drawString(font, text("actual", menu.recent), 8, 112, color, false);
        graphics.drawString(font, text("progress", menu.total, menu.keys), 8, 123, color, false);
        var status = menu.message.isEmpty() ? text(menu.state)
                : menu.message.startsWith("error.") ? Component.translatable("gui.advanced_bulk_storage." + menu.message)
                : text(menu.message);
        graphics.drawString(font, status, 8, 134, color, false);
        if (menu.remaining >= 0) {
            graphics.drawString(font, text("remaining", menu.remaining), 8, 161, color, false);
        }
        if (menu.eta >= 0) {
            var estimate = text("eta", menu.eta);
            graphics.drawString(font, estimate, 250 - font.width(estimate), 161, color, false);
        }
    }

    private void drawCellLabel(GuiGraphics graphics, Slot slot, Component label, int color) {
        graphics.drawString(font, label, slot.x + 8 - font.width(label) / 2, slot.y - 14, color, false);
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.advanced_bulk_storage.migrator." + key, args);
    }
}
