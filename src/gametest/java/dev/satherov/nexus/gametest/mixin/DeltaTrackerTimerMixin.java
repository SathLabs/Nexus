package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.client.Pump;

import net.minecraft.client.DeltaTracker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

///
/// Hands the client one whole tick per frame while an accelerated run owns the clock.
///
@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {

    @Shadow
    private float deltaTicks;
    @Shadow
    private float deltaTickResidual;

    ///
    /// The frame is the tick under an accelerated run, so it carries the whole of one and leaves nothing over as a partial tick.
    ///
    @Inject(method = "advanceGameTime", at = @At("HEAD"), cancellable = true)
    private void advanceOneTick(long currentMs, CallbackInfoReturnable<Integer> callback) {
        if (Pump.getAccelerated() == null) {
            return;
        }

        this.deltaTicks = 1.0F;
        this.deltaTickResidual = 0.0F;
        callback.setReturnValue(1);
    }
}
