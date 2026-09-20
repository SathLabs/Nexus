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
/// Where a run's measurements go: one JSON file per test under the report directory, plus the summary and the comparison against `-Pcompare`.
///
@Slf4j
@ApiStatus.Internal
@Accessors(fluent = true)
public final class Measurements {

    ///
    /// The id the run's empty window is written under.
    ///
    public static final Identifier BASELINE = Identifier.fromNamespaceAndPath("nexus_gametest", "baseline");

    ///
    /// The JSON writer of the measurement files, pretty printed.
    ///
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    ///
    /// The directory the measurement files go into.
    ///
    private final Path directory;

    ///
    /// The directory of an earlier run's measurement files, or `null` if this run compares against nothing.
    ///
    private final @Nullable Path compare;

    ///
    /// The length of the baseline window: the longest window declared by any discovered test of this side.
    ///
    @Getter
    private final int baselineLength;

    ///
    /// Creates the measurements of a run, taking the baseline's length from the longest window the tests declare.
    ///
    /// @param directory The directory the measurement files go into.
    /// @param compare   The directory of an earlier run's measurement files, or `null` for no comparison.
    /// @param tests     Every discovered test of the run.
    ///
    public Measurements(Path directory, @Nullable Path compare, List<Discovered> tests) {
        this.directory = directory;
        this.compare = compare;

        int longest = 0;
        for (Discovered test : tests) {
            // A nested type pattern would match a Valid whose measured is null, so the check stays explicit.
            Measured measured = ObjectMapping.mapIfInstanceOf(test, Discovered.Valid.class, Discovered.Valid::measured);
            if (measured != null) {
                longest = Math.max(longest, measured.value());
            }
        }

        this.baselineLength = longest;
    }

    ///
    /// Writes the measurement's file and logs its summary and, if the compare directory holds a counterpart, the difference.
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
    /// Writes the baseline of the run from an empty window of [#baselineLength()] recorded by the caller.
    ///
    /// @param nanos The duration of each tick or frame of the empty window, in nanoseconds.
    ///
    public void writeBaseline(long[] nanos) {
        this.write(new Measurement(Measurements.BASELINE, nanos, null));
    }

    ///
    /// The file vanilla's profiler breakdown of the test goes into, next to its numbers.
    /// The caller saves the results into it and passes the same path as the measurement's profile.
    ///
    /// @param test The id of the measured test.
    ///
    /// @return The file vanilla's profiler breakdown of the test goes into.
    ///
    public Path profileFile(Identifier test) {
        return Measurements.file(this.directory, test, ".txt");
    }

    ///
    /// The summary the compare directory holds for the test, or `null` if there is none we can read.
    ///
    /// @param test The id of the measured test.
    ///
    /// @return The summary the compare directory holds for the test, or `null` if there is none we can read.
    ///
    private @Nullable Summary earlier(Identifier test) {
        if (this.compare == null) {
            return null;
        }

        Path file = Measurements.file(this.compare, test, ".json");
        if (!Files.exists(file)) {
            return null;
        }

        // A comparison never fails a test, whatever the earlier file holds.
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return Summary.from(GsonHelper.getAsJsonObject(GsonHelper.parse(reader), "summary"));
        } catch (IOException | RuntimeException failure) {
            Measurements.log.warn("Can't read the earlier measurement of '{}' from {}", test, file, failure);
            return null;
        }
    }

    ///
    /// A file of the test under a directory of its namespace, so no two ids can share one file.
    ///
    /// @param directory The directory the file goes under.
    /// @param test      The id of the test.
    /// @param extension The extension of the file, with its dot.
    ///
    /// @return The file of the test under a directory of its namespace.
    ///
    private static Path file(Path directory, Identifier test, String extension) {
        return test.withSuffix(extension).resolveAgainst(directory);
    }

    ///
    /// The numbers a measurement's file carries beside its series, in nanoseconds.
    /// A difference is the earlier run's subtracted from them.
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
        /// The summary of the durations, which are left in the order they were recorded.
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
            return new Summary(sorted[0], median, sorted[(int) Math.ceil(sorted.length * 0.95D) - 1], sorted[sorted.length - 1], Arrays.stream(sorted).average().orElseThrow());
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

            // An infinite difference would write this run's own file as non-JSON.
            if (!Double.isFinite(summary.median) || !Double.isFinite(summary.mean)) {
                throw new JsonSyntaxException("'median' and 'mean' should be finite numbers");
            }

            return summary;
        }

        ///
        /// Each number with the earlier run's subtracted from it.
        ///
        /// @param earlier The summary of the earlier run.
        ///
        /// @return Each number with the earlier run's subtracted from it.
        ///
        private Summary minus(Summary earlier) {
            return new Summary(this.min - earlier.min, this.median - earlier.median, this.p95 - earlier.p95, this.max - earlier.max, this.mean - earlier.mean);
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
        /// The numbers converted to milliseconds, as one line for the log.
        ///
        /// @return The numbers converted to milliseconds, as one line for the log.
        ///
        private String milliseconds() {
            return String.format(
                    Locale.ROOT,
                    "min %.3f, median %.3f, p95 %.3f, max %.3f, mean %.3f ms",
                    this.min / Summary.NANOS_PER_MILLISECOND,
                    this.median / Summary.NANOS_PER_MILLISECOND,
                    this.p95 / Summary.NANOS_PER_MILLISECOND,
                    this.max / Summary.NANOS_PER_MILLISECOND,
                    this.mean / Summary.NANOS_PER_MILLISECOND
            );
        }
    }
}
