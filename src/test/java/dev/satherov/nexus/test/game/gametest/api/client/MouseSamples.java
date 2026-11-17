package dev.satherov.nexus.test.game.gametest.api.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.client.TextBoxScreen;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import org.assertj.core.api.Assertions;

import java.util.Objects;

///
/// The client tests that check clicks and scrolling reach the game.
///
public class MouseSamples {

    private static final Component OPTIONS = Component.translatable("menu.options");

    private static final String TEXT = "nexus gametest";

    private static final String LAST_WORD = "gametest";

    private static final double INSET = 8.0D;

    @ClientTest
    public static void clickOpensOptions(Client client) {
        AbstractWidget options = Objects.requireNonNull(client.screen()).children().stream()
                .filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast)
                .filter(widget -> MouseSamples.OPTIONS.equals(widget.getMessage()))
                .findFirst()
                .orElseThrow();

        client.mouse().click(options.getX() + options.getWidth() / 2.0D, options.getY() + options.getHeight() / 2.0D);
        Assertions.assertThat(client.screen()).isInstanceOf(OptionsScreen.class);
    }

    @ClientTest
    public static void clicksInARowAreSingleClicks(Client client) {
        EditBox box = MouseSamples.openTextBox(client);
        double y = box.getY() + box.getHeight() / 2.0D;

        client.mouse().click(box.getX() + MouseSamples.INSET, y);
        client.mouse().click(box.getX() + box.getWidth() - MouseSamples.INSET, y);
        Assertions.assertThat(box.getHighlighted()).isEmpty();
        Assertions.assertThat(box.getCursorPosition()).isEqualTo(MouseSamples.TEXT.length());
    }

    @ClientTest
    public static void doubleClickSelectsTheWord(Client client) {
        EditBox box = MouseSamples.openTextBox(client);

        client.mouse().doubleClick(box.getX() + box.getWidth() - MouseSamples.INSET, box.getY() + box.getHeight() / 2.0D);
        Assertions.assertThat(box.getHighlighted()).isEqualTo(MouseSamples.LAST_WORD);
    }

    @ClientTest
    public static void scrollSelectsPreviousSlot(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        Inventory inventory = Objects.requireNonNull(client.minecraft().player).getInventory();
        inventory.setSelectedSlot(4);
        client.mouse().scroll(1.0D);
        Assertions.assertThat(inventory.getSelectedSlot()).isEqualTo(3);
        client.leaveWorld();
    }

    private static EditBox openTextBox(Client client) {
        TextBoxScreen screen = new TextBoxScreen();
        client.minecraft().setScreen(screen);
        screen.getBox().setValue(MouseSamples.TEXT);
        return screen.getBox();
    }
}
