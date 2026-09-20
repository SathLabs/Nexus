package dev.satherov.nexus.gametest.internal.server;

import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.gametest.api.measurement.Measured;
import dev.satherov.nexus.gametest.internal.discovery.Discovered;
import dev.satherov.nexus.gametest.internal.measurement.Measurement;
import dev.satherov.nexus.gametest.internal.measurement.Measurements;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

///
/// The measured window of one server test: runs the body, records the server's tick durations for the window, writes the measurement, then succeeds.
///
@Slf4j
@ApiStatus.Internal
public final class TickWindow implements Consumer<GameTestHelper> {

    ///
    /// Where the window's measurement is written.
    ///
    private final Measurements measurements;

    ///
    /// The test the window measures, or `null` for the empty window of the run.
    ///
    private final Discovered.@Nullable Valid test;

    ///
    /// The ticks the window samples.
    ///
    private final int length;

    ///
    /// If vanilla's profiler runs over the window.
    ///
    private final boolean profiled;

    ///
    /// Creates the window of a measured test, as long as the [Measured] the test declares.
    ///
    /// @param test         The test the window measures.
    /// @param measurements Where the window's measurement is written.
    ///
    /// @throws NullPointerException If the test declares no window.
    ///
    public TickWindow(Discovered.Valid test, Measurements measurements) {
        Measured measured = Objects.requireNonNull(test.measured(), "'" + test.id() + "' declares no window");

        this.measurements = measurements;
        this.test = test;
        this.length = measured.value();
        this.profiled = measured.profile();
    }

    ///
    /// Creates the empty window of the run, as long as the longest window any test of this side declares.
    ///
    /// @param measurements Where the window's measurement is written.
    ///
    private TickWindow(Measurements measurements) {
        this.measurements = measurements;
        this.test = null;
        this.length = measurements.baselineLength();
        this.profiled = false;
    }

    ///
    /// The empty window of the run: it records what a test that does nothing costs, so a reader can subtract the harness from every measured window.
    ///
    /// @param measurements Where the window's measurement is written.
    ///
    /// @return The empty window of the run.
    ///
    public static TickWindow baseline(Measurements measurements) {
        return new TickWindow(measurements);
    }

    ///
    /// Runs the body of the test, samples the ticks of the window, writes them, then succeeds the test.
    ///
    /// @param helper The helper of the running test.
    ///
    @Override
    public void accept(GameTestHelper helper) {
        if (this.test != null) {
            this.test.invoke(helper);
            if (helper.testInfo.hasSucceeded()) {
                TickWindow.log.error("'{}' succeeded in its own body, so no window was recorded; a measured test leaves succeeding to the harness", this.test.id());
                return;
            }
        }

        MinecraftServer server = helper.getLevel().getServer();
        LongList nanos = new LongArrayList(this.length);
        Path profile = this.startProfile(helper, server);

        // A sample reads the tick that finished before it, so the window idles past the tick the body ran in.
        helper.startSequence()
                .thenIdle(Discovered.WINDOW_OVERHEAD)
                .thenExecuteFor(this.length, () -> nanos.add(TickWindow.lastTickNanos(server)))
                .thenExecute(() -> this.write(server, nanos.toLongArray(), profile))
                .thenSucceed();
    }

    ///
    /// Starts vanilla's profiler over the window and returns the file its breakdown goes into, or `null` if the test asked for none.
    ///
    /// @param helper The helper of the running test.
    /// @param server The server the profiler records.
    ///
    /// @return The file the breakdown goes into, or `null` if the test asked for none.
    ///
    private @Nullable Path startProfile(GameTestHelper helper, MinecraftServer server) {
        if (this.test == null || !this.profiled) {
            return null;
        }

        // The recorder hands the breakdown to this callback on the server thread, at the end of the tick the recording ends in.
        Path file = this.measurements.profileFile(this.test.id());
        server.startRecordingMetrics(results -> results.saveResults(file), _ -> { });
        helper.testInfo.addListener(new ProfilerEnd(server));
        return file;
    }

    ///
    /// Writes what the window recorded: the baseline of the run, or the measurement of the test it measured.
    ///
    /// @param server  The server the window ran on.
    /// @param nanos   The duration of each tick of the window, in nanoseconds.
    /// @param profile The file the breakdown goes into, or `null` if the test asked for none.
    ///
    private void write(MinecraftServer server, long[] nanos, @Nullable Path profile) {
        if (this.test == null) {
            this.measurements.writeBaseline(nanos);
            return;
        }

        Identifier id = this.test.id();
        this.measurements.write(new Measurement(id, nanos, TickWindow.breakdown(server, id, profile)));
    }

    ///
    /// The file the breakdown of the window goes into, or `null` if the test asked for none or the profiler stopped before the window closed.
    ///
    /// @param server  The server the window ran on.
    /// @param test    The id of the measured test.
    /// @param profile The file the breakdown goes into, or `null` if the test asked for none.
    ///
    /// @return The file the breakdown of the window goes into, or `null` if the test asked for none or the profiler stopped before the window closed.
    ///
    private static @Nullable Path breakdown(MinecraftServer server, Identifier test, @Nullable Path profile) {
        if (profile == null) {
            return null;
        }

        // Vanilla stops the recorder ten seconds in, so one that is no longer running covered a shorter span than the window.
        if (!server.isRecordingMetrics()) {
            TickWindow.log.warn("The profiler of '{}' stopped before its window closed, so its breakdown is left out of the measurement", test);
            return null;
        }

        return profile;
    }

    ///
    /// The duration of the server tick that finished before the one we are in, in nanoseconds.
    ///
    /// @param server The server the durations are read from.
    ///
    /// @return The duration of the server tick that finished before the one we are in, in nanoseconds.
    ///
    private static long lastTickNanos(MinecraftServer server) {
        long[] times = server.getTickTimesNanos();
        return times[Math.floorMod(server.getTickCount() - 1, times.length)];
    }

    ///
    /// Ends the recording with the test, so a test that fails after its body does not leave the profiler running into the tests that follow.
    ///
    /// @param server The server whose recording ends.
    ///
    private record ProfilerEnd(MinecraftServer server) implements GameTestListener {

        ///
        /// Does nothing: the recording ends with the test and nowhere else.
        ///
        /// @param testInfo The test whose structure was loaded.
        ///
        @Override
        public void testStructureLoaded(GameTestInfo testInfo) { }

        ///
        /// Ends the recording.
        ///
        /// @param testInfo The test that passed.
        /// @param runner   The runner of the test.
        ///
        @Override
        public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
            this.server.finishRecordingMetrics();
        }

        ///
        /// Ends the recording.
        ///
        /// @param testInfo The test that failed.
        /// @param runner   The runner of the test.
        ///
        @Override
        public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
            this.server.finishRecordingMetrics();
        }

        ///
        /// Does nothing: the recording ends with the test and nowhere else.
        ///
        /// @param original The test that is rerun.
        /// @param copy     The copy of the test added for the rerun.
        /// @param runner   The runner of the test.
        ///
        @Override
        public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) { }
    }
}
