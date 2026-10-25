package dev.satherov.nexus.api.tick;

import dev.satherov.nexus.api.tick.budget.Budget;
import dev.satherov.nexus.api.tick.ticker.Tickable;
import dev.satherov.nexus.internal.tick.Bindings;
import dev.satherov.nexus.internal.tick.Ticks;
import dev.satherov.nexus.internal.tick.client.ClientTicks;

import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;

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
import java.util.function.Consumer;

///
/// A ticking loop that executes work right after vanilla's own tick ends, either in one level, the entire server, or the client.
///
/// Every pass runs all scheduled tasks, then all tickers, and finally all jobs with their respective budget.
///
/// - A server scope runs while the server is running and drops its work when the server stops.
/// - A level scope only runs while the tick manager of its level runs normally and drops its work when the level unloads.
/// - A client scope only runs while the game is not paused and drops its work when the client logs out.
///
///  Every method throws an [IllegalStateException] if it's called from any other thread other than the scope's own, or after the scope has ended.
///
public final class TickScope {
    
    ///
    /// The scheduler that this scope belongs to.
    ///
    private final Scheduler scheduler;
    
    ///
    /// The name of this scope in crash reports, either the identifier of the level's dimension or the name of its side.
    ///
    private final String name;
    
    ///
    /// The level of this scope, or `null` if it is the server or the client scope.
    ///
    private final @Nullable Level level;
    
    ///
    /// The logical side that this scope runs on.
    ///
    private final LogicalSide side;
    
    ///
    /// The event loop whose thread this scope runs in.
    ///
    private final BlockableEventLoop<?> loop;
    
    ///
    /// All tasks that have not yet finished running
    ///
    private final List<Task> tasks = new ArrayList<>();
    
    ///
    /// All tickers that were started so far.
    ///
    private final List<Ticker> tickers = new ArrayList<>();
    
    ///
    /// All jobs that will run for the first time during the next pass.
    ///
    private final List<Job> pendingJobs = new ArrayList<>();
    
    ///
    /// All jobs that are currently running, by their budget.
    ///
    /// Each queue starts with the job that runs next.
    ///
    private final Map<Budget, Deque<Job>> jobsByBudget = new LinkedHashMap<>();
    
    ///
    /// The number of passes that have been executed so far.
    ///
    long passes;
    
    ///
    /// If this scope has ended.
    ///
    private boolean ended;
    
    ///
    /// Should only ever be called from [Scheduler#in(Level)].
    ///
    /// @param scheduler The scheduler that this scope belongs to.
    /// @param level     The level of this scope.
    ///
    @ApiStatus.Internal
    public TickScope(Scheduler scheduler, Level level) {
        this(scheduler, level.dimension().identifier().toString(), level, level.isClientSide() ? LogicalSide.CLIENT : LogicalSide.SERVER);
    }
    
    ///
    /// Should only ever be called from [Scheduler#server()] and [ClientTicks#getScope()].
    ///
    /// @param scheduler The scheduler that this scope belongs to.
    /// @param side      The logical side of this scope.
    ///
    @ApiStatus.Internal
    public TickScope(Scheduler scheduler, LogicalSide side) {
        this(scheduler, side.name().toLowerCase(Locale.ROOT), null, side);
    }
    
    ///
    /// Creates a new scope that runs on the thread of the given side.
    ///
    private TickScope(Scheduler scheduler, String name, @Nullable Level level, LogicalSide side) {
        this.scheduler = scheduler;
        this.name = name;
        this.level = level;
        this.side = side;
        
        this.loop = switch (side) {
            case SERVER -> Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer(), "No server is running");
            case CLIENT -> ClientTicks.getEventLoop();
        };
    }
    
    ///
    /// Runs a single pass.
    ///
    /// Should only ever be called from [Ticks#onLevelTick(LevelTickEvent.Post)], [Ticks#onServerTick(ServerTickEvent.Post)] or [ClientTicks#onClientTick(ClientTickEvent.Post)]
    ///
    /// @param hasTime The time flag of the current tick, which is checked between the steps of jobs.
    ///
    /// @throws IllegalStateException If called from another thread than the scope's own, or after the scope has ended.
    /// @throws ReportedException     If any work throws. The crash report will contain the mod, the scope, the kind of work,
    ///                               and either its name or the block entity it is bound to.
    ///
    @ApiStatus.Internal
    public void pass(BooleanSupplier hasTime) {
        this.checkUsable();
        ProfilerFiller profiler = Profiler.get();
        profiler.push(this.scheduler.getMod().getModId());
        this.passes++;
        // Since any task to be run during this pass may queue more jobs themselves,
        // we drain the existing queue first and force new ones to wait for the next pass.
        this.queuePendingJobs();
        this.runTasks();
        this.tickTickers(profiler);
        this.stepJobs(profiler, hasTime);
        profiler.pop();
    }
    
    ///
    /// Moves every pending job to the end of the queue.
    ///
    private void queuePendingJobs() {
        for (Job job : this.pendingJobs) {
            this.jobsByBudget.computeIfAbsent(job.budget, _ -> new ArrayDeque<>()).add(job);
        }
        
        this.pendingJobs.clear();
    }
    
    ///
    /// Runs every task that is due, in order, oldest first, and then forgets all the inactive ones.
    ///
    private void runTasks() {
        //noinspection ForLoopReplaceableByForEach Indexed, since a task may schedule more tasks while we're still looping.
        for (int i = 0; i < this.tasks.size(); i++) {
            Task task = this.tasks.get(i);
            if (task.isActive() && task.due <= this.passes) {
                try {
                    task.run();
                } catch (Throwable throwable) {
                    throw this.crash(throwable, "task", category -> category.setDetail("Name", task.action.getClass().getName()));
                }
            }
        }
        
        this.tasks.removeIf(task -> !task.isActive());
    }
    
    ///
    /// Ticks every ticker that is due, in order, oldest first, each inside a profiler section of its name, and then forgets all the inactive ones.
    ///
    private void tickTickers(ProfilerFiller profiler) {
        //noinspection ForLoopReplaceableByForEach Indexed, since a ticker may start more tickers while we're still looping.
        for (int i = 0; i < this.tickers.size(); i++) {
            Ticker ticker = this.tickers.get(i);
            if (ticker.isActive() && ticker.due <= this.passes) {
                profiler.push(ticker.getName());
                try {
                    ticker.tick();
                } catch (Throwable throwable) {
                    throw this.crash(throwable, "ticker", ticker::fillCrashReportCategory);
                }
                
                profiler.pop();
            }
        }
        
        this.tickers.removeIf(ticker -> !ticker.isActive());
    }
    
    ///
    /// Runs the jobs of every budget, for as long as the budget allows it.
    ///
    /// Each budget is run inside a profiler section of its name.
    ///
    private void stepJobs(ProfilerFiller profiler, BooleanSupplier hasTime) {
        for (Map.Entry<Budget, Deque<Job>> entry : this.jobsByBudget.entrySet()) {
            Deque<Job> jobs = entry.getValue();
            jobs.removeIf(job -> !job.isActive());
            if (jobs.isEmpty()) {
                continue;
            }
            
            Budget budget = entry.getKey();
            long minimum = TimeUnit.MICROSECONDS.toNanos(budget.getMinimum());
            long maximum = this.side == LogicalSide.SERVER ? TimeUnit.MICROSECONDS.toNanos(budget.getMaximum()) : minimum;
            
            profiler.push(budget.getName());
            long start = Util.getNanos();
            long elapsed;
            do {
                Job job = jobs.remove();
                if (job.isActive()) {
                    try {
                        job.step();
                    } catch (Throwable throwable) {
                        throw this.crash(throwable, "job", category -> category.setDetail("Name", budget.getName()));
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
    /// Wraps the given crash into a crash report with a category for the mod, scope, and kind of work.
    ///
    /// Any additional details provided will be appended to the report.
    ///
    private ReportedException crash(Throwable throwable, String kind, Consumer<CrashReportCategory> details) {
        CrashReport report = CrashReport.forThrowable(throwable, "Ticking " + kind);
        CrashReportCategory category = report.addCategory("Work being ticked")
                .setDetail("Mod", this.scheduler.getMod().getModId())
                .setDetail("Scope", this.name)
                .setDetail("Kind", kind);
        
        details.accept(category);
        return new ReportedException(report);
    }
    
    ///
    /// Drops every task, ticker, and job of this scope and refuses every call after that.
    ///
    /// Should only ever be called from [Ticks#onLevelTick(LevelTickEvent.Post)], [Ticks#onServerTick(ServerTickEvent.Post)] or [ClientTicks#onClientTick(ClientTickEvent.Post)]
    ///
    /// @throws IllegalStateException If called from another thread than the scope's own, or after the scope has ended.
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
    /// Runs the given task once, after the given number of ticks.
    ///
    /// @param ticks The number of ticks to wait until the task runs.
    /// @param task  The task to run.
    ///
    /// @return The scheduled task.
    ///
    /// @throws IllegalArgumentException If the ticks are below `1`.
    /// @throws IllegalStateException    If called from another thread than the scope's own, or after the scope has ended.
    ///
    @CanIgnoreReturnValue
    public Task delay(@Range(from = 1, to = Integer.MAX_VALUE) int ticks, Runnable task) {
        return this.schedule(ticks, 0, task);
    }
    
    ///
    /// Runs the given task every given number of ticks until it is canceled, the first time after that many ticks from now.
    ///
    /// @param ticks The number of ticks between two runs, and how long to wait until the first execution.
    /// @param task  The task to run.
    ///
    /// @return The scheduled task.
    ///
    /// @throws IllegalArgumentException If the ticks are below `1`.
    /// @throws IllegalStateException    If called from another thread than the scope's own, or after the scope has ended.
    ///
    public Task every(@Range(from = 1, to = Integer.MAX_VALUE) int ticks, Runnable task) {
        return this.schedule(ticks, ticks, task);
    }
    
    ///
    /// Schedules the given action to run after the given number of ticks, and then again every interval if it isn't `0`.
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
    /// Starts a new ticker under the name `<modid>:<name>`.
    /// The ticker starts awake and will first tick on the next pass.
    ///
    /// @param name     The name of the ticker, without the mod id.
    /// @param tickable The tick function of the ticker.
    ///
    /// @return The started ticker.
    ///
    /// @throws IllegalStateException If called from another thread than the scope's own, or after the scope has ended.
    ///
    public Ticker ticker(String name, Tickable tickable) {
        this.checkUsable();
        Ticker ticker = new Ticker(this, this.scheduler.getMod().getModId() + ":" + name, null, tickable);
        this.tickers.add(ticker);
        return ticker;
    }
    
    ///
    /// Starts a new ticker bound to the given block entity, under the identifier of its block entity type.
    /// The ticker starts awake and will first tick on the next pass.
    ///
    /// If the level doesn't tick blocks at the position of the block entity, a due tick is skipped and retried after the interval returned by the last tick.
    /// The ticker will be dropped once the block entity is removed or its chunk unloads.
    ///
    /// Can be woken up through [Scheduler#wake(BlockEntity)].
    ///
    /// @param entity   The block entity to bind the ticker to.
    /// @param tickable The tick function of the ticker.
    ///
    /// @return The started ticker.
    ///
    /// @throws IllegalStateException
    /// - If called from another thread than the scope's own.
    /// - If the scope has already ended.
    /// - If the block entity isn't in the level of this scope.
    /// - If the block entity was already removed.
    /// - If this mod already bound a ticker to the block entity.
    ///
    @CanIgnoreReturnValue
    public Ticker ticker(BlockEntity entity, Tickable tickable) {
        this.checkUsable();
        String name = entity.typeHolder().getRegisteredName();
        if (this.level == null || entity.getLevel() != this.level) {
            throw new IllegalStateException("Block entity '" + name + "' at '" + entity.getBlockPos().toShortString() + "' is not in tick scope '" + this.name + "'");
        }
        
        if (entity.isRemoved()) {
            throw new IllegalStateException("Block entity '" + name + "' at '" + entity.getBlockPos().toShortString() + "' was removed");
        }
        
        if (Bindings.find(entity, this.scheduler) != null) {
            throw new IllegalStateException("Block entity '" + name + "' at '" + entity.getBlockPos().toShortString() + "' already has a ticker of '" + this.scheduler.getMod().getModId() + "'");
        }
        
        Ticker ticker = new Ticker(this, name, entity, tickable);
        this.tickers.add(ticker);
        Bindings.add(entity, this.scheduler, ticker);
        return ticker;
    }
    
    ///
    /// Starts a new job for the given budget that runs together with all other jobs of the same budget as long as there's time left.
    ///
    /// The job will take its first step on the next pass and finishes once its step returns `false`.
    ///
    /// @param budget The budget the job has available to spend.
    /// @param step   The step of the job, which returns `false` once the job is finished.
    ///
    /// @return The started job.
    ///
    /// @throws IllegalArgumentException If the budget belongs to another mod.
    /// @throws IllegalStateException    If called from another thread than the scope's own, or after the scope has ended.
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
    /// Throws an [IllegalStateException] if called from another thread than the scope's own, or after the scope has ended.
    ///
    private void checkUsable() {
        String modId = this.scheduler.getMod().getModId();
        if (this.ended) {
            throw new IllegalStateException("Tick scope '" + this.name + "' of '" + modId + "' has ended");
        }
        
        if (!this.loop.isSameThread()) {
            throw new IllegalStateException("Tick scope '" + this.name + "' of '" + modId + "' was used on thread '" + Thread.currentThread().getName() + "'");
        }
    }
}
