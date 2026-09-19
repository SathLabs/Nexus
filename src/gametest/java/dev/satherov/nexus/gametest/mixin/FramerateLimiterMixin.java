package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.client.Pump;

import net.minecraft.client.FramerateLimiter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Takes the frame cap off an accelerated run, which draws at the rate the machine sustains.
///
@Mixin(FramerateLimiter.class)
public abstract class FramerateLimiterMixin {

    ///
    /// A capped frame is a measurement of the cap, so an accelerated run waits out none of it.
    ///
    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
    private static void skipFrameCap(int framerateLimit, CallbackInfo callback) {
        if (Pump.accelerated() != null) {
            callback.cancel();
        }
    }
}
