package dev.satherov.nexus.test.unit.gametest.internal.measurement;

import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.gametest.internal.discovery.Discovered;
import dev.satherov.nexus.gametest.internal.measurement.Measurement;
import dev.satherov.nexus.gametest.internal.measurement.Measurements;

import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

import com.google.gson.JsonObject;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

///
/// Checks the baseline length of [Measurements] and the file it writes for each measurement.
///
public class MeasurementsTest {
    
    private static final Identifier TEST = Identifier.fromNamespaceAndPath("nexus", "measurements/some_test");
    
    @Test
    public void takesTheLongestWindowOfTheValidTestsAsTheBaselineLength(@TempDir Path directory) {
        List<Discovered> samples = Discovered.all(ServerTest.class)
                .stream()
                .filter(test -> test.id().getPath().startsWith("discovery_samples/"))
                .toList();
        
        Assertions.assertThat(new Measurements(directory, null, samples).baselineLength()).isEqualTo(10);
    }
    
    @Test
    public void writesTheFileUnderTheNamespaceAndPath(@TempDir Path directory) {
        new Measurements(directory, null, List.of()).write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L }, null));
        Assertions.assertThat(directory.resolve("nexus").resolve("measurements").resolve("some_test.json")).isRegularFile();
    }
    
    @Test
    public void keepsIdsOfDifferentNamespacesApart(@TempDir Path directory) throws IOException {
        Identifier nexus = Identifier.fromNamespaceAndPath("nexus", "shared/test");
        Identifier other = Identifier.fromNamespaceAndPath("other", "shared/test");
        Measurements measurements = new Measurements(directory, null, List.of());
        measurements.write(new Measurement(nexus, new long[]{ 10L }, null));
        measurements.write(new Measurement(other, new long[]{ 20L }, null));
        
        Assertions.assertThat(MeasurementsTest.numbers(MeasurementsTest.read(directory, nexus), "summary")).containsOnly(10.0D);
        Assertions.assertThat(MeasurementsTest.numbers(MeasurementsTest.read(directory, other), "summary")).containsOnly(20.0D);
    }
    
    @Test
    public void summarizesAWindowOfOddLength(@TempDir Path directory) throws IOException {
        new Measurements(directory, null, List.of()).write(new Measurement(MeasurementsTest.TEST, new long[]{ 70L, 10L, 40L, 20L, 70L }, null));
        Assertions.assertThat(MeasurementsTest.numbers(MeasurementsTest.read(directory, MeasurementsTest.TEST), "summary")).containsExactly(10.0D, 40.0D, 70.0D, 70.0D, 42.0D);
    }
    
    @Test
    public void summarizesAWindowOfEvenLength(@TempDir Path directory) throws IOException {
        new Measurements(directory, null, List.of()).write(new Measurement(MeasurementsTest.TEST, new long[]{ 50L, 10L, 30L, 20L, 50L, 50L }, null));
        Assertions.assertThat(MeasurementsTest.numbers(MeasurementsTest.read(directory, MeasurementsTest.TEST), "summary")).containsExactly(10.0D, 40.0D, 50.0D, 50.0D, 35.0D);
    }
    
    @Test
    public void writesTheProfileOnlyIfGiven(@TempDir Path directory) throws IOException {
        Identifier unprofiled = Identifier.fromNamespaceAndPath("nexus", "measurements/unprofiled");
        Measurements measurements = new Measurements(directory, null, List.of());
        measurements.write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L }, measurements.profileFile(MeasurementsTest.TEST)));
        measurements.write(new Measurement(unprofiled, new long[]{ 10L }, null));
        
        Assertions.assertThat(MeasurementsTest.read(directory, MeasurementsTest.TEST).has("profile")).isTrue();
        Assertions.assertThat(MeasurementsTest.read(directory, unprofiled).has("profile")).isFalse();
    }
    
    @Test
    public void writesTheDifferenceAsThisRunMinusTheEarlierOne(@TempDir Path directory) throws IOException {
        Path earlier = directory.resolve("earlier");
        Path run = directory.resolve("run");
        new Measurements(earlier, null, List.of()).write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L, 1L, 3L, 10L }, null));
        new Measurements(run, earlier, List.of()).write(new Measurement(MeasurementsTest.TEST, new long[]{ 40L, 10L, 40L, 20L }, null));
        
        Assertions.assertThat(MeasurementsTest.numbers(MeasurementsTest.read(run, MeasurementsTest.TEST), "difference")).containsExactly(9.0D, 23.5D, 30.0D, 30.0D, 21.5D);
    }
    
    @Test
    public void writesNoDifferenceWithoutTheEarlierFile(@TempDir Path directory) throws IOException {
        Path earlier = directory.resolve("earlier");
        Path run = directory.resolve("run");
        new Measurements(earlier, null, List.of()).write(new Measurement(Identifier.fromNamespaceAndPath("nexus", "measurements/other_test"), new long[]{ 10L }, null));
        new Measurements(run, earlier, List.of()).write(new Measurement(MeasurementsTest.TEST, new long[]{ 20L }, null));
        
        Assertions.assertThat(MeasurementsTest.read(run, MeasurementsTest.TEST).has("difference")).isFalse();
    }
    
    @ParameterizedTest
    @ValueSource(strings = { "", "{ not json", "[]", "{}", "{\"summary\":{\"min\":5,\"median\":1e400,\"p95\":15,\"max\":15,\"mean\":10.0}}" })
    public void writesNoDifferenceAgainstAGarbageEarlierFile(String contents, @TempDir Path directory) throws IOException {
        Path earlier = directory.resolve("earlier");
        Path run = directory.resolve("run");
        new Measurements(earlier, null, List.of()).write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L }, null));
        Files.writeString(MeasurementsTest.file(earlier, MeasurementsTest.TEST), contents);
        
        new Measurements(run, earlier, List.of()).write(new Measurement(MeasurementsTest.TEST, new long[]{ 20L }, null));
        Assertions.assertThat(MeasurementsTest.read(run, MeasurementsTest.TEST).has("difference")).isFalse();
    }
    
    @Test
    public void writesNoDifferenceAgainstAnUnreadableEarlierFile(@TempDir Path directory) throws IOException {
        Path earlier = directory.resolve("earlier");
        Path run = directory.resolve("run");
        Path file = MeasurementsTest.file(earlier, MeasurementsTest.TEST);
        new Measurements(earlier, null, List.of()).write(new Measurement(MeasurementsTest.TEST, new long[]{ 10L }, null));
        Files.delete(file);
        Files.createDirectory(file);
        
        new Measurements(run, earlier, List.of()).write(new Measurement(MeasurementsTest.TEST, new long[]{ 20L }, null));
        Assertions.assertThat(MeasurementsTest.read(run, MeasurementsTest.TEST).has("difference")).isFalse();
    }
    
    @Test
    public void writesNoFileForAnEmptyWindow(@TempDir Path directory) {
        new Measurements(directory, null, List.of()).write(new Measurement(MeasurementsTest.TEST, new long[0], null));
        Assertions.assertThat(MeasurementsTest.file(directory, MeasurementsTest.TEST)).doesNotExist();
    }
    
    @Test
    public void writesTheBaselineUnderItsIdentifier(@TempDir Path directory) {
        new Measurements(directory, null, List.of()).writeBaseline(new long[]{ 10L, 20L });
        Assertions.assertThat(MeasurementsTest.file(directory, Measurements.BASELINE)).isRegularFile();
    }
    
    @Test
    public void placesTheProfileInATextFile(@TempDir Path directory) {
        Assertions.assertThat(new Measurements(directory, null, List.of()).profileFile(MeasurementsTest.TEST).toString()).endsWith(".txt");
    }
    
    private static JsonObject read(Path directory, Identifier test) throws IOException {
        return GsonHelper.parse(Files.readString(MeasurementsTest.file(directory, test)));
    }
    
    private static List<Double> numbers(JsonObject measurement, String summary) {
        JsonObject numbers = measurement.getAsJsonObject(summary);
        return Stream.of("min", "median", "p95", "max", "mean")
                .map(key -> numbers.get(key).getAsDouble())
                .toList();
    }
    
    private static Path file(Path directory, Identifier test) {
        return directory.resolve(test.getNamespace()).resolve(test.getPath() + ".json");
    }
}
