package dev.advancedbulkstorage.client;

import appeng.api.client.AEKeyRendering;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.advancedbulkstorage.BulkTooltipConfig;
import dev.advancedbulkstorage.storage.BulkInventoryPreview;
import dev.advancedbulkstorage.storage.BulkInventoryTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.component.CustomData;

final class BulkInventoryTooltipRenderer implements ClientTooltipComponent {
    private static final Cache<CustomData, BulkInventoryPreview> CACHE = CacheBuilder.newBuilder()
            .weakKeys().maximumSize(128).build();
    private static final int COLUMN_WIDTH = 27;
    private static final int ROW_HEIGHT = 27;
    private static final int DIVIDER_HEIGHT = 5;
    private static RegistryAccess previousRegistry;
    private final BulkInventoryPreview preview;
    private final int columns;
    private final int count;
    private final int rows;
    private final String status;
    private final String hint;
    private final String poolHint;

    BulkInventoryTooltipRenderer(BulkInventoryTooltip tooltip) {
        var minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        var font = minecraft.font;
        int width = Math.max(COLUMN_WIDTH, window.getGuiScaledWidth() - 28);
        this.columns = Math.clamp(width / COLUMN_WIDTH, 1, 9);
        this.preview = tooltip.preview() == null ? preview(minecraft, tooltip.contents()) : tooltip.preview();
        boolean expanded = tooltip.expanded() == null ? Screen.hasShiftDown() : tooltip.expanded();
        boolean configured = BulkTooltipConfig.SPEC.isLoaded();
        int normal = configured ? BulkTooltipConfig.NORMAL.get() : 9;
        int shift = configured ? BulkTooltipConfig.EXPANDED.get() : 36;
        // Leave room for the name, filter, cell metadata, GuideME and optional mod name.
        int maximumRows = Math.max(1, (window.getGuiScaledHeight() - 111) / ROW_HEIGHT);
        this.count = preview.visibleCount(expanded, normal, shift, columns, maximumRows);
        this.rows = (count + columns - 1) / columns;
        int remaining = preview.total() - count;
        this.status = preview.invalid() ? text(font, width, "inventory_invalid")
                : preview.total() == 0 ? text(font, width, "inventory_empty")
                : remaining > 0 ? text(font, width, "inventory_remaining", remaining) : "";
        this.hint = !expanded && !preview.invalid() && remaining > 0 && shift > normal
                ? text(font, width, "inventory_expand") : "";
        this.poolHint = preview.entries().subList(0, count).stream().anyMatch(BulkInventoryPreview.Entry::pool)
                ? text(font, width, "inventory_pool") : "";
    }

    private static BulkInventoryPreview preview(Minecraft minecraft, CustomData contents) {
        var registry = minecraft.level == null ? null : minecraft.level.registryAccess();
        if (previousRegistry != registry) {
            CACHE.invalidateAll();
            previousRegistry = registry;
        }
        return CACHE.asMap().computeIfAbsent(contents, data -> BulkInventoryPreview.read(data,
                registry == null ? NbtOps.INSTANCE : RegistryOps.create(NbtOps.INSTANCE, registry)));
    }

    private static String text(Font font, int width, String key, Object... args) {
        return font.plainSubstrByWidth(Component.translatable("tooltip.advanced_bulk_storage." + key, args).getString(),
                width);
    }

    @Override
    public int getHeight() {
        return DIVIDER_HEIGHT + rows * ROW_HEIGHT + (status.isEmpty() ? 0 : 11) + (hint.isEmpty() ? 0 : 11)
                + (poolHint.isEmpty() ? 0 : 11);
    }

    @Override
    public int getWidth(Font font) {
        return Math.max(Math.min(columns, count) * COLUMN_WIDTH,
                Math.max(font.width(poolHint), Math.max(font.width(status), font.width(hint))));
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        graphics.hLine(x, x + getWidth(font) - 1, y + 1, 0xff615575);
        int startY = y + DIVIDER_HEIGHT;
        var minecraft = Minecraft.getInstance();
        for (int index = 0; index < count; index++) {
            int center = x + index % columns * COLUMN_WIDTH + COLUMN_WIDTH / 2;
            int iconY = startY + index / columns * ROW_HEIGHT;
            var entry = preview.entries().get(index);
            AEKeyRendering.drawInGui(minecraft, graphics, center - 8, iconY, entry.key());
            var label = entry.pool() ? entry.label() + "*" : entry.label();
            graphics.drawString(font, label, center - font.width(label) / 2,
                    iconY + 17, 0xfffafafa, true);
        }
        int textY = startY + rows * ROW_HEIGHT;
        if (!status.isEmpty()) {
            graphics.drawString(font, status, x, textY, 0xffb9b2c4, false);
            textY += 11;
        }
        if (!hint.isEmpty()) {
            graphics.drawString(font, hint, x, textY, 0xffaaa1bb, false);
            textY += 11;
        }
        if (!poolHint.isEmpty()) {
            graphics.drawString(font, poolHint, x, textY, 0xffb4e1db, false);
        }
    }
}
