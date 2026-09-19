package dev.satherov.nexus.test.gametest.internal.server;

import dev.satherov.nexus.gametest.api.measurement.Measured;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.gametest.framework.GameTestHelper;

/// The server tests that record a window of their own, so a run writes their measurements next to the report.
public class TickWindowSamples {

    @Measured(7)
    @ServerTest
    public static void window(GameTestHelper helper) { }

    @Measured(value = 3, profile = true)
    @ServerTest
    public static void profiledWindow(GameTestHelper helper) { }
}
