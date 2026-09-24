package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.client.Pump;

import net.minecraft.client.DeltaTracker;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

///
/// Gives the client one whole tick per frame while an accelerated run is active.
///
@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {
    
    ///
    /// The number of ticks the current frame advances the game by.
    ///
    @Shadow
    private float deltaTicks;
    
    ///
    /// The fraction of a tick left over after the whole ticks of the frame.
    ///
    @Shadow
    private float deltaTickResidual;
    
    ///
    /// Sets the frame to exactly one whole tick with nothing left over, if an accelerated run is active.
    ///
    /// @param currentMs The current time, in milliseconds.
    /// @param callback  The callback of the injection.
    ///
    @Inject(
            method = "advanceGameTime",
            at = @At("HEAD"),
            cancellable = true
    )
    private void advanceOneTick(long currentMs, CallbackInfoReturnable<Integer> callback) {
        if (Pump.getInstance() == null) {
            return;
        }
        
        this.deltaTicks = 1.0F;
        this.deltaTickResidual = 0.0F;
        callback.setReturnValue(1);
    }
}
