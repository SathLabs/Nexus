package dev.satherov.nexus.gametest.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

///
/// Acts as if the client had interacted with the keyboard.
///
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerAccess {
    
    ///
    /// Handles the key supposedly interacted with together with its action.
    ///
    /// @param handle The handle of the window that dispatched the event.
    /// @param action The GLFW action of the event.
    /// @param event  The key event.
    ///
    @Invoker("keyPress")
    void invokeKeyPress(long handle, @KeyEvent.Action int action, KeyEvent event);
    
    ///
    /// Handles the character supposedly typed.
    ///
    /// @param handle The handle of the window that dispatched the event.
    /// @param event  The character event.
    ///
    @Invoker("charTyped")
    void invokeCharTyped(long handle, CharacterEvent event);
}
