package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.client.Pump;

import net.minecraft.client.FramerateLimiter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Removes the frame cap while an accelerated run is active.
///
@Mixin(FramerateLimiter.class)
public abstract class FramerateLimiterMixin {
    
    ///
    /// Skips the frame cap if an accelerated run is active.
    ///
    /// @param framerateLimit The frame cap.
    /// @param callback       The callback of the injection.
    ///
    @Inject(
            method = "limitDisplayFPS",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void skipFrameCap(int framerateLimit, CallbackInfo callback) {
        if (Pump.getInstance() != null) {
            callback.cancel();
        }
    }
}
