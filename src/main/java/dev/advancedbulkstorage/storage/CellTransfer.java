package dev.advancedbulkstorage.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.items.storage.BasicStorageCell;
import appeng.me.cells.BasicCellInventory;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** A batch for two physical cell slots saved by the same host, never a storage network projection. */
public final class CellTransfer {
    private static final IActionSource MACHINE = IActionSource.empty();
    private static final ISaveProvider DEFER = () -> {};

    private final StorageCell source;
    private final StorageCell destination;
    private final @Nullable ItemStack newSource;
    private final @Nullable ItemStack newDestination;
    private boolean moved;
    private boolean failed;
    private boolean committed;

    private CellTransfer(StorageCell source, StorageCell destination, @Nullable ItemStack newSource,
                         @Nullable ItemStack newDestination) {
        this.source = source;
        this.destination = destination;
        this.newSource = newSource;
        this.newDestination = newDestination;
    }

    public static boolean supported(ItemStack stack) {
        return stack.getItem() instanceof BulkCellItem
                || stack.getItem() instanceof BasicStorageCell basic && basic.getKeyType() == AEKeyType.items()
                || stack.is(gripe._90.megacells.definition.MEGAItems.BULK_ITEM_CELL.asItem());
    }

    public static @Nullable CellTransfer beginOwned(BulkCellInventory source, BulkCellInventory target) {
        return source.sameInventory(target) ? null : new CellTransfer(source, target, null, null);
    }

    public static @Nullable CellTransfer begin(ItemStack sourceStack, StorageCell sourceInventory,
                                                ItemStack destinationStack, StorageCell destinationInventory) {
        UUID uuid = sourceStack.get(dev.advancedbulkstorage.BulkStorageMod.CELL_ID);
        if (sourceStack == destinationStack || sourceInventory == destinationInventory
                || uuid != null && uuid.equals(destinationStack.get(dev.advancedbulkstorage.BulkStorageMod.CELL_ID))) {
            return null;
        }
        if (sourceInventory instanceof BulkCellInventory from && destinationInventory instanceof BulkCellInventory to) {
            if (from.sameInventory(to)) return null;
            if (from.supportsDirectTransfer() && to.supportsDirectTransfer()) return beginOwned(from, to);
        }
        if (!supported(sourceStack) || !supported(destinationStack)) {
            return null;
        }
        sourceInventory.persist();
        destinationInventory.persist();
        var sourceCopy = sourceStack.copy();
        var destinationCopy = destinationStack.copy();
        var input = StorageCells.getCellInventory(sourceCopy, DEFER);
        var output = StorageCells.getCellInventory(destinationCopy, DEFER);
        if (!supportsSnapshot(input) || !supportsSnapshot(output)) {
            return null;
        }
        return new CellTransfer(input, output, sourceCopy, destinationCopy);
    }

    static boolean supportsSnapshot(@Nullable StorageCell inventory) {
        return inventory instanceof BulkCellInventory || inventory != null
                && (inventory.getClass() == BasicCellInventory.class
                    || inventory.getClass() == gripe._90.megacells.item.cell.BulkCellInventory.class);
    }

    /**
     * Direct cells update both live maps together. Other supported finite cells mutate detached snapshots.
     * A mismatch in the staged path discards the whole batch, including any preceding keys.
     */
    public long move(AEItemKey key, long requested) {
        if (failed || committed || requested <= 0) {
            return 0;
        }
        long amount;
        if (newSource == null) {
            amount = ((BulkCellInventory) source).moveOwnedStock((BulkCellInventory) destination, key, requested);
        } else {
            long available = source.extract(key, requested, Actionable.SIMULATE, MACHINE);
            if (available < 0 || available > requested) {
                failed = true;
                return 0;
            }
            if (available == 0) {
                return 0;
            }
            amount = destination.insert(key, available, Actionable.SIMULATE, MACHINE);
            if (amount == 0) {
                return 0;
            }
            if (amount < 0 || amount > available
                    || source.extract(key, amount, Actionable.MODULATE, MACHINE) != amount
                    || destination.insert(key, amount, Actionable.MODULATE, MACHINE) != amount) {
                failed = true;
                return 0;
            }
        }
        moved |= amount > 0;
        return amount;
    }

    public boolean failed() {
        return failed;
    }

    public boolean commit(BiConsumer<ItemStack, ItemStack> installPair) {
        if (committed || failed || !moved) {
            return false;
        }
        committed = true;
        if (newSource != null) {
            source.persist();
            destination.persist();
            installPair.accept(newSource, newDestination);
        } else {
            // Both states are already committed. Notify the shared block entity only after both writes.
            ((BulkCellInventory) source).publishChange();
            ((BulkCellInventory) destination).publishChange();
        }
        return true;
    }
}
