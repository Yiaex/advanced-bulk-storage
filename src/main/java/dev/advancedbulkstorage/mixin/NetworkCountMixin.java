package dev.advancedbulkstorage.mixin;

import appeng.api.stacks.AEKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.api.stacks.VariantCounter", remap = false)
abstract class NetworkCountMixin {
    @Shadow public abstract long get(AEKey key);
    @Shadow public abstract void set(AEKey key, long amount);

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void advancedBulkStorage$saturatePositiveContribution(AEKey key, long amount, CallbackInfo info) {
        if (amount > 0 && get(key) > Long.MAX_VALUE - amount) {
            set(key, Long.MAX_VALUE);
            info.cancel();
        }
    }
}
