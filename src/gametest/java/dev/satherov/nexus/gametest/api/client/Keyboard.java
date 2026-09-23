package dev.satherov.nexus.gametest.api.client;

import dev.satherov.nexus.gametest.mixin.KeyboardHandlerAccess;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;

import com.mojang.blaze3d.platform.InputConstants;

import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

///
/// The fake keyboard a client may use during a test.
///
/// All input codes are GLFW key codes.
///
/// @see GLFW
/// @see InputConstants
///
public final class Keyboard {
    
    ///
    /// The client that this keyboard belongs to.
    ///
    private final Client client;
    
    ///
    /// All keys currently held down.
    ///
    private final Set<Integer> held = new HashSet<>();
    
    ///
    /// Creates the keyboard of the given client.
    ///
    /// @param client The client the keyboard belongs to.
    ///
    @ApiStatus.Internal
    public Keyboard(Client client) {
        this.client = client;
    }
    
    ///
    /// The GLFW modifier bit that the given key corresponds to, or `0` if it is not a modifier key.
    ///
    /// @param key The key to treat as a modifier.
    ///
    /// @return The GLFW modifier bit that the given key corresponds to, or `0` if it is not a modifier key.
    ///
    @InputWithModifiers.Modifiers
    private static int modifier(@InputConstants.Value int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> GLFW.GLFW_MOD_SHIFT;
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> GLFW.GLFW_MOD_CONTROL;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> GLFW.GLFW_MOD_ALT;
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> GLFW.GLFW_MOD_SUPER;
            default -> 0;
        };
    }
    
    ///
    /// Presses the given key and then releases it.
    ///
    /// @param key The key to press.
    ///
    public void press(@InputConstants.Value int key) {
        this.send(key, GLFW.GLFW_PRESS);
        this.send(key, GLFW.GLFW_RELEASE);
    }
    
    ///
    /// Presses the given key and holds it down until [#release(int)] is called.
    ///
    /// @param key The key to hold down.
    ///
    public void hold(@InputConstants.Value int key) {
        this.held.add(key);
        this.send(key, GLFW.GLFW_PRESS);
    }
    
    ///
    /// Releases a held key.
    ///
    /// Does nothing if it is not held down.
    ///
    /// @param key The key to release.
    ///
    public void release(@InputConstants.Value int key) {
        if (this.held.remove(key)) {
            this.send(key, GLFW.GLFW_RELEASE);
        }
    }
    
    ///
    /// Types the given text as character events.
    ///
    /// @param text The text to type.
    ///
    /// @see KeyboardHandler#charTyped(long, CharacterEvent)
    ///
    public void type(String text) {
        Minecraft minecraft = this.client.minecraft();
        KeyboardHandlerAccess handler = (KeyboardHandlerAccess) minecraft.keyboardHandler;
        long handle = minecraft.getWindow().handle();
        
        for (int codepoint : text.codePoints().toArray()) {
            handler.invokeCharTyped(handle, new CharacterEvent(codepoint));
        }
    }
    
    ///
    /// Releases all held keys.
    ///
    @ApiStatus.Internal
    public void releaseAll() {
        for (int key : Set.copyOf(this.held)) {
            this.release(key);
        }
    }
    
    ///
    /// The GLFW modifier bits that may be currently held down.
    ///
    /// @return The GLFW modifier bits that may be currently held down.
    ///
    /// @see InputWithModifiers
    ///
    @ApiStatus.Internal
    @InputWithModifiers.Modifiers
    public int modifiers() {
        int modifiers = 0;
        for (int key : this.held) {
            modifiers |= Keyboard.modifier(key);
        }
        
        return modifiers;
    }
    
    ///
    /// Sends one key event of the given action type with the given key.
    ///
    /// @param key    The key that this event is for.
    /// @param action The GLFW action of the event.
    ///
    private void send(@InputConstants.Value int key, @KeyEvent.Action int action) {
        Minecraft minecraft = this.client.minecraft();
        ((KeyboardHandlerAccess) minecraft.keyboardHandler).invokeKeyPress(
                minecraft.getWindow().handle(),
                action,
                new KeyEvent(key, 0, this.modifiers())
        );
    }
}
