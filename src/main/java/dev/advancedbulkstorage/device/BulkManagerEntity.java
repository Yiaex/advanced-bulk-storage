package dev.advancedbulkstorage.device;

import appeng.api.networking.GridFlags;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.storage.BulkCellIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class BulkManagerEntity extends AENetworkedBlockEntity {
    public BulkManagerEntity(BlockPos pos, BlockState state) {
        super(BulkStorageMod.MANAGER_ENTITY.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(4);
    }

    @Override protected Item getItemFromBlockEntity() { return getBlockState().getBlock().asItem(); }

    public @Nullable BulkCellIndex index() {
        var grid = getMainNode().getGrid();
        return isRemoved() || grid == null || !getMainNode().isOnline() ? null : grid.getService(BulkCellIndex.class);
    }
}
