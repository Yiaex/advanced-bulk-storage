package dev.advancedbulkstorage.device;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import dev.advancedbulkstorage.BulkStorageMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BulkDriveBlock extends AEBaseEntityBlock<BulkDriveEntity> {
    public BulkDriveBlock() { super(metalProps().requiresCorrectToolForDrops()); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var entity = getBlockEntity(level, pos);
        if (entity == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            MenuOpener.open(BulkStorageMod.BULK_DRIVE_MENU.get(), player, MenuLocators.forBlockEntity(entity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
