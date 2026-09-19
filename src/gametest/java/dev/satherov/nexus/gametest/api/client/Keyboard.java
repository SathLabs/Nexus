package dev.satherov.nexus.gametest.api.client;

import dev.satherov.nexus.gametest.mixin.KeyboardHandlerAccess;

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
/// The keyboard of a client under test; keys are GLFW key codes and every event goes through the same path a real window uses.
///
public final class Keyboard {

    ///
    /// The client the keyboard belongs to.
    ///
    private final Client client;

    ///
    /// The keys the script is holding down.
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
    /// Presses and releases the key within the current frame.
    ///
    /// @param key The key to press.
    ///
    public void press(@InputConstants.Value int key) {
        this.send(key, GLFW.GLFW_PRESS);
        this.send(key, GLFW.GLFW_RELEASE);
    }

    ///
    /// Presses the key and keeps it down until [#release(int)].
    ///
    /// @param key The key to hold down.
    ///
    public void hold(@InputConstants.Value int key) {
        this.held.add(key);
        this.send(key, GLFW.GLFW_PRESS);
    }

    ///
    /// Releases a held key; does nothing if it is not down.
    ///
    /// @param key The key to release.
    ///
    public void release(@InputConstants.Value int key) {
        if (this.held.remove(key)) {
            this.send(key, GLFW.GLFW_RELEASE);
        }
    }

    ///
    /// Types the text as character events.
    ///
    /// @param text The text to type.
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
    /// Releases everything the script still holds.
    ///
    @ApiStatus.Internal
    public void releaseAll() {
        for (int key : Set.copyOf(this.held)) {
            this.release(key);
        }
    }

    ///
    /// The GLFW modifier bits of the modifier keys the script is holding down.
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
    /// Sends one key event of the given action through the client's keyboard handler.
    ///
    private void send(@InputConstants.Value int key, @KeyEvent.Action int action) {
        Minecraft minecraft = this.client.minecraft();
        ((KeyboardHandlerAccess) minecraft.keyboardHandler).invokeKeyPress(
                minecraft.getWindow().handle(),
                action,
                new KeyEvent(key, 0, this.modifiers())
        );
    }

    ///
    /// The GLFW modifier bit of the key, or `0` if it is not a modifier key.
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
}
