package dev.satherov.nexus.gametest.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

///
/// Opens the client's keyboard callbacks to the keyboard of a test.
///
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerAccess {

    ///
    /// Handles a key that was pressed, repeated, or released on the window with the given handle.
    ///
    @Invoker("keyPress")
    void invokeKeyPress(long handle, @KeyEvent.Action int action, KeyEvent event);

    ///
    /// Handles a character that was typed on the window with the given handle.
    ///
    @Invoker("charTyped")
    void invokeCharTyped(long handle, CharacterEvent event);
}
