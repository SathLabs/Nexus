package dev.satherov.nexus.api.tick.budget;

import lombok.Getter;

import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.TickScope;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;

import java.util.function.BooleanSupplier;

///
/// Defines the time budget that a job is using to determine how much time it is allowed to occupy during a tick.
///
/// The minimum time is guaranteed to be spent every single time.
/// The maximum is the ceiling for how much more time is allowed as long as there is time left in the tick.
///
/// In a client scope only the minimum is ever used, as there is no targeted duration for one frame.
///
/// Can be created through [Scheduler#budget(String, int, int)].
///
/// @see TickScope#job(Budget, BooleanSupplier)
///
public final class Budget {
    
    ///
    /// The smallest time a budget spends per pass, in microseconds.
    ///
    public static final int SMALLEST = 1;
    
    ///
    /// The largest time a budget spends per pass, in microseconds.
    ///
    /// 50,000 microseconds is exactly the maximum amount of time a single tick is allowed to take before it starts to slow down the server.
    ///
    public static final int LARGEST = 50_000;
    
    ///
    /// The scheduler that created the budget.
    ///
    /// Only the scopes of this scheduler accept the budget.
    ///
    @Getter
    private final Scheduler scheduler;
    
    ///
    /// The name that the profiler section and the crash reports of the budget's jobs use.
    ///
    /// Will be `<modid>:<name>`.
    ///
    @Getter
    private final String name;
    
    ///
    /// The time spent on every pass, in microseconds.
    ///
    @Getter
    private final @Range(from = Budget.SMALLEST, to = Budget.LARGEST) int minimum;
    
    ///
    /// The time spent on a pass while the tick has time left, in microseconds.
    ///
    @Getter
    private final @Range(from = Budget.SMALLEST, to = Budget.LARGEST) int maximum;
    
    ///
    /// Should only ever be called from [Scheduler#budget(String, int, int)].
    ///
    /// @param scheduler The scheduler that creates the budget.
    /// @param name      The name of the budget, without the mod id.
    /// @param minimum   The time spent on every pass, in microseconds.
    /// @param maximum   The time spent on a pass while the tick has time left, in microseconds.
    ///
    @ApiStatus.Internal
    public Budget(Scheduler scheduler, String name, @Range(from = Budget.SMALLEST, to = Budget.LARGEST) int minimum, @Range(from = Budget.SMALLEST, to = Budget.LARGEST) int maximum) {
        this.scheduler = scheduler;
        this.minimum = minimum;
        this.maximum = maximum;
        
        this.name = scheduler.getMod().getModId() + ":" + name;
    }
}
