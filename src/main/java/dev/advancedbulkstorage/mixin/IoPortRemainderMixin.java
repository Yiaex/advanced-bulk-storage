package dev.advancedbulkstorage.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.storage.cells.StorageCell;
import appeng.blockentity.storage.IOPortBlockEntity;
import dev.advancedbulkstorage.storage.BulkCellInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = IOPortBlockEntity.class, remap = false)
abstract class IoPortRemainderMixin {
    @Redirect(method = "transferContents", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/MEStorage;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"))
    private long advancedBulkStorage$limitExtractionToPoweredInsert(MEStorage source, AEKey key, long amount,
                                                                    Actionable mode, IActionSource actionSource,
                                                                    IGrid grid, StorageCell cell, long remainingOperations) {
        var network = grid.getStorageService().getInventory();
        var destination = source == cell ? network : cell;
        // AE2 performs both operations on the server thread. Leaving the energy-limited remainder
        // in its original storage avoids a second, potentially filtered network insertion.
        long available = StorageHelper.poweredInsert(grid.getEnergyService(), destination, key, amount,
                actionSource, Actionable.SIMULATE);
        return available > 0 ? source.extract(key, available, mode, actionSource) : 0;
    }

    @Redirect(method = "transferContents", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lappeng/api/storage/MEStorage;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"))
    private long advancedBulkStorage$restoreRemainder(MEStorage source, AEKey key, long amount,
                                                     Actionable mode, IActionSource actionSource) {
        if (source instanceof BulkCellInventory bulk && mode == Actionable.MODULATE) {
            return bulk.restoreIoPortRemainder(key, amount);
        }
        return source.insert(key, amount, mode, actionSource);
    }
}
