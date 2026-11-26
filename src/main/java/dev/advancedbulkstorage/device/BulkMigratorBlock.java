package dev.advancedbulkstorage.device;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import dev.advancedbulkstorage.BulkStorageMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BulkMigratorBlock extends AEBaseEntityBlock<BulkMigratorEntity> {
    private final MigratorTier tier;

    public BulkMigratorBlock(MigratorTier tier) {
        super(metalProps().requiresCorrectToolForDrops());
        this.tier = tier;
    }

    public MigratorTier tier() {
        return tier;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        var entity = getBlockEntity(level, pos);
        if (entity == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            MenuOpener.open(BulkStorageMod.MIGRATOR_MENU.get(), player, MenuLocators.forBlockEntity(entity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        var entity = getBlockEntity(level, pos);
        if (entity != null) {
            entity.wake();
        }
    }
}
