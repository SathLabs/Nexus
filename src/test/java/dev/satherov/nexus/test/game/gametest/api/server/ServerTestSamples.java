package dev.satherov.nexus.test.game.gametest.api.server;

import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.gametest.framework.GameTestHelper;

import org.assertj.core.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;

///
/// Checks the ways an optional server test fails and that a sequence retries an assertion until it passes.
///
/// Anything thrown in the test, a callback, a condition, or a step should fail only that test, with a message.
///
public class ServerTestSamples {
    
    private static final int TIMEOUT_TICKS = 3;

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
    
    @ServerTest(required = false)
    public static void throwsAnErrorWithoutAMessage(GameTestHelper helper) {
        throw new AssertionError();
    }
    
    @ServerTest(required = false)
    public static void throwsAnErrorWithoutAMessageInACallback(GameTestHelper helper) {
        helper.runAfterDelay(1, () -> {
            throw new StackOverflowError();
        });
    }
    
    @ServerTest(required = false)
    public static void throwsAnExceptionWithoutAMessage(GameTestHelper helper) {
        throw new IllegalStateException();
    }
    
    @ServerTest(required = false)
    public static void throwsAnExceptionInACondition(GameTestHelper helper) {
        AtomicInteger runs = new AtomicInteger();
        helper.succeedWhen(() -> {
            throw new IllegalStateException("The optional condition threw an exception on run " + runs.incrementAndGet());
        });
    }
    
    @ServerTest(required = false)
    public static void throwsAnExceptionInAStep(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    throw new IllegalStateException("The optional step threw an exception");
                })
                .thenSucceed();
    }
    
    @ServerTest(maxTicks = ServerTestSamples.TIMEOUT_TICKS, required = false)
    public static void throwsAnErrorOnTheTimeoutTick(GameTestHelper helper) {
        helper.succeedWhen(() -> {
            if (helper.getTick() > ServerTestSamples.TIMEOUT_TICKS) {
                throw new Error("The optional condition threw an error on the timeout tick");
            }
            
            helper.fail("The optional condition does not hold before the timeout");
        });
    }
    
    @ServerTest(maxTicks = ServerTestSamples.TIMEOUT_TICKS, required = false)
    public static void throwsAnExceptionOnTheLastTick(GameTestHelper helper) {
        helper.succeedWhen(() -> {
            if (helper.getTick() == ServerTestSamples.TIMEOUT_TICKS) {
                throw new IllegalStateException("The optional condition threw an exception on the last tick before the timeout");
            }
            
            helper.fail("The optional condition does not hold before the last tick");
        });
    }
    
    @ServerTest(required = false)
    public static void failsAnAssertionInAStepAtOnce(GameTestHelper helper) {
        helper.startSequence()
                .thenExecute(() -> {
                    throw new AssertionError("The optional step failed an assertion");
                })
                .thenSucceed();
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
