package dev.advancedbulkstorage.storage;

import appeng.api.config.FuzzyMode;
import appeng.api.implementations.menuobjects.IMenuItem;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.items.contents.CellConfig;
import appeng.menu.MenuOpener;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.util.ConfigInventory;
import dev.advancedbulkstorage.BulkConfig;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.CellTier;
import dev.advancedbulkstorage.filter.FilterCapabilities;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.menu.BulkFilterHost;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class BulkCellItem extends Item implements ICellWorkbenchItem, IMenuItem {
    private final CellTier tier;

    public BulkCellItem(CellTier tier) {
        super(new Properties().stacksTo(1));
        this.tier = tier;
    }

    public CellTier tier() {
        return tier;
    }

    public FilterCapabilities capabilities(ItemStack stack) {
        return FilterCapabilities.forCell(tier, getUpgrades(stack).isInstalled(BulkStorageMod.EXTENDED_TAG_CARD.get()));
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return CellConfig.create(Set.of(AEKeyType.items()), stack);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        var setting = BulkConfig.cell(tier).upgrades();
        int configured = BulkConfig.SPEC.isLoaded() ? setting.get() : tier.defaultSlots();
        int occupied = stack.getOrDefault(AEComponents.UPGRADES, ItemContainerContents.EMPTY).getSlots();
        // Keep an installed card accessible after a slot limit is lowered.
        return UpgradeInventories.forItem(stack, Math.max(configured, Math.min(8, occupied)));
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack stack) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack stack, FuzzyMode mode) {
        // Partitions use exact AEItemKeys.
    }

    @Override
    public ItemMenuHost<?> getMenuHost(Player player, ItemMenuHostLocator locator, @Nullable BlockHitResult hit) {
        return new HandFilterHost(this, player, locator);
    }

    private static final class HandFilterHost extends ItemMenuHost<BulkCellItem> implements BulkFilterHost {
        private HandFilterHost(BulkCellItem cell, Player player, ItemMenuHostLocator locator) {
            super(cell, player, locator);
        }

        @Override
        public ItemStack filterStack() { return getItemStack(); }

        @Override
        public boolean filterAccessible(Player player) { return isValid(); }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            ensureId(stack);
            MenuOpener.open(BulkStorageMod.FILTER_EDITOR.get(), player, MenuLocators.forHand(player, hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static UUID ensureId(ItemStack stack) {
        UUID id = stack.get(BulkStorageMod.CELL_ID);
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(BulkStorageMod.CELL_ID, id);
        }
        return id;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new BulkInventoryTooltip(stack.getOrDefault(BulkStorageMod.CONTENTS, CustomData.EMPTY)));
    }

    @Override
    @SuppressWarnings("deprecation") // Values from the immutable NBT wrapper are inspected without copying or mutating.
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        var config = stack.getOrDefault(BulkStorageMod.FILTER, FilterConfig.EMPTY);
        if (!config.expression().isBlank()) {
            lines.add(Component.translatable(config.blacklist()
                    ? "gui.advanced_bulk_storage.error.legacy_blacklist"
                    : config.expression().strip().equals("*") ? "tooltip.advanced_bulk_storage.old_wildcard"
                    : "gui.advanced_bulk_storage.whitelist")
                    .append("  ").append(config.expression().substring(0, Math.min(96, config.expression().length()))));
        }
        var data = stack.get(BulkStorageMod.CONTENTS);
        int pools = data == null ? 0 : data.getUnsafe().getList("pools", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
        int types = (data == null ? 0 : data.getUnsafe().getList("items", net.minecraft.nbt.Tag.TAG_COMPOUND).size()) + pools;
        var settings = BulkConfig.cell(tier);
        int limit = BulkConfig.SPEC.isLoaded() ? settings.types().get() : tier.defaultTypes();
        var upgrades = stack.getOrDefault(AEComponents.UPGRADES, ItemContainerContents.EMPTY);
        lines.add(Component.translatable("gui.advanced_bulk_storage.tooltip_types", types, limit));
        if (pools > 0) {
            lines.add(Component.translatable("tooltip.advanced_bulk_storage.pool_count", pools));
        }
        if (stack.has(BulkStorageMod.LIMITS)) {
            lines.add(Component.translatable("tooltip.advanced_bulk_storage.limits_configured"));
        }
        if (stack.getOrDefault(BulkStorageMod.AUTO_SORT_ENABLED, false)) {
            lines.add(Component.translatable("tooltip.advanced_bulk_storage.autosort_configured"));
        }
        lines.add(Component.translatable("gui.advanced_bulk_storage.tooltip_upgrades",
                upgrades.nonEmptyStream().count(), BulkConfig.SPEC.isLoaded() ? settings.upgrades().get() : tier.defaultSlots()));
        lines.add(Component.translatable("tooltip.advanced_bulk_storage.open_filter"));
    }
}
