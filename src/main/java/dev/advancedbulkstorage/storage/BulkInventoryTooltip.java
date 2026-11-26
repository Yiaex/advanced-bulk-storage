package dev.advancedbulkstorage.storage;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.CustomData;

public record BulkInventoryTooltip(CustomData contents, BulkInventoryPreview preview, Boolean expanded) implements TooltipComponent {
    public BulkInventoryTooltip(CustomData contents) {
        this(contents, null, null);
    }

    public BulkInventoryTooltip(BulkInventoryPreview preview, boolean expanded) {
        this(null, preview, expanded);
    }
}
