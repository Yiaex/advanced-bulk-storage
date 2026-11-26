package dev.advancedbulkstorage.storage;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.storage.cells.StorageCell;
import dev.advancedbulkstorage.device.BulkDriveEntity;
import dev.advancedbulkstorage.device.BulkMigratorEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class BulkCellIndex implements IGridService, IGridServiceProvider {
    private final IGrid grid;
    private final Map<UUID, List<Mount>> byId = new HashMap<>();
    private final Map<AENetworkedInvBlockEntity, List<Mount>> byDrive = new IdentityHashMap<>();
    private final Map<SaveChunk, List<Mount>> byChunk = new HashMap<>();
    private final Map<SaveChunk, Set<AENetworkedInvBlockEntity>> physicalDrives = new HashMap<>();
    private final Map<SaveChunk, DriveAutoSort> sorting = new HashMap<>();
    private final Map<SaveChunk, Set<BulkMigratorEntity>> migrators = new HashMap<>();
    private long mountRevision;

    private record SaveChunk(Level level, ChunkPos position) {}

    public BulkCellIndex(IGrid grid) {
        this.grid = grid;
    }

    public boolean belongsTo(IGrid grid) {
        return this.grid == grid;
    }

    public record Mount(UUID id, AENetworkedInvBlockEntity drive, int slot, BulkCellInventory inventory) {
        public Component name() {
            return inventory.getDescription();
        }

        public int priority() {
            return BulkCellIndex.priority(drive);
        }
    }

    public static boolean sameSaveChunk(Level a, BlockPos origin, Level b, BlockPos destination) {
        return a == b && new ChunkPos(origin).equals(new ChunkPos(destination));
    }

    /** Changes when an indexed mount or its online availability may have changed. */
    public long mountRevision() { return mountRevision; }

    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag data) {
        if (node.getOwner() instanceof AENetworkedInvBlockEntity drive && indexed(drive)) {
            update(drive);
        }
    }

    @Override
    public void removeNode(IGridNode node) {
        if (node.getOwner() instanceof AENetworkedInvBlockEntity drive && indexed(drive)) {
            remove(drive);
        }
    }

    private static boolean indexed(AENetworkedInvBlockEntity drive) {
        return drive instanceof DriveBlockEntity || drive instanceof BulkDriveEntity;
    }

    public static void refresh(AENetworkedInvBlockEntity drive) {
        if (drive.getLevel() != null && !drive.getLevel().isClientSide()) {
            var grid = drive.getMainNode().getGrid();
            if (grid != null) {
                grid.getService(BulkCellIndex.class).update(drive);
            }
        }
    }

    public static void driveAvailabilityChanged(AENetworkedInvBlockEntity drive) {
        if (drive.getLevel() instanceof ServerLevel) {
            var grid = drive.getMainNode().getGrid();
            if (grid != null) {
                ++grid.getService(BulkCellIndex.class).mountRevision;
            }
        }
    }

    private void update(AENetworkedInvBlockEntity drive) {
        var chunk = saveChunk(drive);
        physicalDrives.computeIfAbsent(chunk, ignored -> java.util.Collections.newSetFromMap(new IdentityHashMap<>())).add(drive);
        var mounts = new ArrayList<Mount>();
        for (int slot = 0; slot < drive.getInternalInventory().size(); slot++) {
            if (((IChestOrDrive) drive).getOriginalCellInventory(slot) instanceof BulkCellInventory inventory && inventory.id() != null) {
                mounts.add(new Mount(inventory.id(), drive, slot, inventory));
            }
        }
        if (mounts.equals(byDrive.get(drive))) {
            wakeMigrators(chunk);
            return;
        }
        var affected = relatedChunks(drive, mounts);
        removeMounts(drive, chunk);
        if (!mounts.isEmpty()) {
            byDrive.put(drive, mounts);
            mounts.forEach(mount -> byId.computeIfAbsent(mount.id(), ignored -> new ArrayList<>(1)).add(mount));
            byChunk.computeIfAbsent(chunk, ignored -> new ArrayList<>()).addAll(mounts);
        }
        ++mountRevision;
        rebuildSorting(chunk);
        notifyChangedChunks(chunk, affected);
    }

    private void remove(AENetworkedInvBlockEntity drive) {
        var chunk = saveChunk(drive);
        var drives = physicalDrives.get(chunk);
        if (drives != null && drives.remove(drive) && drives.isEmpty()) physicalDrives.remove(chunk);
        var affected = relatedChunks(drive, List.of());
        if (removeMounts(drive, chunk)) {
            ++mountRevision;
            rebuildSorting(chunk);
        }
        notifyChangedChunks(chunk, affected);
    }

    private Set<SaveChunk> relatedChunks(AENetworkedInvBlockEntity drive, List<Mount> replacements) {
        var chunks = new java.util.HashSet<SaveChunk>();
        java.util.stream.Stream.concat(byDrive.getOrDefault(drive, List.of()).stream(), replacements.stream())
                .flatMap(mount -> byId.getOrDefault(mount.id(), List.of()).stream())
                .forEach(mount -> chunks.add(saveChunk(mount.drive())));
        return chunks;
    }

    private void notifyChangedChunks(SaveChunk edited, Set<SaveChunk> affected) {
        wakeMigrators(edited);
        for (var chunk : affected) {
            if (edited.equals(chunk)) continue;
            var job = sorting.get(chunk);
            if (job != null) job.rescan();
            wakeMigrators(chunk);
        }
    }

    private static SaveChunk saveChunk(AENetworkedInvBlockEntity drive) {
        return new SaveChunk(drive.getLevel(), new ChunkPos(drive.getBlockPos()));
    }

    private void rebuildSorting(SaveChunk chunk) {
        var sortingJob = sorting.remove(chunk);
        if (sortingJob != null) {
            sortingJob.detach();
        }
        var mounts = byChunk.getOrDefault(chunk, List.of());
        if (chunk.level() instanceof ServerLevel level && mounts.stream().anyMatch(m -> m.inventory().autoSortRate() > 0)) {
            var job = new DriveAutoSort(this, List.copyOf(mounts), level);
            sorting.put(chunk, job);
            job.wake();
        }
    }

    private boolean removeMounts(AENetworkedInvBlockEntity drive, SaveChunk chunk) {
        var previous = byDrive.remove(drive);
        if (previous != null) {
            var local = byChunk.get(chunk);
            local.removeAll(previous);
            if (local.isEmpty()) {
                byChunk.remove(chunk);
            }
            for (var mount : previous) {
                var matches = byId.get(mount.id());
                matches.remove(mount);
                if (matches.isEmpty()) {
                    byId.remove(mount.id());
                }
            }
        }
        return previous != null;
    }

    public static void wakeSorting(AENetworkedInvBlockEntity drive) {
        if (drive.getLevel() instanceof net.minecraft.server.level.ServerLevel) {
            var grid = drive.getMainNode().getGrid();
            if (grid != null) {
                var job = grid.getService(BulkCellIndex.class).sorting.get(saveChunk(drive));
                if (job != null) {
                    job.rescan();
                }
                grid.getService(BulkCellIndex.class).wakeMigrators(saveChunk(drive));
            }
        }
    }

    public List<Mount> localCells(Level level, BlockPos position) {
        return byChunk.getOrDefault(new SaveChunk(level, new ChunkPos(position)), List.of()).stream()
                .filter(mount -> find(mount.id()) == mount)
                .sorted(Comparator.comparingInt(Mount::priority).reversed()
                        .thenComparingLong(mount -> mount.drive().getBlockPos().asLong()).thenComparingInt(Mount::slot))
                .toList();
    }

    public record PhysicalMount(AENetworkedInvBlockEntity drive, int slot, StorageCell inventory, @Nullable UUID id) {}

    public List<PhysicalMount> localPhysicalCells(Level level, BlockPos position) {
        var result = new ArrayList<PhysicalMount>();
        var chunk = new SaveChunk(level, new ChunkPos(position));
        for (var drive : physicalDrives.getOrDefault(chunk, Set.of())) {
            if (!drive.isRemoved() && drive.getMainNode().getGrid() == grid && drive.getMainNode().isOnline()) {
                var chest = (IChestOrDrive) drive;
                for (int slot = 0; slot < chest.getCellCount(); ++slot) {
                    var inventory = chest.getOriginalCellInventory(slot);
                    if (inventory instanceof BulkCellInventory bulk) {
                        var mount = bulk.id() == null ? null : find(bulk.id());
                        if (mount != null && mount.inventory() == bulk)
                            result.add(new PhysicalMount(drive, slot, inventory, mount.id()));
                    } else if (CellTransfer.supportsSnapshot(inventory)
                            && CellTransfer.supported(drive.getInternalInventory().getStackInSlot(slot))) {
                        result.add(new PhysicalMount(drive, slot, inventory, null));
                    }
                }
            }
        }
        result.sort(Comparator.comparingInt((PhysicalMount mount) -> priority(mount.drive())).reversed()
                .thenComparingLong(mount -> mount.drive().getBlockPos().asLong()).thenComparingInt(PhysicalMount::slot));
        return result;
    }

    private static int priority(AENetworkedInvBlockEntity drive) {
        return drive instanceof BulkDriveEntity bulk ? bulk.priority() : ((DriveBlockEntity) drive).getPriority();
    }

    public void configurationChanged(Mount mount) {
        if (find(mount.id()) == mount) {
            var chunk = saveChunk(mount.drive());
            rebuildSorting(chunk);
            wakeMigrators(chunk);
        }
    }

    private void wakeMigrators(SaveChunk chunk) {
        migrators.getOrDefault(chunk, Set.of()).forEach(BulkMigratorEntity::remoteStockChanged);
    }

    public void watchMigrator(BulkMigratorEntity migrator) {
        migrators.computeIfAbsent(saveChunk(migrator), ignored -> java.util.Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(migrator);
    }

    public void unwatchMigrator(BulkMigratorEntity migrator) {
        var chunk = saveChunk(migrator);
        var listeners = migrators.get(chunk);
        if (listeners != null && listeners.remove(migrator) && listeners.isEmpty()) migrators.remove(chunk);
    }

    public long bindingCount(UUID disk) {
        return grid.getMachines(dev.advancedbulkstorage.device.BulkInterfaceEntity.class).stream()
                .filter(port -> port.bindings().contains(disk)).count();
    }

    public @Nullable Mount find(UUID id) {
        var mount = mounted(id);
        return mount != null && mount.drive().getMainNode().isOnline() ? mount : null;
    }

    public @Nullable Mount mounted(UUID id) {
        var matches = byId.get(id);
        if (matches == null || matches.size() != 1) {
            return null;
        }
        var mount = matches.getFirst();
        var drive = mount.drive();
        return !drive.isRemoved() && drive.getMainNode().getGrid() == grid
                && ((IChestOrDrive) drive).getOriginalCellInventory(mount.slot()) == mount.inventory() && id.equals(mount.inventory().id())
                ? mount : null;
    }

    public List<Mount> mountedCells() {
        return byId.keySet().stream().map(this::mounted).filter(java.util.Objects::nonNull).toList();
    }

    public List<Mount> available() {
        return byId.keySet().stream().map(this::find).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing((Mount mount) -> mount.drive().getLevel().dimension().location())
                        .thenComparingLong(mount -> mount.drive().getBlockPos().asLong())
                        .thenComparingInt(Mount::slot)).toList();
    }
}
