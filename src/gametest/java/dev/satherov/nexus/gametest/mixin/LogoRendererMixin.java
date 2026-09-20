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
/// Keeps the easter egg logo out of a client test run, however the renderer's own dice fall.
///
@Mixin(LogoRenderer.class)
public abstract class LogoRendererMixin {

    ///
    /// Vanilla's roll for the MINCERAFT logo, made once when the renderer is built.
    ///
    @Final
    @Shadow
    @Mutable
    private boolean showEasterEgg;

    ///
    /// Takes the roll back once the renderer is built, before anything reads it.
    ///
    /// Vanilla shows MINCERAFT on about one renderer in ten thousand, and a run builds one per title screen, so a byte-exact golden of that screen would fail on the roll alone.
    ///
    /// @param keepLogoThroughFade If the logo is kept through the fade.
    /// @param callback            The callback of the injection.
    ///
    @Inject(method = "<init>", at = @At("RETURN"))
    private void hideEasterEgg(boolean keepLogoThroughFade, CallbackInfo callback) {
        if (ClientRun.isActive()) {
            this.showEasterEgg = false;
        }
    }
}
