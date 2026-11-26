package dev.advancedbulkstorage.device;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.blockentity.inventory.AppEngCellInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import dev.advancedbulkstorage.BulkConfig;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.storage.BulkCellIndex;
import dev.advancedbulkstorage.storage.BulkCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class BulkDriveEntity extends AENetworkedInvBlockEntity implements IStorageProvider, IChestOrDrive {
    public static final int SLOTS = 32;
    private final AppEngCellInventory cells = new AppEngCellInventory(this, SLOTS);
    private final StorageCell[] mounted = new StorageCell[SLOTS];
    private int priority;
    private boolean loading;
    private boolean clientOnline;
    private boolean lastOnline;

    public BulkDriveEntity(BlockPos pos, BlockState state) {
        super(BulkStorageMod.BULK_DRIVE_ENTITY.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).addService(IStorageProvider.class, this);
        cells.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                int enabled = BulkConfig.SPEC.isLoaded() ? BulkConfig.BULK_DRIVE_SLOTS.get() : SLOTS;
                return slot < enabled && supported(stack);
            }
        });
    }

    private static boolean supported(ItemStack stack) {
        return stack.getItem() instanceof BulkCellItem
                || stack.is(gripe._90.megacells.definition.MEGAItems.BULK_ITEM_CELL.asItem());
    }

    @Override
    protected Item getItemFromBlockEntity() { return getBlockState().getBlock().asItem(); }
    @Override
    public InternalInventory getInternalInventory() { return cells; }
    @Override
    public int getCellCount() { return SLOTS; }
    @Override
    public boolean isCellBlinking(int slot) { return false; }
    @Override
    public @Nullable Item getCellItem(int slot) {
        var stack = cells.getStackInSlot(slot);
        return stack.isEmpty() ? null : stack.getItem();
    }
    @Override
    public @Nullable MEStorage getCellInventory(int slot) { return mounted[slot]; }
    @Override
    public @Nullable StorageCell getOriginalCellInventory(int slot) { return mounted[slot]; }
    @Override
    public CellState getCellStatus(int slot) {
        var cell = mounted[slot];
        return cell == null ? CellState.ABSENT : cell.getStatus();
    }
    @Override
    public boolean isPowered() {
        return level != null && (level.isClientSide() ? clientOnline : getMainNode().isOnline());
    }

    public int priority() { return priority; }

    public void setPriority(int value) {
        if (priority == value) return;
        priority = value;
        saveChanges();
        IStorageProvider.requestUpdate(getMainNode());
        BulkCellIndex.wakeSorting(this);
    }

    @Override
    public void onReady() {
        for (int slot = 0; slot < SLOTS; slot++) {
            updateSlot(slot);
        }
        refreshDrain();
        super.onReady();
        BulkCellIndex.refresh(this);
    }

    @Override
    public void mountInventories(IStorageMounts mounts) {
        if (isPowered()) {
            for (var cell : mounted) {
                if (cell != null) {
                    mounts.mount(cell, priority);
                }
            }
        }
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (loading || level == null || level.isClientSide()) {
            return;
        }
        updateSlot(slot);
        refreshDrain();
        BulkCellIndex.refresh(this);
        IStorageProvider.requestUpdate(getMainNode());
        markForUpdate();
    }

    private void updateSlot(int slot) {
        cells.setHandler(slot, null);
        var stack = cells.getStackInSlot(slot);
        var cell = supported(stack) ? StorageCells.getCellInventory(stack, this::markInventory) : null;
        mounted[slot] = cell;
        cells.setHandler(slot, cell);
    }

    private void markInventory() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    private void refreshDrain() {
        double drain = BulkConfig.SPEC.isLoaded() ? BulkConfig.BULK_DRIVE_DRAIN.get() : 4;
        for (var cell : mounted) {
            if (cell != null) {
                drain += cell.getIdleDrain();
            }
        }
        getMainNode().setIdlePowerUsage(drain);
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        BulkCellIndex.driveAvailabilityChanged(this);
        boolean online = getMainNode().isOnline();
        if (lastOnline != online) {
            lastOnline = online;
            IStorageProvider.requestUpdate(getMainNode());
        }
        BulkCellIndex.wakeSorting(this);
        markForUpdate();
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        loading = true;
        try {
            super.loadTag(tag, registries);
            priority = tag.getInt("priority");
        } finally {
            loading = false;
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("priority", priority);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(isPowered());
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean online = data.readBoolean();
        changed |= online != clientOnline;
        clientOnline = online;
        return changed;
    }
}
