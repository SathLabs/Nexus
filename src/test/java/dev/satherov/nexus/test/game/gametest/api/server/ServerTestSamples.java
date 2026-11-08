package dev.satherov.nexus.test.game.gametest.api.server;

import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.gametest.framework.GameTestHelper;

import org.assertj.core.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;

///
/// Checks the ways an optional server test fails and that a sequence retries an assertion until it passes.
///
/// An error thrown in the test, a callback, or a condition should fail only that test.
///
public class ServerTestSamples {

    @ServerTest(maxTicks = 2, required = false)
    public static void pastMaxTicks(GameTestHelper helper) { }

    @ServerTest(required = false)
    public static void optionalFailure(GameTestHelper helper) {
        helper.fail("The optional sample failed");
    }
    
    @ServerTest(required = false)
    public static void throwsAnError(GameTestHelper helper) {
        throw new AssertionError("The optional sample threw an error");
    }
    
    @ServerTest(required = false)
    public static void throwsAnErrorInACallback(GameTestHelper helper) {
        AtomicInteger runs = new AtomicInteger();
        helper.runAfterDelay(1, () -> {
            throw new AssertionError("The optional callback threw an error on run " + runs.incrementAndGet());
        });
    }
    
    @ServerTest(required = false)
    public static void throwsAnErrorInACondition(GameTestHelper helper) {
        AtomicInteger runs = new AtomicInteger();
        helper.succeedWhen(() -> {
            throw new Error("The optional condition threw an error on run " + runs.incrementAndGet());
        });
    }
    
    @ServerTest
    public static void retriesAnAssertionUntilItPasses(GameTestHelper helper) {
        helper.succeedWhen(() -> Assertions.assertThat(helper.getTick()).isGreaterThanOrEqualTo(3L));
    }
    
    @ServerTest(required = false)
    public static void retriesAnAssertionUntilTheTimeout(GameTestHelper helper) {
        helper.succeedWhen(() -> {
            throw new AssertionError("The optional assertion never passed");
        });
    }
}
