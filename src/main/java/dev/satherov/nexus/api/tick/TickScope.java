package dev.satherov.nexus.api.tick;

import dev.satherov.nexus.api.tick.budget.Budget;
import dev.satherov.nexus.api.tick.ticker.Tickable;

import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

///
/// A tick loop that work runs in after vanilla's own tick, the loop of a level, of the server, or of the client.
///
/// A pass runs the due tasks, then the due tickers, and then the jobs of each budget.
/// Work submitted during a pass first runs on the next one.
/// A level scope passes only while the tick rate manager of its level runs normally.
///
/// A level scope drops its work when the level unloads, the server scope when the server stops, and the client scope on logout.
/// Every method throws an [IllegalStateException] if called off the scope's thread or after the scope ended.
///
public final class TickScope {
    
    ///
    /// The scheduler the scope belongs to.
    ///
    private final Scheduler scheduler;
    
    ///
    /// The name of the scope in crash reports, the identifier of its level's dimension or the name of its side.
    ///
    private final String name;

    ///
    /// The side the scope runs on.
    ///
    private final LogicalSide side;

    ///
    /// The thread the scope runs on.
    ///
    private final Thread thread;
    
    ///
    /// The tasks that did not run for the last time yet, in order, oldest first.
    ///
    private final List<Task> tasks = new ArrayList<>();
    
    ///
    /// The started tickers, in order, oldest first.
    ///
    private final List<Ticker> tickers = new ArrayList<>();

    ///
    /// The jobs that first step on the next pass, in order, oldest first.
    ///
    private final List<Job> pendingJobs = new ArrayList<>();

    ///
    /// The stepping jobs by their budget, in the order the budgets were first used on the scope.
    /// Each queue starts with the job that steps next.
    ///
    private final Map<Budget, Deque<Job>> jobsByBudget = new LinkedHashMap<>();

    ///
    /// How many passes have started so far.
    ///
    long passes;
    
    ///
    /// If the scope ended.
    ///
    private boolean ended;
    
    ///
    /// Should only ever be called from [Scheduler#in(Level)].
    ///
    /// @param scheduler The scheduler the scope belongs to.
    /// @param level     The level of the scope.
    ///
    @ApiStatus.Internal
    public TickScope(Scheduler scheduler, Level level) {
        this(scheduler, level.dimension().identifier().toString(), level.isClientSide() ? LogicalSide.CLIENT : LogicalSide.SERVER);
    }
    
    ///
    /// Should only ever be called from [Scheduler#server()] and `internal.tick.client`.
    ///
    /// @param scheduler The scheduler the scope belongs to.
    /// @param side      The side of the scope.
    ///
    @ApiStatus.Internal
    public TickScope(Scheduler scheduler, LogicalSide side) {
        this(scheduler, side.name().toLowerCase(Locale.ROOT), side);
    }
    
    ///
    /// Creates a scope that runs on the thread of the given side.
    ///
    private TickScope(Scheduler scheduler, String name, LogicalSide side) {
        this.scheduler = scheduler;
        this.name = name;
        this.side = side;

        this.thread = switch (side) {
            case SERVER -> Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer(), "No server is running").getRunningThread();
            case CLIENT -> throw new IllegalArgumentException("The client side has no tick scope");
        };
    }
    
    ///
    /// Runs one pass.
    ///
    /// Should only ever be called from the listeners of `internal.tick`.
    ///
    /// @param hasTime The time flag of the tick, read between job steps.
    ///
    /// @throws IllegalStateException If called off the scope's thread or after the scope ended.
    /// @throws ReportedException     If any work throws, with a crash report on the mod, the scope, the kind of work, and its name.
    ///
    @ApiStatus.Internal
    public void pass(BooleanSupplier hasTime) {
        this.checkUsable();
        ProfilerFiller profiler = Profiler.get();
        profiler.push(this.scheduler.getMod().getModId());
        this.passes++;
        // The work of this pass may start more jobs, which have to wait for the next one.
        this.queuePendingJobs();
        this.runTasks();
        this.tickTickers(profiler);
        this.stepJobs(profiler, hasTime);
        profiler.pop();
    }

    ///
    /// Moves every pending job to the end of its budget's queue.
    ///
    private void queuePendingJobs() {
        for (Job job : this.pendingJobs) {
            this.jobsByBudget.computeIfAbsent(job.budget, _ -> new ArrayDeque<>()).add(job);
        }

        this.pendingJobs.clear();
    }

    ///
    /// Runs every due task, in order, oldest first, and then forgets the inactive ones.
    ///
    private void runTasks() {
        // Indexed, because a task may schedule more tasks while the loop runs.
        for (int i = 0; i < this.tasks.size(); i++) {
            Task task = this.tasks.get(i);
            if (task.isActive() && task.due <= this.passes) {
                try {
                    task.run();
                } catch (Throwable throwable) {
                    throw this.crash(throwable, "task", task.action.getClass().getName());
                }
            }
        }
        
        this.tasks.removeIf(task -> !task.isActive());
    }
    
    ///
    /// Ticks every due ticker, in order, oldest first, each in a profiler section of its name, and then forgets the inactive ones.
    ///
    private void tickTickers(ProfilerFiller profiler) {
        // Indexed, because a ticker may start more tickers while the loop runs.
        for (int i = 0; i < this.tickers.size(); i++) {
            Ticker ticker = this.tickers.get(i);
            if (ticker.isActive() && ticker.due <= this.passes) {
                profiler.push(ticker.getName());
                try {
                    ticker.tick();
                } catch (Throwable throwable) {
                    throw this.crash(throwable, "ticker", ticker.getName());
                }
                
                profiler.pop();
            }
        }
        
        this.tickers.removeIf(ticker -> !ticker.isActive());
    }

    ///
    /// Steps the jobs of every budget in turn, each budget in a profiler section of its name, for as long as the budget allows.
    ///
    private void stepJobs(ProfilerFiller profiler, BooleanSupplier hasTime) {
        for (Map.Entry<Budget, Deque<Job>> entry : this.jobsByBudget.entrySet()) {
            Deque<Job> jobs = entry.getValue();
            jobs.removeIf(job -> !job.isActive());
            if (jobs.isEmpty()) {
                continue;
            }

            Budget budget = entry.getKey();
            long minimum = TimeUnit.NANOSECONDS.convert(budget.getMinimum());
            long maximum = this.side == LogicalSide.SERVER ? TimeUnit.NANOSECONDS.convert(budget.getMaximum()) : minimum;
            profiler.push(budget.getName());
            long start = Util.getNanos();
            long elapsed;
            do {
                Job job = jobs.remove();
                if (job.isActive()) {
                    try {
                        job.step();
                    } catch (Throwable throwable) {
                        throw this.crash(throwable, "job", budget.getName());
                    }

                    if (job.isActive()) {
                        jobs.add(job);
                    }
                }

                elapsed = Util.getNanos() - start;
            } while (!jobs.isEmpty() && (elapsed < minimum || (elapsed < maximum && hasTime.getAsBoolean())));

            profiler.pop();
        }
    }

    ///
    /// Wraps the given throwable into a crash report with a category on the mod, the scope, and the work that threw it.
    ///
    private ReportedException crash(Throwable throwable, String kind, String name) {
        CrashReport report = CrashReport.forThrowable(throwable, "Ticking " + kind);
        report.addCategory("Work being ticked")
                .setDetail("Mod", this.scheduler.getMod().getModId())
                .setDetail("Scope", this.name)
                .setDetail("Kind", kind)
                .setDetail("Name", name);
        return new ReportedException(report);
    }
    
    ///
    /// Drops every task, ticker, and job and refuses every later call.
    ///
    /// Should only ever be called from the listeners of `internal.tick`.
    ///
    /// @throws IllegalStateException If called off the scope's thread or after the scope ended.
    ///
    @ApiStatus.Internal
    public void end() {
        this.checkUsable();
        this.ended = true;
        this.tasks.forEach(Task::cancel);
        this.tickers.forEach(Ticker::cancel);
        this.pendingJobs.forEach(Job::cancel);
        this.jobsByBudget.values().forEach(jobs -> jobs.forEach(Job::cancel));
        this.tasks.clear();
        this.tickers.clear();
        this.pendingJobs.clear();
        this.jobsByBudget.clear();
    }
    
    ///
    /// Runs the given task once, the given number of passes from now.
    ///
    /// @param ticks The number of passes until the task runs.
    /// @param task  The task to run.
    ///
    /// @return The scheduled task.
    ///
    /// @throws IllegalArgumentException If the ticks are below 1.
    /// @throws IllegalStateException    If called off the scope's thread or after the scope ended.
    ///
    @CanIgnoreReturnValue
    public Task delay(@Range(from = 1, to = Integer.MAX_VALUE) int ticks, Runnable task) {
        return this.schedule(ticks, 0, task);
    }
    
    ///
    /// Runs the given task every given number of passes until cancelled, first that number of passes from now.
    ///
    /// @param ticks The number of passes between two runs, and until the first run.
    /// @param task  The task to run.
    ///
    /// @return The scheduled task.
    ///
    /// @throws IllegalArgumentException If the ticks are below 1.
    /// @throws IllegalStateException    If called off the scope's thread or after the scope ended.
    ///
    public Task every(@Range(from = 1, to = Integer.MAX_VALUE) int ticks, Runnable task) {
        return this.schedule(ticks, ticks, task);
    }
    
    ///
    /// Schedules the given action to run the given number of passes from now, and then every interval if that is not `0`.
    ///
    private Task schedule(int ticks, int interval, Runnable action) {
        this.checkUsable();
        if (ticks < 1) {
            throw new IllegalArgumentException("Tick count '" + ticks + "' is below 1");
        }
        
        Task task = new Task(action, interval, this.passes + ticks);
        this.tasks.add(task);
        return task;
    }
    
    ///
    /// Starts a new ticker under the name `<modid>:<name>`, awake and first ticked on the next pass.
    ///
    /// @param name     The name of the ticker, without the mod id.
    /// @param tickable The tick of the ticker.
    ///
    /// @return The started ticker.
    ///
    /// @throws IllegalStateException If called off the scope's thread or after the scope ended.
    ///
    public Ticker ticker(String name, Tickable tickable) {
        this.checkUsable();
        Ticker ticker = new Ticker(this, this.scheduler.getMod().getModId() + ":" + name, tickable);
        this.tickers.add(ticker);
        return ticker;
    }

    ///
    /// Starts a new job that steps in turn with the other jobs of the given budget, for as long as the budget allows on each pass.
    ///
    /// The job will first step on the next pass and will finish once its step returns `false`.
    ///
    /// @param budget The budget the job spends.
    /// @param step   The step of the job, which returns `false` once the job is finished.
    ///
    /// @return The started job.
    ///
    /// @throws IllegalArgumentException If the budget belongs to another mod.
    /// @throws IllegalStateException    If called off the scope's thread or after the scope ended.
    ///
    @CanIgnoreReturnValue
    public Job job(Budget budget, BooleanSupplier step) {
        this.checkUsable();
        if (budget.getScheduler() != this.scheduler) {
            throw new IllegalArgumentException("Budget '" + budget.getName() + "' does not belong to '" + this.scheduler.getMod().getModId() + "'");
        }

        Job job = new Job(budget, step);
        this.pendingJobs.add(job);
        return job;
    }

    ///
    /// Throws an [IllegalStateException] if called off the scope's thread or after the scope ended.
    ///
    private void checkUsable() {
        String modId = this.scheduler.getMod().getModId();
        if (this.ended) {
            throw new IllegalStateException("Tick scope '" + this.name + "' of '" + modId + "' has ended");
        }
        
        if (Thread.currentThread() != this.thread) {
            throw new IllegalStateException("Tick scope '" + this.name + "' of '" + modId + "' was used on thread '" + Thread.currentThread().getName() + "'");
        }
    }
}
