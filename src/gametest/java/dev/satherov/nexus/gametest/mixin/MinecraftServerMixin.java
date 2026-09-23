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
/// Makes the integrated server wait for a released tick instead of its clock while the pump runs it in lockstep.
///
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin extends ReentrantBlockableEventLoop<TickTask> {
    
    ///
    /// The time the next tick is due at, in nanoseconds.
    ///
    @Shadow
    protected long nextTickTimeNanos;
    
    ///
    /// Never called.
    ///
    /// The mixin only extends the server's event loop so that [#waitForReleasedTick(CallbackInfo)] can run its tasks.
    ///
    private MinecraftServerMixin() {
        //noinspection DataFlowIssue This is never called.
        super(null, false);
    }
    
    ///
    /// Runs the server's tasks and then waits for the pump to release a tick, if the pump runs this server in lockstep.
    ///
    /// The next tick is due immediately after the wait.
    ///
    /// @param callback The callback of the injection.
    ///
    @Inject(method = "waitUntilNextTick", at = @At("HEAD"), cancellable = true)
    private void waitForReleasedTick(CallbackInfo callback) {
        Pump pump = Pump.getInstance();
        //noinspection ConstantValue No intellij, this is not actually always true.
        if (pump == null || !pump.isLockstepped((MinecraftServer) (Object) this)) {
            return;
        }
        
        this.runAllTasks();
        pump.awaitTick();
        
        this.nextTickTimeNanos = Util.getNanos();
        callback.cancel();
    }
}
