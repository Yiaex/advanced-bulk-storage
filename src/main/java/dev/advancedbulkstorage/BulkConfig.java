package dev.advancedbulkstorage;

import java.util.EnumMap;
import java.util.Map;
import dev.advancedbulkstorage.device.MigratorTier;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class BulkConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue IDENTIFIER_LENGTH;
    public static final ModConfigSpec.IntValue EXPRESSION_LENGTH;
    public static final ModConfigSpec.IntValue EXPRESSION_DEPTH;
    public static final ModConfigSpec.IntValue CONDITIONS;
    public static final ModConfigSpec.IntValue EXTENDED_TAGS;
    public static final ModConfigSpec.IntValue QUANTUM_EXTENDED_TAGS;
    public static final ModConfigSpec.IntValue NAMESPACE_RULES;
    public static final ModConfigSpec.IntValue GLOB_MATCHES;
    public static final ModConfigSpec.BooleanValue EXACT_EQUIPMENT;
    public static final ModConfigSpec.IntValue BACKGROUND_MICROSECONDS;
    public static final ModConfigSpec.IntValue BACKGROUND_TYPES;
    public static final ModConfigSpec.BooleanValue ADAPTIVE_BACKGROUND;
    public static final ModConfigSpec.DoubleValue BATCH_POWER_PER_THOUSAND;
    public static final ModConfigSpec.DoubleValue AE_POWER_PER_ITEM;
    public static final ModConfigSpec.DoubleValue ENERGY_CARD_FACTOR;
    public static final ModConfigSpec.DoubleValue ENERGY_CARD_MINIMUM;
    public static final ModConfigSpec.BooleanValue ALLOW_OVERFLOW_DISCARD;
    public static final ModConfigSpec.IntValue BULK_DRIVE_SLOTS;
    public static final ModConfigSpec.DoubleValue BULK_DRIVE_DRAIN;
    private static final Map<CellTier, CellSettings> CELLS = new EnumMap<>(CellTier.class);
    private static final Map<MigratorTier, ModConfigSpec.DoubleValue> MIGRATION_DRAIN = new EnumMap<>(MigratorTier.class);

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("filter");
        IDENTIFIER_LENGTH = builder.defineInRange("identifierLength", 256, 16, 256);
        EXPRESSION_LENGTH = builder.defineInRange("expressionLength", 1024, 16, 4096);
        EXPRESSION_DEPTH = builder.defineInRange("expressionDepth", 16, 1, 32);
        CONDITIONS = builder.defineInRange("conditions", 128, 1, 256);
        EXTENDED_TAGS = builder.defineInRange("extendedTags", 64, 1, 128);
        QUANTUM_EXTENDED_TAGS = builder.defineInRange("quantumExtendedTags", 128, 1, 128);
        NAMESPACE_RULES = builder.defineInRange("namespaceRules", 64, 1, 128);
        GLOB_MATCHES = builder.defineInRange("globMatches", 256, 1, 2048);
        EXACT_EQUIPMENT = builder.define("allowExactEquipment", true);
        builder.pop();
        builder.push("background");
        BACKGROUND_MICROSECONDS = builder.defineInRange("microsecondsPerTick", 2000, 100, 20000);
        BACKGROUND_TYPES = builder.defineInRange("keysPerTick", 2048, 16, 16384);
        ADAPTIVE_BACKGROUND = builder.define("adaptiveMspt", true);
        builder.pop();
        builder.push("migration");
        BATCH_POWER_PER_THOUSAND = builder.defineInRange("aePerThousandItems", 1.0, 0.0001, 100000);
        AE_POWER_PER_ITEM = builder.defineInRange("compatibilityAePerItem", 1.0, 0.0001, 100000);
        for (var tier : MigratorTier.values()) {
            MIGRATION_DRAIN.put(tier, builder.defineInRange(tier.id + "IdleAe", tier.drain, 0.01, 65536.0));
        }
        builder.pop();
        builder.push("upgrades");
        ENERGY_CARD_FACTOR = builder.defineInRange("energyCardMultiplier", 0.8, 0.01, 1.0);
        ENERGY_CARD_MINIMUM = builder.defineInRange("minimumEnergyFraction", 0.1, 0.01, 1.0);
        ALLOW_OVERFLOW_DISCARD = builder.define("allowOverflowDiscard", true);
        builder.pop();
        builder.push("bulkDrive");
        BULK_DRIVE_SLOTS = builder.defineInRange("activeSlots", 32, 1, 32);
        BULK_DRIVE_DRAIN = builder.defineInRange("baseIdleAe", 4.0, 0.01, 4096.0);
        builder.pop();
        builder.push("cells");
        for (var tier : CellTier.values()) {
            builder.push(tier.id());
            CELLS.put(tier, new CellSettings(
                    builder.defineInRange("types", tier.defaultTypes(), 1, 8192),
                    builder.defineInRange("upgrades", tier.defaultSlots(), 1, 8),
                    builder.defineInRange("tags", tier.defaultTags(), 0, 128),
                    builder.defineInRange("idleDrain", tier.defaultDrain(), 0.01, 4096),
                    builder.defineInRange("itemsPerType", Long.MAX_VALUE, 1, Long.MAX_VALUE)));
            builder.pop();
        }
        builder.pop();
        SPEC = builder.build();
    }

    private BulkConfig() {}

    public static CellSettings cell(CellTier tier) {
        return CELLS.get(tier);
    }

    public static double energyMultiplier(int cards) {
        return cards == 0 ? 1.0 : Math.max(ENERGY_CARD_MINIMUM.get(), Math.pow(ENERGY_CARD_FACTOR.get(), cards));
    }

    public static double migratorDrain(MigratorTier tier) {
        return SPEC.isLoaded() ? MIGRATION_DRAIN.get(tier).get() : tier.drain;
    }

    public record CellSettings(ModConfigSpec.IntValue types, ModConfigSpec.IntValue upgrades,
                               ModConfigSpec.IntValue tags, ModConfigSpec.DoubleValue idleDrain,
                               ModConfigSpec.LongValue itemsPerType) {}
}
