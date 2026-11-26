package dev.advancedbulkstorage;

import dev.advancedbulkstorage.storage.BulkInventoryPreview;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class BulkTooltipConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue NORMAL;
    public static final ModConfigSpec.IntValue EXPANDED;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("inventoryTooltip");
        NORMAL = builder.comment("Maximum item icons displayed on a normal hover.")
                .defineInRange("normalItems", 9, 1, 27);
        EXPANDED = builder.comment("Maximum item icons displayed with Shift held.")
                .defineInRange("shiftItems", 36, 1, BulkInventoryPreview.MAX_ENTRIES);
        builder.pop();
        SPEC = builder.build();
    }

    private BulkTooltipConfig() {}
}
