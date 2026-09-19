package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.client.ClientRun;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuBackend;

import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

///
/// Keeps the window of a client test run off the screen; it still renders every frame into it.
///
@Mixin(Window.class)
public abstract class WindowMixin {

    ///
    /// Hides the window a client test run is about to create, unless [RunOptions#SHOW] asks for a visible one.
    ///
    /// The hint sits on the creation call and not at the head, because NeoForge takes the early loading screen's window over where there is one.
    ///
    @Inject(
            method = "createGlfwWindow",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"),
            allow = 1
    )
    private static void hideWindow(int width, int height, String title, long monitor, GpuBackend backend, CallbackInfoReturnable<Long> callback) {
        if (ClientRun.isActive() && !Boolean.getBoolean(RunOptions.SHOW)) {
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        }
    }
}
