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

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

///
/// The tick window of one server test that was measured, including the time each server tick took.
///
@Slf4j
@ApiStatus.Internal
public final class TickWindow implements Consumer<GameTestHelper> {
    
    ///
    /// Where the window's measurements are written into.
    ///
    private final Measurements measurements;
    
    ///
    /// The test that this window measures, or `null` for the empty baseline window.
    ///
    private final Discovered.@Nullable Valid test;
    
    ///
    /// The number of ticks that the window checks.
    ///
    private final int length;
    
    ///
    /// If the vanilla profiler also checks the window.
    ///
    private final boolean profiled;
    
    ///
    /// Creates the test window, provided that the test actually does declare a window to test.
    ///
    /// @param test         The test that this window measures.
    /// @param measurements Where the window's measurements are written into.
    ///
    /// @throws NullPointerException If the test doesn't declare a window.
    ///
    public TickWindow(Discovered.Valid test, Measurements measurements) {
        Measured measured = Objects.requireNonNull(test.measured(), "'" + test.id() + "' declares no window");
        
        this.measurements = measurements;
        this.test = test;
        this.length = measured.value();
        this.profiled = measured.profile();
    }
    
    ///
    /// Creates an empty window for the baseline run.
    ///
    /// @param measurements Where the window's measurements are written into.
    ///
    private TickWindow(Measurements measurements) {
        this.measurements = measurements;
        this.test = null;
        this.length = measurements.baselineLength();
        this.profiled = false;
    }
    
    ///
    /// Creates the empty tick window for the baseline run of the measurements.
    ///
    /// It exists to check what a test looks like that does nothing at all, to subtract those numbers from every real test
    /// to ensure that we only get the numbers we actually want from the measurements.
    ///
    /// @param measurements Where the window's measurements are written into.
    ///
    /// @return The tick window for an empty baseline run.
    ///
    public static TickWindow baseline(Measurements measurements) {
        return new TickWindow(measurements);
    }
    
    ///
    /// The path to the file where the profiler wrote its report or `null` didn't ask for one or the profiler stopped before the window.
    ///
    /// @param server  The server the window ran on.
    /// @param test    The id of the measured test.
    /// @param profile The file the profiler report goes into, or `null` if the test didn't ask for one.
    ///
    /// @return The file the profiler report goes into, or `null` if the test didn't ask for one or the profiler stopped before the window closed.
    ///
    private static @Nullable Path getProfileFilePath(MinecraftServer server, Identifier test, @Nullable Path profile) {
        if (profile == null) {
            return null;
        }
        
        // Vanilla stops the recorder after ten seconds, so if it's no longer running here, that means it's a shorter timespan than the window asked for.
        if (!server.isRecordingMetrics()) {
            TickWindow.log.warn("The profiler of '{}' stopped before its window closed, so its breakdown is left out of the measurement", test);
            return null;
        }
        
        return profile;
    }
    
    ///
    /// The duration of the last server tick that finished before us in nanoseconds.
    ///
    /// @param server The server that the durations are read from.
    ///
    /// @return The duration of the last server tick that finished before us in nanoseconds.
    ///
    private static long lastTickNanos(MinecraftServer server) {
        long[] times = server.getTickTimesNanos();
        return times[Math.floorMod(server.getTickCount() - 1, times.length)];
    }
    
    ///
    /// Executes the actual test and records the nanoseconds each tick took.
    ///
    /// @param helper The helper that is running the test.
    ///
    @Override
    public void accept(GameTestHelper helper) {
        if (this.test != null) {
            this.test.invoke(helper);
            if (helper.testInfo.hasSucceeded()) {
                TickWindow.log.error("'{}' succeeded in its own, so no window could be recorded.", this.test.id());
                return;
            }
        }
        
        MinecraftServer server = helper.getLevel().getServer();
        LongList nanos = new LongArrayList(this.length);
        Path profile = this.startProfile(helper, server);
        
        helper.startSequence()
                .thenIdle(Discovered.WINDOW_OVERHEAD)
                .thenExecuteFor(this.length, () -> nanos.add(TickWindow.lastTickNanos(server)))
                .thenExecute(() -> this.write(server, nanos.toLongArray(), profile))
                .thenSucceed();
    }
    
    ///
    /// Starts the vanilla profiler to watch over the measuring window and then returns the file it wrote its results into,
    /// or `null` if the test didn't ask for any.
    ///
    /// @param helper The helper that is running the test.
    /// @param server The server that the profiler records.
    ///
    /// @return The file the profiler report goes into, or `null` if the test asked for none.
    ///
    private @Nullable Path startProfile(GameTestHelper helper, MinecraftServer server) {
        if (this.test == null || !this.profiled) {
            return null;
        }
        
        Path file = this.measurements.profileFile(this.test.id());
        server.startRecordingMetrics(results -> results.saveResults(file), _ -> { });
        helper.testInfo.addListener(new ProfilerEnd(server));
        return file;
    }
    
    ///
    /// Writes what the window recorded to a file, either as the baseline of the run or as the measurement of the test.
    ///
    /// @param server  The server that the window was recorded on.
    /// @param nanos   The duration of each tick, in nanoseconds.
    /// @param profile The file the profiler report goes into, or `null` if the test didn't ask for one.
    ///
    private void write(MinecraftServer server, long[] nanos, @Nullable Path profile) {
        if (this.test == null) {
            this.measurements.writeBaseline(nanos);
            return;
        }
        
        Identifier id = this.test.id();
        this.measurements.write(new Measurement(id, nanos, TickWindow.getProfileFilePath(server, id, profile)));
    }
    
    ///
    /// Ends the recording of the profiler together with the test, so that a test will end the profiler recording even if it fails.
    ///
    /// @param server The server that we are recording from.
    ///
    private record ProfilerEnd(MinecraftServer server) implements GameTestListener {
        
        ///
        /// Does nothing.
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
        /// Does nothing.
        ///
        /// @param original The test that is rerun.
        /// @param copy     The copy of the test added for the rerun.
        /// @param runner   The runner of the test.
        ///
        @Override
        public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) { }
    }
}
