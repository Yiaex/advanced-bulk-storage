package dev.advancedbulkstorage.storage;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DynamicOps;
import dev.advancedbulkstorage.BulkConfig;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.CellTier;
import dev.advancedbulkstorage.migration.BulkScheduler;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.filter.FilterEngine;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.math.BigInteger;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public final class BulkCellInventory implements StorageCell {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int DATA_VERSION = 1;
    static final int POOL_VERSION = 2;
    static final int WIDE_VERSION = 3;
    private static final Cache<CustomData, Boolean> REPORTED_INVALID_CONTENTS = CacheBuilder.newBuilder()
            .weakKeys().maximumSize(256).build();
    // ItemStack uses identity equality. Values must never retain the stack or its host strongly.
    private static final WeakHashMap<ItemStack, State> LIVE = new WeakHashMap<>();
    private static final Cache<State, String> REMOTE_PREVIEWS = CacheBuilder.newBuilder()
            .weakKeys().maximumWeight(2 * 1024 * 1024)
            .weigher((State state, String data) -> data.length() * 2 + 64).build();
    private final ItemStack stack;
    private final BulkCellItem item;
    private final @Nullable ISaveProvider host;
    private final DynamicOps<Tag> ops;
    private final State state;
    private final Object2ObjectOpenHashMap<AEItemKey, BigInteger> amounts;
    private final Set<AEKey> partition;
    private FilterEngine.Compiled filter;
    private int filterGeneration = -1;
    private FilterConfig lastConfig;
    private ItemContainerContents lastUpgrades;
    private boolean hasCompressionCard;
    private boolean hasLimitCard;
    private int energyCards;
    private int autoRate;

    private static final class State {
        final Object2ObjectOpenHashMap<AEItemKey, BigInteger> amounts = new Object2ObjectOpenHashMap<>();
        final LinkedHashMap<AEItemKey, CompressionPool> pools = new LinkedHashMap<>();
        final HashMap<AEItemKey, CompressionPool> poolByForm = new HashMap<>();
        CustomData persisted;
        WeakReference<ISaveProvider> saveHost = new WeakReference<>(null);
        boolean dirty;
        boolean quarantined;
        long revision;
        long keyRevision;
        long lastSortedTick = Long.MIN_VALUE;
        long lastSorted;
        boolean previewCached;
        PreviewWork previewWork;
        LimitProfile.Ledger limits;
        String limitsText;
        int limitsGeneration;
        WeakReference<StockListener> stockListener;
        List<WeakReference<StockListener>> monitors = List.of();
    }

    @FunctionalInterface
    public interface StockListener {
        void changed(AEItemKey key, BigInteger delta);
    }

    void observeStock(@Nullable StockListener listener) {
        state.stockListener = listener == null ? null : new WeakReference<>(listener);
    }

    public void addStockMonitor(StockListener listener) {
        removeStockMonitor(listener);
        var monitors = new java.util.ArrayList<>(state.monitors);
        monitors.add(new WeakReference<>(listener));
        state.monitors = List.copyOf(monitors);
    }

    public void removeStockMonitor(StockListener listener) {
        state.monitors = state.monitors.stream().filter(reference -> {
            var existing = reference.get();
            return existing != null && existing != listener;
        }).toList();
    }

    public int autoSortRate() {
        updateUpgradeFlags();
        return stack.getOrDefault(BulkStorageMod.AUTO_SORT_ENABLED, false) ? autoRate : 0;
    }

    private BulkCellInventory(ItemStack stack, BulkCellItem item, @Nullable ISaveProvider host) {
        this.stack = stack;
        this.item = item;
        this.host = host;
        var server = ServerLifecycleHooks.getCurrentServer();
        this.ops = server == null ? NbtOps.INSTANCE : RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        this.partition = Set.copyOf(item.getConfigInventory(stack).keySet());
        if (server != null && host != null && stack.get(BulkStorageMod.CELL_ID) == null) {
            BulkCellItem.ensureId(stack);
        }
        synchronized (LIVE) {
            state = LIVE.computeIfAbsent(stack, ignored -> new State());
            amounts = state.amounts;
            var contents = stack.getOrDefault(BulkStorageMod.CONTENTS, CustomData.EMPTY);
            if (!state.dirty && contents != state.persisted) {
                state.persisted = contents;
                state.quarantined = false;
                amounts.clear();
                state.pools.clear();
                state.poolByForm.clear();
                state.limits = null;
                state.limitsText = null;
                read(contents);
                state.revision++;
                state.keyRevision++;
                invalidatePreview();
            }
            if (host != null) {
                state.saveHost = new WeakReference<>(host);
            }
        }
    }

    private void read(CustomData contents) {
        var data = contents.copyTag();
        if (data.isEmpty()) {
            return;
        }
        int version = data.getInt("version");
        if (!data.contains("version", Tag.TAG_INT) || version < DATA_VERSION || version > WIDE_VERSION) {
            quarantine(contents, "unsupported inventory version (found "
                    + data.get("version") + ")");
            return;
        }
        if (!(data.get("items") instanceof ListTag list) || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            quarantine(contents, "missing or invalid inventory item list");
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            var row = list.getCompound(i);
            var amount = StockAmount.read(row, "amount", version == WIDE_VERSION);
            if (amount == null || !row.contains("key", Tag.TAG_COMPOUND)) {
                quarantine(contents, "invalid item or amount in inventory row " + i);
                return;
            }
            var key = AEItemKey.CODEC.parse(ops, row.getCompound("key")).result().orElse(null);
            if (key == null || amounts.putIfAbsent(key, amount) != null) {
                quarantine(contents, "unreadable or duplicate item key in inventory row " + i);
                return;
            }
        }
        if (version == POOL_VERSION || version == WIDE_VERSION && data.contains("pools")) {
            if (!(data.get("pools") instanceof ListTag pools) || pools.isEmpty() || pools.getElementType() != Tag.TAG_COMPOUND) {
                quarantine(contents, "missing compression pools");
                return;
            }
            for (int index = 0; index < pools.size(); index++) {
                var pool = CompressionPool.decode(pools.getCompound(index), ops, version == WIDE_VERSION);
                if (pool == null || pool.chain.factors().keySet().stream().anyMatch(state.poolByForm::containsKey)) {
                    quarantine(contents, "invalid or overlapping compression pool " + index);
                    return;
                }
                registerPool(pool);
            }
        }
    }

    private void quarantine(CustomData contents, String reason) {
        state.quarantined = true;
        amounts.clear();
        state.pools.clear();
        state.poolByForm.clear();
        if (REPORTED_INVALID_CONTENTS.asMap().putIfAbsent(contents, Boolean.TRUE) == null) {
            LOGGER.warn("Quarantined bulk cell {} (UUID {}): {}. Original inventory data was preserved.",
                    item.tier().id(), id(), reason);
        }
    }

    @Override
    public void persist() {
        if (!state.dirty || state.quarantined) {
            return;
        }
        if (amounts.isEmpty() && state.pools.isEmpty()) {
            stack.remove(BulkStorageMod.CONTENTS);
        } else {
            var rows = new ListTag();
            boolean wide = false;
            for (var entry : amounts.object2ObjectEntrySet()) {
                var row = new CompoundTag();
                row.put("key", AEItemKey.CODEC.encodeStart(ops, entry.getKey()).getOrThrow());
                wide |= StockAmount.write(row, "amount", entry.getValue());
                rows.add(row);
            }
            var data = new CompoundTag();
            data.put("items", rows);
            if (!state.pools.isEmpty()) {
                var pools = new ListTag();
                for (var pool : state.pools.values()) {
                    var row = new CompoundTag();
                    wide |= pool.encode(row, ops);
                    pools.add(row);
                }
                data.put("pools", pools);
            }
            data.putInt("version", wide ? WIDE_VERSION : state.pools.isEmpty() ? DATA_VERSION : POOL_VERSION);
            stack.set(BulkStorageMod.CONTENTS, CustomData.of(data));
        }
        state.persisted = stack.getOrDefault(BulkStorageMod.CONTENTS, CustomData.EMPTY);
        state.dirty = false;
    }

    private void markChanged(boolean keysChanged) {
        if (stack.get(BulkStorageMod.CELL_ID) == null && ServerLifecycleHooks.getCurrentServer() != null) {
            BulkCellItem.ensureId(stack);
        }
        state.dirty = true;
        state.revision++;
        invalidatePreview();
        if (keysChanged) {
            state.keyRevision++;
        }
    }

    void publishChange() {
        if (host == null) {
            persist();
            var mountedHost = state.saveHost.get();
            if (mountedHost != null) {
                mountedHost.saveChanges();
            }
        } else {
            host.saveChanges();
        }
    }

    public @Nullable UUID id() {
        return stack.get(BulkStorageMod.CELL_ID);
    }

    public int types() {
        return amounts.size() + state.pools.size();
    }

    public long revision() {
        return state.revision;
    }

    public long keyRevision() {
        return state.keyRevision;
    }

    public CellTier tier() {
        return item.tier();
    }

    public int maximumTypes() {
        return BulkConfig.cell(tier()).types().get();
    }

    public String customName() {
        var name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return name == null ? "" : name.getString();
    }

    public String filterExpression() {
        return stack.getOrDefault(BulkStorageMod.FILTER, FilterConfig.EMPTY).expression();
    }

    public String limitExpression() {
        return stack.getOrDefault(BulkStorageMod.LIMITS, "");
    }

    public boolean hasLimitUpgrade() {
        updateUpgradeFlags();
        return hasLimitCard;
    }

    public boolean atConfiguredLimit() {
        var ledger = limitLedger();
        return ledger != null && ledger.profile.valid() && ledger.atLimit();
    }

    public int compressionPools() { return state.pools.size(); }

    public boolean quarantined() { return state.quarantined; }

    public List<String> upgradeIds() {
        return java.util.stream.StreamSupport.stream(item.getUpgrades(stack).spliterator(), false)
                .filter(upgrade -> !upgrade.isEmpty())
                .map(upgrade -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(upgrade.getItem()).toString())
                .toList();
    }

    public long recentSortRate(long tick) {
        return tick - 20 <= state.lastSortedTick ? state.lastSorted : 0;
    }

    void recordSort(long tick, long moved) {
        state.lastSorted = state.lastSortedTick == tick ? saturated(state.lastSorted, moved) : moved;
        state.lastSortedTick = tick;
    }

    @FunctionalInterface
    public interface PhysicalStockVisitor {
        void visit(AEItemKey key, BigInteger amount, boolean pool);
    }

    public void visitPhysicalStock(PhysicalStockVisitor visitor) {
        amounts.object2ObjectEntrySet().fastForEach(entry -> visitor.visit(entry.getKey(), entry.getValue(), false));
        state.pools.values().forEach(pool -> visitor.visit(pool.chain.base(), pool.units, true));
    }

    /** One row per retrievable key, folding the base units into any ordinary stock of that key. */
    public void visitStock(java.util.function.BiConsumer<AEItemKey, BigInteger> visitor) {
        amounts.keySet().forEach(key -> visitor.accept(key, physicalAmount(key)));
        state.pools.values().forEach(pool -> {
            var base = pool.chain.base();
            if (!amounts.containsKey(base)) visitor.accept(base, pool.units);
        });
    }

    public record Configuration(FilterConfig filter, String limits, boolean sorting, @Nullable ItemContainerContents upgrades) {}

    public Configuration configuration() {
        return new Configuration(stack.getOrDefault(BulkStorageMod.FILTER, FilterConfig.EMPTY), limitExpression(),
                stack.getOrDefault(BulkStorageMod.AUTO_SORT_ENABLED, false), stack.get(AEComponents.UPGRADES));
    }

    public boolean configure(String operation, String value) {
        if (state.quarantined) return false;
        switch (operation) {
            case "filter" -> {
                var candidate = new FilterConfig(value.strip(), false);
                if (!candidate.equals(stack.getOrDefault(BulkStorageMod.FILTER, FilterConfig.EMPTY))
                        && LimitProfile.parse(limitExpression()).hasOverflow()) return false;
                if (!candidate.expression().isEmpty() && FilterEngine.compile(candidate, item.capabilities(stack)).error() != null) {
                    return false;
                }
                stack.set(BulkStorageMod.FILTER, candidate);
            }
            case "limit" -> {
                var profile = LimitProfile.parse(value);
                if (!hasLimitUpgrade() || !profile.valid() || profile.hasOverflow()) {
                    return false;
                }
                if (value.isBlank()) stack.remove(BulkStorageMod.LIMITS);
                else stack.set(BulkStorageMod.LIMITS, value.strip());
            }
            case "enable_sort", "disable_sort" -> {
                updateUpgradeFlags();
                boolean enable = operation.equals("enable_sort");
                if (enable && (autoRate == 0 || FilterEngine.compile(stack.getOrDefault(BulkStorageMod.FILTER, FilterConfig.EMPTY),
                        item.capabilities(stack)).error() != null)) {
                    return false;
                }
                stack.set(BulkStorageMod.AUTO_SORT_ENABLED, enable);
            }
            default -> { return false; }
        }
        publishChange();
        return true;
    }

    public @Nullable String readyPreview() {
        return REMOTE_PREVIEWS.getIfPresent(state);
    }

    private void invalidatePreview() {
        if (state.previewCached) {
            REMOTE_PREVIEWS.invalidate(state);
            state.previewCached = false;
        }
    }

    private void cachePreview(String data) {
        REMOTE_PREVIEWS.put(state, data);
        state.previewCached = true;
    }

    public PreviewWork requestPreview(BulkScheduler scheduler) {
        var work = state.previewWork;
        if (work == null) {
            work = new PreviewWork(scheduler);
            state.previewWork = work;
            scheduler.wakePreview(work);
        }
        work.viewers++;
        return work;
    }

    /** Only server-thread readers hold this ticket, and only while a matching row is hovered. */
    public final class PreviewWork implements BulkScheduler.Job, AutoCloseable {
        private final BulkScheduler scheduler;
        private int viewers;
        private long revision = -1;
        private java.util.Iterator<Object2ObjectMap.Entry<AEItemKey, BigInteger>> ordinary;
        private java.util.Iterator<CompressionPool> pools;
        private java.util.Iterator<BulkInventoryPreview.Entry> selected;
        private BulkInventoryPreview.Selection selection;
        private BulkInventoryPreview.RemoteEncoder encoder;

        private PreviewWork(BulkScheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public boolean run(BulkScheduler.Allowance allowance) {
            try {
                return collect(allowance);
            } catch (RuntimeException failure) {
                cachePreview("-1");
                stop();
                throw failure;
            }
        }

        private boolean collect(BulkScheduler.Allowance allowance) {
            if (revision != state.revision) {
                revision = state.revision;
                ordinary = amounts.object2ObjectEntrySet().fastIterator();
                pools = state.pools.values().iterator();
                selected = null;
                selection = new BulkInventoryPreview.Selection();
                encoder = new BulkInventoryPreview.RemoteEncoder(types(), state.quarantined, ops);
            }
            for (int i = 0; i < 512 && allowance.takeKey(); ++i) {
                if (ordinary.hasNext()) {
                    var entry = ordinary.next();
                    selection.add(entry.getKey(), entry.getValue(), false);
                } else if (pools.hasNext()) {
                    var pool = pools.next();
                    selection.add(pool.chain.base(), pool.units, true);
                } else if (selected == null) {
                    selected = selection.entries().iterator();
                } else if (selected.hasNext()) {
                    encoder.add(selected.next());
                } else {
                    cachePreview(encoder.finish());
                    stop();
                    return false;
                }
            }
            return true;
        }

        @Override
        public void close() {
            if (--viewers == 0 && state.previewWork == this) {
                stop();
            }
        }

        public boolean active() {
            return state.previewWork == this;
        }

        private void stop() {
            scheduler.cancel(this);
            state.previewWork = null;
        }
    }

    public long amount(AEItemKey key) {
        return StockAmount.toLong(amountExact(key));
    }

    public BigInteger amountExact(AEItemKey key) {
        var ordinary = ordinaryAmount(key);
        var pool = state.poolByForm.get(key);
        if (pool == null) {
            return ordinary;
        }
        long factor = pool.factor(key, compressionInstalled());
        return factor == 0 ? ordinary : ordinary.add(StockAmount.items(pool.units, factor));
    }

    private BigInteger ordinaryAmount(AEItemKey key) {
        return amounts.getOrDefault(key, BigInteger.ZERO);
    }

    private BigInteger physicalAmount(AEItemKey key) {
        var amount = ordinaryAmount(key);
        var pool = state.pools.get(key);
        return pool == null ? amount : amount.add(pool.units);
    }

    @Override
    public CellState getStatus() {
        if (state.quarantined) {
            return CellState.FULL;
        }
        if (types() == 0) {
            return CellState.EMPTY;
        }
        return types() >= BulkConfig.cell(item.tier()).types().get() ? CellState.TYPES_FULL : CellState.NOT_EMPTY;
    }

    @Override
    public double getIdleDrain() {
        updateUpgradeFlags();
        return BulkConfig.cell(item.tier()).idleDrain().get() * BulkConfig.energyMultiplier(energyCards);
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    @Override
    public boolean canFitInsideCell() {
        return !state.quarantined && types() == 0;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        return key instanceof AEItemKey itemKey && accepts(itemKey);
    }

    private boolean accepts(AEItemKey key) {
        if (state.quarantined || !partition.isEmpty() && !partition.contains(key)) {
            return false;
        }
        var current = stack.getOrDefault(BulkStorageMod.FILTER, FilterConfig.EMPTY);
        if (filterGeneration != FilterEngine.generation() || !current.equals(lastConfig)) {
            filter = FilterEngine.compile(current, item.capabilities(stack));
            filterGeneration = FilterEngine.generation();
            lastConfig = current;
        }
        return filter.matches(key);
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        return insertTracked(key, amount, mode, null);
    }

    private long insertTracked(AEKey key, long amount, Actionable mode, @Nullable List<StockDelta> pendingNotifications) {
        if (amount <= 0 || !(key instanceof AEItemKey itemKey)) {
            return 0;
        }
        if (compressionInstalled()) {
            var chain = CompressionCatalog.lookup(itemKey);
            if (chain != null) {
                return insertCompressed(itemKey, amount, mode, chain, pendingNotifications);
            }
        }
        var current = ordinaryAmount(itemKey);
        long inserted = acceptedAmount(itemKey, amount, current);
        if (inserted > 0 && mode == Actionable.MODULATE) {
            var delta = BigInteger.valueOf(inserted);
            amounts.put(itemKey, current.add(delta));
            markChanged(current.signum() == 0);
            recordStock(itemKey, delta, pendingNotifications);
            if (pendingNotifications == null) publishChange();
        }
        return inserted;
    }

    private long acceptedAmount(AEItemKey itemKey, long amount, BigInteger current) {
        if (!accepts(itemKey)) {
            return 0;
        }
        var settings = BulkConfig.cell(item.tier());
        if (current.signum() == 0 && (types() >= settings.types().get()
                || StorageCells.isCellHandled(itemKey.getReadOnlyStack()))) {
            return 0;
        }
        return limitInsertion(itemKey, amount, 1, settings.itemsPerType().get());
    }

    private long insertCompressed(AEItemKey key, long amount, Actionable mode, CompressionCatalog.Chain chain,
                                  @Nullable List<StockDelta> pendingNotifications) {
        if (!accepts(key)) {
            return 0;
        }
        var pool = state.poolByForm.get(key);
        if (pool == null && chain.factors().keySet().stream().anyMatch(state.poolByForm::containsKey)
                || pool != null && !pool.chain.equals(chain)) {
            return 0;
        }
        var settings = BulkConfig.cell(item.tier());
        if (pool == null && types() >= settings.types().get()) {
            return 0;
        }
        long factor = chain.factor(key);
        long accepted = limitInsertion(chain.base(), amount, factor, settings.itemsPerType().get());
        if (accepted > 0 && mode == Actionable.MODULATE) {
            boolean newPool = pool == null;
            if (newPool) {
                pool = new CompressionPool(chain, BigInteger.ZERO);
                registerPool(pool);
            }
            var delta = StockAmount.units(accepted, factor);
            pool.units = pool.units.add(delta);
            markChanged(newPool);
            recordStock(chain.base(), delta, pendingNotifications);
            if (pendingNotifications == null) publishChange();
        }
        return accepted;
    }

    private void registerPool(CompressionPool pool) {
        state.pools.put(pool.chain.base(), pool);
        pool.chain.factors().keySet().forEach(key -> state.poolByForm.put(key, pool));
    }

    private boolean compressionInstalled() {
        updateUpgradeFlags();
        return hasCompressionCard;
    }

    private void updateUpgradeFlags() {
        var upgrades = stack.get(AEComponents.UPGRADES);
        if (lastUpgrades != upgrades) {
            lastUpgrades = upgrades;
            hasCompressionCard = false;
            hasLimitCard = false;
            energyCards = 0;
            autoRate = 0;
            if (upgrades != null) {
                var iterator = upgrades.nonEmptyStream().iterator();
                while (iterator.hasNext()) {
                    var upgrade = iterator.next();
                    hasCompressionCard |= upgrade.is(BulkStorageMod.MULTI_COMPRESSION_CARD.get());
                    hasLimitCard |= upgrade.is(BulkStorageMod.STORAGE_LIMIT_CARD.get());
                    if (upgrade.is(BulkStorageMod.ENERGY_SAVING_CARD.get())) {
                        energyCards++;
                    }
                    for (var entry : BulkStorageMod.SORTING_CARDS.entrySet()) {
                        if (upgrade.is(entry.getValue().get())) {
                            autoRate = autoRate == 0 ? entry.getKey().rate : -1;
                        }
                    }
                }
            }
            autoRate = Math.max(0, autoRate);
        }
    }

    private long limitInsertion(AEItemKey key, long requested, long factor, long configuredCap) {
        var ledger = limitLedger();
        if (ledger == null && configuredCap == Long.MAX_VALUE) return requested;
        var stored = physicalAmount(key);
        long headroom = configuredHeadroom(configuredCap, stored);
        if (ledger != null) {
            headroom = LimitProfile.Ledger.minimum(headroom, ledger.remaining(key, stored, true, true));
        }
        return headroom == LimitProfile.Ledger.UNLIMITED ? requested : Math.min(requested, headroom / factor);
    }

    private static long configuredHeadroom(long cap, BigInteger stored) {
        // The old default Long.MAX_VALUE now means no per-key cap. Explicit lower values still apply.
        return cap == Long.MAX_VALUE ? LimitProfile.Ledger.UNLIMITED : LimitProfile.Ledger.remaining(cap, stored);
    }

    private @Nullable LimitProfile.Ledger limitLedger() {
        updateUpgradeFlags();
        String text = hasLimitCard ? stack.get(BulkStorageMod.LIMITS) : null;
        if (text == null || text.isBlank()) {
            state.limits = null;
            state.limitsText = null;
            return null;
        }
        int generation = FilterEngine.generation();
        if (state.limits == null || state.limitsGeneration != generation || !text.equals(state.limitsText)) {
            state.limits = new LimitProfile.Ledger(LimitProfile.parse(text));
            state.limitsText = text;
            state.limitsGeneration = generation;
            amounts.forEach(state.limits::change);
            state.pools.values().forEach(pool -> state.limits.change(pool.chain.base(), pool.units));
        }
        return state.limits;
    }

    public long discardedItems() {
        return stack.getOrDefault(BulkStorageMod.DISCARDED_ITEMS, 0L);
    }

    /**
     * The interface calls this only after every bound disk had a chance to store the items.
     * The global ME routing path and migration never call it.
     */
    public long discardOverflow(AEItemKey key, long amount, long simulatedInsertion, Actionable mode) {
        if (amount <= 0 || !BulkConfig.ALLOW_OVERFLOW_DISCARD.get() || !accepts(key)) {
            return 0;
        }
        var ledger = limitLedger();
        if (ledger == null || !ledger.profile.hasOverflow()) {
            return 0;
        }
        var chain = compressionInstalled() ? CompressionCatalog.lookup(key) : null;
        var base = chain == null ? key : chain.base();
        var pool = state.pools.get(base);
        long factor = chain == null ? 1 : chain.factor(key);
        boolean newType = chain == null ? !amounts.containsKey(key) : pool == null;
        if (StorageCells.isCellHandled(key.getReadOnlyStack())
                || newType && simulatedInsertion == 0 && types() >= maximumTypes()
                || chain != null && (pool != null && !pool.chain.equals(chain)
                        || pool == null && chain.factors().keySet().stream().anyMatch(state.poolByForm::containsKey))) {
            return 0;
        }
        var present = physicalAmount(base);
        long discarding = ledger.remaining(base, present, false, true);
        if (discarding == LimitProfile.Ledger.UNLIMITED || discarding / factor > simulatedInsertion) {
            return 0;
        }
        long hard = configuredHeadroom(BulkConfig.cell(tier()).itemsPerType().get(), present);
        long refusing = LimitProfile.Ledger.minimum(hard, ledger.remaining(base, present, true, false));
        long discarded = refusing == LimitProfile.Ledger.UNLIMITED ? amount
                : Math.min(amount, Math.max(0, refusing / factor - simulatedInsertion));
        long previousDiscarded = discardedItems();
        discarded = Math.min(discarded, Long.MAX_VALUE - previousDiscarded);
        if (discarded > 0 && mode == Actionable.MODULATE) {
            stack.set(BulkStorageMod.DISCARDED_ITEMS, previousDiscarded + discarded);
            publishChange();
        }
        return discarded;
    }

    private record StockDelta(BulkCellInventory inventory, AEItemKey key, BigInteger amount) {
        void publish() { inventory.notifyStock(key, amount); }
    }

    private void recordLimit(AEItemKey key, BigInteger delta) {
        if (state.limits != null) {
            state.limits.change(key, delta);
        }
    }

    private void recordStock(AEItemKey key, BigInteger delta, @Nullable List<StockDelta> pendingNotifications) {
        if (delta.signum() == 0) return;
        recordLimit(key, delta);
        if (pendingNotifications == null) notifyStock(key, delta);
        else pendingNotifications.add(new StockDelta(this, key, delta));
    }

    private void notifyStock(AEItemKey key, BigInteger delta) {
        var sorter = state.stockListener == null ? null : state.stockListener.get();
        if (sorter != null) {
            try {
                sorter.changed(key, delta);
            } catch (RuntimeException failure) {
                LOGGER.error("Disabled a failing auto-sort listener for bulk cell {}", id(), failure);
                state.stockListener = null;
            }
        }
        for (var reference : state.monitors) {
            var listener = reference.get();
            if (listener == null) continue;
            try {
                listener.changed(key, delta);
            } catch (RuntimeException failure) {
                LOGGER.error("Disabled a failing stock listener for bulk cell {}", id(), failure);
                removeStockMonitor(listener);
            }
        }
    }

    boolean supportsDirectTransfer() {
        return state.pools.isEmpty() && !compressionInstalled();
    }

    public List<AEItemKey> transferKeys() {
        if (state.pools.isEmpty()) {
            return List.copyOf(amounts.keySet());
        }
        var keys = new java.util.LinkedHashSet<AEItemKey>(amounts.keySet());
        boolean installed = compressionInstalled();
        for (var pool : state.pools.values()) {
            addPoolTransferKeys(pool, installed, keys);
        }
        return List.copyOf(keys);
    }

    void addAffectedTransferKeys(AEItemKey physicalKey, Set<AEItemKey> keys) {
        var pool = state.pools.get(physicalKey);
        if (pool != null) addPoolTransferKeys(pool, compressionInstalled(), keys);
        keys.add(physicalKey);
    }

    private static void addPoolTransferKeys(CompressionPool pool, boolean installed, Set<AEItemKey> keys) {
        if (!installed || !pool.chain.equals(CompressionCatalog.lookup(pool.chain.base()))) {
            keys.add(pool.chain.base());
            return;
        }
        long visibleUnits = StockAmount.toLong(pool.units);
        pool.chain.factors().entrySet().stream().filter(form -> form.getValue() <= visibleUnits)
                .sorted(java.util.Map.Entry.<AEItemKey, Long>comparingByValue().reversed())
                .forEach(form -> keys.add(form.getKey()));
    }

    private static long saturated(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    boolean sameInventory(BulkCellInventory other) {
        return state == other.state || id() != null && id().equals(other.id());
    }

    long moveOwnedStock(BulkCellInventory target, AEItemKey key, long requested) {
        if (state.quarantined || requested <= 0 || sameInventory(target)) {
            return 0;
        }
        if (!supportsDirectTransfer() || !target.supportsDirectTransfer()) {
            // Same server thread, both endpoints are native ABS inventories. Plan against their
            // independent live state, then publish the save notifications only after both writes.
            long extracted = extractTracked(key, requested, Actionable.SIMULATE, null);
            long accepted = target.insertTracked(key, extracted, Actionable.SIMULATE, null);
            if (accepted > 0) {
                var notifications = new java.util.ArrayList<StockDelta>(3);
                extractTracked(key, accepted, Actionable.MODULATE, notifications);
                target.insertTracked(key, accepted, Actionable.MODULATE, notifications);
                notifications.forEach(StockDelta::publish);
            }
            return accepted;
        }
        var before = ordinaryAmount(key);
        if (before.signum() == 0) {
            return 0;
        }
        var targetBefore = target.ordinaryAmount(key);
        long amount = target.acceptedAmount(key, Math.min(StockAmount.toLong(before), requested), targetBefore);
        if (amount <= 0) {
            return 0;
        }
        var delta = BigInteger.valueOf(amount);
        var remaining = before.subtract(delta);
        boolean emptied = remaining.signum() == 0;
        if (emptied) {
            amounts.remove(key);
        } else {
            amounts.put(key, remaining);
        }
        target.amounts.put(key, targetBefore.add(delta));
        var removed = delta.negate();
        recordLimit(key, removed);
        target.recordLimit(key, delta);
        markChanged(emptied);
        target.markChanged(targetBefore.signum() == 0);
        notifyStock(key, removed);
        target.notifyStock(key, delta);
        return amount;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        return extractTracked(key, amount, mode, null);
    }

    private long extractTracked(AEKey key, long amount, Actionable mode, @Nullable List<StockDelta> pendingNotifications) {
        if (state.quarantined || amount <= 0 || !(key instanceof AEItemKey itemKey)) {
            return 0;
        }
        var current = ordinaryAmount(itemKey);
        long raw = Math.min(amount, StockAmount.toLong(current));
        var pool = state.poolByForm.get(itemKey);
        long factor = pool == null ? 0 : pool.factor(itemKey, compressionInstalled());
        long pooled = factor > 0 && amount > raw ? Math.min(amount - raw, StockAmount.toLong(
                StockAmount.items(pool.units, factor))) : 0;
        if ((raw > 0 || pooled > 0) && mode == Actionable.MODULATE) {
            boolean keysChanged = false;
            var rawDelta = BigInteger.valueOf(-raw);
            if (raw > 0) {
                var remaining = current.add(rawDelta);
                if (remaining.signum() == 0) {
                    amounts.remove(itemKey);
                    keysChanged = true;
                } else {
                    amounts.put(itemKey, remaining);
                }
            }
            var poolDelta = BigInteger.ZERO;
            if (pooled > 0) {
                poolDelta = StockAmount.units(-pooled, factor);
                pool.units = pool.units.add(poolDelta);
                if (pool.units.signum() == 0) {
                    state.pools.remove(pool.chain.base());
                    pool.chain.factors().keySet().forEach(state.poolByForm::remove);
                    keysChanged = true;
                }
            }
            markChanged(keysChanged);
            if (pooled > 0) recordStock(pool.chain.base(), poolDelta, pendingNotifications);
            recordStock(itemKey, rawDelta, pendingNotifications);
            if (pendingNotifications == null) publishChange();
        }
        return raw + pooled;
    }

    // Only the AE2 IO port's compensation call reaches this method. It returns stock just extracted
    // from this cell, so applying its current insertion filter or configured limits would lose stock.
    public long restoreIoPortRemainder(AEKey key, long amount) {
        if (state.quarantined || amount <= 0 || !(key instanceof AEItemKey itemKey)) {
            return 0;
        }
        var current = ordinaryAmount(itemKey);
        var delta = BigInteger.valueOf(amount);
        amounts.put(itemKey, current.add(delta));
        markChanged(current.signum() == 0);
        recordStock(itemKey, delta, null);
        publishChange();
        return amount;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var iterator = amounts.object2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            var entry = iterator.next();
            var key = entry.getKey();
            out.set(key, saturated(out.get(key), StockAmount.toLong(entry.getValue())));
        }
        for (var pool : state.pools.values()) {
            var key = pool.chain.base();
            out.set(key, saturated(out.get(key), StockAmount.toLong(pool.units)));
        }
    }

    public void visitInterfaceKeys(java.util.function.Consumer<AEItemKey> visitor) {
        amounts.keySet().forEach(visitor);
        // Aliases are addresses into one pool. Quantity and permission are checked at each access.
        state.pools.values().forEach(pool -> pool.chain.factors().keySet().forEach(visitor));
    }

    public static final class Handler implements ICellHandler {
        @Override
        public boolean isCell(ItemStack stack) {
            return stack != null && stack.getItem() instanceof BulkCellItem;
        }

        @Override
        public @Nullable StorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
            return stack != null && stack.getItem() instanceof BulkCellItem item
                    ? new BulkCellInventory(stack, item, host) : null;
        }
    }
}
