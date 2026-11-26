package dev.advancedbulkstorage.device;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.parts.storagebus.StorageBusPart;
import dev.advancedbulkstorage.BulkConfig;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.CellTier;
import dev.advancedbulkstorage.filter.FilterCapabilities;
import dev.advancedbulkstorage.filter.FilterConfig;
import dev.advancedbulkstorage.filter.FilterEngine;
import dev.advancedbulkstorage.menu.BulkFilterHost;
import dev.advancedbulkstorage.storage.BulkCellIndex;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public final class BulkInterfaceEntity extends AENetworkedBlockEntity implements BulkFilterHost {
    public enum Access {
        BOTH, INSERT_ONLY, EXTRACT_ONLY, READ_ONLY, DISABLED;
        boolean inserts() { return this == BOTH || this == INSERT_ONLY; }
        boolean extracts() { return this == BOTH || this == EXTRACT_ONLY; }
    }

    public enum Redstone {
        IGNORE, REQUIRE_SIGNAL, REQUIRE_NO_SIGNAL
    }

    private final ArrayList<UUID> bindings = new ArrayList<>();
    private final Access[] sideModes = new Access[6];
    private final Port[] ports = new Port[7];
    private Access global = Access.BOTH;
    private Redstone redstone = Redstone.IGNORE;
    private FilterConfig secondary = FilterConfig.EMPTY;
    private FilterEngine.Compiled checkedFilter;
    private int filterGeneration = -1;
    private @Nullable BulkCellIndex catalog;
    private @Nullable BulkCellIndex resolvedIndex;
    private long resolvedMountRevision;
    private List<BulkCellInventory> resolvedInventories = List.of();
    private List<BulkCellInventory> lastInventories = List.of();
    private long[] lastRevisions = new long[0];
    private final ArrayList<ItemSlot> slots = new ArrayList<>();
    private final HashMap<AEItemKey, Integer> slotByKey = new HashMap<>();

    private record ItemSlot(AEItemKey key, int cellMask) {}

    public BulkInterfaceEntity(BlockPos pos, BlockState state) {
        super(BulkStorageMod.INTERFACE_ENTITY.get(), pos, state);
        Arrays.fill(sideModes, Access.BOTH);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(advanced() ? 16 : 4);
    }

    public boolean advanced() {
        return ((BulkInterfaceBlock) getBlockState().getBlock()).advanced();
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return getBlockState().getBlock().asItem();
    }

    public int bindingLimit() { return advanced() ? 16 : 1; }
    public List<UUID> bindings() { return List.copyOf(bindings); }
    public FilterConfig filter() { return secondary; }
    @Override public FilterConfig filterConfig() { return secondary; }
    @Override public void applyFilter(FilterConfig config, Player player) { setFilter(config.expression()); }
    @Override public boolean filterAccessible(Player player) {
        return advanced() && !isRemoved() && level == player.level() && player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }
    public Access mode(@Nullable Direction side) { return advanced() && side != null ? sideModes[side.ordinal()] : global; }
    public Redstone redstone() { return redstone; }

    public @Nullable BulkCellIndex index() {
        return getMainNode().isOnline() ? catalogIndex() : null;
    }

    public @Nullable BulkCellIndex catalogIndex() {
        if (level == null || level.isClientSide() || isRemoved()) {
            return null;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            catalog = null;
        } else if (catalog == null || !catalog.belongsTo(grid)) {
            catalog = grid.getService(BulkCellIndex.class);
        }
        return catalog;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (level instanceof ServerLevel && !isRemoved()) {
            var state = level.getBlockState(worldPosition);
            boolean online = getMainNode().isOnline();
            if (state.getBlock() instanceof BulkInterfaceBlock && state.getValue(BlockStateProperties.LIT) != online) {
                level.setBlock(worldPosition, state.setValue(BlockStateProperties.LIT, online), Block.UPDATE_CLIENTS);
            }
        }
    }

    public boolean bind(UUID id) {
        var index = index();
        if (index == null || index.find(id) == null || bindings.contains(id) || bindings.size() >= bindingLimit()) {
            return false;
        }
        bindings.add(id);
        invalidateBindings();
        saveChanges();
        return true;
    }

    public boolean replaceBinding(UUID id) {
        if (advanced()) {
            return bind(id);
        }
        var index = index();
        if (index == null || index.find(id) == null || bindings.contains(id)) {
            return false;
        }
        bindings.clear();
        bindings.add(id);
        invalidateBindings();
        saveChanges();
        return true;
    }

    public boolean unbind(UUID id) {
        if (!bindings.remove(id)) {
            return false;
        }
        invalidateBindings();
        saveChanges();
        return true;
    }

    public boolean prioritize(UUID id) {
        if (!advanced() || !bindings.remove(id)) {
            return false;
        }
        bindings.addFirst(id);
        invalidateBindings();
        saveChanges();
        return true;
    }

    public void cycleMode(@Nullable Direction side) {
        var next = Access.values()[(mode(side).ordinal() + 1) % Access.values().length];
        if (advanced() && side != null) {
            sideModes[side.ordinal()] = next;
        } else {
            global = next;
        }
        saveChanges();
    }

    public void cycleRedstone() {
        if (advanced()) {
            redstone = Redstone.values()[(redstone.ordinal() + 1) % Redstone.values().length];
            saveChanges();
        }
    }

    public @Nullable String setFilter(String expression) {
        if (!advanced()) {
            return "unsupported";
        }
        var rule = new FilterConfig(expression.strip(), false);
        var compiled = FilterEngine.compile(rule, FilterCapabilities.forCell(CellTier.QUANTUM, false));
        if (!rule.expression().isEmpty() && compiled.error() != null) {
            return compiled.error();
        }
        secondary = rule;
        checkedFilter = compiled;
        filterGeneration = FilterEngine.generation();
        saveChanges();
        return null;
    }

    public @Nullable IItemHandler itemHandler(@Nullable Direction side) {
        return externalSide(side) ? port(side) : null;
    }

    public @Nullable BulkItemAccess bulkHandler(@Nullable Direction side) {
        return externalSide(side) ? port(side) : null;
    }

    private Port port(@Nullable Direction side) {
        int ordinal = side == null ? 6 : side.ordinal();
        if (ports[ordinal] == null) {
            ports[ordinal] = new Port(side);
        }
        return ports[ordinal];
    }

    private boolean externalSide(@Nullable Direction side) {
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()
                || isRemoved() || advanced() && side == null) {
            return false;
        }
        if (side == null) {
            return true;
        }
        var neighbor = worldPosition.relative(side);
        return level.hasChunkAt(neighbor) && !(level.getBlockEntity(neighbor) instanceof IPartHost parts
                && parts.getPart(side.getOpposite()) instanceof StorageBusPart);
    }

    private boolean enabled(@Nullable Direction side) {
        return externalSide(side) && mode(side) != Access.DISABLED && switch (redstone) {
            case IGNORE -> true;
            case REQUIRE_SIGNAL -> level.hasNeighborSignal(worldPosition);
            case REQUIRE_NO_SIGNAL -> !level.hasNeighborSignal(worldPosition);
        };
    }

    public boolean allowsSide(@Nullable Direction side) {
        return enabled(side);
    }

    private boolean matches(AEItemKey key) {
        if (secondary.expression().isEmpty()) {
            return true;
        }
        if (checkedFilter == null || filterGeneration != FilterEngine.generation()) {
            checkedFilter = FilterEngine.compile(secondary, FilterCapabilities.forCell(CellTier.QUANTUM, false));
            filterGeneration = FilterEngine.generation();
        }
        return checkedFilter.matches(key);
    }

    private void invalidateBindings() {
        resolvedIndex = null;
        resolvedInventories = List.of();
    }

    private List<BulkCellInventory> readInventories() {
        var current = index();
        long revision = current == null ? 0 : current.mountRevision();
        if (current != resolvedIndex || revision != resolvedMountRevision) {
            resolvedInventories = resolveInventories(current);
            resolvedIndex = current;
            resolvedMountRevision = revision;
        }
        return resolvedInventories;
    }

    // Read snapshots are invalidated by mount changes. Writes verify each addressed mount before using it.
    private List<BulkCellInventory> resolveInventories(@Nullable BulkCellIndex index) {
        if (index == null || bindings.isEmpty()) {
            return List.of();
        }
        var live = new ArrayList<BulkCellInventory>(bindings.size());
        for (var id : bindings) {
            var mount = index.find(id);
            if (mount != null) {
                live.add(mount.inventory());
            }
        }
        return live.isEmpty() ? List.of() : live;
    }

    private void updateSlots(List<BulkCellInventory> live) {
        boolean changed = live != lastInventories;
        if (!changed) {
            for (int i = 0; i < live.size(); i++) {
                changed |= lastRevisions[i] != live.get(i).keyRevision();
            }
        }
        if (!changed) {
            return;
        }
        lastInventories = live;
        lastRevisions = new long[live.size()];
        var cellsByKey = new Object2IntLinkedOpenHashMap<AEItemKey>();
        for (int i = 0; i < live.size(); i++) {
            var inventory = live.get(i);
            lastRevisions[i] = inventory.keyRevision();
            int cellBit = 1 << i;
            inventory.visitInterfaceKeys(key -> cellsByKey.put(key, cellsByKey.getInt(key) | cellBit));
        }
        var existing = slotByKey.entrySet().iterator();
        while (existing.hasNext()) {
            var entry = existing.next();
            if (!cellsByKey.containsKey(entry.getKey())) {
                slots.set(entry.getValue(), null);
                existing.remove();
            }
        }
        int free = 0;
        for (var entry : cellsByKey.object2IntEntrySet()) {
            var key = entry.getKey();
            int mask = entry.getIntValue();
            Integer existingSlot = slotByKey.get(key);
            if (existingSlot != null) {
                var slot = slots.get(existingSlot);
                if (slot.cellMask() != mask) {
                    slots.set(existingSlot, new ItemSlot(key, mask));
                }
                continue;
            }
            while (free < slots.size() && slots.get(free) != null) {
                free++;
            }
            if (free == slots.size()) {
                slots.add(new ItemSlot(key, mask));
            } else {
                slots.set(free, new ItemSlot(key, mask));
            }
            slotByKey.put(key, free);
        }
        while (!slots.isEmpty() && slots.getLast() == null) {
            slots.removeLast();
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        bindings.clear();
        invalidateBindings();
        for (var row : data.getList("bindings", Tag.TAG_INT_ARRAY)) {
            try {
                UUID id = NbtUtils.loadUUID(row);
                if (bindings.size() < bindingLimit() && !bindings.contains(id)) {
                    bindings.add(id);
                }
            } catch (IllegalArgumentException malformed) {
                // An unreadable binding cannot address an inventory.
            }
        }
        global = valueAt(Access.values(), data.getInt("access"), Access.BOTH);
        redstone = valueAt(Redstone.values(), data.getInt("redstone"), Redstone.IGNORE);
        var modes = data.getIntArray("sides");
        for (int i = 0; i < sideModes.length; i++) {
            sideModes[i] = i < modes.length ? valueAt(Access.values(), modes[i], Access.BOTH) : Access.BOTH;
        }
        secondary = advanced() ? new FilterConfig(data.getString("filter"), false) : FilterConfig.EMPTY;
        checkedFilter = null;
    }

    private static <T> T valueAt(T[] values, int index, T fallback) {
        return index >= 0 && index < values.length ? values[index] : fallback;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        var list = new ListTag();
        bindings.forEach(id -> list.add(NbtUtils.createUUID(id)));
        data.put("bindings", list);
        data.putInt("access", global.ordinal());
        data.putInt("redstone", redstone.ordinal());
        data.putIntArray("sides", Arrays.stream(sideModes).mapToInt(Access::ordinal).toArray());
        data.putString("filter", secondary.expression());
    }

    private final class Port implements IItemHandler, BulkItemAccess {
        private static final IActionSource SOURCE = IActionSource.empty();
        private final @Nullable Direction side;

        private Port(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int count(AEItemKey key) {
            if (!enabled(side) || !matches(key)) {
                return 0;
            }
            var live = readInventories();
            return count(key, live, (1 << live.size()) - 1, Integer.MAX_VALUE);
        }

        private int count(AEItemKey key, List<BulkCellInventory> live, int cellMask, int limit) {
            long count = 0;
            while (cellMask != 0) {
                var inventory = live.get(Integer.numberOfTrailingZeros(cellMask));
                count += Math.min(inventory.amount(key), limit);
                if (count >= limit) {
                    return limit;
                }
                cellMask &= cellMask - 1;
            }
            return (int) count;
        }

        @Override
        public int insert(AEItemKey key, int amount, Actionable action) {
            return move(key, amount, action, true);
        }

        @Override
        public Insertion insertWithOverflow(AEItemKey key, int amount, Actionable action) {
            if (amount <= 0 || !canInsert(key)) {
                return new Insertion(0, 0);
            }
            return insertWithOverflowChecked(key, amount, action);
        }

        private Insertion insertWithOverflowChecked(AEItemKey key, int amount, Actionable action) {
            var index = index();
            if (index == null) {
                return new Insertion(0, 0);
            }
            int[] planned = null;
            int remaining = amount;
            for (int i = 0; i < bindings.size(); ++i) {
                var mount = index.find(bindings.get(i));
                if (mount == null) {
                    continue;
                }
                int accepted = (int) mount.inventory().insert(key, remaining, action, SOURCE);
                remaining -= accepted;
                if (remaining == 0) {
                    return new Insertion(amount, 0);
                }
                if (action == Actionable.SIMULATE && accepted > 0) {
                    if (planned == null) planned = new int[bindings.size()];
                    planned[i] = accepted;
                }
            }
            int stored = amount - remaining;
            if (BulkConfig.ALLOW_OVERFLOW_DISCARD.get()) {
                for (int i = 0; i < bindings.size() && remaining > 0; ++i) {
                    var mount = index.find(bindings.get(i));
                    if (mount != null) {
                        remaining -= (int) mount.inventory().discardOverflow(
                                key, remaining, planned == null ? 0 : planned[i], action);
                    }
                }
            }
            return new Insertion(stored, amount - stored - remaining);
        }

        private boolean canInsert(AEItemKey key) {
            return enabled(side) && mode(side).inserts() && matches(key);
        }

        @Override
        public int extract(AEItemKey key, int amount, Actionable action) {
            return move(key, amount, action, false);
        }

        private int move(AEItemKey key, int amount, Actionable action, boolean insert) {
            if (amount <= 0 || !enabled(side) || !(insert ? mode(side).inserts() : mode(side).extracts()) || !matches(key)) {
                return 0;
            }
            var index = index();
            if (index == null) {
                return 0;
            }
            int remaining = amount;
            for (int i = 0; i < bindings.size(); ++i) {
                var mount = index.find(bindings.get(i));
                if (mount == null) {
                    continue;
                }
                var inventory = mount.inventory();
                remaining -= (int) (insert ? inventory.insert(key, remaining, action, SOURCE)
                        : inventory.extract(key, remaining, action, SOURCE));
                if (remaining == 0) {
                    break;
                }
            }
            return amount - remaining;
        }

        @Override
        public int getSlots() {
            if (!enabled(side)) {
                return 1;
            }
            updateSlots(readInventories());
            return slots.size() + 1;
        }

        private @Nullable AEItemKey keyAt(int slot) {
            var item = slot > 0 && slot < getSlots() ? slots.get(slot - 1) : null;
            return item == null ? null : item.key();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot <= 0 || !enabled(side)) return ItemStack.EMPTY;
            var live = readInventories();
            updateSlots(live);
            var item = slot <= slots.size() ? slots.get(slot - 1) : null;
            if (item == null || !matches(item.key())) return ItemStack.EMPTY;
            var key = item.key();
            int count = count(key, live, item.cellMask(), key.getReadOnlyStack().getMaxStackSize());
            return count <= 0 ? ItemStack.EMPTY : key.toStack(count);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            var key = AEItemKey.of(stack);
            if (!acceptsSlot(slot, key)) {
                return stack;
            }
            int inserted = insertWithOverflowChecked(key, stack.getCount(),
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE).accepted();
            return inserted == 0 ? stack : inserted == stack.getCount() ? ItemStack.EMPTY
                    : stack.copyWithCount(stack.getCount() - inserted);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var key = keyAt(slot);
            if (amount <= 0 || key == null) {
                return ItemStack.EMPTY;
            }
            int extracted = extract(key, Math.min(amount, key.getReadOnlyStack().getMaxStackSize()),
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE);
            return extracted <= 0 ? ItemStack.EMPTY : key.toStack(extracted);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return acceptsSlot(slot, AEItemKey.of(stack));
        }

        private boolean acceptsSlot(int slot, @Nullable AEItemKey key) {
            return key != null && canInsert(key) && (slot == 0 || key.equals(keyAt(slot)));
        }
    }
}
