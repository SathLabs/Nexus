package dev.satherov.nexus.api.tick;

import lombok.Getter;

///
/// One task that a scope can run, either after a specified delay or every x ticks.
///
/// Can be scheduled through [TickScope#delay(int, Runnable)] and [TickScope#every(int, Runnable)].
///
public final class Task {
    
    ///
    /// The action that runs when the task is executed.
    ///
    final Runnable action;
    
    ///
    /// The specified number of ticks between each run, or `0` if the task runs only once.
    ///
    private final int interval;
    
    ///
    /// The next tick that this task will run on.
    ///
    long due;
    
    ///
    /// `true` until the task was canceled, dropped by its scope, or was executed, if the task that only runs once.
    ///
    @Getter
    private boolean active = true;
    
    ///
    /// Should only ever be called from [TickScope].
    ///
    /// @param action   The action that runs on every run.
    /// @param interval The number of ticks between each run, or `0` if the task runs once.
    /// @param due      The next tick that this task will run on.
    ///
    Task(Runnable action, int interval, long due) {
        this.action = action;
        this.interval = interval;
        this.due = due;
    }
    
    ///
    /// Stops the task before its next run.
    ///
    /// If called from inside its own run, the task will not run again.
    ///
    /// Does nothing once the task is already inactive.
    ///
    public void cancel() {
        this.active = false;
    }
    
    ///
    /// Runs the action and then schedules it for the next due tick, or deactivates it if it only runs once.
    ///
    void run() {
        this.action.run();
        if (this.interval == 0) {
            this.active = false;
        } else {
            this.due += this.interval;
        }
    }
}
