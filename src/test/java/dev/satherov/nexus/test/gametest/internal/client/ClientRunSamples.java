package dev.satherov.nexus.test.gametest.internal.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.minecraft.client.gui.screens.TitleScreen;

///
/// The client tests of a run: one that waits for the title screen and passes, and one that fails without failing the run.
///
public class ClientRunSamples {

    @ClientTest
    public static void titleScreen(Client client) {
        client.until("the title screen", () -> client.screen() instanceof TitleScreen);
        client.ticks(2);
    }

    @ClientTest(required = false)
    public static void optionalFailure(Client client) {
        client.tick();
        throw new AssertionError("the sample client test fails on purpose");
    }
}
