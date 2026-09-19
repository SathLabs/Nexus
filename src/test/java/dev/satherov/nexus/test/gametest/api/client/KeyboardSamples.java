package dev.satherov.nexus.test.gametest.api.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

///
/// The client test that opens the pause screen with the escape key and closes it again by clicking its return to game button.
///
public class KeyboardSamples {

    ///
    /// The label of the pause screen's return to game button.
    ///
    private static final Component RETURN_TO_GAME = Component.translatable("menu.returnToGame");

    @ClientTest
    public static void pauseScreen(Client client) {
        client.joinWorld();
        client.until("the world to be in view", () -> client.screen() == null);

        client.keyboard().press(GLFW.GLFW_KEY_ESCAPE);
        client.until("the pause screen", () -> client.screen() instanceof PauseScreen);

        AbstractWidget returnToGame = KeyboardSamples.returnToGame(client);
        client.mouse().click(returnToGame.getX() + returnToGame.getWidth() / 2.0D, returnToGame.getY() + returnToGame.getHeight() / 2.0D);
        client.until("the pause screen to close", () -> client.screen() == null);

        client.leaveWorld();
    }

    ///
    /// The return to game button of the pause screen the client shows.
    ///
    private static AbstractWidget returnToGame(Client client) {
        if (!(client.screen() instanceof PauseScreen pause)) {
            throw new AssertionError("the client does not show the pause screen");
        }

        return pause.children().stream()
                .filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(widget -> KeyboardSamples.RETURN_TO_GAME.equals(widget.getMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the pause screen has no return to game button"));
    }
}
