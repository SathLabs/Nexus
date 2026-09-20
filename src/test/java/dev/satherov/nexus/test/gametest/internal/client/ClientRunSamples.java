package dev.satherov.nexus.test.gametest.internal.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.measurement.Measured;

import net.minecraft.client.gui.screens.TitleScreen;

///
/// The client tests of a run: one that waits for the title screen and passes, two that record a window of their own, and one that fails without failing the run.
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

    @Measured(40)
    @ClientTest
    public static void window(Client client) {
        client.until("the title screen", () -> client.screen() instanceof TitleScreen);
    }

    @Measured(value = 3, profile = true)
    @ClientTest
    public static void profiledWindow(Client client) {
        client.until("the title screen", () -> client.screen() instanceof TitleScreen);
    }
}
