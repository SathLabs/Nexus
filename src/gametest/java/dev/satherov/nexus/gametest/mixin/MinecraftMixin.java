package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.client.ClientRun;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Hands the client loop to the client run.
///
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    ///
    /// Takes the loop on its first pass, at the top of the body and outside the frame's profiler scope, and stops the client once the run is done.
    ///
    @Inject(
            method = "run",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/SingleTickProfiler;createTickProfiler(Ljava/lang/String;)Lnet/minecraft/util/profiling/SingleTickProfiler;"),
            allow = 1
    )
    private void runClientTests(CallbackInfo callback) {
        if (!ClientRun.isActive()) {
            return;
        }

        Minecraft minecraft = (Minecraft) (Object) this;
        ClientRun.run(minecraft);
        minecraft.stop();
    }
}
