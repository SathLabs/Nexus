package dev.satherov.nexus.test.unit.gametest;

import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.test.game.gametest.internal.discovery.DiscoverySamples;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

///
/// Checks the reports and measurement files both gametest runs left under `build/reports/gametest` against every
/// sample's expected outcome. Under a `nexus.gametest.tests` selector only the reported cases are checked and the
/// measurement files are not. Run by `verifyGameTestRuns` after both runs, never by `test`.
///
public class RunReportTest {

    private static final String SELECTOR_PROPERTY = "nexus.gametest.tests";
    private static final String PASSED = "passed";
    private static final String SAMPLES = DiscoverySamples.class.getName();
    private static final Path REPORTS = Path.of(Objects.requireNonNull(System.getProperty("nexus.gametest.reports"), "The system property 'nexus.gametest.reports' is not set"));

    @EnumSource
    @ParameterizedTest
    public void writesReport(Run run) {
        Assertions.assertThat(run.report()).isRegularFile();
    }

    @EnumSource
    @ParameterizedTest
    public void passesEveryRequiredCase(Run run) throws IOException, ParserConfigurationException, SAXException {
        Assertions.assertThat(run.readCases())
                .filteredOn(reported -> reported.outcome().equals("failure"))
                .extracting(Case::id)
                .as("Required cases that failed")
                .isEmpty();
    }

    @EnumSource
    @ParameterizedTest
    @DisabledIfSystemProperty(named = RunReportTest.SELECTOR_PROPERTY, matches = ".*")
    public void reportsEveryCase(Run run) throws IOException, ParserConfigurationException, SAXException {
        Assertions.assertThat(run.readCases())
                .extracting(Case::id)
                .containsAll(run.cases.keySet());
    }

    @EnumSource
    @ParameterizedTest
    public void endsEveryReportedCaseAsExpected(Run run) throws IOException, ParserConfigurationException, SAXException {
        for (Case reported : run.readCases()) {
            Expected expected = run.cases.get(reported.id());
            if (expected == null) {
                continue;
            }

            Assertions.assertThat(reported.outcome())
                    .as("The outcome of '%s'", reported.id())
                    .isEqualTo(expected.outcome());
            Assertions.assertThat(reported.message())
                    .as("The message of '%s'", reported.id())
                    .contains(expected.message());
        }
    }

    @EnumSource
    @ParameterizedTest
    public void reportsOnlyExpectedCases(Run run) throws IOException, ParserConfigurationException, SAXException {
        Assertions.assertThat(run.readCases())
                .extracting(Case::id)
                .filteredOn(id -> id.startsWith("nexus:") && !run.cases.containsKey(id))
                .as("Cases without an expected outcome")
                .isEmpty();
    }

    @EnumSource
    @ParameterizedTest
    @DisabledIfSystemProperty(named = RunReportTest.SELECTOR_PROPERTY, matches = ".*")
    public void writesEveryMeasurement(Run run) throws IOException {
        for (Measurement measurement : run.measurements) {
            Path json = run.directory().resolve(measurement.path() + ".json");
            Assertions.assertThat(json).isRegularFile();

            JsonArray nanos = JsonParser.parseString(Files.readString(json))
                    .getAsJsonObject()
                    .getAsJsonArray("nanos");

            Assertions.assertThat(nanos.size())
                    .as("The number of ticks or frames in '%s.json'", measurement.path())
                    .isEqualTo(measurement.window());
            Assertions.assertThat(Files.isRegularFile(run.directory().resolve(measurement.path() + ".txt")))
                    .as("If the run wrote a profile to '%s.txt'", measurement.path())
                    .isEqualTo(measurement.profile());
        }
    }

    @EnumSource
    @ParameterizedTest
    @DisabledIfSystemProperty(named = RunReportTest.SELECTOR_PROPERTY, matches = ".*")
    public void writesNoMeasurementOfUnmeasuredCases(Run run) {
        for (String path : run.unmeasured) {
            Assertions.assertThat(run.directory().resolve(path + ".json")).doesNotExist();
            Assertions.assertThat(run.directory().resolve(path + ".txt")).doesNotExist();
        }
    }

    @RequiredArgsConstructor
    public enum Run {
        SERVER(
                Map.ofEntries(
                        Map.entry("nexus:discovery_samples/meets_constraints", Expected.passed()),
                        Map.entry("nexus:discovery_samples/optional", Expected.passed()),
                        Map.entry("nexus:discovery_samples/measured", Expected.passed()),
                        Map.entry("nexus:discovery_samples/long_window", Expected.passed()),
                        Map.entry("nexus:discovery_samples/not_public", Expected.skipped("Method '" + RunReportTest.SAMPLES + "#notPublic' is not public")),
                        Map.entry("nexus:discovery_samples/not_static", Expected.skipped("Method '" + RunReportTest.SAMPLES + "#notStatic' is not static")),
                        Map.entry("nexus:discovery_samples/not_void", Expected.skipped("Method '" + RunReportTest.SAMPLES + "#notVoid' does not return void")),
                        Map.entry("nexus:discovery_samples/two_parameters", Expected.skipped("Found more than one parameter, 2 total")),
                        Map.entry("nexus:discovery_samples/takes_a_string", Expected.skipped("Parameter is not a 'GameTestHelper', found 'String' instead")),
                        Map.entry("nexus:discovery_samples/empty_window", Expected.skipped("Measured#value is not positive")),
                        Map.entry("nexus:discovery_samples/no_ticks", Expected.skipped("ServerTest#maxTicks is not positive")),
                        Map.entry("nexus:discovery_samples/window_overruns_ticks", Expected.skipped("ServerTest#maxTicks is too short for Measured#value")),
                        Map.entry("nexus:discovery_samples/negative_setup", Expected.skipped("ServerTest#setupTicks is negative")),
                        Map.entry("nexus:discovery_samples/wrong_structure", Expected.skipped("ServerTest#structure is not an identifier")),
                        Map.entry("nexus:discovery_samples/same_id", Expected.skipped("Declared twice")),
                        Map.entry("nexus:discovery_samples/throws_its_own_exception", Expected.skipped("Thrown by the sample on purpose")),
                        Map.entry("nexus:discovery_samples.hidden_class/hidden_method", Expected.skipped("Class '" + RunReportTest.SAMPLES + "$HiddenClass' is not public")),
                        Map.entry("nexus:void_level_samples/air_around_and_below", Expected.passed()),
                        Map.entry("nexus:tick_window_samples/window", Expected.passed()),
                        Map.entry("nexus:tick_window_samples/profiled_window", Expected.passed()),
                        Map.entry("nexus:tick_window_samples/succeeds_itself", Expected.passed()),
                        Map.entry("nexus:tick_window_samples/tick_rate", Expected.passed()),
                        Map.entry("nexus:server_test_samples/past_max_ticks", Expected.skipped("within 2 ticks")),
                        Map.entry("nexus:server_test_samples/optional_failure", Expected.skipped("The optional sample failed")),
                        Map.entry("nexus_gametest:baseline", Expected.passed())
                ),
                List.of(
                        Measurement.of("nexus/discovery_samples/measured", 5),
                        Measurement.of("nexus/discovery_samples/long_window", 10),
                        Measurement.of("nexus/tick_window_samples/window", 7),
                        Measurement.profiled("nexus/tick_window_samples/profiled_window", 3),
                        Measurement.of("nexus_gametest/baseline", 10)
                ),
                List.of(
                        "nexus/discovery_samples/window_overruns_ticks",
                        "nexus/discovery_samples/empty_window",
                        "nexus/tick_window_samples/succeeds_itself"
                )
        ),
        CLIENT(
                Map.ofEntries(
                        Map.entry("nexus:discovery_samples/client", Expected.passed()),
                        Map.entry("nexus:discovery_samples/client_with_helper", Expected.skipped("Parameter is not a 'Client', found 'GameTestHelper' instead")),
                        Map.entry("nexus:discovery_samples/no_frames", Expected.skipped("ClientTest#maxFrames is not positive")),
                        Map.entry("nexus:client_samples/title_screen_at_start", Expected.passed()),
                        Map.entry("nexus:client_samples/ticks_advance_server", Expected.passed()),
                        Map.entry("nexus:client_samples/until_out_of_frames", Expected.passed()),
                        Map.entry("nexus:client_samples/server_level_outside_world", Expected.passed()),
                        Map.entry("nexus:test_world_samples/join_world", Expected.passed()),
                        Map.entry("nexus:test_world_samples/leave_world", Expected.passed()),
                        Map.entry("nexus:keyboard_samples/press_opens_inventory", Expected.passed()),
                        Map.entry("nexus:keyboard_samples/hold_sets_modifier", Expected.passed()),
                        Map.entry("nexus:keyboard_samples/type_into_text_field", Expected.passed()),
                        Map.entry("nexus:mouse_samples/click_opens_options", Expected.passed()),
                        Map.entry("nexus:mouse_samples/scroll_selects_previous_slot", Expected.passed()),
                        Map.entry("nexus:capture_samples/size_of_render_target", Expected.passed()),
                        Map.entry("nexus:capture_samples/pixel_matches_write", Expected.passed()),
                        Map.entry("nexus:capture_samples/pixel_outside_frame", Expected.passed()),
                        Map.entry("nexus:capture_samples/missing_golden", Expected.passed()),
                        Map.entry("nexus:capture_samples/title_screen", Expected.passed()),
                        Map.entry("nexus:client_run_samples/window", Expected.passed()),
                        Map.entry("nexus:client_run_samples/profiled_window", Expected.passed()),
                        Map.entry("nexus:client_run_samples/optional_failure", Expected.skipped("This test always fails")),
                        Map.entry("nexus:client_run_samples/out_of_frames", Expected.skipped("ran out of frames"))
                ),
                List.of(
                        Measurement.of("nexus/client_run_samples/window", 40),
                        Measurement.profiled("nexus/client_run_samples/profiled_window", 3),
                        Measurement.of("nexus_gametest/baseline", 40)
                ),
                List.of()
        );

        // Every case the run should report, by its identifier.
        private final Map<String, Expected> cases;

        // The measurement files the run should write, by their path without the extension.
        private final List<Measurement> measurements;

        // The measured cases that should leave no measurement files, by their path without the extension.
        private final List<String> unmeasured;

        private final String side = this.name().toLowerCase(Locale.ROOT);

        private Path directory() {
            return RunReportTest.REPORTS.resolve(this.side);
        }

        private Path report() {
            return this.directory().resolve(this.side + ".xml");
        }

        private List<Case> readCases() throws IOException, ParserConfigurationException, SAXException {
            NodeList testcases = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(this.report().toFile())
                    .getElementsByTagName("testcase");

            return IntStream.range(0, testcases.getLength())
                    .mapToObj(i -> Case.from((Element) testcases.item(i)))
                    .toList();
        }
    }

    private record Case(String id, String outcome, String message) {

        private static Case from(Element testcase) {
            NodeList results = testcase.getElementsByTagName("*");
            if (results.getLength() == 0) {
                return new Case(testcase.getAttribute("name"), RunReportTest.PASSED, "");
            }

            Element result = (Element) results.item(0);
            return new Case(testcase.getAttribute("name"), result.getTagName(), result.getAttribute("message"));
        }
    }

    private record Expected(String outcome, String message) {

        private static Expected passed() {
            return new Expected(RunReportTest.PASSED, "");
        }

        private static Expected skipped(String message) {
            return new Expected("skipped", message);
        }
    }

    private record Measurement(String path, int window, boolean profile) {

        private static Measurement of(String path, int window) {
            return new Measurement(path, window, false);
        }

        private static Measurement profiled(String path, int window) {
            return new Measurement(path, window, true);
        }
    }
}
