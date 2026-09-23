package dev.satherov.nexus.test.game.gametest.api.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.minecraft.client.gui.components.AbstractWidget;
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
    public static void scrollSelectsPreviousSlot(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        Inventory inventory = Objects.requireNonNull(client.minecraft().player).getInventory();
        inventory.setSelectedSlot(4);
        client.mouse().scroll(1.0D);
        Assertions.assertThat(inventory.getSelectedSlot()).isEqualTo(3);
        client.leaveWorld();
    }
}
