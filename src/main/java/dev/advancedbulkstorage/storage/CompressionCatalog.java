package dev.advancedbulkstorage.storage;

import appeng.api.stacks.AEItemKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

/** Verified exact reversible recipes. Only linear chains without competing ratios are admitted. */
public final class CompressionCatalog {
    static final int MAX_FORMS = 8;
    private static volatile Map<AEItemKey, Chain> index = Map.of();

    private CompressionCatalog() {}

    record Chain(AEItemKey base, Map<AEItemKey, Long> factors) {
        Chain { factors = Map.copyOf(factors); }
        long factor(AEItemKey key) { return factors.getOrDefault(key, 0L); }
    }

    private record RecipeEdge(AEItemKey from, int input, AEItemKey to, int output) {
        RecipeEdge reverse() { return new RecipeEdge(to, output, from, input); }
    }

    static @Nullable Chain lookup(AEItemKey key) { return index.get(key); }

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> rebuild(event.getServer()));
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) {
                rebuild(event.getPlayerList().getServer());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> index = Map.of());
    }

    private static void rebuild(MinecraftServer server) {
        var verified = new HashSet<RecipeEdge>();
        for (var holder : server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            var recipe = holder.value();
            if (recipe.getClass() != ShapedRecipe.class && recipe.getClass() != ShapelessRecipe.class) {
                continue;
            }
            var output = recipe.getResultItem(server.registryAccess());
            if (!plain(output) || output.getCount() > 9) {
                continue;
            }
            ItemStack first = ItemStack.EMPTY;
            int count = 0;
            boolean valid = true;
            for (var ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                var items = ingredient.getItems();
                if (items.length != 1 || !plain(items[0]) || items[0].getCount() != 1
                        || !first.isEmpty() && !ItemStack.isSameItemSameComponents(first, items[0])) {
                    valid = false;
                    break;
                }
                first = items[0];
                count++;
            }
            if (valid && !first.isEmpty() && !first.is(output.getItem()) && count <= 9
                    && (count > 1 && output.getCount() == 1 || count == 1 && output.getCount() > 1)) {
                verified.add(new RecipeEdge(AEItemKey.of(first), count, AEItemKey.of(output), output.getCount()));
            }
        }
        var edges = new HashMap<AEItemKey, RecipeEdge>();
        var incoming = new HashMap<AEItemKey, Integer>();
        var ambiguous = new HashSet<AEItemKey>();
        for (var edge : verified) {
            if (edge.input() <= 1 || !verified.contains(edge.reverse())) {
                continue;
            }
            if (edges.putIfAbsent(edge.from(), edge) != null) {
                ambiguous.add(edge.from());
            }
            incoming.merge(edge.to(), 1, Integer::sum);
        }
        var result = new HashMap<AEItemKey, Chain>();
        for (var root : edges.keySet()) {
            if (incoming.containsKey(root)) {
                continue;
            }
            var factors = new LinkedHashMap<AEItemKey, Long>();
            factors.put(root, 1L);
            var key = root;
            long factor = 1;
            boolean valid = true;
            while (edges.containsKey(key)) {
                var edge = edges.get(key);
                if (ambiguous.contains(key) || incoming.getOrDefault(edge.to(), 0) != 1
                        || factors.containsKey(edge.to()) || factors.size() >= MAX_FORMS) {
                    valid = false;
                    break;
                }
                key = edge.to();
                factor *= edge.input();
                factors.put(key, factor);
            }
            if (valid && factors.size() > 1) {
                var chain = new Chain(root, factors);
                factors.keySet().forEach(form -> result.put(form, chain));
            }
        }
        index = Map.copyOf(result);
    }

    private static boolean plain(ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxStackSize() > 1 && stack.isComponentsPatchEmpty()
                && !stack.getItem().hasCraftingRemainingItem() && stack.getCraftingRemainingItem().isEmpty();
    }
}
