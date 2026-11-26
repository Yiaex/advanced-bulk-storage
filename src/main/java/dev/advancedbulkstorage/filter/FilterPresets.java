package dev.advancedbulkstorage.filter;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.logging.LogUtils;
import dev.advancedbulkstorage.BulkStorageMod;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class FilterPresets extends SimpleJsonResourceReloadListener {
    public static final FilterPresets INSTANCE = new FilterPresets();
    private List<Preset> presets = List.of();

    private FilterPresets() {
        super(new Gson(), "advanced_bulk_storage/filter_preset");
    }

    public List<Preset> getPresets() {
        return presets;
    }

    public void clear() {
        presets = List.of();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        presets = files.entrySet().stream().sorted(Map.Entry.comparingByKey()).flatMap(entry ->
                Definition.CODEC.parse(JsonOps.INSTANCE, entry.getValue())
                        .resultOrPartial(message -> LogUtils.getLogger().warn("Invalid bulk filter preset {}: {}",
                                entry.getKey(), message))
                        .filter(Definition::enabled)
                        .map(definition -> new Preset(entry.getKey(), definition)).stream())
                .sorted(Comparator.comparingInt((Preset preset) -> preset.definition().weight())
                        .thenComparing(Preset::id))
                .limit(512).toList();
    }

    public record Preset(ResourceLocation id, Definition definition) {}

    public record Definition(boolean enabled, String name, String description, String category,
                             ResourceLocation icon, String expression, int weight, String requirement) {
        private static final Codec<Definition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Definition::enabled),
                Codec.string(0, 128).optionalFieldOf("name", "").forGetter(Definition::name),
                Codec.string(0, 256).optionalFieldOf("description", "").forGetter(Definition::description),
                Codec.string(0, 64).optionalFieldOf("category", "basic").forGetter(Definition::category),
                ResourceLocation.CODEC.optionalFieldOf("icon", ResourceLocation.withDefaultNamespace("paper"))
                        .forGetter(Definition::icon),
                Codec.string(0, FilterConfig.MAX_INPUT_LENGTH).optionalFieldOf("expression", "")
                        .forGetter(Definition::expression),
                Codec.INT.optionalFieldOf("weight", 0).forGetter(Definition::weight),
                Codec.string(1, 16).optionalFieldOf("requirement", "any").forGetter(Definition::requirement)
        ).apply(instance, Definition::new));

        public Optional<String> unavailable(FilterCapabilities capabilities) {
            if (requirement.equals("full") && !capabilities.items() || !requirement.equals("any") && !requirement.equals("full")) {
                return Optional.of("unsupported");
            }
            return Optional.ofNullable(FilterEngine.compile(new FilterConfig(expression, false), capabilities).error());
        }
    }
}
