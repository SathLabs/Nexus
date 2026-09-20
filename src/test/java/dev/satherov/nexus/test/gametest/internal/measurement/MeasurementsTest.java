package dev.satherov.nexus.test.gametest.internal.measurement;

import dev.satherov.nexus.gametest.api.measurement.Measured;
import dev.satherov.nexus.gametest.internal.discovery.Discovered;
import dev.satherov.nexus.gametest.internal.measurement.Measurement;
import dev.satherov.nexus.gametest.internal.measurement.Measurements;

import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

import com.google.gson.JsonObject;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.LongStream;

///
/// Checks that [Measurements] summarizes a recorded window, files it under its id, and compares it against an earlier run.
///
public class MeasurementsTest {

    private static final Identifier TEST = Identifier.fromNamespaceAndPath("nexus", "measurements/some_test");

    @TempDir
    Path directory;

    @TempDir
    Path compare;

    @Test
    void writesTheSeriesAndItsSummary() throws IOException {
        JsonObject json = this.write(new Measurement(MeasurementsTest.TEST, new long[]{ 30L, 10L, 50L, 20L, 40L }, null), null);
        Assertions.assertThat(json.get("test").getAsString()).isEqualTo("nexus:measurements/some_test");
        Assertions.assertThat(json.getAsJsonArray("nanos").toString()).isEqualTo("[30,10,50,20,40]");
        Assertions.assertThat(json.getAsJsonObject("summary").toString()).isEqualTo("{\"min\":10,\"median\":30.0,\"p95\":50,\"max\":50,\"mean\":30.0}");
        Assertions.assertThat(json.has("profile")).isFalse();
        Assertions.assertThat(json.has("difference")).isFalse();
    }

    @Test
    void summarizesAWindowOfOne() throws IOException {
        JsonObject json = this.write(new Measurement(MeasurementsTest.TEST, new long[]{ 7L }, null), null);
        Assertions.assertThat(json.getAsJsonObject("summary").toString()).isEqualTo("{\"min\":7,\"median\":7.0,\"p95\":7,\"max\":7,\"mean\":7.0}");
    }

    @Test
    void averagesTheMiddleOfAnEvenWindow() throws IOException {
        JsonObject json = this.write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L, 20L, 30L, 40L }, null), null);
        Assertions.assertThat(json.getAsJsonObject("summary").toString()).isEqualTo("{\"min\":10,\"median\":25.0,\"p95\":40,\"max\":40,\"mean\":25.0}");
    }

    @Test
    void takesThePercentileFromTheNineteenthOfTwenty() throws IOException {
        JsonObject json = this.write(new Measurement(MeasurementsTest.TEST, LongStream.rangeClosed(1L, 20L).toArray(), null), null);
        Assertions.assertThat(json.getAsJsonObject("summary").toString()).isEqualTo("{\"min\":1,\"median\":10.5,\"p95\":19,\"max\":20,\"mean\":10.5}");
    }

    @Test
    void filesTheMeasurementUnderItsNamespace() throws IOException {
        this.write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L }, null), null);
        Assertions.assertThat(this.directory.resolve("nexus").resolve("measurements").resolve("some_test.json")).exists();
    }

    @Test
    void placesTheProfileNextToTheNumbers() {
        Measurements measurements = new Measurements(this.directory, null, List.of());
        Assertions.assertThat(measurements.profileFile(MeasurementsTest.TEST)).isEqualTo(this.directory.resolve("nexus").resolve("measurements").resolve("some_test.txt"));
    }

    @Test
    void keepsIdsOfOneDebugNameApart() throws IOException {
        Identifier slashed = Identifier.fromNamespaceAndPath("nexus", "foo/bar_baz");
        Identifier underscored = Identifier.fromNamespaceAndPath("nexus", "foo_bar/baz");
        new Measurements(this.compare, null, List.of()).write(new Measurement(slashed, new long[]{ 10L }, null));

        Measurements measurements = new Measurements(this.directory, this.compare, List.of());
        measurements.write(new Measurement(slashed, new long[]{ 20L }, null));
        measurements.write(new Measurement(underscored, new long[]{ 30L }, null));

        JsonObject json = MeasurementsTest.read(this.directory, underscored);
        Assertions.assertThat(json.get("test").getAsString()).isEqualTo("nexus:foo_bar/baz");
        Assertions.assertThat(json.has("difference")).isFalse();
        Assertions.assertThat(MeasurementsTest.read(this.directory, slashed).has("difference")).isTrue();
    }

    @Test
    void writesTheProfilePath() throws IOException {
        Path profile = Path.of("profile", "some_test.txt");
        JsonObject json = this.write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L, 20L }, profile), null);
        Assertions.assertThat(json.get("profile").getAsString()).isEqualTo(profile.toString());
    }

    @Test
    void comparesAgainstTheCounterpart() throws IOException {
        this.counterpart(new long[]{ 5L, 15L, 25L, 35L });
        JsonObject json = this.write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L, 20L, 30L, 40L }, null), this.compare);
        Assertions.assertThat(json.getAsJsonObject("difference").toString()).isEqualTo("{\"min\":5,\"median\":5.0,\"p95\":5,\"max\":5,\"mean\":5.0}");
    }

    @Test
    void writesWithoutACounterpart() throws IOException {
        JsonObject json = this.write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L, 20L }, null), this.compare);
        Assertions.assertThat(json.has("summary")).isTrue();
        Assertions.assertThat(json.has("difference")).isFalse();
    }

    @Test
    void writesWithAMalformedCounterpart() throws IOException {
        Files.writeString(this.counterpart(new long[]{ 5L, 15L }), "{ not json");
        JsonObject json = this.write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L, 20L }, null), this.compare);
        Assertions.assertThat(json.has("summary")).isTrue();
        Assertions.assertThat(json.has("difference")).isFalse();
    }

    @Test
    void writesWithAnUnreadableCounterpart() throws IOException {
        Path counterpart = this.counterpart(new long[]{ 5L, 15L });
        Files.delete(counterpart);
        Files.createDirectory(counterpart);

        JsonObject json = this.write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L, 20L }, null), this.compare);
        Assertions.assertThat(json.has("summary")).isTrue();
        Assertions.assertThat(json.has("difference")).isFalse();
    }

    @Test
    void writesWithANonFiniteCounterpart() throws IOException {
        Files.writeString(this.counterpart(new long[]{ 5L, 15L }), "{\"summary\":{\"min\":5,\"median\":1e400,\"p95\":15,\"max\":15,\"mean\":10.0}}");
        JsonObject json = this.write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L, 20L }, null), this.compare);
        Assertions.assertThat(json.has("summary")).isTrue();
        Assertions.assertThat(json.has("difference")).isFalse();
    }

    @Test
    void takesTheLongestDeclaredWindowAsTheBaselineLength() throws NoSuchMethodException {
        List<Discovered> tests = List.of(
                MeasurementsTest.valid("unmeasured"),
                MeasurementsTest.valid("shortWindow"),
                MeasurementsTest.valid("longWindow"),
                new Discovered.Invalid(MeasurementsTest.TEST, "method is not static", false)
        );

        Assertions.assertThat(new Measurements(this.directory, null, tests).baselineLength()).isEqualTo(40);
    }

    @Test
    void hasNoBaselineLengthWithoutAMeasuredTest() throws NoSuchMethodException {
        List<Discovered> tests = List.of(MeasurementsTest.valid("unmeasured"));
        Assertions.assertThat(new Measurements(this.directory, null, tests).baselineLength()).isZero();
    }

    @Test
    void writesTheBaselineOfTheRun() throws IOException {
        new Measurements(this.directory, null, List.of()).writeBaseline(new long[]{ 10L, 20L });

        JsonObject json = MeasurementsTest.read(this.directory, Identifier.fromNamespaceAndPath("nexus_gametest", "baseline"));
        Assertions.assertThat(json.get("test").getAsString()).isEqualTo("nexus_gametest:baseline");
        Assertions.assertThat(json.getAsJsonObject("summary").toString()).isEqualTo("{\"min\":10,\"median\":15.0,\"p95\":20,\"max\":20,\"mean\":15.0}");
    }

    private JsonObject write(Measurement measurement, @Nullable Path compare) throws IOException {
        new Measurements(this.directory, compare, List.of()).write(measurement);
        return MeasurementsTest.read(this.directory, measurement.test());
    }

    private Path counterpart(long[] nanos) throws IOException {
        new Measurements(this.compare, null, List.of()).write(new Measurement(MeasurementsTest.TEST, nanos, null));
        return MeasurementsTest.file(this.compare, MeasurementsTest.TEST);
    }

    private static JsonObject read(Path directory, Identifier test) throws IOException {
        return GsonHelper.parse(Files.readString(MeasurementsTest.file(directory, test)));
    }

    private static Path file(Path directory, Identifier test) {
        return directory.resolve(test.getNamespace()).resolve(test.getPath() + ".json");
    }

    private static Discovered.Valid valid(String sample) throws NoSuchMethodException {
        Method method = MeasurementsTest.class.getDeclaredMethod(sample);
        return new Discovered.Valid(MeasurementsTest.TEST, method, method.getAnnotation(Sample.class), method.getAnnotation(Measured.class));
    }

    @Sample
    private static void unmeasured() { }

    @Sample
    @Measured(10)
    private static void shortWindow() { }

    @Sample
    @Measured(40)
    private static void longWindow() { }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface Sample { }
}
