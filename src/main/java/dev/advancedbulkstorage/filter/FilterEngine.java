package dev.advancedbulkstorage.filter;

import appeng.api.stacks.AEItemKey;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class FilterEngine {
    private static final Cache<Rule, Compiled> RULES = CacheBuilder.newBuilder().maximumSize(512).build();
    private static final Set<String> EQUIPMENT_GROUPS = Set.of(
            "minecraft:swords", "minecraft:axes", "minecraft:pickaxes", "minecraft:shovels", "minecraft:hoes",
            "minecraft:trident", "c:armors", "c:tools", "c:bows", "c:shields", "advanced_bulk_storage:equipment");
    private static Map<String, BitSet> namespaces = Map.of();
    private static Map<ResourceLocation, BitSet> tags = Map.of();
    private static BitSet equipment = new BitSet();
    private static List<String> index = List.of();
    private static int generation;

    private FilterEngine() {}

    public static void reset() {
        clear();
        var registry = BuiltInRegistries.ITEM;
        var items = registry.keySet();
        var byNamespace = new HashMap<String, BitSet>();
        registry.holders().forEach(holder -> {
            int id = registry.getId(holder.value());
            byNamespace.computeIfAbsent(holder.key().location().getNamespace(), ignored -> new BitSet()).set(id);
            if (EquipmentPolicy.isEquipment(holder)) {
                equipment.set(id);
            }
        });
        namespaces = Map.copyOf(byNamespace);
        var byTag = new HashMap<ResourceLocation, BitSet>();
        registry.getTagNames().forEach(tag -> registry.getTag(tag).ifPresent(members -> {
            var bits = new BitSet();
            for (var member : members) {
                bits.set(registry.getId(member.value()));
            }
            byTag.put(tag.location(), bits);
        }));
        tags = Map.copyOf(byTag);
        index = java.util.stream.Stream.concat(
                java.util.stream.Stream.concat(items.stream().map(ResourceLocation::toString),
                        namespaces.keySet().stream().map(namespace -> "@" + namespace)),
                tags.keySet().stream().map(tag -> "#" + tag))
                .sorted().toList();
    }

    public static void clear() {
        generation++;
        RULES.invalidateAll();
        namespaces = Map.of();
        tags = Map.of();
        equipment = new BitSet();
        index = List.of();
    }

    public static int generation() {
        return generation;
    }

    static Map<String, BitSet> namespaces() { return namespaces; }
    static Map<ResourceLocation, BitSet> tags() { return tags; }
    static boolean isEquipment(int itemId) { return equipment.get(itemId); }

    static boolean allEquipment(BitSet items) {
        if (items.isEmpty()) {
            return false;
        }
        var resources = (BitSet) items.clone();
        resources.andNot(equipment);
        return resources.isEmpty();
    }

    static boolean isEquipmentTag(ResourceLocation tag, BitSet items) {
        String id = tag.toString();
        return EQUIPMENT_GROUPS.contains(id) || id.startsWith("c:armors/") || id.startsWith("c:tools/")
                || allEquipment(items);
    }

    public static boolean isNarrowerTag(ResourceLocation child, ResourceLocation parent) {
        if (child.equals(parent) || !child.getNamespace().equals(parent.getNamespace())
                || !child.getPath().startsWith(parent.getPath() + "/")) {
            return false;
        }
        var childItems = tags.get(child);
        var parentItems = tags.get(parent);
        if (childItems == null || parentItems == null || childItems.isEmpty()) {
            return false;
        }
        var outsideParent = (BitSet) childItems.clone();
        outsideParent.andNot(parentItems);
        return outsideParent.isEmpty();
    }

    public static List<String> suggest(String input) {
        int start = Collections.binarySearch(index, input);
        if (start < 0) {
            start = -start - 1;
        }
        int end = start;
        while (end < index.size() && end - start < 50 && index.get(end).startsWith(input)) {
            end++;
        }
        return index.subList(start, end);
    }

    public static Compiled compile(FilterConfig config, FilterCapabilities caps) {
        return RULES.asMap().computeIfAbsent(new Rule(config, caps), rule -> {
            try {
                return new Compiled(FilterParser.parse(rule.config(), rule.capabilities()), null, 0);
            } catch (FilterParser.Failure failure) {
                return new Compiled(item -> false, failure.getMessage(), failure.position());
            }
        });
    }

    private record Rule(FilterConfig config, FilterCapabilities capabilities) {}
    public record Compiled(IntPredicate predicate, String error, int position) {
        public boolean matches(AEItemKey key) {
            return matches(key.getItem());
        }

        public boolean matches(Item item) {
            return testUncached(item);
        }

        public boolean testUncached(Item item) {
            int id = BuiltInRegistries.ITEM.getId(item);
            return error == null && id >= 0 && predicate.test(id);
        }
    }
}
