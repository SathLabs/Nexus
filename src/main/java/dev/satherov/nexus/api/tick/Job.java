package dev.satherov.nexus.api.tick;

import lombok.Getter;

import dev.satherov.nexus.api.tick.budget.Budget;

import java.util.function.BooleanSupplier;

///
/// A job that a scope can progress together with other jobs of the same budget.
///
/// Can be started through [TickScope#job(Budget, BooleanSupplier)].
///
public final class Job {
    
    ///
    /// The budget the job has available to spend.
    ///
    final Budget budget;
    
    ///
    /// The step of the job.
    ///
    /// Returns `false` once the job is finished.
    ///
    private final BooleanSupplier step;
    
    ///
    /// `true` until a step returns `false`, the job was canceled, or its scope dropped it.
    ///
    @Getter
    private boolean active = true;
    
    ///
    /// `true` once a step returned `false`.
    ///
    /// Stays `false` if the job was canceled or dropped by its scope.
    ///
    @Getter
    private boolean finished;
    
    ///
    /// Should only ever be called from [TickScope#job(Budget, BooleanSupplier)].
    ///
    /// @param budget The budget the job spends.
    /// @param step   The step of the job, which returns `false` once the job is finished.
    ///
    Job(Budget budget, BooleanSupplier step) {
        this.budget = budget;
        this.step = step;
    }
    
    ///
    /// Stops the job before its next step.
    ///
    /// If called from inside its own step, the job will be dropped after that step finishes, regardless of the return value.
    ///
    /// Does nothing once the job is inactive.
    ///
    public void cancel() {
        this.active = false;
    }
    
    ///
    /// Executes one step of the job and ends if the step returned `false` while the job is still active.
    ///
    void step() {
        if (!this.step.getAsBoolean() && this.active) {
            this.active = false;
            this.finished = true;
        }
    }
}
