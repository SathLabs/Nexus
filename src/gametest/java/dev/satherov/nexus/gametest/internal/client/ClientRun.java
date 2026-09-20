package dev.satherov.nexus.gametest.internal.client;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.measurement.Measured;
import dev.satherov.nexus.gametest.internal.discovery.Discovered;
import dev.satherov.nexus.gametest.internal.measurement.Measurement;
import dev.satherov.nexus.gametest.internal.measurement.Measurements;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.neoforged.neoforge.common.util.Lazy;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
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
    /// Every discovered client test the run selects, the invalid ones included.
    ///
    private static final Lazy<List<Discovered>> TESTS = Lazy.of(ClientRun::selected);

    ///
    /// The measurements of the run, shared by its baseline and every window it records.
    ///
    private static final Lazy<Measurements> MEASUREMENTS = Lazy.of(ClientRun::measurements);

    ///
    /// If the client was started as a test run.
    ///
    /// @return `true` if the client was started as a test run.
    ///
    public static boolean isActive() {
        return ClientRun.ACTIVE;
    }

    ///
    /// Records the baseline, runs every selected test, writes the report.
    /// Returns when the last test is done.
    ///
    /// The report holds what ran however the run ends, so a client that dies mid-run still leaves the tests it got through.
    ///
    /// A run with a required failure exits the process with the number of them, since the client's own shutdown always exits `0`.
    ///
    /// @param minecraft The client the tests run on.
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
    /// @param minecraft The client the tests run on.
    /// @param options   The options of the run.
    /// @param report    The report every outcome is recorded in.
    ///
    /// @return The required tests that failed.
    ///
    private static int runTests(Minecraft minecraft, RunOptions options, TestReport report) {
        // A window the operator clicks away from opens the pause screen a frame later: no script clicks it away, and a paused game stops ticking the server.
        minecraft.options.pauseOnLostFocus = false;

        // What moves on its own is frozen, so one machine draws the same frame on every run.
        // The panorama has to stop before the first frame for its angle to stay zero.
        minecraft.options.hideSplashTexts().set(true);
        minecraft.options.panoramaSpeed().set(0.0D);
        minecraft.options.cloudStatus().set(CloudStatus.OFF);

        Pump pump = new Pump(minecraft, options.realtime());
        List<Discovered> tests = ClientRun.TESTS.get();

        ClientRun.awaitLoaded(minecraft, pump);
        ClientRun.reset(minecraft, pump);
        ClientRun.baseline(pump);

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
    /// Pumps frames until the client has finished loading and its loading overlay is gone.
    ///
    /// The load counts as finished when the overlay starts fading out, and the overlay draws over the screen until the fade ends, so a test before that reads the overlay and not its own screen.
    ///
    /// A reload that fails and recovers leaves the client loading for good, so the run gives up after [#LOADING_FRAMES] frames.
    ///
    /// @param minecraft The client that loads.
    /// @param pump      The pump that runs the frames.
    ///
    /// @throws IllegalStateException If the client is still loading after [#LOADING_FRAMES] frames.
    ///
    private static void awaitLoaded(Minecraft minecraft, Pump pump) {
        for (int frame = 0; frame < ClientRun.LOADING_FRAMES; frame++) {
            if (minecraft.isGameLoadFinished() && minecraft.getOverlay() == null) {
                return;
            }

            pump.frame();
        }

        throw new IllegalStateException("the client did not finish loading in " + ClientRun.LOADING_FRAMES + " frames");
    }

    ///
    /// Records the empty window of the run, out of the state every test starts in, so a reader can subtract the harness from every measured window.
    ///
    /// A run whose tests declare no window records none.
    ///
    /// @param pump The pump that records the window.
    ///
    private static void baseline(Pump pump) {
        Measurements measurements = ClientRun.MEASUREMENTS.get();
        int length = measurements.baselineLength();
        if (length > 0) {
            measurements.writeBaseline(pump.measure(length, null));
        }
    }

    ///
    /// Runs the test and records its outcome in the report.
    /// An invalid one is recorded as a failure with its reason.
    ///
    /// @param test      The test to run.
    /// @param minecraft The client the test runs on.
    /// @param pump      The pump that runs the frames.
    /// @param report    The report the outcome is recorded in.
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
    /// Invokes the test with a client of its own, records the window it declared, and takes back what the script still holds however the test ends.
    ///
    /// @param test      The test to invoke.
    /// @param minecraft The client the test runs on.
    /// @param pump      The pump that runs the frames.
    ///
    private static void invoke(Discovered.Valid test, Minecraft minecraft, Pump pump) {
        ClientTest annotation = (ClientTest) test.annotation();
        Client client = new Client(minecraft, test.id(), annotation.maxFrames(), pump::frame);

        try {
            test.invoke(client);
            ClientRun.measure(test, pump);
        } finally {
            ClientRun.release("the keys it held", client.keyboard()::releaseAll);
            ClientRun.release("the buttons it held", client.mouse()::releaseAll);
            ClientRun.release("the world it was in", client::leaveWorld);
        }
    }

    ///
    /// Records the window the test declared, out of the state its body left, and writes it.
    /// A test that declares none records nothing.
    ///
    /// The frames of the window are the harness's own, so none of them counts against the test's frame budget.
    ///
    /// A window that fails to record is logged and leaves the test passing.
    ///
    /// @param test The test whose window is recorded.
    /// @param pump The pump that records the window.
    ///
    private static void measure(Discovered.Valid test, Pump pump) {
        Measured measured = test.measured();
        if (measured == null) {
            return;
        }

        Measurements measurements = ClientRun.MEASUREMENTS.get();
        Path profile = measured.profile() ? measurements.profileFile(test.id()) : null;

        try {
            long[] nanos = pump.measure(measured.value(), profile);
            measurements.write(new Measurement(test.id(), nanos, ClientRun.breakdown(test.id(), profile)));
        } catch (Throwable failure) {
            ClientRun.log.error("the window of client test '{}' was not recorded", test.id(), failure);
        }
    }

    ///
    /// The file the breakdown of the window went into, or `null` if the test asked for none or the profiler wrote none.
    ///
    /// @param test    The id of the test the window belongs to.
    /// @param profile The file the profiler breakdown goes into, or `null` if the test asked for none.
    ///
    /// @return The file the breakdown of the window went into, or `null` if the test asked for none or the profiler wrote none.
    ///
    private static @Nullable Path breakdown(Identifier test, @Nullable Path profile) {
        if (profile == null) {
            return null;
        }

        if (!Files.isRegularFile(profile)) {
            ClientRun.log.warn("The profiler of '{}' wrote no breakdown, so it is left out of the measurement", test);
            return null;
        }

        return profile;
    }

    ///
    /// Takes back one thing the script held, logging a failure of that instead of throwing it, so the test's own failure stays the one reported.
    ///
    /// @param what    The thing the script held, as it is logged.
    /// @param release The taking back of the thing.
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
    /// @param started The nanos the measurement started at.
    ///
    /// @return The milliseconds since the given nanos.
    ///
    private static long millis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    ///
    /// Puts the client where every test starts: out of any level the test before it joined, on a fresh title screen, one frame in.
    ///
    /// A run directory of its own has the client open on the accessibility onboarding, which no script can click away, so the run sets the screen itself.
    ///
    /// @param minecraft The client to reset.
    /// @param pump      The pump that runs the frame.
    ///
    private static void reset(Minecraft minecraft, Pump pump) {
        minecraft.disconnect(new TitleScreen(), false);
        pump.frame();
    }

    ///
    /// Every discovered client test the run selects, the invalid ones included.
    ///
    /// @return Every discovered client test the run selects, the invalid ones included.
    ///
    private static List<Discovered> selected() {
        RunOptions options = RunOptions.fromProperties();
        return Discovered.all(ClientTest.class)
                .stream()
                .filter(test -> options.selects(test.id()))
                .toList();
    }

    ///
    /// The measurements of the run, written next to its report.
    ///
    /// @return The measurements of the run, written next to its report.
    ///
    private static Measurements measurements() {
        RunOptions options = RunOptions.fromProperties();
        return new Measurements(options.report().toAbsolutePath().getParent(), options.compare(), ClientRun.TESTS.get());
    }
}
