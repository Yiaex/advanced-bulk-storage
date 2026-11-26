package dev.advancedbulkstorage.mixin;

import appeng.blockentity.storage.DriveBlockEntity;
import appeng.api.networking.IGridNodeListener;
import dev.advancedbulkstorage.storage.BulkCellIndex;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DriveBlockEntity.class, remap = false)
abstract class DriveCellIndexMixin {
    @Inject(method = "updateState", at = @At(value = "FIELD",
            target = "Lappeng/blockentity/storage/DriveBlockEntity;isCached:Z",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void advancedBulkStorage$refreshDriveCellIndex(CallbackInfo info) {
        BulkCellIndex.refresh((DriveBlockEntity) (Object) this);
    }

    @Inject(method = "onMainNodeStateChanged", at = @At("HEAD"))
    private void advancedBulkStorage$invalidateBindings(IGridNodeListener.State reason, CallbackInfo info) {
        BulkCellIndex.driveAvailabilityChanged((DriveBlockEntity) (Object) this);
    }

    @Inject(method = "onMainNodeStateChanged", at = @At("TAIL"))
    private void advancedBulkStorage$wakeSorting(IGridNodeListener.State reason, CallbackInfo info) {
        BulkCellIndex.wakeSorting((DriveBlockEntity) (Object) this);
    }

    @Inject(method = "setPriority", at = @At("TAIL"))
    private void advancedBulkStorage$priorityChanged(int priority, CallbackInfo info) {
        BulkCellIndex.wakeSorting((DriveBlockEntity) (Object) this);
    }
}
