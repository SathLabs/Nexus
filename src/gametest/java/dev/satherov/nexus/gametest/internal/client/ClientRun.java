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
import dev.satherov.nexus.gametest.mixin.LogoRendererMixin;
import dev.satherov.nexus.gametest.mixin.MinecraftMixin;
import dev.satherov.nexus.gametest.mixin.WindowMixin;

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
/// Responsible for running all client tests, writing the reports, and exiting.
///
/// @see MinecraftMixin
/// @see LogoRendererMixin
/// @see WindowMixin
///
@Slf4j
@UtilityClass
@ApiStatus.Internal
public class ClientRun {
    
    ///
    /// If the client was started as a test run.
    ///
    /// If a path for the report was given, this is automatically considered a test run.
    ///
    private static final boolean ACTIVE = System.getProperty(RunOptions.REPORT) != null;
    
    ///
    /// How many frames the client is allowed to take to load or reload.
    ///
    private static final int LOADING_FRAMES = 60_000;
    
    ///
    /// Every client test that was found.
    ///
    private static final Lazy<List<Discovered>> TESTS = Lazy.of(ClientRun::selected);
    
    ///
    /// All measurements taken by the run.
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
    /// Runs every selected test and then writes the report.
    ///
    /// Reports are written even if a failure occurs.
    ///
    /// The number of failed tests corresponds to the system exit code.
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
    /// Configures the client and then runs every test selected.
    ///
    /// @param minecraft The client that all tests run on.
    /// @param options   The configured options of the run.
    /// @param report    The report every outcome is written into.
    ///
    /// @return The number of required tests that failed.
    ///
    private static int runTests(Minecraft minecraft, RunOptions options, TestReport report) {
        // Accidentally clicking off the screen would open the pause-screen which would cause issues in the tests.
        minecraft.options.pauseOnLostFocus = false;
        
        // Prevent anything from moving on its own to ensure goldens have a consistent baseline.
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
        
        ClientRun.log.info("Ran {} client tests, {} tests marked as required failed", tests.size(), failed);
        return failed;
    }
    
    ///
    /// Progresses the game until the client has finished loading and the overlay is gone.
    ///
    /// If a reload fails and does not leave the client in a proper state after {@value ClientRun#LOADING_FRAMES} frames, the run is aborted.
    ///
    /// @param minecraft The minecraft client that is currently loading.
    /// @param pump      The pump that executes each frame.
    ///
    /// @throws IllegalStateException If the client is still loading after {@value ClientRun#LOADING_FRAMES} frames.
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
    /// Records how long an empty window takes to render so that it can be subtracted from every measured window.
    ///
    /// If a test doesn't declare a window, nothing will be recorded.
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
    /// Runs one test and records its outcome.
    ///
    /// An invalid test is recorded as a failure with its violation.
    ///
    /// @param test      The test to run.
    /// @param minecraft The minecraft client that runs the test.
    /// @param pump      The pump that executes each frame.
    /// @param report    The report the outcome is written into.
    ///
    /// @return `true` if the test passed.
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
            ClientRun.log.error("Client test '{}' failed", test.id(), failure);
            return false;
        }
        
        report.passed(test.id(), ClientRun.millis(started));
        return true;
    }
    
    ///
    /// Invokes the specific test with a new [Client], measures its duration, and then resets the client after it completed.
    ///
    /// @param test      The test to invoke.
    /// @param minecraft The minecraft client that runs the test.
    /// @param pump      The pump that executes each frame.
    ///
    private static void invoke(Discovered.Valid test, Minecraft minecraft, Pump pump) {
        ClientTest annotation = (ClientTest) test.annotation();
        Client client = new Client(minecraft, test.id(), annotation.maxFrames(), pump::frame);
        
        try {
            test.invoke(client);
            ClientRun.measure(test, pump);
        } finally {
            ClientRun.release("The keys it held", client.keyboard()::releaseAll);
            ClientRun.release("The buttons it held", client.mouse()::releaseAll);
            ClientRun.release("The world it was in", client::leaveWorld);
        }
    }
    
    ///
    /// Measures the duration of the test's window and writes it to a file.
    ///
    /// A test that does not declare any window doesn't record anything.
    ///
    /// The time it takes to record the window is not counted against the test's frame budget.
    ///
    /// If a recording fails, the error is logged and the test will still pass.
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
            ClientRun.log.error("The window of client test '{}' could not be recorded", test.id(), failure);
        }
    }
    
    ///
    /// The path to the file where vanilla's profiler wrote its report.
    ///
    /// Will be `null` if the test never asked for it or the profiler didn't write anything.
    ///
    /// @param test    The id of the test that the window belongs to.
    /// @param profile The path that vanilla's profiler wrote its report into or `null` if it didn't.
    ///
    /// @return `profile` if it is a valid file or `null` otherwise.
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
    /// Executes the given release runnable and logs on failure.
    ///
    /// Meant to be used for resetting the client back to a clean slate.
    ///
    /// @param what    The thing the script held, used in the failure message.
    /// @param release The action to run to release the held thing.
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
    /// @param started The nanos that we started at.
    ///
    /// @return The milliseconds since the given nanos.
    ///
    private static long millis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
    
    ///
    /// Disconnects the client from any server it is connected to and places it back on the title screen and then runs a single frame.
    ///
    /// @param minecraft The client to reset.
    /// @param pump      The pump that executes the frame.
    ///
    private static void reset(Minecraft minecraft, Pump pump) {
        minecraft.disconnect(new TitleScreen(), false);
        pump.frame();
    }
    
    ///
    /// Every discovered client test that the current run selects.
    ///
    /// @return Every discovered client test that the current run selects.
    ///
    private static List<Discovered> selected() {
        RunOptions options = RunOptions.fromProperties();
        return Discovered.all(ClientTest.class)
                .stream()
                .filter(test -> options.selects(test.id()))
                .toList();
    }
    
    ///
    /// The measurements of the run, written in the same directory as the report.
    ///
    /// @return The measurements of the run, written in the same directory as the report.
    ///
    private static Measurements measurements() {
        RunOptions options = RunOptions.fromProperties();
        return new Measurements(options.report().toAbsolutePath().getParent(), options.compare(), ClientRun.TESTS.get());
    }
}
