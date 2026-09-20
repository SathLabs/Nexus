package dev.satherov.nexus.gametest.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

///
/// Opens the client's mouse callbacks to the mouse of a test.
///
@Mixin(MouseHandler.class)
public interface MouseHandlerAccess {

    ///
    /// Handles a button that was pressed or released on the window with the given handle.
    ///
    /// @param handle     The handle of the window the event came from.
    /// @param buttonInfo The button of the event and the modifiers held with it.
    /// @param action     The GLFW action of the event.
    ///
    @Invoker("onButton")
    void invokeOnButton(long handle, MouseButtonInfo buttonInfo, @MouseButtonInfo.Action int action);

    ///
    /// Handles the cursor moving to the given position of the window with the given handle.
    ///
    /// @param handle The handle of the window the event came from.
    /// @param xpos   The x of the position.
    /// @param ypos   The y of the position.
    ///
    @Invoker("onMove")
    void invokeOnMove(long handle, double xpos, double ypos);

    ///
    /// Handles the wheel scrolling by the given offsets on the window with the given handle.
    ///
    /// @param handle  The handle of the window the event came from.
    /// @param xoffset The horizontal offset scrolled by.
    /// @param yoffset The vertical offset scrolled by.
    ///
    @Invoker("onScroll")
    void invokeOnScroll(long handle, double xoffset, double yoffset);
}
