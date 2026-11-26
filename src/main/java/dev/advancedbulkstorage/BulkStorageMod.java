package dev.advancedbulkstorage;

import appeng.api.AECapabilities;
import appeng.api.networking.GridServices;
import appeng.api.storage.StorageCells;
import appeng.api.upgrades.Upgrades;
import dev.advancedbulkstorage.device.BulkItemAccess;
import dev.advancedbulkstorage.device.BulkInterfaceBlock;
import dev.advancedbulkstorage.device.BulkInterfaceEntity;
import dev.advancedbulkstorage.device.BulkMigratorBlock;
import dev.advancedbulkstorage.device.BulkMigratorEntity;
import dev.advancedbulkstorage.device.MigratorTier;
import dev.advancedbulkstorage.device.BulkDriveBlock;
import dev.advancedbulkstorage.device.BulkDriveEntity;
import dev.advancedbulkstorage.device.BulkWorkbenchBlock;
import dev.advancedbulkstorage.device.BulkWorkbenchEntity;
import dev.advancedbulkstorage.device.BulkManagerBlock;
import dev.advancedbulkstorage.device.BulkManagerEntity;
import dev.advancedbulkstorage.device.BulkMonitorBlock;
import dev.advancedbulkstorage.device.BulkMonitorEntity;
import dev.advancedbulkstorage.migration.BulkScheduler;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.filter.FilterEngine;
import dev.advancedbulkstorage.filter.FilterPresets;
import dev.advancedbulkstorage.menu.FilterEditorMenu;
import dev.advancedbulkstorage.menu.BulkInterfaceMenu;
import dev.advancedbulkstorage.menu.BulkMigratorMenu;
import dev.advancedbulkstorage.menu.BulkDriveMenu;
import dev.advancedbulkstorage.menu.BulkManagerMenu;
import dev.advancedbulkstorage.menu.BulkMonitorMenu;
import dev.advancedbulkstorage.storage.BulkCellItem;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import dev.advancedbulkstorage.storage.BulkCellIndex;
import dev.advancedbulkstorage.storage.CellContentsStream;
import dev.advancedbulkstorage.storage.CompressionCatalog;
import dev.advancedbulkstorage.storage.LimitProfile;
import dev.advancedbulkstorage.storage.AutoSortTier;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(BulkStorageMod.ID)
public final class BulkStorageMod {
    public static final String ID = "advanced_bulk_storage";
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final Map<CellTier, DeferredHolder<Item, BulkCellItem>> CELLS = cells();
    public static final DeferredHolder<Item, Item> EXTENDED_TAG_CARD =
            ITEMS.register("extended_tag_card", () -> Upgrades.createUpgradeCardItem(new Item.Properties()));
    public static final DeferredHolder<Item, Item> MULTI_COMPRESSION_CARD =
            ITEMS.register("multi_compression_card", () -> Upgrades.createUpgradeCardItem(new Item.Properties()));
    public static final DeferredHolder<Item, Item> STORAGE_LIMIT_CARD =
            ITEMS.register("storage_limit_card", () -> Upgrades.createUpgradeCardItem(new Item.Properties()));
    public static final DeferredHolder<Item, Item> ENERGY_SAVING_CARD =
            ITEMS.register("energy_saving_card", () -> Upgrades.createUpgradeCardItem(new Item.Properties()));
    public static final Map<AutoSortTier, DeferredHolder<Item, Item>> SORTING_CARDS = sortingCards();
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilterConfig>> FILTER =
            COMPONENTS.registerComponentType("filter", builder -> builder
                    .persistent(FilterConfig.CODEC).networkSynchronized(FilterConfig.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> CELL_ID =
            COMPONENTS.registerComponentType("cell_id", builder -> builder.persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CustomData>> CONTENTS =
            COMPONENTS.registerComponentType("contents", builder -> builder.persistent(CustomData.CODEC)
                    .networkSynchronized(CellContentsStream.CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> LIMITS =
            COMPONENTS.registerComponentType("limits", builder -> builder
                    .persistent(com.mojang.serialization.Codec.STRING.validate(value -> value.length() <= LimitProfile.MAX_LENGTH
                            ? com.mojang.serialization.DataResult.success(value)
                            : com.mojang.serialization.DataResult.error(() -> "Oversized bulk storage limit profile")))
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.stringUtf8(LimitProfile.MAX_LENGTH)));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> AUTO_SORT_ENABLED =
            COMPONENTS.registerComponentType("auto_sort_enabled", builder -> builder
                    .persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> DISCARDED_ITEMS =
            COMPONENTS.registerComponentType("discarded_items", builder -> builder
                    .persistent(com.mojang.serialization.Codec.LONG.validate(count -> count >= 0
                            ? com.mojang.serialization.DataResult.success(count)
                            : com.mojang.serialization.DataResult.error(() -> "Negative discarded item count")))
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG));
    public static final DeferredHolder<MenuType<?>, MenuType<FilterEditorMenu>> FILTER_EDITOR =
            MENUS.register("filter_editor", FilterEditorMenu::createType);
    public static final net.neoforged.neoforge.registries.DeferredBlock<BulkInterfaceBlock> BASIC_INTERFACE =
            BLOCKS.register("bulk_storage_interface", () -> new BulkInterfaceBlock(false));
    public static final net.neoforged.neoforge.registries.DeferredBlock<BulkInterfaceBlock> ADVANCED_INTERFACE =
            BLOCKS.register("advanced_bulk_storage_interface", () -> new BulkInterfaceBlock(true));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BulkInterfaceEntity>> INTERFACE_ENTITY =
            BLOCK_ENTITIES.register("bulk_storage_interface", () -> BlockEntityType.Builder.of(
                    BulkInterfaceEntity::new, BASIC_INTERFACE.get(), ADVANCED_INTERFACE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<BulkInterfaceMenu>> INTERFACE_MENU =
            MENUS.register("bulk_storage_interface", BulkInterfaceMenu::createType);
    public static final Map<MigratorTier, DeferredBlock<BulkMigratorBlock>> MIGRATORS = migrators();
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BulkMigratorEntity>> MIGRATOR_ENTITY =
            BLOCK_ENTITIES.register("bulk_storage_migrator", () -> BlockEntityType.Builder.of(BulkMigratorEntity::new,
                    MIGRATORS.values().stream().map(DeferredBlock::get).toArray(BulkMigratorBlock[]::new)).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<BulkMigratorMenu>> MIGRATOR_MENU =
            MENUS.register("bulk_storage_migrator", BulkMigratorMenu::createType);
    public static final DeferredBlock<BulkDriveBlock> BULK_DRIVE = BLOCKS.register("bulk_storage_drive", BulkDriveBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BulkDriveEntity>> BULK_DRIVE_ENTITY =
            BLOCK_ENTITIES.register("bulk_storage_drive",
                    () -> BlockEntityType.Builder.of(BulkDriveEntity::new, BULK_DRIVE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<BulkDriveMenu>> BULK_DRIVE_MENU =
            MENUS.register("bulk_storage_drive", BulkDriveMenu::createType);
    public static final DeferredBlock<BulkWorkbenchBlock> WORKBENCH =
            BLOCKS.register("advanced_cell_workbench", BulkWorkbenchBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BulkWorkbenchEntity>> WORKBENCH_ENTITY =
            BLOCK_ENTITIES.register("advanced_cell_workbench",
                    () -> BlockEntityType.Builder.of(BulkWorkbenchEntity::new, WORKBENCH.get()).build(null));
    public static final DeferredBlock<BulkManagerBlock> MANAGER = BLOCKS.register("bulk_storage_manager", BulkManagerBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BulkManagerEntity>> MANAGER_ENTITY =
            BLOCK_ENTITIES.register("bulk_storage_manager",
                    () -> BlockEntityType.Builder.of(BulkManagerEntity::new, MANAGER.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<BulkManagerMenu>> MANAGER_MENU =
            MENUS.register("bulk_storage_manager", BulkManagerMenu::createType);
    public static final DeferredBlock<BulkMonitorBlock> MONITOR = BLOCKS.register("bulk_storage_monitor", BulkMonitorBlock::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BulkMonitorEntity>> MONITOR_ENTITY =
            BLOCK_ENTITIES.register("bulk_storage_monitor",
                    () -> BlockEntityType.Builder.of(BulkMonitorEntity::new, MONITOR.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<BulkMonitorMenu>> MONITOR_MENU =
            MENUS.register("bulk_storage_monitor", BulkMonitorMenu::createType);

    static {
        ITEMS.register("bulk_storage_interface", () -> new BlockItem(BASIC_INTERFACE.get(), new Item.Properties()));
        ITEMS.register("advanced_bulk_storage_interface", () -> new BlockItem(ADVANCED_INTERFACE.get(), new Item.Properties()));
        ITEMS.register("bulk_storage_drive", () -> new BlockItem(BULK_DRIVE.get(), new Item.Properties()));
        ITEMS.register("advanced_cell_workbench", () -> new BlockItem(WORKBENCH.get(), new Item.Properties()));
        ITEMS.register("bulk_storage_manager", () -> new BlockItem(MANAGER.get(), new Item.Properties()));
        ITEMS.register("bulk_storage_monitor", () -> new BlockItem(MONITOR.get(), new Item.Properties()));
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.advanced_bulk_storage"))
                .icon(() -> CELLS.get(CellTier.QUANTUM).get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    CELLS.values().forEach(cell -> output.accept(cell.get()));
                    output.accept(EXTENDED_TAG_CARD.get());
                    output.accept(MULTI_COMPRESSION_CARD.get());
                    output.accept(STORAGE_LIMIT_CARD.get());
                    output.accept(ENERGY_SAVING_CARD.get());
                    SORTING_CARDS.values().forEach(card -> output.accept(card.get()));
                    output.accept(BASIC_INTERFACE.get());
                    output.accept(ADVANCED_INTERFACE.get());
                    MIGRATORS.values().forEach(block -> output.accept(block.get()));
                    output.accept(BULK_DRIVE.get());
                    output.accept(WORKBENCH.get());
                    output.accept(MANAGER.get());
                    output.accept(MONITOR.get());
                }).build());
    }

    public BulkStorageMod(IEventBus bus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, BulkConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, BulkTooltipConfig.SPEC, ID + "-client.toml");
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        COMPONENTS.register(bus);
        TABS.register(bus);
        MENUS.register(bus);
        bus.addListener(this::setup);
        bus.addListener(this::registerCapabilities);
        bus.addListener(this::reloadConfig);
        NeoForge.EVENT_BUS.addListener(this::updateTags);
        NeoForge.EVENT_BUS.addListener(this::reloadData);
        NeoForge.EVENT_BUS.addListener(this::stopServer);
        NeoForge.EVENT_BUS.addListener(this::copyPlayerData);
        BulkScheduler.registerEvents();
        CompressionCatalog.registerEvents();
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GridServices.register(BulkCellIndex.class, BulkCellIndex.class);
            BASIC_INTERFACE.get().setBlockEntity(BulkInterfaceEntity.class, INTERFACE_ENTITY.get(), null, null);
            ADVANCED_INTERFACE.get().setBlockEntity(BulkInterfaceEntity.class, INTERFACE_ENTITY.get(), null, null);
            BULK_DRIVE.get().setBlockEntity(BulkDriveEntity.class, BULK_DRIVE_ENTITY.get(), null, null);
            WORKBENCH.get().setBlockEntity(BulkWorkbenchEntity.class, WORKBENCH_ENTITY.get(), null, null);
            MANAGER.get().setBlockEntity(BulkManagerEntity.class, MANAGER_ENTITY.get(), null, null);
            MONITOR.get().setBlockEntity(BulkMonitorEntity.class, MONITOR_ENTITY.get(), null, null);
            MIGRATORS.forEach((tier, block) -> {
                block.get().setBlockEntity(BulkMigratorEntity.class, MIGRATOR_ENTITY.get(), null, null);
                Upgrades.add(appeng.core.definitions.AEItems.SPEED_CARD, block.get(), tier.upgrades);
                Upgrades.add(ENERGY_SAVING_CARD.get(), block.get(), tier.upgrades);
            });
            StorageCells.addCellHandler(new BulkCellInventory.Handler());
            for (var cell : CELLS.values()) {
                Upgrades.add(EXTENDED_TAG_CARD.get(), cell.get(), 1);
                Upgrades.add(MULTI_COMPRESSION_CARD.get(), cell.get(), 1);
                Upgrades.add(STORAGE_LIMIT_CARD.get(), cell.get(), 1);
                Upgrades.add(ENERGY_SAVING_CARD.get(), cell.get(), cell.get().tier().defaultSlots());
                SORTING_CARDS.values().forEach(card -> Upgrades.add(card.get(), cell.get(), 1));
            }
        });
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, INTERFACE_ENTITY.get(), (entity, context) -> entity);
        event.setNonProxyable(BulkItemAccess.CAPABILITY);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, INTERFACE_ENTITY.get(), BulkInterfaceEntity::itemHandler);
        event.registerBlockEntity(BulkItemAccess.CAPABILITY, INTERFACE_ENTITY.get(), BulkInterfaceEntity::bulkHandler);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, MIGRATOR_ENTITY.get(), (entity, context) -> entity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, BULK_DRIVE_ENTITY.get(), (entity, context) -> entity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, MANAGER_ENTITY.get(), (entity, context) -> entity);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, MONITOR_ENTITY.get(), (entity, context) -> entity);
    }

    private void updateTags(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            FilterEngine.reset();
        }
    }

    private void reloadConfig(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == BulkConfig.SPEC) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(FilterEngine::reset);
            }
        }
    }

    private void stopServer(ServerStoppedEvent event) {
        FilterEngine.clear();
        FilterPresets.INSTANCE.clear();
    }

    private void reloadData(AddReloadListenerEvent event) {
        event.addListener(FilterPresets.INSTANCE);
    }

    private void copyPlayerData(PlayerEvent.Clone event) {
        FilterEditorMenu.copyPersonalRules(event.getOriginal(), event.getEntity());
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    private static Map<CellTier, DeferredHolder<Item, BulkCellItem>> cells() {
        var cells = new EnumMap<CellTier, DeferredHolder<Item, BulkCellItem>>(CellTier.class);
        for (var tier : CellTier.values()) {
            cells.put(tier, ITEMS.register(tier.id(), () -> new BulkCellItem(tier)));
        }
        return java.util.Collections.unmodifiableMap(cells);
    }

    private static Map<MigratorTier, DeferredBlock<BulkMigratorBlock>> migrators() {
        var blocks = new EnumMap<MigratorTier, DeferredBlock<BulkMigratorBlock>>(MigratorTier.class);
        for (var tier : MigratorTier.values()) {
            var block = BLOCKS.register(tier.id, () -> new BulkMigratorBlock(tier));
            ITEMS.register(tier.id, () -> new BlockItem(block.get(), new Item.Properties()));
            blocks.put(tier, block);
        }
        return java.util.Collections.unmodifiableMap(blocks);
    }

    private static Map<AutoSortTier, DeferredHolder<Item, Item>> sortingCards() {
        var cards = new EnumMap<AutoSortTier, DeferredHolder<Item, Item>>(AutoSortTier.class);
        for (var tier : AutoSortTier.values()) {
            cards.put(tier, ITEMS.register(tier.id, () -> Upgrades.createUpgradeCardItem(new Item.Properties())));
        }
        return java.util.Collections.unmodifiableMap(cards);
    }
}
