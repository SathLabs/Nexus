package dev.satherov.nexus.test.unit.gametest;

import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.test.game.gametest.internal.discovery.DiscoverySamples;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

///
/// Checks the reports and measurement files the gametest runs left under `build/reports/gametest` against every
/// sample's expected outcome. Under a `nexus.gametest.tests` selector only the reported cases are checked and the
/// measurement files are not. Run by `verifyGameTestRuns` after the runs, never by `test`.
///
public class RunReportTest {

    private static final String SELECTOR_PROPERTY = "nexus.gametest.tests";
    private static final String PASSED = "passed";
    private static final String BASELINE = "nexus_gametest:baseline";
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
                .containsAll(run.ids(false));
    }

    @EnumSource
    @ParameterizedTest
    @DisabledIfSystemProperty(named = RunReportTest.SELECTOR_PROPERTY, matches = ".*")
    public void leavesOutEveryExplicitCase(Run run) throws IOException, ParserConfigurationException, SAXException {
        Assertions.assertThat(run.readCases())
                .extracting(Case::id)
                .filteredOn(run.ids(true)::contains)
                .as("Explicit cases in a run without a selector")
                .isEmpty();
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
                    .as("The number of durations in '%s.json'", measurement.path())
                    .isEqualTo(measurement.window());
            Assertions.assertThat(Files.isRegularFile(run.directory().resolve(measurement.path() + ".txt")))
                    .as("If the run wrote a profile to '%s.txt'", measurement.path())
                    .isEqualTo(measurement.profile());
        }
    }

    @ParameterizedTest
    @EnumSource(names = "SERVER")
    @EnabledIfSystemProperty(named = RunReportTest.SELECTOR_PROPERTY, matches = ".*")
    public void reportsTheBaselineWithAMeasuredCase(Run run) throws IOException, ParserConfigurationException, SAXException {
        List<String> reported = run.readCases()
                .stream()
                .map(Case::id)
                .toList();
        boolean measured = run.measurements
                .stream()
                .map(Measurement::id)
                .anyMatch(id -> !id.equals(RunReportTest.BASELINE) && reported.contains(id));

        if (measured) {
            Assertions.assertThat(reported)
                    .as("Cases of a run that reported a measured case")
                    .contains(RunReportTest.BASELINE);
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
                "server",
                Map.ofEntries(
                        Map.entry("nexus:discovery_samples/meets_constraints", Expected.passed()),
                        Map.entry("nexus:discovery_samples/optional", Expected.passed()),
                        Map.entry("nexus:discovery_samples/explicit", Expected.passedWhenSelected()),
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
                        Map.entry("nexus:tick_window_samples/leaves_a_condition_behind", Expected.skipped("left a sequence or a callback for a later tick")),
                        Map.entry("nexus:server_test_samples/past_max_ticks", Expected.skipped("within 2 ticks")),
                        Map.entry("nexus:server_test_samples/optional_failure", Expected.skipped("The optional sample failed")),
                        Map.entry("nexus:server_test_samples/throws_an_error", Expected.skipped("The optional sample threw an error")),
                        Map.entry("nexus:server_test_samples/throws_an_error_in_a_callback", Expected.skipped("The optional callback threw an error on run 1")),
                        Map.entry("nexus:server_test_samples/throws_an_error_in_a_condition", Expected.skipped("The optional condition threw an error on run 1")),
                        Map.entry("nexus:server_test_samples/throws_an_error_without_a_message", Expected.skipped("java.lang.AssertionError")),
                        Map.entry("nexus:server_test_samples/throws_an_error_without_a_message_in_a_callback", Expected.skipped("java.lang.StackOverflowError")),
                        Map.entry("nexus:server_test_samples/throws_an_exception_without_a_message", Expected.skipped("java.lang.IllegalStateException")),
                        Map.entry("nexus:server_test_samples/throws_an_exception_in_a_condition", Expected.skipped("The optional condition threw an exception on run 1")),
                        Map.entry("nexus:server_test_samples/throws_an_exception_in_a_step", Expected.skipped("The optional step threw an exception")),
                        Map.entry("nexus:server_test_samples/throws_an_error_on_the_timeout_tick", Expected.skipped("The optional condition threw an error on the timeout tick")),
                        Map.entry("nexus:server_test_samples/throws_an_exception_on_the_last_tick", Expected.skipped("The optional condition threw an exception on the last tick before the timeout")),
                        Map.entry("nexus:server_test_samples/fails_an_assertion_in_a_step_at_once", Expected.skipped("The optional step failed an assertion on tick 0")),
                        Map.entry("nexus:server_test_samples/retries_an_assertion_until_it_passes", Expected.passed()),
                        Map.entry("nexus:server_test_samples/retries_an_assertion_until_the_timeout", Expected.skipped("The optional assertion never passed")),
                        Map.entry("nexus:timing_samples/records_a_series", Expected.passed()),
                        Map.entry("nexus:timing_samples/refuses_an_invalid_name", Expected.passed()),
                        Map.entry("nexus:timing_samples/refuses_a_duplicate_name", Expected.passed()),
                        Map.entry("nexus:timing_samples/empty_series_keeps_its_name", Expected.passed()),
                        Map.entry("nexus:attach_tooltips_event_samples/attaches_between_hover_text_and_components", Expected.passed()),
                        Map.entry("nexus:attach_tooltips_event_samples/skips_other_items", Expected.passed()),
                        Map.entry("nexus:attach_tooltips_event_samples/attaches_to_fluid_stacks", Expected.passed()),
                        Map.entry("nexus:attach_tooltips_event_samples/skips_other_fluids", Expected.passed()),
                        Map.entry("nexus:attach_tooltips_event_samples/hints_at_shift_once_for_several_attachments", Expected.passed()),
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
                        Map.entry("nexus:block_builder_samples/keeps_the_blocks_features_on_an_item_whose_properties_were_replaced", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/keeps_the_edits_of_shared_properties_to_their_own_block", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/declares_no_translation_for_a_block_that_was_not_registered", Expected.passed()),
                        Map.entry("nexus:block_builder_samples/refuses_a_block_whose_item_is_taken_before_declaring_anything", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/is_valid_for_every_given_block_only", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/at_finds_the_placed_block_entity", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/at_is_null_without_a_block_entity_of_the_type", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/is_matches_block_entities_of_the_type_only", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/finds_the_capability_on_every_block_entity_of_the_type", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/ticker_binds_on_load_and_sleeps", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/wake_ticks_the_bound_ticker", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/breaking_the_block_drops_the_ticker", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/second_ticker_on_the_block_entity_throws", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/refuses_a_type_without_blocks", Expected.passed()),
                        Map.entry("nexus:block_entity_builder_samples/registers_a_type_without_blocks_for_the_blocks_added_later", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/reads_nothing_from_a_stack_without_the_component", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/sets_the_value_on_a_stack", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/saves_the_value_with_the_stack", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/syncs_the_value_with_the_stack", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/set_refuses_a_value_its_codec_cannot_encode", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/update_refuses_a_value_its_codec_cannot_encode", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/saves_the_value_with_the_stack_in_nbt", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/receives_the_value_through_the_validated_stream_codec", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/refuses_to_receive_a_value_its_codec_cannot_encode", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/hashes_the_value_the_same_on_a_copy_and_a_received_stack", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/menu_resends_only_a_slot_whose_hash_differs", Expected.passed()),
                        Map.entry("nexus:data_component_builder_samples/menu_hashes_a_value_its_codec_cannot_encode", Expected.passed()),
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
                        Map.entry("nexus:entity_builder_samples/never_runs_the_client_code_on_a_dedicated_server", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/the_egg_spawns_an_entity_with_the_default_data", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/keeps_the_features_of_the_type_on_an_egg_whose_properties_were_replaced", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/refuses_a_living_type_without_attributes", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/refuses_a_type_whose_egg_is_taken_before_declaring_anything", Expected.passed()),
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
                        Map.entry("nexus:fluid_builder_samples/never_runs_the_client_code_on_a_dedicated_server", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/keeps_the_bucket_defaults_on_a_bucket_whose_properties_were_replaced", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/refuses_a_fluid_whose_bucket_is_taken_before_registering_anything", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/refuses_a_fluid_whose_type_translation_is_taken_before_registering_anything", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/refuses_a_fluid_whose_block_item_translation_is_taken_before_registering_anything", Expected.passed()),
                        Map.entry("nexus:fluid_builder_samples/refuses_a_fluid_whose_block_translation_is_taken_before_registering_anything", Expected.passed()),
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
                        Map.entry("nexus:item_builder_samples/keeps_the_edits_of_shared_properties_to_their_own_item", Expected.passed()),
                        Map.entry("nexus:item_builder_samples/shows_an_item_in_the_operator_tab_only_with_permissions", Expected.passed()),
                        Map.entry("nexus:menu_builder_samples/names_the_title_after_its_key", Expected.passed()),
                        Map.entry("nexus:menu_builder_samples/opens_the_menu_under_its_title", Expected.passed()),
                        Map.entry("nexus:menu_builder_samples/writes_the_extra_data_the_factory_reads", Expected.passed()),
                        Map.entry("nexus:menu_builder_samples/never_runs_the_client_code_on_a_dedicated_server", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/spawn_sends_the_particles_to_the_players_near_the_position", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/spawn_sends_the_particles_of_a_type_that_overrides_the_limiter_farther", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/is_matches_options_of_the_type_only", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/spawn_sends_the_particles_with_the_given_spread_and_speed", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/spawn_sends_the_particles_that_always_show_to_the_players_near_the_position", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/spawn_sends_the_particles_that_override_the_limiter_farther", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/spawn_sends_the_particles_to_the_given_player_only", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/spawn_sends_nothing_to_a_given_player_out_of_range", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/spawn_sends_the_particles_that_override_the_limiter_to_a_given_far_player", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/never_runs_the_client_code_on_a_dedicated_server", Expected.passed()),
                        Map.entry("nexus:attachment_entry_samples/update_in_place_on_a_chunk_marks_it_unsaved", Expected.passed()),
                        Map.entry("nexus:attachment_entry_samples/update_in_place_on_a_block_entity_marks_its_chunk_unsaved", Expected.passed()),
                        Map.entry("nexus:attachment_entry_samples/chunk_loads_the_other_attachments_beside_one_the_codec_refuses", Expected.passed()),
                        Map.entry("nexus:attachment_entry_samples/chunk_saves_the_other_attachments_beside_one_the_codec_cannot_write", Expected.passed()),
                        Map.entry("nexus:attachment_entry_samples/death_copy_keeps_only_the_attachments_copied_on_death", Expected.passed()),
                        Map.entry("nexus:attachment_entry_samples/initial_sync_sends_an_attachment_that_writes_no_bytes", Expected.passed()),
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
                        Map.entry("nexus:tick_scope_samples/delayed_task_runs_on_its_pass_in_level", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/delayed_task_runs_on_its_pass_on_server", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/repeating_task_counts_until_cancelled_in_level", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/repeating_task_counts_until_cancelled_on_server", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/ticker_sleeps_and_wakes_in_level", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/ticker_sleeps_and_wakes_on_server", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/wake_during_own_tick_is_kept_in_level", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/wake_during_own_tick_is_kept_on_server", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/task_cancelled_inside_its_run_ends_in_level", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/task_cancelled_inside_its_run_ends_on_server", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/ticker_cancelled_inside_its_tick_ends_in_level", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/ticker_cancelled_inside_its_tick_ends_on_server", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/work_submitted_during_pass_waits_in_level", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/work_submitted_during_pass_waits_on_server", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/server_scope_works_when_first_called_after_start", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/scope_ended_inside_a_job_step_stops_the_pass", Expected.passed()),
                        Map.entry("nexus:tick_scope_samples/scopes_requested_from_another_thread_throw", Expected.passed()),
                        Map.entry("nexus:budget_samples/job_spends_the_minimum_without_time_left", Expected.passed()),
                        Map.entry("nexus:budget_samples/job_spends_up_to_the_maximum_with_time_left", Expected.passed()),
                        Map.entry("nexus:budget_samples/job_finishes_in_level", Expected.passed()),
                        Map.entry("nexus:budget_samples/job_finishes_on_server", Expected.passed()),
                        Map.entry("nexus:budget_samples/jobs_of_one_budget_take_turns_in_level", Expected.passed()),
                        Map.entry("nexus:budget_samples/jobs_of_one_budget_take_turns_on_server", Expected.passed()),
                        Map.entry("nexus:budget_samples/every_budget_steps_on_every_pass_in_level", Expected.passed()),
                        Map.entry("nexus:budget_samples/every_budget_steps_on_every_pass_on_server", Expected.passed()),
                        Map.entry("nexus:budget_samples/job_cancelled_inside_its_step_ends_in_level", Expected.passed()),
                        Map.entry("nexus:budget_samples/job_cancelled_inside_its_step_ends_on_server", Expected.passed()),
                        Map.entry("nexus:budget_samples/budget_of_another_mod_is_refused", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/has_reads_the_default_components_of_the_item", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/exact_passes_an_equal_value", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/compare_tests_the_part_at_the_path", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/compare_fails_every_operator_on_an_absent_part", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/matching_tests_the_vanilla_predicate", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/reads_an_item_resource_through_its_stack", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/reads_a_fluid_resource_through_its_stack", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/tests_a_fluid_stack", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/matchers_hold_the_filter_as_their_one_predicate", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/item_predicate_from_the_matchers_passes_what_the_filter_passes", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/ingredient_passes_stacks_of_the_set_that_pass_the_filter", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/fluid_ingredient_passes_stacks_of_the_set_that_pass_the_filter", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/ingredients_are_equal_by_set_and_filter", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/predicate_round_trips_in_json", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/predicate_round_trips_over_the_network", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/ingredient_round_trips_in_json", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/ingredient_round_trips_over_the_network", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/fluid_ingredient_round_trips_in_json", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/fluid_ingredient_round_trips_over_the_network", Expected.passed()),
                        Map.entry("nexus:component_filter_samples/stores_the_result_of_a_resource_or_a_fluid_stack", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/every_write_drops_the_stored_result", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/write_to_a_copy_is_seen_only_on_the_copy", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/every_filter_gets_its_own_result", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/alternating_filters_never_store", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/another_holder_does_not_get_the_stored_result", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/tag_update_drops_the_stored_result", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/write_during_the_evaluation_is_seen", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/tag_update_during_the_evaluation_is_seen", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/result_of_two_direct_reads_is_stored", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/result_of_one_direct_read_is_never_stored", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/result_of_a_scan_read_is_stored", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/two_threads_get_the_result_of_their_own_filter", Expected.passed()),
                        Map.entry("nexus:memoized_component_map_samples/result_is_stored_on_default_item_and_fluid_resources", Expected.passed()),
                        Map.entry("nexus:vanilla_readers_samples/enchantments_match_the_encoding", Expected.passed()),
                        Map.entry("nexus:vanilla_readers_samples/container_slots_match_the_encoding", Expected.passed()),
                        Map.entry("nexus:vanilla_readers_samples/bundle_items_match_the_encoding", Expected.passed()),
                        Map.entry("nexus:vanilla_readers_samples/container_stack_parts_match_the_encoding", Expected.passed()),
                        Map.entry("nexus:vanilla_readers_samples/bundle_stack_parts_match_the_encoding", Expected.passed()),
                        Map.entry("nexus:vanilla_readers_samples/enchantments_of_another_registry_set_read_as_absent", Expected.passed()),
                        Map.entry("nexus:scenario_samples/farm_rules_agree_with_their_direct_reads", Expected.passed()),
                        Map.entry("nexus:scenario_samples/storage_rules_agree_with_their_direct_reads", Expected.passed()),
                        Map.entry("nexus:scenario_samples/modded_loot_rules_agree_with_their_direct_reads", Expected.passed()),
                        Map.entry("nexus:scenario_samples/apotheosis_rules_agree_with_their_direct_reads", Expected.passed()),
                        Map.entry("nexus:dev_operators_samples/ops_a_listed_player_until_they_log_out", Expected.passed()),
                        Map.entry("nexus:dev_operators_samples/leaves_an_unlisted_player_alone", Expected.passed()),
                        Map.entry("nexus:dev_operators_samples/keeps_an_operator_opped_before_login", Expected.passed()),
                        Map.entry("nexus:dev_operators_samples/deops_a_player_left_opped_by_an_unclean_stop_on_the_next_start", Expected.passed()),
                        Map.entry("nexus:component_filter_speed_samples/times_farm_rules", Expected.passedWhenSelected()),
                        Map.entry("nexus:component_filter_speed_samples/times_storage_rules", Expected.passedWhenSelected()),
                        Map.entry("nexus:component_filter_speed_samples/times_modded_loot_rules", Expected.passedWhenSelected()),
                        Map.entry("nexus:component_filter_speed_samples/times_apotheosis_rules", Expected.passedWhenSelected()),
                        Map.entry("nexus:component_filter_speed_samples/times_item_resources", Expected.passedWhenSelected()),
                        Map.entry("nexus:attachment_entry_samples/block_entity_keeps_the_formatted_value_through_a_save_and_load", Expected.passed()),
                        Map.entry("nexus:attachment_entry_samples/block_entity_loads_a_map_without_a_version_through_the_steps", Expected.passed()),
                        Map.entry("nexus:attachment_entry_samples/block_entity_writes_the_formatted_value_with_its_chunk", Expected.passed()),
                        Map.entry("nexus:attachment_entry_samples/set_on_a_block_entity_marks_its_chunk_unsaved", Expected.passed()),
                        Map.entry("nexus:attachment_entry_samples/set_on_a_chunk_marks_it_unsaved", Expected.passed()),
                        Map.entry("nexus:player_data_samples/edit_keeps_pearls_vehicle_and_dimension_and_saves_the_attachment", Expected.passed()),
                        Map.entry("nexus:player_data_samples/edit_of_an_unknown_player_does_not_run", Expected.passed()),
                        Map.entry("nexus:player_data_samples/inspect_leaves_the_file_and_returns_the_result", Expected.passed()),
                        Map.entry("nexus:player_data_samples/replace_swaps_the_file_of_an_offline_player", Expected.passed()),
                        Map.entry("nexus:player_data_samples/throwing_edit_leaves_the_file", Expected.passed()),
                        Map.entry("nexus:saved_value_samples/level_value_survives_a_fresh_storage", Expected.passed()),
                        Map.entry("nexus:saved_value_samples/refuses_a_register_after_the_server_started", Expected.passed()),
                        Map.entry("nexus:saved_value_samples/refuses_and_keeps_a_file_of_a_newer_version", Expected.passed()),
                        Map.entry("nexus:saved_value_samples/server_value_survives_a_fresh_storage", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/capture_copies_the_stacks_and_leaves_the_holder_alone", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/clear_empties_every_group_of_the_layout", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/decodes_the_documented_form", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/empty_snapshot_holds_no_groups", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/encodes_the_documented_form", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/groups_of_an_absent_source_survive_and_come_back_as_leftovers", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/refuse_keeps_stacks_out_of_the_own_slot_and_the_fallback", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/registered_leaves_out_the_excepted_sources", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/restore_passes_over_a_slot_that_does_not_accept", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/restore_returns_what_no_slot_takes", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/restore_tops_up_matching_stacks_before_taking_a_free_slot", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/second_source_of_one_id_throws", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/sources_give_their_groups_under_the_source_id", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/take_with_a_filter_empties_only_the_matching_slots", Expected.passed()),
                        Map.entry("nexus:slot_layout_samples/vanilla_sources_carry_the_slots_of_a_player", Expected.passed()),
                        Map.entry(RunReportTest.BASELINE, Expected.passed())
                ),
                List.of(
                        Measurement.of("nexus/discovery_samples/measured", 5),
                        Measurement.of("nexus/discovery_samples/long_window", 10),
                        Measurement.of("nexus/tick_window_samples/window", 7),
                        Measurement.profiled("nexus/tick_window_samples/profiled_window", 3),
                        Measurement.of("nexus/timing_samples/records_a_series.sample", 3),
                        Measurement.of("nexus/timing_samples/refuses_a_duplicate_name.sample", 2),
                        Measurement.of("nexus_gametest/baseline", 10)
                ),
                List.of(
                        "nexus/discovery_samples/window_overruns_ticks",
                        "nexus/discovery_samples/empty_window",
                        "nexus/tick_window_samples/succeeds_itself",
                        "nexus/tick_window_samples/leaves_a_condition_behind",
                        "nexus/timing_samples/empty_series_keeps_its_name.empty"
                )
        ),
        CLIENT(
                "client",
                Map.ofEntries(
                        Map.entry("nexus:discovery_samples/client", Expected.passed()),
                        Map.entry("nexus:discovery_samples/client_with_helper", Expected.skipped("Parameter is not a 'Client', found 'GameTestHelper' instead")),
                        Map.entry("nexus:discovery_samples/no_frames", Expected.skipped("ClientTest#maxFrames is not positive")),
                        Map.entry("nexus:client_samples/title_screen_at_start", Expected.passed()),
                        Map.entry("nexus:client_samples/ticks_advance_server", Expected.passed()),
                        Map.entry("nexus:client_samples/until_out_of_frames", Expected.passed()),
                        Map.entry("nexus:client_samples/server_level_outside_world", Expected.passed()),
                        Map.entry("nexus:client_samples/on_server_runs_the_task_on_the_server_thread", Expected.passed()),
                        Map.entry("nexus:client_samples/on_server_rethrows_what_the_task_throws", Expected.passed()),
                        Map.entry("nexus:client_samples/on_server_rethrows_what_a_nested_task_throws", Expected.passed()),
                        Map.entry("nexus:client_samples/settle_refuses_a_pausing_screen", Expected.passed()),
                        Map.entry("nexus:client_samples/pose_moves_camera", Expected.passed()),
                        Map.entry("nexus:client_samples/settle_shows_server_block", Expected.passed()),
                        Map.entry("nexus:client_samples/settle_renders_server_block", Expected.passed()),
                        Map.entry("nexus:client_samples/await_still_ends_on_still_frames", Expected.passed()),
                        Map.entry("nexus:client_samples/await_still_out_of_frames", Expected.passed()),
                        Map.entry("nexus:client_samples/await_still_refuses_one_frame", Expected.passed()),
                        Map.entry("nexus:client_samples/still_world_frames_match", Expected.passed()),
                        Map.entry("nexus:client_samples/hide_hud_clears_hotbar", Expected.passed()),
                        Map.entry("nexus:test_world_samples/join_world", Expected.passed()),
                        Map.entry("nexus:test_world_samples/leave_world", Expected.passed()),
                        Map.entry("nexus:keyboard_samples/press_opens_inventory", Expected.passed()),
                        Map.entry("nexus:keyboard_samples/hold_sets_modifier", Expected.passed()),
                        Map.entry("nexus:keyboard_samples/type_into_text_field", Expected.passed()),
                        Map.entry("nexus:mouse_samples/click_opens_options", Expected.passed()),
                        Map.entry("nexus:mouse_samples/clicks_in_a_row_are_single_clicks", Expected.passed()),
                        Map.entry("nexus:mouse_samples/double_click_selects_the_word", Expected.passed()),
                        Map.entry("nexus:mouse_samples/scroll_selects_previous_slot", Expected.passed()),
                        Map.entry("nexus:capture_samples/size_of_render_target", Expected.passed()),
                        Map.entry("nexus:capture_samples/pixel_matches_write", Expected.passed()),
                        Map.entry("nexus:capture_samples/pixel_outside_frame", Expected.passed()),
                        Map.entry("nexus:capture_samples/region_inside_frame", Expected.passed()),
                        Map.entry("nexus:capture_samples/region_outside_frame", Expected.passed()),
                        Map.entry("nexus:capture_samples/pixel_assertion", Expected.passed()),
                        Map.entry("nexus:capture_samples/golden_mismatch_writes_files", Expected.passed()),
                        Map.entry("nexus:capture_samples/missing_golden", Expected.passed()),
                        Map.entry("nexus:capture_samples/title_screen", Expected.passed()),
                        Map.entry("nexus:client_run_samples/window", Expected.passed()),
                        Map.entry("nexus:client_run_samples/profiled_window", Expected.passed()),
                        Map.entry("nexus:client_run_samples/optional_failure", Expected.skipped("This test always fails")),
                        Map.entry("nexus:client_run_samples/out_of_frames", Expected.skipped("ran out of frames")),
                        Map.entry("nexus:client_run_samples/changes_the_window_the_gui_scale_and_the_debug_overlay", Expected.passed()),
                        Map.entry("nexus:client_run_samples/keeps_the_window_of_the_run", Expected.passed()),
                        Map.entry("nexus:client_run_samples/shows_no_tutorial_toast_in_a_world", Expected.passed()),
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
                        Map.entry("nexus:payload_samples/tracking_entity_and_self_reaches_the_player_itself", Expected.passed()),
                        Map.entry("nexus:payload_samples/optional_payload_skips_a_server_without_it", Expected.passed()),
                        Map.entry("nexus:payload_samples/optional_reply_skips_a_server_without_it", Expected.passed()),
                        Map.entry("nexus:payload_samples/can_send_to_server_refuses_without_a_connection", Expected.passed()),
                        Map.entry("nexus:payload_samples/configuration_payload_refuses_a_play_connection", Expected.passed()),
                        Map.entry("nexus:payload_samples/network_thread_handler_runs_off_the_main_thread_and_enqueues_onto_it", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/client_level_task_runs", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/client_scope_task_runs", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/client_ticker_binds", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/scopes_end_on_logout", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/client_level_scope_works_in_its_load", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/scopes_end_on_reconfiguration", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/paused_client_scope_does_not_pass", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/frozen_level_scope_does_not_pass", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/frozen_level_scope_forgets_cancelled_tickers", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/unloading_the_chunk_drops_the_ticker", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/bound_ticker_ends_with_its_level", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/bound_ticker_waits_outside_the_world_border", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/client_job_spends_the_minimum_only", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/scopes_end_on_a_login_without_a_logout", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/level_built_by_a_mod_is_not_held", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/server_scope_left_by_a_stop_is_replaced", Expected.passed()),
                        Map.entry("nexus:client_tick_scope_samples/level_swapped_in_the_packet_handler_is_held", Expected.passed()),
                        Map.entry("nexus:scene_samples/visible_box_is_cut_by_wall", Expected.passed()),
                        Map.entry("nexus:scene_samples/hidden_box_shows_only_behind_wall", Expected.passed()),
                        Map.entry("nexus:scene_samples/fill_stands_in_water", Expected.passed()),
                        Map.entry("nexus:scene_samples/keyed_draw_replaces", Expected.passed()),
                        Map.entry("nexus:scene_samples/expiry_removes_after_ticks_and_not_before", Expected.passed()),
                        Map.entry("nexus:scene_samples/followed_entity_moves_drawing", Expected.passed()),
                        Map.entry("nexus:scene_samples/repaint_changes_color", Expected.passed()),
                        Map.entry("nexus:scene_samples/leaving_level_drops_everything", Expected.passed()),
                        Map.entry("nexus:scene_samples/debug_renderer_refills_while_on", Expected.passed()),
                        Map.entry("nexus:scene_samples/visible_circle_and_sphere_are_cut_by_wall", Expected.passed()),
                        Map.entry("nexus:scene_samples/hidden_circle_and_sphere_show_only_behind_wall", Expected.passed()),
                        Map.entry("nexus:scene_samples/always_circle_and_sphere_show_everywhere", Expected.passed()),
                        Map.entry("nexus:scene_samples/near_sphere_fills_its_silhouette", Expected.passed()),
                        Map.entry("nexus:scene_samples/edge_on_ring_keeps_its_band", Expected.passed()),
                        Map.entry("nexus:scene_samples/debug_renderer_after_the_event_throws", Expected.passed()),
                        Map.entry("nexus:outlines_samples/replacing_rule_hides_vanilla_outline", Expected.passed()),
                        Map.entry("nexus:outlines_samples/adding_rule_keeps_vanilla_outline", Expected.passed()),
                        Map.entry("nexus:outlines_samples/rule_draws_circles_and_spheres", Expected.passed()),
                        Map.entry("nexus:outlines_samples/rule_is_asked_once_per_target", Expected.passed()),
                        Map.entry("nexus:glows_samples/entity_glows_in_color", Expected.passed()),
                        Map.entry("nexus:glows_samples/block_glow_stops_when_broken", Expected.passed()),
                        Map.entry("nexus:glows_samples/chest_glows", Expected.passed()),
                        Map.entry("nexus:glows_samples/expiry_removes_after_ticks_and_not_before", Expected.passed()),
                        Map.entry("nexus:glows_samples/block_glow_keeps_its_color_under_the_pack", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/own_shader_draws_through_draw_hook", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/bound_uniform_reaches_shader", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/later_binding_replaces_earlier", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/registration_after_the_event_throws", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/color_program_runs_every_hook", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/textured_program_runs_every_hook", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/entity_program_runs_every_hook", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/shaded_program_is_drawn_by_the_pack", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/own_program_keeps_its_hooks_under_the_pack", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/broken_program_is_skipped_and_logged_once", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/missing_program_file_throws", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/vanilla_block_of_program_needs_no_binding", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/hook_throwing_mid_vertex_leaves_next_frame_clean", Expected.passed()),
                        Map.entry("nexus:pipeline_samples/unbound_program_block_throws_at_first_draw", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/own_type_on_pipeline_target_keeps_its_color", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/own_type_on_pipeline_target_is_hidden_by_the_level", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/own_type_on_main_target_loses_its_color_under_the_pack", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/additive_own_type_on_pipeline_target_adds_to_the_frame", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/lightning_own_type_on_pipeline_target_adds_to_the_frame", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/own_type_behind_glass_is_unchanged_by_an_earlier_draw", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/own_type_from_the_feature_pass_is_hidden_by_the_level", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/own_item_entity_type_keeps_its_color_through_draw_hook", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/additive_item_entity_type_keeps_its_blend_with_improved_transparency", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/geometry_drawn_after_the_level_covers_the_own_type", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/overlay_is_freed_once_the_level_unloads", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/own_type_falls_back_to_the_main_target_on_another_depth_format", Expected.passed()),
                        Map.entry("nexus:pipeline_target_samples/main_target_stencil_follows_the_run_property", Expected.passed()),
                        Map.entry("nexus:post_effect_samples/activated_effect_changes_pixel", Expected.passed()),
                        Map.entry("nexus:post_effect_samples/effects_run_in_activation_order", Expected.passed()),
                        Map.entry("nexus:post_effect_samples/deactivation_restores", Expected.passed()),
                        Map.entry("nexus:post_effect_samples/missing_file_runs_nothing", Expected.passed()),
                        Map.entry("nexus:hud_layer_samples/layer_draws_above_crosshair", Expected.passed()),
                        Map.entry("nexus:hud_layer_samples/below_slot_draws_under_anchor", Expected.passed()),
                        Map.entry("nexus:hud_layer_samples/projected_marker_hits_block", Expected.passed()),
                        Map.entry("nexus:hud_layer_samples/hidden_hud_skips_layer", Expected.passed()),
                        Map.entry("nexus:encoding_samples/holder_of_the_client_level_encodes_on_the_client_thread", Expected.passed()),
                        Map.entry("nexus:encoding_samples/owner_outside_a_world_is_the_built_in_registry", Expected.passed()),
                        Map.entry("nexus:entry_builder_samples/runs_the_client_code_of_the_taken_builder_once", Expected.passed()),
                        Map.entry("nexus:menu_builder_samples/opens_the_screen_of_the_menu_on_the_client", Expected.passed()),
                        Map.entry("nexus:entity_builder_samples/renders_a_subclass_with_the_renderer_of_its_superclass", Expected.passed()),
                        Map.entry("nexus:particle_entry_samples/creates_the_particles_with_the_registered_providers", Expected.passed()),
                        Map.entry("nexus:encoding_samples/leaving_the_world_drops_the_registries_of_its_server", Expected.passed()),
                        Map.entry("nexus:activation_samples/double_tap_over_idle_ticks_fires", Expected.passed()),
                        Map.entry("nexus:activation_samples/held_for_does_not_fire_on_an_earlier_release", Expected.passed()),
                        Map.entry("nexus:activation_samples/held_for_fires_once_on_the_fifth_tick", Expected.passed()),
                        Map.entry("nexus:activation_samples/held_ticks_and_progress_read_back_per_tick", Expected.passed()),
                        Map.entry("nexus:activation_samples/release_does_not_fire_when_a_screen_opens_over_the_held_key", Expected.passed()),
                        Map.entry("nexus:activation_samples/release_fires_on_key_up", Expected.passed()),
                        Map.entry("nexus:activation_samples/repeat_fires_on_held_ticks_one_four_and_seven", Expected.passed()),
                        Map.entry("nexus:activation_samples/tap_inside_one_tick_fires_press_once", Expected.passed()),
                        Map.entry("nexus:carry_samples/carry_chain_without_a_server_action_leaves_the_key_without_a_server_half", Expected.passed()),
                        Map.entry("nexus:carry_samples/eight_values_arrive_typed_and_in_order", Expected.passed()),
                        Map.entry("nexus:carry_samples/null_reader_runs_the_client_action_and_sends_nothing", Expected.passed()),
                        Map.entry("nexus:carry_samples/one_value_arrives_typed", Expected.passed()),
                        Map.entry("nexus:carry_samples/registry_reading_codec_carries_its_value", Expected.passed()),
                        Map.entry("nexus:carry_samples/two_values_arrive_typed_and_in_order", Expected.passed()),
                        Map.entry("nexus:key_event_samples/activated_event_carries_the_key_that_fired", Expected.passed()),
                        Map.entry("nexus:key_event_samples/cancelled_activation_skips_the_client_action_and_the_send", Expected.passed()),
                        Map.entry("nexus:key_event_samples/cancelled_arrival_skips_the_server_action_and_keeps_the_held_state", Expected.passed()),
                        Map.entry("nexus:key_event_samples/received_event_carries_the_joined_player", Expected.passed()),
                        Map.entry("nexus:key_sync_samples/existing_jump_runs_the_server_action_while_the_player_jumps", Expected.passed()),
                        Map.entry("nexus:key_sync_samples/existing_name_without_a_mapping_stays_inert", Expected.passed()),
                        Map.entry("nexus:key_sync_samples/held_key_reads_down_on_the_server_until_its_release", Expected.passed()),
                        Map.entry("nexus:key_sync_samples/held_key_reads_up_on_the_server_after_a_relog", Expected.passed()),
                        Map.entry("nexus:key_sync_samples/held_key_reads_up_on_the_server_when_a_screen_opens_over_it", Expected.passed()),
                        Map.entry("nexus:key_sync_samples/press_runs_the_server_action_with_the_joined_player_after_the_client_action", Expected.passed()),
                        Map.entry("nexus:key_sync_samples/server_held_ticks_start_at_one_and_grow", Expected.passed()),
                        Map.entry("nexus:key_sync_samples/server_progress_of_a_held_for_key_reaches_one", Expected.passed()),
                        Map.entry("nexus:key_tick_speed_samples/times_the_key_pass", Expected.passed()),
                        Map.entry("nexus:keybind_samples/bound_key_follows_a_rebind", Expected.passed()),
                        Map.entry("nexus:keybind_samples/click_the_screen_handled_fires_nothing", Expected.passed()),
                        Map.entry("nexus:keybind_samples/game_key_does_nothing_in_a_screen", Expected.passed()),
                        Map.entry("nexus:keybind_samples/mapping_has_the_declared_key_modifier_and_context", Expected.passed()),
                        Map.entry("nexus:keybind_samples/press_runs_the_client_action_once", Expected.passed()),
                        Map.entry("nexus:keybind_samples/register_after_the_mappings_were_registered_throws", Expected.passed()),
                        Map.entry("nexus:keybind_samples/screen_key_fires_in_a_screen_and_goes_up_when_it_closes", Expected.passed()),
                        Map.entry("nexus:keybind_samples/shift_key_fires_only_with_shift_held", Expected.passed()),
                        Map.entry("nexus:keybind_samples/typing_the_letter_into_a_text_box_fires_nothing", Expected.passed()),
                        Map.entry("nexus:keybind_samples/unbound_and_mouse_keys_register", Expected.passed()),
                        Map.entry("nexus:keyboard_samples/hold_makes_letter_down", Expected.passed()),
                        Map.entry("nexus:keyboard_samples/hold_makes_shift_down", Expected.passed()),
                        Map.entry("nexus:keyboard_samples/release_all_makes_keys_up", Expected.passed()),
                        Map.entry("nexus:keyboard_samples/shift_modifier_follows_held_shift", Expected.passed()),
                        Map.entry("nexus:player_data_samples/edit_runs_on_the_joined_player", Expected.passed()),
                        Map.entry("nexus:player_data_samples/inspect_leaves_the_joined_player_alone", Expected.passed()),
                        Map.entry("nexus:player_data_samples/replace_swaps_the_joined_player_for_one_loaded_from_the_tag", Expected.passed()),
                        Map.entry("nexus:saved_value_samples/client_sees_the_synced_value_on_join_and_on_change", Expected.passed())
                ),
                List.of(
                        Measurement.of("nexus/client_run_samples/window", 40),
                        Measurement.profiled("nexus/client_run_samples/profiled_window", 3),
                        Measurement.of("nexus/key_tick_speed_samples/times_the_key_pass", 400),
                        Measurement.of("nexus_gametest/baseline", 400)
                ),
                List.of()
        ),
        CLIENT_SHADERS(
                "client-shaders",
                Run.replaced(Run.CLIENT.cases, "nexus:glows_samples/block_glow_keeps_its_color_under_the_pack", Expected.skipped("the red pixels around the block")),
                Run.CLIENT.measurements,
                Run.CLIENT.unmeasured
        );

        // The name of the report directory and file of the run.
        private final String side;

        // Every case the run should report, by its identifier, the explicit ones only under a selector.
        private final Map<String, Expected> cases;

        // The measurement files the run should write, by their path without the extension.
        private final List<Measurement> measurements;

        // The measured cases and series that should leave no measurement files, by their path without the extension.
        private final List<String> unmeasured;

        private static Map<String, Expected> replaced(Map<String, Expected> cases, String id, Expected expected) {
            Map<String, Expected> copy = new HashMap<>(cases);
            copy.put(id, expected);
            return Map.copyOf(copy);
        }

        private List<String> ids(boolean explicit) {
            return this.cases.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().explicit() == explicit)
                    .map(Map.Entry::getKey)
                    .toList();
        }

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

    private record Expected(String outcome, String message, boolean explicit) {

        private static Expected passed() {
            return new Expected(RunReportTest.PASSED, "", false);
        }

        private static Expected passedWhenSelected() {
            return new Expected(RunReportTest.PASSED, "", true);
        }

        private static Expected skipped(String message) {
            return new Expected("skipped", message, false);
        }
    }

    private record Measurement(String path, int window, boolean profile) {

        private static Measurement of(String path, int window) {
            return new Measurement(path, window, false);
        }

        private static Measurement profiled(String path, int window) {
            return new Measurement(path, window, true);
        }

        private String id() {
            return this.path.replaceFirst("/", ":");
        }
    }
}
