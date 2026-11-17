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
/// The fake mouse a client may use during a test.
///
/// Positions are all GUI-scaled pixels.
///
/// All buttons are GLFW button codes.
///
/// @see GLFW
///
public final class Mouse {
    
    ///
    /// A button that no click has, which makes vanilla count the next press as a single click.
    ///
    private static final int NO_BUTTON = -1;
    
    ///
    /// The client that this mouse belongs to.
    ///
    private final Client client;
    
    ///
    /// All buttons currently held down.
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
    /// Moves the cursor to the given position.
    ///
    /// @param x The horizontal position to move, starting from the left side.
    /// @param y The vertical position to move, starting from the top side.
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
    /// Clicks the given button and then releases it.
    ///
    /// The click is always a single click. Vanilla counts a second click on the same screen within 250 ms of wall-clock time as a
    /// double click, so two clicks in a row would depend on how fast the machine runs the test. [#doubleClick(double, double)] double-clicks.
    ///
    /// @param button The button to click.
    ///
    public void click(@MouseButtonInfo.MouseButton int button) {
        this.forgetLastClick();
        this.send(button, GLFW.GLFW_PRESS);
        this.send(button, GLFW.GLFW_RELEASE);
    }
    
    ///
    /// Moves to the position and clicks the left button.
    ///
    /// @param x The horizontal position to move, starting from the left side.
    /// @param y The vertical position to move, starting from the top side.
    ///
    public void click(double x, double y) {
        this.move(x, y);
        this.click(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
    
    ///
    /// Moves to the position and double-clicks the left button.
    ///
    /// The second click only counts as the second half of a double click if the screen handled the first one.
    ///
    /// @param x The horizontal position to move, starting from the left side.
    /// @param y The vertical position to move, starting from the top side.
    ///
    public void doubleClick(double x, double y) {
        this.click(x, y);
        this.send(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS);
        this.send(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE);
    }
    
    ///
    /// Clicks the given button and holds it down until [#release(int)] is called.
    ///
    /// The press is always a single click, the same as [#click(int)].
    ///
    /// @param button The button to hold down.
    ///
    public void hold(@MouseButtonInfo.MouseButton int button) {
        this.held.add(button);
        this.forgetLastClick();
        this.send(button, GLFW.GLFW_PRESS);
    }
    
    ///
    /// Releases a held button.
    ///
    /// Does nothing if it is not held down.
    ///
    /// @param button The button to release.
    ///
    public void release(@MouseButtonInfo.MouseButton int button) {
        if (this.held.remove(button)) {
            this.send(button, GLFW.GLFW_RELEASE);
        }
    }
    
    ///
    /// Scrolls vertically by the given amount.
    ///
    /// Positive means scrolling the wheel up, away from the user, the same as GLFW.
    ///
    /// A unit of `1.0` would be one "step" of the wheel.
    ///
    /// @param amount The distance to scroll by.
    ///
    public void scroll(double amount) {
        Minecraft minecraft = this.client.minecraft();
        ((MouseHandlerAccess) minecraft.mouseHandler).invokeOnScroll(minecraft.getWindow().handle(), 0.0D, amount);
    }
    
    ///
    /// Releases all held buttons.
    ///
    @ApiStatus.Internal
    public void releaseAll() {
        for (int button : Set.copyOf(this.held)) {
            this.release(button);
        }
    }
    
    ///
    /// Makes vanilla forget the last click, so that the next press is a single click.
    ///
    private void forgetLastClick() {
        ((MouseHandlerAccess) this.client.minecraft().mouseHandler).setLastClickButton(Mouse.NO_BUTTON);
    }
    
    ///
    /// Sends one button event of the given action type with the given button.
    ///
    /// @param button The button that the event is for.
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
