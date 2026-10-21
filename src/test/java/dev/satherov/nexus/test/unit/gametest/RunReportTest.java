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
                        Map.entry("nexus:attach_tooltips_event_samples/attaches_between_hover_text_and_components", Expected.passed()),
                        Map.entry("nexus:attach_tooltips_event_samples/skips_other_items", Expected.passed()),
                        Map.entry("nexus:attach_tooltips_event_samples/attaches_to_fluid_stacks", Expected.passed()),
                        Map.entry("nexus:attach_tooltips_event_samples/skips_other_fluids", Expected.passed()),
                        Map.entry("nexus:attribute_builder_samples/adds_the_attribute_with_its_default_to_every_type", Expected.passed()),
                        Map.entry("nexus:attribute_builder_samples/get_is_the_current_value_on_the_entity", Expected.passed()),
                        Map.entry("nexus:attribute_builder_samples/get_is_the_default_on_an_entity_without_the_attribute", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/default_states_are_the_blocks_own", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/is_matches_every_state_of_the_block_only", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/applies_the_properties_in_call_order", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/translates_the_block_under_its_key", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/registers_no_item_for_a_plain_block", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/registers_the_block_item_under_the_blocks_name", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/stacks_hold_the_block_item", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/translates_the_block_item_as_the_block", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/gives_the_item_factory_the_block_and_its_features", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/applies_the_item_edits_after_the_blocks_features", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/keeps_what_was_recorded_before_the_item", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/refuses_changes_to_the_block_builder_after_the_item", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/refuses_to_register_the_item_on_its_own", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/never_requests_the_tints_on_a_dedicated_server", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/is_valid_for_every_given_block_only", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/at_finds_the_placed_block_entity", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/at_is_null_without_a_block_entity_of_the_type", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/is_matches_block_entities_of_the_type_only", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/finds_the_capability_on_every_block_entity_of_the_type", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/reads_nothing_from_a_stack_without_the_component", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/sets_the_value_on_a_stack", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/saves_the_value_with_the_stack", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/syncs_the_value_with_the_stack", Expected.passed()),
                        Map.entry("nexus:effect_entry_samples/applies_the_effect_to_a_mob", Expected.passed()),
                        Map.entry("nexus:effect_entry_samples/apply_is_false_if_the_mob_refuses_the_effect", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/registers_the_type_under_its_name", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/applies_the_size_before_the_edits", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/translates_the_type_under_its_key", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/translates_a_slashed_name_under_the_description_id", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/creates_an_entity_of_the_type_outside_the_level", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/spawns_an_entity_of_the_type_at_the_position", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/spawn_is_null_if_the_entity_could_not_join_the_level", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/is_matches_entities_of_the_type_only", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/registers_the_attributes_of_the_mob", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/registers_the_spawn_placement_of_the_mob", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/names_the_egg_after_the_entity", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/the_egg_spawns_an_entity_of_the_type", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/keeps_the_type_of_an_egg_whose_properties_were_replaced", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/applies_the_egg_edits_after_the_features_of_the_type", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/gives_the_egg_factory_the_properties_with_the_type", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/refuses_changes_to_the_entity_builder_after_the_egg", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/never_requests_the_renderer_on_a_dedicated_server", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/registers_the_type_and_both_fluids_under_their_names", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/links_the_type_and_both_fluids", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/registers_no_block_or_bucket_for_a_plain_fluid", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/applies_the_edits_in_call_order", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/translates_the_type_under_its_key", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/stacks_hold_the_source", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/is_matches_the_states_of_both_fluids_only", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/replaces_the_type_and_both_fluids", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/links_the_block_and_the_bucket", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/gives_the_block_the_liquid_properties_of_water", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/gives_the_bucket_factory_the_source_and_the_bucket_properties", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/names_the_bucket_after_the_fluid", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/shows_the_buckets_in_the_first_tab_of_the_mod", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/registers_the_block_builder_that_took_over_the_block", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/refuses_changes_to_the_fluid_builder_after_the_block", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/never_requests_the_client_hooks_on_a_dedicated_server", Expected.passed()),
                        Map.entry("nexus:item_builder_samples/creates_stacks_of_the_item", Expected.passed()),
                        Map.entry("nexus:item_builder_samples/names_the_item_after_its_key", Expected.passed()),
                        Map.entry("nexus:item_builder_samples/named_replaces_the_english_name", Expected.passed()),
                        Map.entry("nexus:item_builder_samples/applies_the_properties_edits_in_order", Expected.passed()),
                        Map.entry("nexus:item_builder_samples/replacing_the_properties_drops_earlier_edits", Expected.passed()),
                        Map.entry("nexus:item_builder_samples/an_edit_of_the_description_wins_over_the_translation", Expected.passed()),
                        Map.entry("nexus:item_builder_samples/shows_the_item_in_the_given_tab", Expected.passed()),
                        Map.entry("nexus:item_builder_samples/shows_the_item_in_a_vanilla_tab", Expected.passed()),
                        Map.entry("nexus:item_builder_samples/keeps_an_item_without_tabs_out_of_every_tab", Expected.passed()),
                        Map.entry("nexus:item_builder_samples/records_an_item_without_a_given_tab_in_the_first_tab_of_the_mod", Expected.passed()),
                        Map.entry("nexus:menu_builder_samples/names_the_title_after_its_key", Expected.passed()),
                        Map.entry("nexus:menu_builder_samples/opens_the_menu_under_its_title", Expected.passed()),
                        Map.entry("nexus:menu_builder_samples/writes_the_extra_data_the_factory_reads", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/spawn_sends_the_particles_to_the_players_near_the_position", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/spawn_sends_the_particles_of_a_type_that_overrides_the_limiter_farther", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/is_matches_options_of_the_type_only", Expected.passed()),
                        Map.entry("nexus:potion_entry_samples/stacks_hold_the_potion_in_each_container", Expected.passed()),
                        Map.entry("nexus:potion_entry_samples/names_each_stack_under_a_defined_key", Expected.passed()),
                        Map.entry("nexus:potion_entry_samples/is_matches_stacks_of_the_potion_only", Expected.passed()),
                        Map.entry("nexus:potion_entry_samples/brews_the_potion_from_the_base_with_the_ingredient", Expected.passed()),
                        Map.entry("nexus:sound_builder_samples/plays_at_the_center_of_the_block_for_a_nearby_player", Expected.passed()),
                        Map.entry("nexus:sound_builder_samples/plays_with_volume_and_pitch_one_by_default", Expected.passed()),
                        Map.entry("nexus:sound_builder_samples/plays_at_the_entity_for_a_nearby_player", Expected.passed()),
                        Map.entry("nexus:sound_builder_samples/reaches_no_player_beyond_a_fixed_range", Expected.passed()),
                        Map.entry("nexus:tab_builder_samples/titles_the_tab_with_its_key", Expected.passed()),
                        Map.entry("nexus:tab_builder_samples/edits_the_vanilla_builder_after_the_title", Expected.passed()),
                        Map.entry("nexus:tab_builder_samples/shows_the_given_icon", Expected.passed()),
                        Map.entry("nexus:tab_builder_samples/shows_the_first_item_without_an_icon", Expected.passed()),
                        Map.entry("nexus:tab_builder_samples/shows_the_entries_the_predicate_accepts_in_order", Expected.passed()),
                        Map.entry("nexus:tab_builder_samples/adds_the_extra_stacks_after_the_mod_items", Expected.passed()),
                        Map.entry("nexus:tab_builder_samples/orders_the_entries_recorded_in_the_tab", Expected.passed()),
                        Map.entry("nexus:tab_builder_samples/shows_an_item_the_tab_already_has_once", Expected.passed()),
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
                        Map.entry("nexus:client_run_samples/out_of_frames", Expected.skipped("ran out of frames")),
                        Map.entry("nexus:payload_samples/send_to_server_reaches_the_server_and_the_reply_the_client", Expected.passed()),
                        Map.entry("nexus:payload_samples/bidirectional_reaches_both_sides", Expected.passed()),
                        Map.entry("nexus:payload_samples/optional_payload_can_send_both_ways", Expected.passed()),
                        Map.entry("nexus:payload_samples/clientbound_payload_refuses_send_to_server", Expected.passed()),
                        Map.entry("nexus:payload_samples/configuration_handler_answers_during_the_join_without_a_player", Expected.passed()),
                        Map.entry("nexus:payload_samples/player_reaches_the_client", Expected.passed()),
                        Map.entry("nexus:payload_samples/players_reaches_the_client", Expected.passed()),
                        Map.entry("nexus:payload_samples/all_reaches_the_client", Expected.passed()),
                        Map.entry("nexus:payload_samples/dimension_reaches_the_client", Expected.passed()),
                        Map.entry("nexus:payload_samples/near_reaches_the_client_inside_the_radius", Expected.passed()),
                        Map.entry("nexus:payload_samples/near_skips_the_client_outside_the_radius", Expected.passed()),
                        Map.entry("nexus:payload_samples/tracking_chunk_reaches_the_client", Expected.passed()),
                        Map.entry("nexus:payload_samples/tracking_position_reaches_the_client", Expected.passed()),
                        Map.entry("nexus:payload_samples/tracking_entity_reaches_the_client_tracking_it", Expected.passed()),
                        Map.entry("nexus:payload_samples/tracking_entity_skips_the_player_itself", Expected.passed()),
                        Map.entry("nexus:payload_samples/tracking_entity_and_self_reaches_the_player_itself", Expected.passed())
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
