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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public final class BulkInterfaceBlock extends AEBaseEntityBlock<BulkInterfaceEntity> {
    private final boolean advanced;

    public BulkInterfaceBlock(boolean advanced) {
        super(metalProps().requiresCorrectToolForDrops());
        this.advanced = advanced;
        registerDefaultState(defaultBlockState().setValue(BlockStateProperties.LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.LIT);
    }

    public boolean advanced() {
        return advanced;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                                BlockHitResult hit) {
        var entity = getBlockEntity(level, pos);
        if (entity == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            MenuOpener.open(BulkStorageMod.INTERFACE_MENU.get(), player, MenuLocators.forBlockEntity(entity));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
