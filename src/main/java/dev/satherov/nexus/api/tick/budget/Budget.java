package dev.satherov.nexus.api.tick.budget;

import lombok.Getter;

import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.TickScope;

import org.jetbrains.annotations.ApiStatus;

import java.time.Duration;
import java.util.function.BooleanSupplier;

///
/// A budget of time per pass that the jobs started with it share, counted on each scope separately.
///
/// The minimum is spent on every pass.
/// The rest up to the maximum is spent only while the server still has time in the tick.
/// A client scope spends the minimum only.
///
/// Can be created through [Scheduler#budget(String, Duration, Duration)].
///
/// @see TickScope#job(Budget, BooleanSupplier)
///
public final class Budget {

    ///
    /// The scheduler that created the budget.
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
    /// The time spent on every pass.
    ///
    @Getter
    private final Duration minimum;

    ///
    /// The time spent on a pass while the tick has time left.
    ///
    @Getter
    private final Duration maximum;

    ///
    /// Should only ever be called from [Scheduler#budget(String, Duration, Duration)].
    ///
    /// @param scheduler The scheduler that creates the budget.
    /// @param name      The name of the budget, without the mod id.
    /// @param minimum   The time spent on every pass.
    /// @param maximum   The time spent on a pass while the tick has time left.
    ///
    @ApiStatus.Internal
    public Budget(Scheduler scheduler, String name, Duration minimum, Duration maximum) {
        this.scheduler = scheduler;
        this.minimum = minimum;
        this.maximum = maximum;

        this.name = scheduler.getMod().getModId() + ":" + name;
    }
}
