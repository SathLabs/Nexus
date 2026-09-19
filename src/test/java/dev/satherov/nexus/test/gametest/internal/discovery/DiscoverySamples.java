package dev.satherov.nexus.test.gametest.internal.discovery;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.measurement.Measured;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.gametest.framework.GameTestHelper;

///
/// Test methods for [DiscoveredTest]; the broken ones are optional so the runs only report them.
///
public class DiscoverySamples {

    @ServerTest
    public static void valid(GameTestHelper helper) {
        helper.succeed();
    }

    @Measured(5)
    @ServerTest
    public static void measured(GameTestHelper helper) { }

    @ClientTest
    public static void client(Client client) { }

    @ServerTest(required = false)
    static void notPublic(GameTestHelper helper) { }

    @ServerTest(required = false)
    public void notStatic(GameTestHelper helper) { }

    @ServerTest(required = false)
    public static int notVoid(GameTestHelper helper) {
        return 0;
    }

    @ServerTest(required = false)
    public static void wrongParameter(String helper) { }

    @ServerTest(maxTicks = 0, required = false)
    public static void noTicks(GameTestHelper helper) { }

    @ServerTest(setupTicks = -1, required = false)
    public static void negativeSetup(GameTestHelper helper) { }

    @ServerTest(structure = "My Structure", required = false)
    public static void wrongStructure(GameTestHelper helper) { }

    @Measured(0)
    @ServerTest(required = false)
    public static void emptyWindow(GameTestHelper helper) { }

    @Measured(5)
    @ServerTest(maxTicks = 6, required = false)
    public static void windowOverrunsTicks(GameTestHelper helper) { }

    @ServerTest(required = false)
    public static void sameId(GameTestHelper helper) { }

    @ServerTest(required = false)
    public static void same_id(GameTestHelper helper) { }

    @ClientTest(maxFrames = 0, required = false)
    public static void noFrames(Client client) { }

    ///
    /// Not public, so the method in it is rejected for the class.
    ///
    static class Hidden {

        @ServerTest(required = false)
        public static void method(GameTestHelper helper) { }
    }
}
