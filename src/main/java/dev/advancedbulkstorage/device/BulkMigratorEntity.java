package dev.advancedbulkstorage.device;

import appeng.api.config.PowerMultiplier;
import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import dev.advancedbulkstorage.BulkConfig;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.CellTier;
import dev.advancedbulkstorage.filter.FilterCapabilities;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.filter.FilterEngine;
import dev.advancedbulkstorage.migration.BulkScheduler;
import dev.advancedbulkstorage.menu.BulkFilterHost;
import dev.advancedbulkstorage.storage.CellTransfer;
import dev.advancedbulkstorage.storage.BulkCellIndex;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class BulkMigratorEntity extends AENetworkedInvBlockEntity implements BulkFilterHost {
    public enum Energy { FIXED, BATCH, AE2 }
    public enum Signal { IGNORE, HIGH, LOW }
    public enum Scope { SLOTS, SLOT_TO_GRID, GRID_TO_SLOT, GRID_PAIR }
    public enum Status { PAUSED, OFFLINE, REDSTONE, NEED_CELLS, READY, RUNNING, BLOCKED, FINISHED, UNSUPPORTED, ERROR, ENDPOINT_UNAVAILABLE }

    private final CellSlots cells = new CellSlots();
    private final IUpgradeInventory upgrades;
    private final BulkScheduler.Job job = this::work;
    private final ItemStack[] attached = {ItemStack.EMPTY, ItemStack.EMPTY};
    private final StorageCell[] inventories = new StorageCell[2];
    private final ArrayDeque<AEItemKey> pending = new ArrayDeque<>();
    private boolean configured;
    private boolean reversed;
    private boolean rescan = true;
    private boolean installing;
    private Energy energy = Energy.FIXED;
    private Signal signal = Signal.IGNORE;
    private Scope scope = Scope.SLOTS;
    private UUID origin;
    private UUID destination;
    private BulkCellIndex routeGrid;
    private long routeRevision = -1;
    private List<Route> routes = List.of();
    private int routeIndex;
    private boolean blockedRoute;
    private BulkCellIndex watchedGrid;
    private final Set<BulkCellInventory> watchedDisks = new HashSet<>();
    private final BulkCellInventory.StockListener stockListener = (key, delta) -> remoteStockChanged();
    private boolean transferring;
    private @Nullable BigInteger remainingItems;
    private Status status = Status.PAUSED;
    private FilterConfig rule = FilterConfig.EMPTY;
    private FilterEngine.Compiled filter;
    private int filterGeneration = -1;
    private long totalMoved;
    private long lastMoved;
    private long lastMoveTime = Long.MIN_VALUE;

    public BulkMigratorEntity(BlockPos pos, BlockState state) {
        super(BulkStorageMod.MIGRATOR_ENTITY.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(BulkConfig.migratorDrain(tier()));
        upgrades = UpgradeInventories.forMachine(getItemFromBlockEntity(), tier().upgrades, this::onUpgradesChanged);
    }

    public MigratorTier tier() { return ((BulkMigratorBlock) getBlockState().getBlock()).tier(); }
    public IUpgradeInventory upgrades() { return upgrades; }
    public boolean configured() { return configured; }
    public boolean reversed() { return reversed; }
    public Energy energyMode() { return energy; }
    public Signal signalMode() { return signal; }
    public Scope scope() { return scope; }
    public Status status() { return status; }
    public int remainingKeys() { return pending.size(); }
    public long remainingItems() {
        return remainingItems == null || remainingItems.bitLength() > 63 ? -1 : remainingItems.longValue();
    }
    public long totalMoved() { return totalMoved; }
    public long rate() { return tier().rate(upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)); }
    public String expression() { return rule.expression(); }
    @Override public FilterConfig filterConfig() { return rule; }
    @Override public void applyFilter(FilterConfig config, Player player) { setFilter(config.expression()); }
    @Override public boolean filterAccessible(Player player) {
        return !isRemoved() && level == player.level() && player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }

    public long recentRate() {
        return level != null && level.getGameTime() - 1 <= lastMoveTime ? lastMoved : 0;
    }

    public long sampledRate() {
        return level != null && level.getGameTime() - 20 <= lastMoveTime ? lastMoved : 0;
    }

    @Override
    protected Item getItemFromBlockEntity() { return getBlockState().getBlock().asItem(); }

    @Override
    public InternalInventory getInternalInventory() { return cells; }

    public void toggle() {
        configured = !configured;
        if (!configured) cancel();
        status = configured ? Status.READY : Status.PAUSED;
        resetWork();
    }

    public void reverse() {
        reversed = !reversed;
        routeChanged();
    }

    public void cycleEnergy() {
        energy = Energy.values()[(energy.ordinal() + 1) % Energy.values().length];
        resetWork();
    }

    public void cycleSignal() {
        signal = Signal.values()[(signal.ordinal() + 1) % Signal.values().length];
        resetWork();
    }

    public void cycleScope() {
        scope = Scope.values()[(scope.ordinal() + 1) % Scope.values().length];
        routeChanged();
    }

    public void cycleEndpoint(boolean source) {
        if (source ? scope != Scope.GRID_TO_SLOT && scope != Scope.GRID_PAIR
                : scope != Scope.SLOT_TO_GRID && scope != Scope.GRID_PAIR) {
            return;
        }
        var grid = getMainNode().getGrid();
        if (grid == null || level == null) {
            return;
        }
        var mounts = grid.getService(BulkCellIndex.class).localCells(level, worldPosition);
        var current = source ? origin : destination;
        int next = 0;
        for (int i = 0; i < mounts.size(); ++i) {
            if (mounts.get(i).id().equals(current)) {
                next = i + 1;
                break;
            }
        }
        UUID selected = next < mounts.size() ? mounts.get(next).id() : null;
        if (source) {
            origin = selected;
        } else {
            destination = selected;
        }
        routeChanged();
    }

    private void routeChanged() {
        if (configured) {
            configured = false;
            status = Status.PAUSED;
            cancel();
        }
        resetWork();
    }

    public String endpointLabel(boolean source) {
        var id = source ? origin : destination;
        if (id == null) {
            return "";
        }
        var grid = getMainNode().getGrid();
        var mount = grid == null ? null : grid.getService(BulkCellIndex.class).mounted(id);
        return mount == null ? id.toString() : shortLabel(mount);
    }

    private static String shortLabel(BulkCellIndex.Mount mount) {
        var name = mount.name().getString();
        return name.substring(0, name.offsetByCodePoints(0, Math.min(75, name.codePointCount(0, name.length()))))
                + " · " + mount.drive().getBlockPos().toShortString() + " #" + (mount.slot() + 1);
    }

    public @Nullable String setFilter(String expression) {
        var candidate = new FilterConfig(expression.strip(), false);
        var checked = FilterEngine.compile(candidate, FilterCapabilities.forCell(CellTier.QUANTUM, false));
        if (!candidate.expression().isEmpty() && checked.error() != null) {
            return checked.error();
        }
        rule = candidate;
        filter = checked;
        filterGeneration = FilterEngine.generation();
        resetWork();
        return null;
    }

    private void resetWork() {
        rescan = true;
        routeRevision = -1;
        remainingItems = null;
        saveChanges();
        wake();
    }

    private void onUpgradesChanged() {
        getMainNode().setIdlePowerUsage(BulkConfig.migratorDrain(tier()) * energyMultiplier());
        resetWork();
    }

    public double energyMultiplier() {
        return BulkConfig.energyMultiplier(upgrades.getInstalledUpgrades(BulkStorageMod.ENERGY_SAVING_CARD.get()));
    }

    public void wake() {
        if (level instanceof ServerLevel server && !isRemoved() && configured) {
            BulkScheduler.get(server.getServer()).wake(job, false);
        }
    }

    private void cancel() {
        unwatch();
        if (level instanceof ServerLevel server) {
            BulkScheduler.get(server.getServer()).cancel(job);
        }
    }

    private void unwatch() {
        if (watchedGrid != null) {
            watchedGrid.unwatchMigrator(this);
            watchedGrid = null;
        }
        watchedDisks.forEach(disk -> disk.removeStockMonitor(stockListener));
        watchedDisks.clear();
    }

    public void remoteStockChanged() {
        if (!installing && !transferring && configured && scope != Scope.SLOTS) {
            routeRevision = -1;
            rescan = true;
            wake();
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        wake();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        unwatch();
        rescan = true;
        routeRevision = -1;
        wake();
    }

    @Override
    public void onChunkUnloaded() {
        cancel();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        cancel();
        super.setRemoved();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        if (!installing) {
            rescan = true;
            routeRevision = -1;
            wake();
        }
    }

    private @Nullable StorageCell inventory(int slot) {
        var stack = cells.peek(slot);
        if (stack != attached[slot]) {
            attached[slot] = stack;
            inventories[slot] = CellTransfer.supported(stack) ? StorageCells.getCellInventory(stack, () -> {}) : null;
        }
        return inventories[slot];
    }

    private boolean allowedBySignal() {
        return switch (signal) {
            case IGNORE -> true;
            case HIGH -> level.hasNeighborSignal(worldPosition);
            case LOW -> !level.hasNeighborSignal(worldPosition);
        };
    }

    private boolean stop(Status reason) {
        status = reason;
        return false;
    }

    private record Endpoint(int slot, @Nullable BulkCellIndex.PhysicalMount mount) {
        boolean mounted() { return mount != null; }
    }

    private record Route(Endpoint source, Endpoint target) {}

    private @Nullable StorageCell storage(Endpoint endpoint) {
        if (!endpoint.mounted()) {
            return inventory(endpoint.slot());
        }
        var mount = endpoint.mount();
        var drive = mount.drive();
        if (drive.isRemoved() || drive.getMainNode().getGrid() != getMainNode().getGrid()
                || !drive.getMainNode().isOnline()
                || ((IChestOrDrive) drive).getOriginalCellInventory(endpoint.slot()) != mount.inventory()) return null;
        var address = mount.id() == null ? null : routeGrid.find(mount.id());
        return mount.id() == null || address != null && address.inventory() == mount.inventory() ? mount.inventory() : null;
    }

    private ItemStack stack(Endpoint endpoint) {
        return endpoint.mounted()
                ? endpoint.mount().drive().getInternalInventory().getStackInSlot(endpoint.slot())
                : cells.peek(endpoint.slot());
    }

    private void write(Endpoint endpoint, ItemStack stack) {
        if (endpoint.mounted()) {
            endpoint.mount().drive().getInternalInventory().setItemDirect(endpoint.slot(), stack);
        } else {
            cells.setItemDirect(endpoint.slot(), stack);
        }
    }

    private @Nullable Endpoint mounted(BulkCellIndex index, UUID id) {
        var mount = id == null ? null : index.find(id);
        return mount != null && BulkCellIndex.sameSaveChunk(level, worldPosition, mount.drive().getLevel(),
                mount.drive().getBlockPos())
                ? new Endpoint(mount.slot(), new BulkCellIndex.PhysicalMount(mount.drive(), mount.slot(), mount.inventory(), id))
                : null;
    }

    private List<Route> plan(BulkCellIndex index) {
        var input = new Endpoint(reversed ? 1 : 0, null);
        var output = new Endpoint(1 - input.slot(), null);
        var from = mounted(index, origin);
        var to = mounted(index, destination);
        return switch (scope) {
            case SLOTS -> List.of(new Route(input, output));
            case GRID_PAIR -> from == null || to == null || from.mount().id().equals(to.mount().id())
                    ? List.of() : List.of(new Route(from, to));
            case SLOT_TO_GRID -> destination == null
                    ? index.localPhysicalCells(level, worldPosition).stream()
                            .map(m -> new Route(input, new Endpoint(m.slot(), m))).toList()
                    : to == null ? List.of() : List.of(new Route(input, to));
            case GRID_TO_SLOT -> origin == null
                    ? index.localPhysicalCells(level, worldPosition).reversed().stream()
                            .map(m -> new Route(new Endpoint(m.slot(), m), output)).toList()
                    : from == null ? List.of() : List.of(new Route(from, output));
        };
    }

    private void watch(BulkCellIndex index) {
        unwatch();
        if (scope == Scope.SLOTS) return;
        watchedGrid = index;
        index.watchMigrator(this);
        for (var route : routes) {
            if (route.source().mounted() && route.source().mount().inventory() instanceof BulkCellInventory bulk)
                watchedDisks.add(bulk);
            if (route.target().mounted() && route.target().mount().inventory() instanceof BulkCellInventory bulk)
                watchedDisks.add(bulk);
        }
        watchedDisks.forEach(disk -> disk.addStockMonitor(stockListener));
    }

    private boolean work(BulkScheduler.Allowance allowance) {
        if (isRemoved() || !configured) {
            return stop(Status.PAUSED);
        }
        var grid = getMainNode().getGrid();
        if (grid == null || !getMainNode().isOnline()) {
            return stop(Status.OFFLINE);
        }
        if (!allowedBySignal()) {
            return stop(Status.REDSTONE);
        }
        if (!allowance.takeKey()) {
            return true;
        }
        var index = grid.getService(BulkCellIndex.class);
        if (index != routeGrid || routeRevision < 0) {
            routeGrid = index;
            routeRevision = index.mountRevision();
            routes = plan(index);
            watch(index);
            routeIndex = 0;
            blockedRoute = false;
            rescan = true;
        }
        if (routes.isEmpty()) {
            return stop(Status.ENDPOINT_UNAVAILABLE);
        }
        if (routeIndex >= routes.size()) {
            return stop(blockedRoute ? Status.BLOCKED : Status.FINISHED);
        }
        var route = routes.get(routeIndex);
        var source = storage(route.source());
        var target = storage(route.target());
        if (source == null || target == null) {
            return stop(route.source().mounted() && source == null || route.target().mounted() && target == null
                    ? Status.ENDPOINT_UNAVAILABLE
                    : stack(route.source()).isEmpty() || stack(route.target()).isEmpty() ? Status.NEED_CELLS : Status.UNSUPPORTED);
        }
        int generation = FilterEngine.generation();
        if (generation != filterGeneration || filter == null) {
            filter = FilterEngine.compile(rule, FilterCapabilities.forCell(CellTier.QUANTUM, false));
            filterGeneration = generation;
            rescan = true;
        }
        if (!rule.expression().isEmpty() && filter.error() != null) {
            return stop(Status.ERROR);
        }
        if (rescan) {
            pending.clear();
            var keys = source instanceof BulkCellInventory bulk
                    ? bulk.transferKeys() : java.util.stream.StreamSupport.stream(source.getAvailableStacks().spliterator(), false)
                            .map(java.util.Map.Entry::getKey).filter(AEItemKey.class::isInstance).map(AEItemKey.class::cast).toList();
            for (var key : keys) {
                if (rule.expression().isEmpty() || filter.matches(key)) {
                    pending.add(key);
                }
            }
            remainingItems = null;
            if (source instanceof BulkCellInventory bulk && bulk.compressionPools() == 0) {
                var total = BigInteger.ZERO;
                for (var key : pending) {
                    total = total.add(bulk.amountExact(key));
                }
                remainingItems = total;
            }
            rescan = false;
        }
        if (pending.isEmpty()) {
            return advanceRoute(route);
        }
        var power = grid.getEnergyService();
        long budget = rate();
        double unitPower = switch (energy) {
            case FIXED -> 0;
            case BATCH -> BulkConfig.BATCH_POWER_PER_THOUSAND.get() / 1000.0;
            case AE2 -> BulkConfig.AE_POWER_PER_ITEM.get();
        };
        unitPower *= energyMultiplier();
        if (unitPower > 0) {
            double available = power.extractAEPower(budget * unitPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
            budget = Math.min(budget, Math.max(0L, (long) (available / unitPower)));
            if (budget == 0) {
                status = Status.OFFLINE;
                return true;
            }
        }
        boolean owned = scope != Scope.SLOTS && source instanceof BulkCellInventory && target instanceof BulkCellInventory;
        var sourceStack = owned ? ItemStack.EMPTY : stack(route.source());
        var targetStack = owned ? ItemStack.EMPTY : stack(route.target());
        var batch = owned ? CellTransfer.beginOwned((BulkCellInventory) source, (BulkCellInventory) target)
                : CellTransfer.begin(sourceStack, source, targetStack, target);
        if (batch == null) {
            return scope == Scope.SLOTS ? stop(Status.UNSUPPORTED) : advanceRoute(route);
        }
        long moved = 0;
        int limit = tier().keysPerTick;
        transferring = true;
        try {
            for (int examined = 0; examined < limit && !pending.isEmpty() && moved < budget; examined++) {
                if (examined > 0 && !allowance.takeKey()) break;
                var key = pending.getFirst();
                long amount = batch.move(key, budget - moved);
                if (batch.failed()) {
                    rescan = true;
                    return stop(Status.ERROR);
                }
                moved += amount;
                if (moved < budget) pending.removeFirst();
            }
        } finally {
            transferring = false;
        }
        if (moved > 0) {
            try {
                if (!batch.commit((from, to) -> install(route, sourceStack, targetStack, from, to))) {
                    return stop(Status.ERROR);
                }
            } catch (RuntimeException failure) {
                com.mojang.logging.LogUtils.getLogger().error("Bulk migrator at {} could not commit the cell pair",
                        worldPosition, failure);
                return stop(Status.ERROR);
            }
            if (unitPower > 0) {
                power.extractAEPower(moved * unitPower, Actionable.MODULATE, PowerMultiplier.CONFIG);
            }
            totalMoved += Math.min(moved, Long.MAX_VALUE - totalMoved);
            if (remainingItems != null) remainingItems = remainingItems.subtract(BigInteger.valueOf(moved)).max(BigInteger.ZERO);
            lastMoved = moved;
            lastMoveTime = level.getGameTime();
            saveChanges();
            status = Status.RUNNING;
        }
        return routeRevision < 0 || !pending.isEmpty() || advanceRoute(route);
    }

    private boolean advanceRoute(Route route) {
        var current = storage(route.source());
        blockedRoute |= current == null || current.getStatus() != CellState.EMPTY;
        ++routeIndex;
        rescan = true;
        if (!blockedRoute) remainingItems = BigInteger.ZERO;
        if (scope == Scope.SLOT_TO_GRID && current != null && current.getStatus() == CellState.EMPTY) {
            return stop(Status.FINISHED);
        }
        return routeIndex < routes.size() || stop(blockedRoute ? Status.BLOCKED : Status.FINISHED);
    }

    private void install(Route route, ItemStack originalSource, ItemStack originalTarget, ItemStack source, ItemStack target) {
        if (storage(route.source()) == null || storage(route.target()) == null
                || stack(route.source()) != originalSource || stack(route.target()) != originalTarget) {
            throw new IllegalStateException("A staged migration endpoint changed before commit");
        }
        installing = true;
        try {
            write(route.source(), source);
            if (stack(route.target()) != originalTarget) {
                throw new IllegalStateException("Destination changed during staged commit");
            }
            write(route.target(), target);
        } catch (RuntimeException failure) {
            restoreSlot(route.target(), originalTarget, target, failure);
            restoreSlot(route.source(), originalSource, source, failure);
            throw failure;
        } finally {
            installing = false;
            if (route.source().mounted() || route.target().mounted()) routeRevision = -1;
        }
    }

    private void restoreSlot(Endpoint endpoint, ItemStack original, ItemStack installed, RuntimeException failure) {
        try {
            var current = stack(endpoint);
            if (current == installed) {
                write(endpoint, original);
            } else if (current != original) {
                failure.addSuppressed(new IllegalStateException("Slot no longer holds either staged version"));
            }
        } catch (RuntimeException restoration) {
            if (restoration != failure) failure.addSuppressed(restoration);
        }
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        if (!installing) {
            super.saveChangedInventory(inventory);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        configured = tag.getBoolean("running");
        reversed = tag.getBoolean("reverse");
        energy = readEnum(Energy.values(), tag.getInt("energy"));
        signal = readEnum(Signal.values(), tag.getInt("signal"));
        scope = readEnum(Scope.values(), tag.getInt("scope"));
        origin = tag.hasUUID("origin") ? tag.getUUID("origin") : null;
        destination = tag.hasUUID("destination") ? tag.getUUID("destination") : null;
        totalMoved = Math.max(0, tag.getLong("totalMoved"));
        rule = new FilterConfig(tag.getString("whitelist"), false);
        upgrades.readFromNBT(tag, "upgrades", registries);
        filter = null;
        rescan = true;
        routeRevision = -1;
        wake();
    }

    private static <T> T readEnum(T[] values, int index) {
        return index >= 0 && index < values.length ? values[index] : values[0];
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("migrationVersion", 2);
        tag.putBoolean("running", configured);
        tag.putBoolean("reverse", reversed);
        tag.putInt("energy", energy.ordinal());
        tag.putInt("signal", signal.ordinal());
        tag.putInt("scope", scope.ordinal());
        if (origin != null) tag.putUUID("origin", origin);
        if (destination != null) tag.putUUID("destination", destination);
        tag.putLong("totalMoved", totalMoved);
        tag.putString("whitelist", rule.expression());
        upgrades.writeToNBT(tag, "upgrades", registries);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var upgrade : upgrades) {
            drops.add(upgrade);
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        upgrades.clear();
    }

    private final class CellSlots extends AppEngInternalInventory {
        private CellSlots() {
            super(BulkMigratorEntity.this, 2, 1, new IAEItemFilter() {
                @Override
                public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
                    return CellTransfer.supported(stack);
                }
            });
        }

        private ItemStack peek(int slot) {
            return super.getStackInSlot(slot);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (level instanceof ServerLevel && attached[slot] == peek(slot) && inventories[slot] != null) {
                inventories[slot].persist();
            }
            return peek(slot);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= 0 && slot < size()) {
                getStackInSlot(slot);
            }
            return super.extractItem(slot, amount, simulate);
        }
    }
}
