package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.api.client.Keyboard;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

///
/// Reports the keys that the fake [Keyboard] of a client test holds down as down, on top of the real GLFW state.
///
@Mixin(InputConstants.class)
public abstract class InputConstantsMixin {

    ///
    /// Reports the given key as down if it is down on the keyboard of the running client test.
    ///
    /// @param window   The window whose key state is read.
    /// @param key      The key to check.
    /// @param callback The callback of the injection.
    ///
    /// @see Keyboard#isDown(int)
    ///
    @Inject(
            method = "isKeyDown",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void nexus_gametest$reportFakeKey(Window window, int key, CallbackInfoReturnable<Boolean> callback) {
        if (Keyboard.isDown(key)) {
            callback.setReturnValue(true);
        }
    }
}
