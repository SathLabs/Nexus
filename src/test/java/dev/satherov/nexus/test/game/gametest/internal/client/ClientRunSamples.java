package dev.satherov.nexus.test.game.gametest.internal.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.measurement.Measured;

///
/// The client tests a run measures, profiles, or lets fail without failing the run.
///
public class ClientRunSamples {
    
    @ClientTest
    @Measured(40)
    public static void window(Client client) { }
    
    @ClientTest
    @Measured(value = 3, profile = true)
    public static void profiledWindow(Client client) { }
    
    @ClientTest(required = false)
    public static void optionalFailure(Client client) {
        throw new AssertionError("This test always fails");
    }
    
    @ClientTest(maxFrames = 1, required = false)
    public static void outOfFrames(Client client) {
        client.ticks(2);
    }
}
