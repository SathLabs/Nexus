package dev.satherov.nexus.gametest.api.client;

import dev.satherov.nexus.gametest.mixin.MouseHandlerAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;

import com.mojang.blaze3d.platform.Window;

import org.jetbrains.annotations.ApiStatus;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

///
/// The mouse of a client under test.
/// Positions are gui-scaled pixels and buttons are GLFW button codes.
///
public final class Mouse {

    ///
    /// The client the mouse belongs to.
    ///
    private final Client client;

    ///
    /// The buttons the script is holding down.
    ///
    private final Set<Integer> held = new HashSet<>();

    ///
    /// Creates the mouse of the given client.
    ///
    /// @param client The client the mouse belongs to.
    ///
    @ApiStatus.Internal
    public Mouse(Client client) {
        this.client = client;
    }

    ///
    /// Moves the cursor to the position.
    ///
    /// @param x The x of the position.
    /// @param y The y of the position.
    ///
    public void move(double x, double y) {
        Minecraft minecraft = this.client.minecraft();
        Window window = minecraft.getWindow();

        ((MouseHandlerAccess) minecraft.mouseHandler).invokeOnMove(
                window.handle(),
                x * window.getScreenWidth() / window.getGuiScaledWidth(),
                y * window.getScreenHeight() / window.getGuiScaledHeight()
        );
    }

    ///
    /// Presses and releases the button at the current position within the current frame.
    ///
    /// @param button The button to click.
    ///
    public void click(@MouseButtonInfo.MouseButton int button) {
        this.send(button, GLFW.GLFW_PRESS);
        this.send(button, GLFW.GLFW_RELEASE);
    }

    ///
    /// Moves to the position and clicks the left button.
    ///
    /// @param x The x of the position.
    /// @param y The y of the position.
    ///
    public void click(double x, double y) {
        this.move(x, y);
        this.click(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    ///
    /// Presses the button and keeps it down until [#release(int)].
    ///
    /// @param button The button to hold down.
    ///
    public void hold(@MouseButtonInfo.MouseButton int button) {
        this.held.add(button);
        this.send(button, GLFW.GLFW_PRESS);
    }

    ///
    /// Releases a held button.
    /// Does nothing if it is not down.
    ///
    /// @param button The button to release.
    ///
    public void release(@MouseButtonInfo.MouseButton int button) {
        if (this.held.remove(button)) {
            this.send(button, GLFW.GLFW_RELEASE);
        }
    }

    ///
    /// Scrolls vertically by the amount, positive away from the user.
    ///
    /// @param amount The distance to scroll by.
    ///
    public void scroll(double amount) {
        Minecraft minecraft = this.client.minecraft();
        ((MouseHandlerAccess) minecraft.mouseHandler).invokeOnScroll(minecraft.getWindow().handle(), 0.0D, amount);
    }

    ///
    /// Releases everything the script still holds.
    ///
    @ApiStatus.Internal
    public void releaseAll() {
        for (int button : Set.copyOf(this.held)) {
            this.release(button);
        }
    }

    ///
    /// Sends one button event of the given action through the client's mouse handler.
    ///
    /// @param button The button the event is for.
    /// @param action The GLFW action of the event.
    ///
    private void send(@MouseButtonInfo.MouseButton int button, @MouseButtonInfo.Action int action) {
        Minecraft minecraft = this.client.minecraft();
        ((MouseHandlerAccess) minecraft.mouseHandler).invokeOnButton(
                minecraft.getWindow().handle(),
                new MouseButtonInfo(button, this.client.keyboard().modifiers()),
                action
        );
    }
}
