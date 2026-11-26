package dev.advancedbulkstorage.device;

import appeng.api.inventories.ISegmentedInventory;
import appeng.blockentity.misc.CellWorkbenchBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.menu.BulkFilterHost;
import dev.advancedbulkstorage.storage.BulkCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class BulkWorkbenchEntity extends CellWorkbenchBlockEntity implements BulkFilterHost {
    public BulkWorkbenchEntity(BlockPos pos, BlockState state) {
        super(BulkStorageMod.WORKBENCH_ENTITY.get(), pos, state);
    }

    @Override protected Item getItemFromBlockEntity() { return getBlockState().getBlock().asItem(); }

    @Override
    public ItemStack filterStack() {
        var stack = getSubInventory(ISegmentedInventory.CELLS).getStackInSlot(0);
        return stack.getItem() instanceof BulkCellItem ? stack : ItemStack.EMPTY;
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        super.onChangeInventory(inv, slot);
        var stack = filterStack();
        if (getLevel() != null && !getLevel().isClientSide() && !stack.isEmpty()) {
            BulkCellItem.ensureId(stack);
        }
    }

    @Override
    public boolean filterAccessible(Player player) {
        return !isRemoved() && getLevel() == player.level() && player.distanceToSqr(getBlockPos().getCenter()) <= 64
                && (getLevel().isClientSide() || !filterStack().isEmpty());
    }

    @Override public void filterChanged(Player player) { saveChanges(); }
}
