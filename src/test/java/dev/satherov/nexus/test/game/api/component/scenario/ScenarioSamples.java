package dev.satherov.nexus.test.game.api.component.scenario;

import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

import org.assertj.core.api.SoftAssertions;

import java.util.ArrayList;
import java.util.List;

///
/// Checks that every scenario rule agrees with its direct read and passes at least 5 of its check stacks, or none if it's reject-only.
///
@Slf4j
public class ScenarioSamples {
    
    private static void check(GameTestHelper helper, Scenario scenario) {
        SoftAssertions softly = new SoftAssertions();
        List<String> counts = new ArrayList<>();
        for (Rule rule : scenario.getAllRules()) {
            List<ItemStack> stacks = new ArrayList<>(scenario.checkStacks(rule));
            for (Scenario.Flow flow : Scenario.Flow.values()) {
                stacks.addAll(scenario.stacks(rule, flow, 0));
            }
            
            int disagreements = 0;
            String first = "";
            for (ItemStack stack : stacks) {
                if (rule.filter().test(stack) != rule.direct().test(stack)) {
                    if (disagreements == 0) {
                        first = stack.getItem() + " " + stack.getComponentsPatch();
                    }
                    
                    disagreements++;
                }
            }
            
            int passed = 0;
            for (ItemStack stack : scenario.checkStacks(rule)) {
                passed += rule.filter().test(stack) ? 1 : 0;
            }
            
            counts.add(rule.name() + "=" + passed);
            softly.assertThat(disagreements).as("Stacks on which '%s' disagrees with its direct read, the first being '%s'", rule.name(), first).isZero();
            if (scenario.isRejectOnly(rule)) {
                softly.assertThat(passed).as("Check stacks that the reject-only rule '%s' passes", rule.name()).isZero();
            } else {
                softly.assertThat(passed).as("Check stacks that '%s' passes", rule.name()).isGreaterThanOrEqualTo(5);
            }
        }
        
        ScenarioSamples.log.info("Check stacks passed by each rule: {}", String.join(", ", counts));
        softly.assertAll();
        helper.succeed();
    }
    
    @ServerTest
    public static void farmRulesAgreeWithTheirDirectReads(GameTestHelper helper) {
        ScenarioSamples.check(helper, new FarmScenario(helper.getLevel().registryAccess()));
    }
    
    @ServerTest
    public static void storageRulesAgreeWithTheirDirectReads(GameTestHelper helper) {
        ScenarioSamples.check(helper, new StorageScenario(helper.getLevel().registryAccess()));
    }
    
    @ServerTest
    public static void moddedLootRulesAgreeWithTheirDirectReads(GameTestHelper helper) {
        ScenarioSamples.check(helper, new ModdedLootScenario(helper.getLevel().registryAccess()));
    }
    
    @ServerTest
    public static void apotheosisRulesAgreeWithTheirDirectReads(GameTestHelper helper) {
        ScenarioSamples.check(helper, new ApotheosisScenario(helper.getLevel().registryAccess()));
    }
}
