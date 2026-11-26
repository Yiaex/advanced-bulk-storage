package dev.advancedbulkstorage.device;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.CellWorkbenchMenu;
import appeng.menu.locator.MenuLocators;
import dev.advancedbulkstorage.BulkStorageMod;
import dev.advancedbulkstorage.storage.BulkCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BulkWorkbenchBlock extends AEBaseEntityBlock<BulkWorkbenchEntity> {
    public BulkWorkbenchBlock() { super(metalProps().requiresCorrectToolForDrops()); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var entity = getBlockEntity(level, pos);
        if (entity == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            var stack = entity.filterStack();
            if (player.isShiftKeyDown() && !stack.isEmpty()) {
                BulkCellItem.ensureId(stack);
                MenuOpener.open(BulkStorageMod.FILTER_EDITOR.get(), player, MenuLocators.forBlockEntity(entity));
            } else {
                MenuOpener.open(CellWorkbenchMenu.TYPE, player, MenuLocators.forBlockEntity(entity));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                             InteractionHand hand, BlockHitResult hit) {
        var entity = getBlockEntity(level, pos);
        var name = stack.get(DataComponents.CUSTOM_NAME);
        if (entity != null && stack.is(Items.NAME_TAG) && name != null && !entity.filterStack().isEmpty()) {
            if (!level.isClientSide()) {
                entity.filterStack().set(DataComponents.CUSTOM_NAME, name);
                entity.filterChanged(player);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
