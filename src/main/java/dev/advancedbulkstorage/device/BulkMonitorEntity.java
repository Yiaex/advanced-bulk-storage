package dev.advancedbulkstorage.device;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.filter.FilterEngine;
import dev.advancedbulkstorage.menu.BulkFilterHost;
import dev.advancedbulkstorage.storage.BulkCellIndex;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class BulkMonitorEntity extends AENetworkedBlockEntity implements BulkFilterHost, IStorageWatcherNode, IGridTickable {
    public enum Metric { NETWORK_STOCK, DISK_STOCK, TYPE_PERMILLE, SORT_RATE, MIGRATION_RATE, LIMIT_REACHED, DISCARDED }
    public enum Comparison {
        AT_LEAST, GREATER, AT_MOST, LESS, EQUAL;
        boolean matches(int order) {
            return switch (this) {
                case AT_LEAST -> order >= 0;
                case GREATER -> order > 0;
                case AT_MOST -> order <= 0;
                case LESS -> order < 0;
                case EQUAL -> order == 0;
            };
        }
    }

    private final Object2LongOpenHashMap<AEItemKey> matching = new Object2LongOpenHashMap<>();
    private IStackWatcher watcher;
    private FilterConfig rule = FilterConfig.EMPTY;
    private FilterEngine.Compiled filter;
    private int generation = -1;
    private BigInteger total = BigInteger.ZERO;
    private long threshold = 1;
    private Comparison comparison = Comparison.AT_LEAST;
    private Metric metric = Metric.NETWORK_STOCK;
    private UUID disk;
    private BlockPos machine;
    private BulkCellInventory watchedDisk;
    private final BulkCellInventory.StockListener diskListener = this::onDiskChange;
    private long diskRevision = -1;
    private boolean diskChanged;
    private boolean reading;
    private boolean rebuilding;

    public BulkMonitorEntity(BlockPos pos, BlockState state) {
        super(BulkStorageMod.MONITOR_ENTITY.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(4).addService(IStorageWatcherNode.class, this)
                .addService(IGridTickable.class, this);
    }

    @Override protected Item getItemFromBlockEntity() { return getBlockState().getBlock().asItem(); }
    @Override public FilterConfig filterConfig() { return rule; }
    public long count() { return total.bitLength() > 63 ? Long.MAX_VALUE : total.longValue(); }
    public long threshold() { return threshold; }
    public Comparison comparison() { return comparison; }
    public boolean signal() { return getBlockState().getValue(BlockStateProperties.POWERED); }
    public Metric metric() { return metric; }
    public boolean reading() { return reading; }

    public void cycleMetric() {
        metric = Metric.values()[(metric.ordinal() + 1) % Metric.values().length];
        saveChanges();
        refresh();
    }

    public void cycleTarget() {
        var grid = getMainNode().getGrid();
        if (grid == null || metric == Metric.NETWORK_STOCK) return;
        if (metric == Metric.MIGRATION_RATE) {
            var choices = grid.getMachines(BulkMigratorEntity.class).stream()
                    .filter(device -> device.getLevel() == level && !device.isRemoved())
                    .map(BulkMigratorEntity::getBlockPos).sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
            int next = machine == null ? 0 : choices.indexOf(machine) + 1;
            machine = next < choices.size() ? choices.get(next) : null;
        } else {
            var choices = grid.getService(BulkCellIndex.class).available().stream().map(BulkCellIndex.Mount::id).toList();
            int next = disk == null ? 0 : choices.indexOf(disk) + 1;
            disk = next < choices.size() ? choices.get(next) : null;
        }
        saveChanges();
        refresh();
    }

    public String targetLabel() {
        var grid = getMainNode().getGrid();
        if (metric == Metric.NETWORK_STOCK) return "";
        if (metric == Metric.MIGRATION_RATE) return machine == null ? "" : machine.toShortString();
        var mount = grid == null || disk == null ? null : grid.getService(BulkCellIndex.class).mounted(disk);
        return mount == null ? disk == null ? "" : disk.toString()
                : mount.drive().getBlockPos().toShortString() + " #" + (mount.slot() + 1);
    }

    public void setThreshold(long threshold) {
        this.threshold = Math.max(0, threshold);
        saveChanges();
        publishSignal();
    }

    public void nextComparison() {
        comparison = Comparison.values()[(comparison.ordinal() + 1) % Comparison.values().length];
        saveChanges();
        publishSignal();
    }

    @Override
    public void applyFilter(FilterConfig config, Player player) {
        rule = config;
        saveChanges();
        refresh();
    }

    @Override
    public boolean filterAccessible(Player player) {
        return !isRemoved() && level == player.level() && player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }

    @Override
    public void onReady() {
        super.onReady();
        refresh();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        refresh();
    }

    @Override
    public void updateWatcher(IStackWatcher watcher) {
        this.watcher = watcher;
        refresh();
    }

    @Override
    public void onStackChange(AEKey key, long amount) {
        if (metric != Metric.NETWORK_STOCK || rebuilding || !(key instanceof AEItemKey item)) {
            return;
        }
        if (generation != FilterEngine.generation()) {
            refresh();
            return;
        }
        if (readyToMeasure() && filter.matches(item)) {
            long safe = Math.max(0, amount);
            long former = safe == 0 ? matching.removeLong(item) : matching.put(item, safe);
            total = total.add(BigInteger.valueOf(safe - former));
            publishSignal();
        }
    }

    private boolean readyToMeasure() {
        return metric == Metric.NETWORK_STOCK && filter != null && filter.error() == null
                && !rule.expression().isBlank() && getMainNode().isOnline();
    }

    private void refresh() {
        refresh(true);
    }

    private void refresh(boolean changeWatchList) {
        if (!(level instanceof ServerLevel) || rebuilding) {
            return;
        }
        rebuilding = true;
        try {
            filter = FilterEngine.compile(rule, filterCapabilities());
            generation = FilterEngine.generation();
            matching.clear();
            total = BigInteger.ZERO;
            detachDisk();
            if (watcher != null && changeWatchList) {
                watcher.reset();
                watcher.setWatchAll(readyToMeasure());
            }
            var grid = getMainNode().getGrid();
            if (grid != null && readyToMeasure()) {
                for (var entry : grid.getStorageService().getCachedInventory()) {
                    if (entry.getKey() instanceof AEItemKey item && entry.getLongValue() > 0 && filter.matches(item)) {
                        long amount = entry.getLongValue();
                        matching.put(item, amount);
                        total = total.add(BigInteger.valueOf(amount));
                    }
                }
            }
            reading = readyToMeasure();
            if (metric != Metric.NETWORK_STOCK) refreshDevice();
            publishSignal();
        } finally {
            rebuilding = false;
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(10, 10, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (generation != FilterEngine.generation()) {
            refresh();
        } else if (metric != Metric.NETWORK_STOCK) {
            refreshDevice();
            publishSignal();
        }
        return TickRateModulation.SAME;
    }

    private boolean matchesDisk(AEItemKey key) {
        return rule.expression().isBlank() || filter != null && filter.error() == null && filter.matches(key);
    }

    private void onDiskChange(AEItemKey key, long difference) {
        if (matchesDisk(key)) total = total.add(BigInteger.valueOf(difference));
        diskChanged = true;
    }

    private void detachDisk() {
        if (watchedDisk != null) {
            watchedDisk.removeStockMonitor(diskListener);
            watchedDisk = null;
        }
    }

    private void refreshDevice() {
        var grid = getMainNode().getGrid();
        if (!(level instanceof ServerLevel server) || grid == null || !getMainNode().isOnline()) {
            detachDisk();
            total = BigInteger.ZERO;
            reading = false;
            return;
        }
        if (metric == Metric.MIGRATION_RATE) {
            var entity = machine != null && server.hasChunkAt(machine) ? server.getBlockEntity(machine) : null;
            reading = entity instanceof BulkMigratorEntity migrator && migrator.getMainNode().getGrid() == grid
                    && migrator.getMainNode().isOnline();
            total = BigInteger.valueOf(reading ? ((BulkMigratorEntity) entity).sampledRate() : 0);
            return;
        }
        var mount = disk == null ? null : grid.getService(BulkCellIndex.class).find(disk);
        if (mount == null || mount.inventory().quarantined()) {
            detachDisk();
            total = BigInteger.ZERO;
            reading = false;
            return;
        }
        var inventory = mount.inventory();
        reading = metric != Metric.DISK_STOCK || rule.expression().isBlank() || filter.error() == null;
        if (metric == Metric.DISK_STOCK) {
            if (watchedDisk != inventory || inventory.revision() != diskRevision && !diskChanged) {
                detachDisk();
                total = BigInteger.ZERO;
                inventory.visitPhysicalStock((key, amount, pool) -> {
                    if (matchesDisk(key)) total = total.add(BigInteger.valueOf(amount));
                });
                watchedDisk = inventory;
                inventory.addStockMonitor(diskListener);
            }
            diskRevision = inventory.revision();
            diskChanged = false;
        } else {
            total = BigInteger.valueOf(switch (metric) {
                case TYPE_PERMILLE -> 1000L * inventory.types() / inventory.maximumTypes();
                case SORT_RATE -> inventory.recentSortRate(mount.drive().getLevel().getGameTime());
                case LIMIT_REACHED -> inventory.atConfiguredLimit() ? 1 : 0;
                case DISCARDED -> inventory.discardedItems();
                default -> 0;
            });
        }
    }

    @Override
    public void setRemoved() {
        detachDisk();
        super.setRemoved();
    }

    private void publishSignal() {
        if (!(level instanceof ServerLevel server) || isRemoved()) {
            return;
        }
        boolean value = reading && getMainNode().isOnline() && comparison.matches(total.compareTo(BigInteger.valueOf(threshold)));
        var state = getBlockState();
        if (state.getValue(BlockStateProperties.POWERED) != value) {
            server.setBlock(worldPosition, state.setValue(BlockStateProperties.POWERED, value), Block.UPDATE_ALL);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        rule = new FilterConfig(tag.getString("filter"), false);
        threshold = tag.contains("threshold") ? Math.max(0, tag.getLong("threshold")) : 1;
        int cmp = tag.getInt("comparison");
        comparison = cmp >= 0 && cmp < Comparison.values().length ? Comparison.values()[cmp] : Comparison.AT_LEAST;
        int index = tag.getInt("metric");
        metric = index >= 0 && index < Metric.values().length ? Metric.values()[index] : Metric.NETWORK_STOCK;
        disk = tag.hasUUID("disk") ? tag.getUUID("disk") : null;
        machine = tag.contains("machine") ? BlockPos.of(tag.getLong("machine")) : null;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("filter", rule.expression());
        tag.putLong("threshold", threshold);
        tag.putInt("comparison", comparison.ordinal());
        tag.putInt("metric", metric.ordinal());
        if (disk != null) tag.putUUID("disk", disk);
        if (machine != null) tag.putLong("machine", machine.asLong());
    }
}
