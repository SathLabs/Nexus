package dev.satherov.nexus.test.game.api.tick.budget;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.tick.Job;
import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.Task;
import dev.satherov.nexus.api.tick.TickScope;
import dev.satherov.nexus.api.tick.budget.Budget;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.fml.ModList;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

///
/// Checks how the jobs of budgets step on the passes of a level scope and of the server scope.
///
public class BudgetSamples {
    
    @ServerTest
    public static void jobFinishesInLevel(GameTestHelper helper) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        BudgetSamples.jobFinishes(helper, scheduler.in(helper.getLevel()), scheduler.budget("budget_samples_finishing_in_level", 1_000, 1_000));
    }
    
    @ServerTest
    public static void jobFinishesOnServer(GameTestHelper helper) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        BudgetSamples.jobFinishes(helper, scheduler.server(), scheduler.budget("budget_samples_finishing_on_server", 1_000, 1_000));
    }
    
    @ServerTest
    public static void jobsOfOneBudgetTakeTurnsInLevel(GameTestHelper helper) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        BudgetSamples.jobsOfOneBudgetTakeTurns(helper, scheduler.in(helper.getLevel()), scheduler.budget("budget_samples_turns_in_level", 1, 1));
    }
    
    @ServerTest
    public static void jobsOfOneBudgetTakeTurnsOnServer(GameTestHelper helper) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        BudgetSamples.jobsOfOneBudgetTakeTurns(helper, scheduler.server(), scheduler.budget("budget_samples_turns_on_server", 1, 1));
    }
    
    @ServerTest
    public static void everyBudgetStepsOnEveryPassInLevel(GameTestHelper helper) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        BudgetSamples.everyBudgetStepsOnEveryPass(
                helper,
                scheduler.in(helper.getLevel()),
                List.of(
                        scheduler.budget("budget_samples_minimum_in_level", 2_000, 2_000),
                        scheduler.budget("budget_samples_maximum_in_level", 1, 20_000),
                        scheduler.budget("budget_samples_zero_in_level", 1, 1)
                )
        );
    }
    
    @ServerTest
    public static void everyBudgetStepsOnEveryPassOnServer(GameTestHelper helper) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        BudgetSamples.everyBudgetStepsOnEveryPass(
                helper,
                scheduler.server(),
                List.of(
                        scheduler.budget("budget_samples_minimum_on_server", 2_000, 2_000),
                        scheduler.budget("budget_samples_maximum_on_server", 1, 20_000),
                        scheduler.budget("budget_samples_zero_on_server", 1, 1)
                )
        );
    }
    
    @ServerTest
    public static void jobCancelledInsideItsStepEndsInLevel(GameTestHelper helper) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        BudgetSamples.jobCancelledInsideItsStepEnds(
                helper,
                scheduler.in(helper.getLevel()),
                scheduler.budget("budget_samples_cancelling_in_level", 1_000, 1_000)
        );
    }
    
    @ServerTest
    public static void jobCancelledInsideItsStepEndsOnServer(GameTestHelper helper) {
        Scheduler scheduler = Nexus.getMod().getScheduler();
        BudgetSamples.jobCancelledInsideItsStepEnds(
                helper,
                scheduler.server(),
                scheduler.budget("budget_samples_cancelling_on_server", 1_000, 1_000)
        );
    }
    
    @ServerTest
    public static void budgetOfAnotherModIsRefused(GameTestHelper helper) {
        NexusMod other = NexusMod.of(ModList.get().getModContainerById("nexus_gametest").orElseThrow());
        Budget budget = other.getScheduler().budget("budget_samples_foreign", 1, 1);
        Scheduler scheduler = Nexus.getMod().getScheduler();
        BudgetSamples.assertRefused(helper, scheduler.in(helper.getLevel()), budget);
        BudgetSamples.assertRefused(helper, scheduler.server(), budget);
        helper.succeed();
    }
    
    private static void jobFinishes(GameTestHelper helper, TickScope scope, Budget budget) {
        AtomicInteger steps = new AtomicInteger();
        Job job = scope.job(budget, () -> steps.incrementAndGet() < 3);
        helper.assertTrue(job.isActive(), "The job is inactive before its first step");
        helper.runAfterDelay(4L, () -> {
            helper.assertValueEqual(steps.get(), 3, "steps of the job after four passes");
            helper.assertTrue(job.isFinished(), "The job is not finished after its step returned false");
            helper.assertFalse(job.isActive(), "The job is still active after it finished");
            helper.succeed();
        });
    }
    
    private static void jobsOfOneBudgetTakeTurns(GameTestHelper helper, TickScope scope, Budget budget) {
        List<String> order = new ArrayList<>();
        Job first = scope.job(budget, () -> {
            order.add("first");
            return true;
        });
        Job second = scope.job(budget, () -> {
            order.add("second");
            return true;
        });
        helper.runAfterDelay(4L, () -> {
            first.cancel();
            second.cancel();
            helper.assertTrue(order.size() >= 4, "The jobs stepped fewer than four times in four passes");
            for (int i = 0; i < order.size(); i++) {
                helper.assertValueEqual(order.get(i), i % 2 == 0 ? "first" : "second", "job of step '" + i + "'");
            }
            
            helper.succeed();
        });
    }
    
    private static void everyBudgetStepsOnEveryPass(GameTestHelper helper, TickScope scope, List<Budget> budgets) {
        AtomicInteger pass = new AtomicInteger();
        Task counter = scope.every(1, pass::incrementAndGet);
        Map<String, Set<Integer>> passesByBudget = new HashMap<>();
        List<Job> jobs = new ArrayList<>();
        for (Budget budget : budgets) {
            Set<Integer> passes = new HashSet<>();
            passesByBudget.put(budget.getName(), passes);
            jobs.add(scope.job(budget, () -> {
                passes.add(pass.get());
                return true;
            }));
        }
        
        helper.runAfterDelay(4L, () -> {
            counter.cancel();
            jobs.forEach(Job::cancel);
            passesByBudget.forEach((name, passes) -> helper.assertValueEqual(passes, Set.of(1, 2, 3, 4), "passes the job of '" + name + "' stepped on"));
            helper.succeed();
        });
    }
    
    private static void jobCancelledInsideItsStepEnds(GameTestHelper helper, TickScope scope, Budget budget) {
        AtomicInteger steps = new AtomicInteger();
        AtomicReference<Job> self = new AtomicReference<>();
        self.set(scope.job(budget, () -> {
            if (steps.incrementAndGet() == 2) {
                self.get().cancel();
            }
            
            return true;
        }));
        helper.runAfterDelay(4L, () -> {
            helper.assertValueEqual(steps.get(), 2, "steps of the job after four passes");
            helper.assertFalse(self.get().isActive(), "The job is still active after it cancelled itself");
            helper.assertFalse(self.get().isFinished(), "The job is finished after it was cancelled");
            helper.succeed();
        });
    }
    
    private static void assertRefused(GameTestHelper helper, TickScope scope, Budget budget) {
        try {
            scope.job(budget, () -> false);
        } catch (IllegalArgumentException _) {
            return;
        }
        
        helper.fail("The job of another mod's budget was started");
    }
}
