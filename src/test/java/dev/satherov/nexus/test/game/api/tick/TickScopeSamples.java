package dev.satherov.nexus.test.game.api.tick;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.tick.Task;
import dev.satherov.nexus.api.tick.TickScope;
import dev.satherov.nexus.api.tick.Ticker;
import dev.satherov.nexus.api.tick.ticker.Next;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

///
/// Checks the passes on which the tasks and tickers of a level scope and of the server scope run.
///
public class TickScopeSamples {

    @ServerTest
    public static void delayedTaskRunsOnItsPassInLevel(GameTestHelper helper) {
        TickScopeSamples.delayedTaskRunsOnItsPass(helper, Nexus.getMod().getScheduler().in(helper.getLevel()));
    }

    @ServerTest
    public static void delayedTaskRunsOnItsPassOnServer(GameTestHelper helper) {
        TickScopeSamples.delayedTaskRunsOnItsPass(helper, Nexus.getMod().getScheduler().server());
    }

    @ServerTest
    public static void repeatingTaskCountsUntilCancelledInLevel(GameTestHelper helper) {
        TickScopeSamples.repeatingTaskCountsUntilCancelled(helper, Nexus.getMod().getScheduler().in(helper.getLevel()));
    }

    @ServerTest
    public static void repeatingTaskCountsUntilCancelledOnServer(GameTestHelper helper) {
        TickScopeSamples.repeatingTaskCountsUntilCancelled(helper, Nexus.getMod().getScheduler().server());
    }

    @ServerTest
    public static void tickerSleepsAndWakesInLevel(GameTestHelper helper) {
        TickScopeSamples.tickerSleepsAndWakes(helper, Nexus.getMod().getScheduler().in(helper.getLevel()));
    }

    @ServerTest
    public static void tickerSleepsAndWakesOnServer(GameTestHelper helper) {
        TickScopeSamples.tickerSleepsAndWakes(helper, Nexus.getMod().getScheduler().server());
    }

    @ServerTest
    public static void wakeDuringOwnTickIsKeptInLevel(GameTestHelper helper) {
        TickScopeSamples.wakeDuringOwnTickIsKept(helper, Nexus.getMod().getScheduler().in(helper.getLevel()));
    }

    @ServerTest
    public static void wakeDuringOwnTickIsKeptOnServer(GameTestHelper helper) {
        TickScopeSamples.wakeDuringOwnTickIsKept(helper, Nexus.getMod().getScheduler().server());
    }

    @ServerTest
    public static void taskCancelledInsideItsRunEndsInLevel(GameTestHelper helper) {
        TickScopeSamples.taskCancelledInsideItsRunEnds(helper, Nexus.getMod().getScheduler().in(helper.getLevel()));
    }

    @ServerTest
    public static void taskCancelledInsideItsRunEndsOnServer(GameTestHelper helper) {
        TickScopeSamples.taskCancelledInsideItsRunEnds(helper, Nexus.getMod().getScheduler().server());
    }

    @ServerTest
    public static void tickerCancelledInsideItsTickEndsInLevel(GameTestHelper helper) {
        TickScopeSamples.tickerCancelledInsideItsTickEnds(helper, Nexus.getMod().getScheduler().in(helper.getLevel()));
    }

    @ServerTest
    public static void tickerCancelledInsideItsTickEndsOnServer(GameTestHelper helper) {
        TickScopeSamples.tickerCancelledInsideItsTickEnds(helper, Nexus.getMod().getScheduler().server());
    }

    @ServerTest
    public static void workSubmittedDuringPassWaitsInLevel(GameTestHelper helper) {
        TickScopeSamples.workSubmittedDuringPassWaits(helper, Nexus.getMod().getScheduler().in(helper.getLevel()));
    }

    @ServerTest
    public static void workSubmittedDuringPassWaitsOnServer(GameTestHelper helper) {
        TickScopeSamples.workSubmittedDuringPassWaits(helper, Nexus.getMod().getScheduler().server());
    }

    @ServerTest
    public static void serverScopeWorksWhenFirstCalledAfterStart(GameTestHelper helper) {
        TickScope scope = Nexus.getMod().getScheduler().server();
        AtomicInteger runs = new AtomicInteger();
        scope.delay(1, runs::incrementAndGet);
        helper.assertTrue(Nexus.getMod().getScheduler().server() == scope, "The second call returned another server scope");
        helper.runAfterDelay(1L, () -> {
            helper.assertValueEqual(runs.get(), 1, "runs of the task after one pass");
            helper.succeed();
        });
    }

    private static void delayedTaskRunsOnItsPass(GameTestHelper helper, TickScope scope) {
        AtomicInteger runs = new AtomicInteger();
        Task task = scope.delay(3, runs::incrementAndGet);
        helper.runAfterDelay(2L, () -> helper.assertValueEqual(runs.get(), 0, "runs of the task after two passes"));
        helper.runAfterDelay(3L, () -> helper.assertValueEqual(runs.get(), 1, "runs of the task after three passes"));
        helper.runAfterDelay(5L, () -> {
            helper.assertValueEqual(runs.get(), 1, "runs of the task after five passes");
            helper.assertFalse(task.isActive(), "The task is still active after it ran");
            helper.succeed();
        });
    }

    private static void repeatingTaskCountsUntilCancelled(GameTestHelper helper, TickScope scope) {
        AtomicInteger runs = new AtomicInteger();
        Task task = scope.every(2, runs::incrementAndGet);
        helper.runAfterDelay(1L, () -> helper.assertValueEqual(runs.get(), 0, "runs of the task after one pass"));
        helper.runAfterDelay(2L, () -> helper.assertValueEqual(runs.get(), 1, "runs of the task after two passes"));
        helper.runAfterDelay(6L, () -> {
            helper.assertValueEqual(runs.get(), 3, "runs of the task after six passes");
            helper.assertTrue(task.isActive(), "The task is inactive before it was cancelled");
            task.cancel();
        });
        helper.runAfterDelay(10L, () -> {
            helper.assertValueEqual(runs.get(), 3, "runs of the task after it was cancelled");
            helper.assertFalse(task.isActive(), "The task is still active after it was cancelled");
            helper.succeed();
        });
    }

    private static void tickerSleepsAndWakes(GameTestHelper helper, TickScope scope) {
        List<Integer> elapsed = new ArrayList<>();
        Ticker ticker = scope.ticker("tick_scope_samples_sleeper", passes -> {
            elapsed.add(passes);
            return Next.SLEEP;
        });

        helper.runAfterDelay(1L, () -> helper.assertValueEqual(elapsed, List.of(1), "elapsed passes of the ticks after one pass"));
        helper.runAfterDelay(3L, () -> {
            helper.assertValueEqual(elapsed, List.of(1), "elapsed passes of the ticks after three passes asleep");
            ticker.wake();
        });
        helper.runAfterDelay(4L, () -> helper.assertValueEqual(elapsed, List.of(1, 3), "elapsed passes of the ticks after the wake"));
        helper.runAfterDelay(6L, () -> {
            helper.assertValueEqual(elapsed, List.of(1, 3), "elapsed passes of the ticks after it slept again");
            ticker.cancel();
            helper.succeed();
        });
    }

    private static void wakeDuringOwnTickIsKept(GameTestHelper helper, TickScope scope) {
        AtomicInteger ticks = new AtomicInteger();
        AtomicReference<Ticker> self = new AtomicReference<>();
        self.set(scope.ticker("tick_scope_samples_self_waking", _ -> {
            if (ticks.incrementAndGet() == 1) {
                self.get().wake();
            }

            return Next.SLEEP;
        }));
        helper.runAfterDelay(1L, () -> helper.assertValueEqual(ticks.get(), 1, "ticks of the ticker after one pass"));
        helper.runAfterDelay(2L, () -> helper.assertValueEqual(ticks.get(), 2, "ticks of the ticker after two passes"));
        helper.runAfterDelay(4L, () -> {
            helper.assertValueEqual(ticks.get(), 2, "ticks of the ticker after four passes");
            self.get().cancel();
            helper.succeed();
        });
    }

    private static void taskCancelledInsideItsRunEnds(GameTestHelper helper, TickScope scope) {
        AtomicInteger runs = new AtomicInteger();
        AtomicReference<Task> self = new AtomicReference<>();
        self.set(scope.every(1, () -> {
            runs.incrementAndGet();
            self.get().cancel();
        }));
        helper.runAfterDelay(3L, () -> {
            helper.assertValueEqual(runs.get(), 1, "runs of the task after three passes");
            helper.assertFalse(self.get().isActive(), "The task is still active after it cancelled itself");
            helper.succeed();
        });
    }

    private static void tickerCancelledInsideItsTickEnds(GameTestHelper helper, TickScope scope) {
        AtomicInteger ticks = new AtomicInteger();
        AtomicReference<Ticker> self = new AtomicReference<>();
        self.set(scope.ticker("tick_scope_samples_self_cancelling", _ -> {
            ticks.incrementAndGet();
            self.get().cancel();
            return Next.TICK;
        }));
        helper.runAfterDelay(3L, () -> {
            helper.assertValueEqual(ticks.get(), 1, "ticks of the ticker after three passes");
            helper.assertFalse(self.get().isActive(), "The ticker is still active after it cancelled itself");
            helper.succeed();
        });
    }

    private static void workSubmittedDuringPassWaits(GameTestHelper helper, TickScope scope) {
        Set<String> ran = new HashSet<>();
        List<Ticker> started = new ArrayList<>();
        scope.delay(1, () -> {
            scope.delay(1, () -> ran.add("task submitted by a task"));
            started.add(scope.ticker("tick_scope_samples_started_by_task", _ -> {
                ran.add("ticker started by a task");
                return Next.SLEEP;
            }));
        });
        started.add(scope.ticker("tick_scope_samples_starter", _ -> {
            started.add(scope.ticker("tick_scope_samples_started_by_ticker", _ -> {
                ran.add("ticker started by a ticker");
                return Next.SLEEP;
            }));
            return Next.SLEEP;
        }));
        helper.runAfterDelay(1L, () -> helper.assertValueEqual(ran, Set.of(), "work run on the pass it was submitted in"));
        helper.runAfterDelay(2L, () -> {
            helper.assertValueEqual(ran, Set.of("task submitted by a task", "ticker started by a task", "ticker started by a ticker"), "work run on the next pass");
            started.forEach(Ticker::cancel);
            helper.succeed();
        });
    }
}
