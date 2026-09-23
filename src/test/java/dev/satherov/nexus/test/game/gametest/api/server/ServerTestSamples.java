package dev.satherov.nexus.test.game.gametest.api.server;

import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.gametest.framework.GameTestHelper;

///
/// Checks that a server test fails past its maximum ticks and that an optional failure leaves the run passing.
///
public class ServerTestSamples {

    @ServerTest(maxTicks = 2, required = false)
    public static void pastMaxTicks(GameTestHelper helper) { }

    @ServerTest(required = false)
    public static void optionalFailure(GameTestHelper helper) {
        helper.fail("The optional sample failed");
    }
}
