package dev.satherov.nexus.gametest.api.client;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

///
/// A screen with one text box, for tests that type into an [EditBox].
///
/// The screen handles keys exactly as a vanilla screen with a focused [EditBox] does.
///
public final class TextBoxScreen extends Screen {

    ///
    /// The width of the text box, in GUI pixels.
    ///
    private static final int WIDTH = 200;

    ///
    /// The height of the text box, in GUI pixels.
    ///
    private static final int HEIGHT = 20;

    ///
    /// The text box of the screen.
    ///
    private final EditBox box = new EditBox(this.font, TextBoxScreen.WIDTH, TextBoxScreen.HEIGHT, Component.empty());

    ///
    /// Creates a new screen with an empty text box, which is added and focused once the screen is initialized.
    ///
    public TextBoxScreen() {
        super(Component.empty());
    }

    ///
    /// The text box of the screen.
    ///
    /// @return The text box of the screen.
    ///
    public EditBox getBox() {
        return this.box;
    }

    ///
    /// Adds the text box to the screen and focuses it.
    ///
    @Override
    protected void init() {
        this.addRenderableWidget(this.box);
        this.setInitialFocus(this.box);
    }
}
