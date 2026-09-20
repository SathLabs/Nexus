package dev.satherov.nexus.test.gametest.api.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.minecraft.client.gui.screens.TitleScreen;

///
/// The client test that renders the title screen and checks the frame against the golden recorded of it.
///
public class CaptureSamples {

    @ClientTest
    public static void titleScreen(Client client) {
        // The screen is already the title screen while the Mojang overlay fades over it.
        client.until("the loading overlay to go", () -> client.minecraft().getOverlay() == null);
        client.until("the title screen", () -> client.screen() instanceof TitleScreen);
        client.tick();

        client.capture().assertGolden("title_screen");
    }
}
