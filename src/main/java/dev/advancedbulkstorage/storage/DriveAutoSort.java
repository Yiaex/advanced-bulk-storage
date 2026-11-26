package dev.advancedbulkstorage.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import dev.advancedbulkstorage.migration.BulkScheduler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/** One wake-driven sorter per grid and save chunk. Both ends share one chunk snapshot. */
final class DriveAutoSort implements BulkScheduler.Job {
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final Comparator<BulkCellIndex.Mount> ORDER =
            Comparator.comparingInt(BulkCellIndex.Mount::priority).reversed()
                    .thenComparingLong(mount -> mount.drive().getBlockPos().asLong())
                    .thenComparingInt(BulkCellIndex.Mount::slot);
    private final BulkCellIndex index;
    private final List<BulkCellIndex.Mount> mounts;
    private final List<BulkCellIndex.Mount> orderedMounts;
    private final List<BulkCellIndex.Mount> active;
    private final int[] rates;
    private final long[] quota;
    private final BulkScheduler scheduler;
    private final ServerLevel level;
    private final List<BulkCellInventory.StockListener> listeners = new ArrayList<>();
    private final LinkedHashSet<AEItemKey> pending = new LinkedHashSet<>();
    private Iterator<AEItemKey> scan = List.<AEItemKey>of().iterator();
    private int scanSlot;
    private boolean orderDirty = true;
    private boolean rescanRequested;
    private boolean pendingFirst = true;
    private boolean applying;

    DriveAutoSort(BulkCellIndex index, List<BulkCellIndex.Mount> mounts, ServerLevel level) {
        this.index = index;
        this.mounts = mounts;
        this.orderedMounts = new ArrayList<>(mounts);
        this.active = new ArrayList<>(mounts.size());
        this.rates = new int[mounts.size()];
        this.quota = new long[mounts.size()];
        this.scheduler = BulkScheduler.get(level.getServer());
        this.level = level;
        for (var mount : mounts) {
            var inventory = mount.inventory();
            BulkCellInventory.StockListener listener = (key, change) -> {
                if (!applying) {
                    if (change < 0 && inventory.autoSortRate() > 0) {
                        // Freeing capacity can unblock a previously visited key. Finish the
                        // current pass before revisiting it, even if extraction continues.
                        rescanRequested = true;
                    }
                    inventory.addAffectedTransferKeys(key, pending);
                    wake();
                }
            };
            listeners.add(listener);
            inventory.observeStock(listener);
        }
    }

    void wake() { scheduler.wake(this, false); }

    void rescan() {
        orderDirty = true;
        wake();
    }

    void detach() {
        scheduler.cancel(this);
        for (var mount : mounts) {
            mount.inventory().observeStock(null);
        }
    }

    @Override
    public boolean run(BulkScheduler.Allowance allowance) {
        if (orderDirty) {
            orderedMounts.sort(ORDER);
            scanSlot = 0;
            scan = List.<AEItemKey>of().iterator();
            orderDirty = false;
            rescanRequested = false;
        } else if (rescanRequested && scanSlot >= mounts.size() && !scan.hasNext()) {
            scanSlot = 0;
            rescanRequested = false;
        }
        active.clear();
        boolean hasTarget = false;
        for (var mount : orderedMounts) {
            if (index.find(mount.id()) == mount) {
                int slot = active.size();
                int rate = mount.inventory().autoSortRate();
                rates[slot] = rate;
                quota[slot] = rate;
                hasTarget |= rate > 0;
                active.add(mount);
            }
        }
        if (!hasTarget) {
            pending.clear();
            orderDirty = true;
            return false;
        }
        var nextTick = new LinkedHashSet<AEItemKey>();
        long tick = level.getGameTime();
        applying = true;
        try {
            for (int checked = 0; checked < 128 && (hasScanWork() || !pending.isEmpty())
                    && allowance.takeKey(); checked++) {
                boolean usePending = !pending.isEmpty() && (pendingFirst || !hasScanWork());
                pendingFirst = !usePending;
                if (!usePending && !scan.hasNext()) {
                    var mount = mounts.get(scanSlot++);
                    if (index.find(mount.id()) == mount) {
                        scan = mount.inventory().transferKeys().iterator();
                    }
                    continue;
                }
                var key = usePending ? pending.removeFirst() : scan.next();
                if (process(key, tick)) {
                    nextTick.add(key);
                }
            }
        } finally {
            applying = false;
        }
        pending.addAll(nextTick);
        return !pending.isEmpty() || hasScanWork() || rescanRequested;
    }

    private boolean hasScanWork() {
        return scan.hasNext() || scanSlot < mounts.size();
    }

    private boolean process(AEItemKey key, long tick) {
        for (int dest = 0; dest < active.size(); dest++) {
            var target = active.get(dest).inventory();
            if (rates[dest] == 0 || target.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, SOURCE) == 0) {
                continue;
            }
            for (int origin = 0; origin < active.size(); origin++) {
                var source = active.get(origin).inventory();
                if (dest == origin || origin < dest && rates[origin] != 0 || source.amount(key) == 0) {
                    continue;
                }
                if (quota[dest] == 0) {
                    return true;
                }
                var batch = CellTransfer.beginOwned(source, target);
                long moved = batch == null ? 0 : batch.move(key, quota[dest]);
                if (moved > 0) {
                    batch.commit((a, b) -> {});
                    quota[dest] -= moved;
                    target.recordSort(tick, moved);
                    return true;
                }
            }
        }
        return false;
    }
}
