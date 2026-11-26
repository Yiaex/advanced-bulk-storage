package dev.advancedbulkstorage.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import dev.advancedbulkstorage.menu.BulkDriveMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class BulkDriveScreen extends AEBaseScreen<BulkDriveMenu> {
    private AETextField priority;
    private int previous;

    public BulkDriveScreen(BulkDriveMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        setTextContent(TEXT_ID_DIALOG_TITLE, title);
    }

    @Override
    protected void init() {
        super.init();
        priority = addRenderableWidget(new AETextField(style, font, leftPos + 8, topPos + 108, 82, 14));
        priority.setMaxLength(11);
        previous = menu.priority;
        priority.setValue(Integer.toString(previous));
        addRenderableWidget(new AE2Button(leftPos + 93, topPos + 108, 86, 13,
                Component.translatable("gui.advanced_bulk_storage.drive.priority"), button -> menu.setPriority(priority.getValue())));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (previous != menu.priority) {
            previous = menu.priority;
            priority.setValue(Integer.toString(previous));
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int left, int top, int mx, int my, float partial) {
        super.drawBG(graphics, left, top, mx, my, partial);
        var icon = Icon.SLOT_BACKGROUND.getBlitter();
        for (var slot : menu.slots) {
            icon.dest(left + slot.x - 1, top + slot.y - 1).blit(graphics);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int left, int top, int mx, int my) {
        super.drawFG(graphics, left, top, mx, my);
        graphics.drawString(font, Component.translatable(menu.powered ? "gui.advanced_bulk_storage.drive.online"
                : "gui.advanced_bulk_storage.drive.offline"), 8, 96, style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), false);
    }
}
