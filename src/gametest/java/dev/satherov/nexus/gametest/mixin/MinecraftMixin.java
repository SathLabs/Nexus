package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.client.ClientRun;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Starts [ClientRun] from the client loop.
///
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    
    ///
    /// Runs the client tests on the first pass of the loop and then stops the client, if a client test run is active.
    ///
    /// @param callback The callback of the injection.
    ///
    @Inject(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/SingleTickProfiler;createTickProfiler(Ljava/lang/String;)Lnet/minecraft/util/profiling/SingleTickProfiler;"
            ),
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
