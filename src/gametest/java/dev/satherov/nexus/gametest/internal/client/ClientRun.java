package dev.satherov.nexus.gametest.internal.client;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.internal.discovery.Discovered;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.concurrent.TimeUnit;

///
/// The client run: takes the loop the moment the client starts, runs every selected client test in turn from a clean state, writes the report, exits with the outcome.
///
@UtilityClass
@Slf4j
@ApiStatus.Internal
public class ClientRun {

    ///
    /// If the client was started as a test run, which the report property marks.
    ///
    private static final boolean ACTIVE = System.getProperty(RunOptions.REPORT) != null;

    ///
    /// The frames the client may take to load: the reload runs on them, so a client that never finishes spins through them in about a minute.
    ///
    private static final int LOADING_FRAMES = 60_000;

    ///
    /// If the client was started as a test run.
    ///
    public static boolean isActive() {
        return ClientRun.ACTIVE;
    }

    ///
    /// Runs every selected test, writes the report, and returns when the last test is done.
    ///
    /// The report holds what ran however the run ends, so a client that dies mid-run still leaves the tests it got through.
    ///
    /// A run with a required failure exits the process with the number of them, since the client's own shutdown always exits `0`.
    ///
    public static void run(Minecraft minecraft) {
        RunOptions options = RunOptions.fromProperties();
        TestReport report = new TestReport(options.report());

        int failed;
        try {
            failed = ClientRun.runTests(minecraft, options, report);
        } finally {
            report.write();
        }

        if (failed > 0) {
            System.exit(failed);
        }
    }

    ///
    /// Runs every test the options select, each from the title screen and back to it.
    ///
    /// @return The required tests that failed.
    ///
    private static int runTests(Minecraft minecraft, RunOptions options, TestReport report) {
        // A window the operator clicks away from opens the pause screen a frame later: no script clicks it away, and a paused game stops ticking the server.
        minecraft.options.pauseOnLostFocus = false;

        Pump pump = new Pump(minecraft, options.realtime());
        List<Discovered> tests = Discovered.all(ClientTest.class)
                .stream()
                .filter(test -> options.selects(test.id()))
                .toList();

        ClientRun.awaitLoaded(minecraft, pump);
        ClientRun.reset(minecraft, pump);

        int failed = 0;
        for (Discovered test : tests) {
            if (!ClientRun.runTest(test, minecraft, pump, report) && test.required()) {
                failed++;
            }

            ClientRun.reset(minecraft, pump);
        }

        ClientRun.log.info("ran {} client tests, {} required failures", tests.size(), failed);
        return failed;
    }

    ///
    /// Pumps frames until the client has finished loading.
    ///
    /// A reload that fails and recovers leaves the client loading for good, so the run gives up after [#LOADING_FRAMES] frames.
    ///
    private static void awaitLoaded(Minecraft minecraft, Pump pump) {
        for (int frame = 0; frame < ClientRun.LOADING_FRAMES; frame++) {
            if (minecraft.isGameLoadFinished()) {
                return;
            }

            pump.frame();
        }

        throw new IllegalStateException("the client did not finish loading in " + ClientRun.LOADING_FRAMES + " frames");
    }

    ///
    /// Runs the test and records its outcome in the report; an invalid one is recorded as a failure with its reason.
    ///
    /// @return If the test passed.
    ///
    private static boolean runTest(Discovered test, Minecraft minecraft, Pump pump, TestReport report) {
        long started = System.nanoTime();

        try {
            switch (test) {
                case Discovered.Valid valid -> ClientRun.invoke(valid, minecraft, pump);
                case Discovered.Invalid(_, String reason, _) -> throw new AssertionError(reason);
            }
        } catch (Throwable failure) {
            report.failed(test.id(), ClientRun.millis(started), failure, test.required());
            ClientRun.log.error("client test '{}' failed", test.id(), failure);
            return false;
        }

        report.passed(test.id(), ClientRun.millis(started));
        return true;
    }

    ///
    /// Invokes the test with a client of its own, and takes back what the script still holds however the test ends.
    ///
    private static void invoke(Discovered.Valid test, Minecraft minecraft, Pump pump) {
        ClientTest annotation = (ClientTest) test.annotation();
        Client client = new Client(minecraft, annotation.maxFrames(), pump::frame);

        try {
            test.invoke(client);
        } finally {
            ClientRun.release("the keys it held", client.keyboard()::releaseAll);
            ClientRun.release("the buttons it held", client.mouse()::releaseAll);
            ClientRun.release("the world it was in", client::leaveWorld);
        }
    }

    ///
    /// Takes back one thing the script held, logging a failure of that instead of throwing it, so the test's own failure stays the one reported.
    ///
    private static void release(String what, Runnable release) {
        try {
            release.run();
        } catch (Throwable failure) {
            ClientRun.log.error("the client test did not give up {}", what, failure);
        }
    }

    ///
    /// The milliseconds since the given nanos.
    ///
    private static long millis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    ///
    /// Puts the client where every test starts: out of any level the test before it joined, on a fresh title screen, one frame in.
    ///
    /// A run directory of its own has the client open on the accessibility onboarding, which no script can click away, so the run sets the screen itself.
    ///
    private static void reset(Minecraft minecraft, Pump pump) {
        minecraft.disconnect(new TitleScreen(), false);
        pump.frame();
    }
}
