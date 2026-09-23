package dev.satherov.nexus.gametest.internal.measurement;

import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.gametest.api.measurement.Measured;
import dev.satherov.nexus.gametest.internal.discovery.Discovered;
import dev.satherov.zelqro.mapping.ObjectMapping;

import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

///
/// Manages the measurements of a test case.
///
/// Writes the results into a json file under the report directory, adds the vanilla profiler's summary and a comparison if one was given with `-Pcompare`.
///
@Slf4j
@ApiStatus.Internal
@Accessors(fluent = true)
public final class Measurements {
    
    ///
    /// The identifier that the empty window of the run is written under.
    ///
    public static final Identifier BASELINE = Identifier.fromNamespaceAndPath("nexus_gametest", "baseline");
    
    ///
    /// The json writer to use for the result files.
    ///
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    ///
    /// The directory that the measurement files are saved in.
    ///
    private final Path directory;
    
    ///
    /// The directory that an earlier measurement is saved in to compare against, or `null` if we don't compare against anything.
    ///
    private final @Nullable Path compare;
    
    ///
    /// The duration of the baseline, which is the longest duration of any discovered test.
    ///
    @Getter
    private final int baselineLength;
    
    ///
    /// Creates the measurements of a run, getting the baseline duration from the longest window that any test declares.
    ///
    /// @param directory The directory that the measurement files are saved in.
    /// @param compare   The directory that an earlier measurement is saved in to compare against, or `null` if we don't compare against anything.
    /// @param tests     Every discovered test of the run.
    ///
    public Measurements(Path directory, @Nullable Path compare, List<Discovered> tests) {
        this.directory = directory;
        this.compare = compare;
        
        int longest = 0;
        for (Discovered test : tests) {
            if (test instanceof Discovered.Valid(_, _, _, @Nullable Measured measured) && measured != null) {
                longest = Math.max(longest, measured.value());
            }
        }
        
        this.baselineLength = longest;
    }
    
    ///
    /// Gets the path to the file where the given test's measurements are stored, based on the mod's directory.
    ///
    /// @param directory The mod's directory.
    /// @param test      The identifier of the test.
    /// @param extension The extension of the file, with its dot.
    ///
    /// @return The path to a test file under the mod's directory.
    ///
    private static Path file(Path directory, Identifier test, String extension) {
        return test.withSuffix(extension).resolveAgainst(directory);
    }
    
    ///
    /// Writes the given measurement to file and then logs its summary.
    ///
    /// If there is an older measurement, we also log the difference between the two.
    ///
    /// @param measurement The measurement to write.
    ///
    public void write(Measurement measurement) {
        if (measurement.nanos().length == 0) {
            Measurements.log.warn("'{}' recorded an empty window", measurement.test());
            return;
        }
        
        Summary summary = Summary.of(measurement.nanos());
        Summary difference = ObjectMapping.mapNonNull(this.earlier(measurement.test()), summary::minus);
        
        JsonArray nanos = new JsonArray(measurement.nanos().length);
        for (long duration : measurement.nanos()) {
            nanos.add(duration);
        }
        
        JsonObject json = new JsonObject();
        json.addProperty("test", measurement.test().toString());
        json.add("nanos", nanos);
        json.add("summary", summary.toJson());
        if (measurement.profile() != null) {
            json.addProperty("profile", measurement.profile().toString());
        }
        
        if (difference != null) {
            json.add("difference", difference.toJson());
        }
        
        Path file = Measurements.file(this.directory, measurement.test(), ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Measurements.GSON.toJson(json));
        } catch (IOException failure) {
            Measurements.log.warn("Can't write the measurement of '{}' to {}", measurement.test(), file, failure);
        }
        
        Measurements.log.info("{}: {}", measurement.test(), summary.milliseconds());
        if (difference != null) {
            Measurements.log.info("{} against the earlier run: {}", measurement.test(), difference.milliseconds());
        }
    }
    
    ///
    /// Writes the baseline of a run from an empty window of [#baselineLength()] recorded by the caller.
    ///
    /// @param nanos The duration of each frame or tick in nanoseconds, where the index corresponds to the respective tick or frame.
    ///
    public void writeBaseline(long[] nanos) {
        this.write(new Measurement(Measurements.BASELINE, nanos, null));
    }
    
    ///
    /// The file that vanilla's profiler results are written into.
    ///
    /// @param test The identifier of the test we measured.
    ///
    /// @return The path to the file vanilla's profiler results are written into.
    ///
    public Path profileFile(Identifier test) {
        return Measurements.file(this.directory, test, ".txt");
    }
    
    ///
    /// The summary that the directory with the comparison holds for the test with the given identifier, or `null` if none exists.
    ///
    /// @param test The identifier of the test we want to find the summary of.
    ///
    /// @return The summary for the comparison for the test with the given identifier, or `null` if none exists.
    ///
    private @Nullable Summary earlier(Identifier test) {
        if (this.compare == null) {
            return null;
        }
        
        Path file = Measurements.file(this.compare, test, ".json");
        if (!Files.exists(file)) {
            return null;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return Summary.from(GsonHelper.getAsJsonObject(GsonHelper.parse(reader), "summary"));
        } catch (IOException | RuntimeException failure) {
            Measurements.log.warn("Could not read an earlier measurement of '{}' from {}", test, file, failure);
            return null;
        }
    }
    
    ///
    /// The durations for one measurement recording.
    ///
    /// @param min    The shortest duration.
    /// @param median The median duration.
    /// @param p95    The 95th percentile of the durations.
    /// @param max    The longest duration.
    /// @param mean   The mean duration.
    ///
    private record Summary(long min, double median, long p95, long max, double mean) {
        
        ///
        /// The number of nanoseconds in a millisecond.
        ///
        private static final double NANOS_PER_MILLISECOND = 1_000_000.0D;
        
        ///
        /// The summary of the durations, where the index corresponds to the tick or frame order.
        ///
        /// @param nanos The durations to summarize, in nanoseconds.
        ///
        /// @return The summary of the durations.
        ///
        private static Summary of(long[] nanos) {
            long[] sorted = nanos.clone();
            Arrays.sort(sorted);
            
            int middle = sorted.length / 2;
            double median = sorted.length % 2 == 0 ? (sorted[middle - 1] + sorted[middle]) / 2.0D : sorted[middle];
            
            return new Summary(
                    sorted[0],
                    median,
                    sorted[(int) Math.ceil(sorted.length * 0.95D) - 1],
                    sorted[sorted.length - 1],
                    Arrays.stream(sorted).average().orElseThrow()
            );
        }
        
        ///
        /// The summary the JSON holds.
        ///
        /// @param json The summary as JSON.
        ///
        /// @return The summary the JSON holds.
        ///
        /// @throws JsonSyntaxException If a number is missing, of the wrong type, or the median or mean is not finite.
        ///
        private static Summary from(JsonObject json) {
            Summary summary = new Summary(
                    GsonHelper.getAsLong(json, "min"),
                    GsonHelper.getAsDouble(json, "median"),
                    GsonHelper.getAsLong(json, "p95"),
                    GsonHelper.getAsLong(json, "max"),
                    GsonHelper.getAsDouble(json, "mean")
            );
            
            // An infinite difference would end up as invalid json.
            if (!Double.isFinite(summary.median) || !Double.isFinite(summary.mean)) {
                throw new JsonSyntaxException("'median' and 'mean' should be finite numbers");
            }
            
            return summary;
        }
        
        ///
        /// The given duration converted to milliseconds.
        ///
        /// @param nanos The duration in nanoseconds.
        ///
        /// @return The duration in milliseconds.
        ///
        private static double toMillis(double nanos) {
            return nanos / Summary.NANOS_PER_MILLISECOND;
        }
        
        ///
        /// Creates a summary where each duration is subtracted by the one of an earlier summary.
        ///
        /// @param earlier The summary of the earlier run.
        ///
        /// @return The summary with all numbers subtracted by those of an earlier summary.
        ///
        private Summary minus(Summary earlier) {
            return new Summary(
                    this.min - earlier.min,
                    this.median - earlier.median,
                    this.p95 - earlier.p95,
                    this.max - earlier.max,
                    this.mean - earlier.mean
            );
        }
        
        ///
        /// The summary as a JSON object.
        ///
        /// @return The summary as a JSON object.
        ///
        private JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("min", this.min);
            json.addProperty("median", this.median);
            json.addProperty("p95", this.p95);
            json.addProperty("max", this.max);
            json.addProperty("mean", this.mean);
            return json;
        }
        
        ///
        /// The numbers converted to milliseconds, all in one line to be logged.
        ///
        /// @return The numbers converted to milliseconds, all in one line.
        ///
        private String milliseconds() {
            return String.format(
                    Locale.ROOT,
                    "min %.3f, median %.3f, p95 %.3f, max %.3f, mean %.3f ms",
                    Summary.toMillis(this.min),
                    Summary.toMillis(this.median),
                    Summary.toMillis(this.p95),
                    Summary.toMillis(this.max),
                    Summary.toMillis(this.mean)
            );
        }
    }
}
