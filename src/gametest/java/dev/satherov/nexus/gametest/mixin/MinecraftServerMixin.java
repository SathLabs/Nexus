package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.client.Pump;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.util.Util;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Ties the integrated server's tick to the pump: it waits for a released tick instead of waiting on the clock.
///
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends ReentrantBlockableEventLoop<TickTask> {

    @Shadow
    protected long nextTickTimeNanos;

    ///
    /// Never called: the mixin extends the server's event loop only so [#waitForReleasedTick(CallbackInfo)] can drain its tasks.
    ///
    private MinecraftServerMixin() {
        //noinspection DataFlowIssue The constructor is dropped when the mixin is applied
        super(null, false);
    }

    ///
    /// Vanilla's wait stays in place wherever the pump drives no tick of this server: every other server, and the spans where this one starts up or stops.
    ///
    /// The released tick is what the server's clock counts from, so a tick keeps its normal budget and vanilla's wait is usable again the moment the lockstep ends.
    ///
    @Inject(method = "waitUntilNextTick", at = @At("HEAD"), cancellable = true)
    private void waitForReleasedTick(CallbackInfo callback) {
        Pump pump = Pump.accelerated();
        if (pump == null || !pump.isLockstepped((MinecraftServer) (Object) this)) {
            return;
        }

        this.runAllTasks();
        pump.awaitTick();

        // The tick loop adds a whole tick's worth to this per pass and never takes it back, so a lockstep that outruns real time runs it ever further ahead.
        this.nextTickTimeNanos = Util.getNanos();
        callback.cancel();
    }
}
