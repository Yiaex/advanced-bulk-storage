package dev.advancedbulkstorage.menu;

import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.CellTier;
import dev.advancedbulkstorage.filter.FilterCapabilities;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.storage.BulkCellItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface BulkFilterHost {
    default ItemStack filterStack() { return ItemStack.EMPTY; }

    default Component filterTitle() {
        return !filterStack().isEmpty() ? filterStack().getHoverName()
                : this instanceof Nameable named ? named.getName() : Component.empty();
    }

    default FilterCapabilities filterCapabilities() {
        var stack = filterStack();
        return stack.getItem() instanceof BulkCellItem cell ? cell.capabilities(stack)
                : FilterCapabilities.forCell(CellTier.QUANTUM, false);
    }

    default FilterConfig filterConfig() {
        return filterStack().getOrDefault(BulkStorageMod.FILTER, FilterConfig.EMPTY);
    }

    default void applyFilter(FilterConfig config, Player player) {
        filterStack().set(BulkStorageMod.FILTER, config);
        filterChanged(player);
    }

    default void filterChanged(Player player) {
        player.getInventory().setChanged();
    }

    boolean filterAccessible(Player player);
}
