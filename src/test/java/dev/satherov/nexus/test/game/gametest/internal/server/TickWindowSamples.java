package dev.satherov.nexus.test.game.gametest.internal.server;

import dev.satherov.nexus.gametest.api.measurement.Measured;
import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

///
/// Checks the tick windows that measured server tests record and the tick rate of the run.
///
public class TickWindowSamples {

    @ServerTest
    @Measured(7)
    public static void window(GameTestHelper helper) { }

    @ServerTest
    @Measured(value = 3, profile = true)
    public static void profiledWindow(GameTestHelper helper) { }

    @ServerTest
    @Measured(2)
    public static void succeedsItself(GameTestHelper helper) {
        helper.succeed();
    }

    @ServerTest
    public static void tickRate(GameTestHelper helper) {
        AtomicLong start = new AtomicLong();
        helper.runAfterDelay(20L, () -> start.set(System.nanoTime()));
        helper.runAfterDelay(40L, () -> {
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start.get());
            if (RunOptions.fromProperties().realtime()) {
                helper.assertTrue(millis >= 800L && millis <= 1_500L, "Twenty ticks took '" + millis + "' ms at the normal tick rate");
            } else {
                helper.assertTrue(millis < 500L, "Twenty ticks took '" + millis + "' ms");
            }

            helper.succeed();
        });
    }
}
