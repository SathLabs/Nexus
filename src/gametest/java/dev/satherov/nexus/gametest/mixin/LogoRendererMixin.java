package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.client.ClientRun;

import net.minecraft.client.gui.components.LogoRenderer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Keeps the easter egg logo off the title screen during a client test run.
///
@Mixin(LogoRenderer.class)
public abstract class LogoRendererMixin {
    
    ///
    /// `true` if the MINCERAFT logo is shown, rolled once when the renderer is created.
    ///
    @Final
    @Shadow
    @Mutable
    private boolean showEasterEgg;
    
    ///
    /// Turns the easter egg off once the renderer is created, if a client test run is active.
    ///
    /// @param keepLogoThroughFade If the logo is kept through the fade.
    /// @param callback            The callback of the injection.
    ///
    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void hideEasterEgg(boolean keepLogoThroughFade, CallbackInfo callback) {
        if (ClientRun.isActive()) {
            this.showEasterEgg = false; // fuck you for breaking my goldens with your random ass chance once every ten thousand runs
        }
    }
}
