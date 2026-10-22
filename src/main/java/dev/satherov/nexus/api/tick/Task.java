package dev.satherov.nexus.api.tick;

import lombok.Getter;

///
/// A task that a scope runs once or repeatedly.
///
/// Can be scheduled through [TickScope#delay(int, Runnable)] and [TickScope#every(int, Runnable)].
///
public final class Task {
    
    ///
    /// The action that runs on every run of the task.
    ///
    final Runnable action;
    
    ///
    /// The number of passes between two runs, or `0` if the task runs once.
    ///
    private final int interval;
    
    ///
    /// The pass the task runs on next.
    ///
    long due;
    
    ///
    /// `true` until the task was cancelled, dropped by its scope, or ran as a once task.
    ///
    @Getter
    private boolean active = true;
    
    ///
    /// Should only ever be called from [TickScope].
    ///
    /// @param action   The action that runs on every run.
    /// @param interval The number of passes between two runs, or `0` if the task runs once.
    /// @param due      The pass of the first run.
    ///
    Task(Runnable action, int interval, long due) {
        this.action = action;
        this.interval = interval;
        this.due = due;
    }
    
    ///
    /// Stops the task before its next run.
    ///
    /// If called from inside its own run, this ends the repetition.
    ///
    /// Does nothing once the task is inactive.
    ///
    public void cancel() {
        this.active = false;
    }
    
    ///
    /// Runs the action and then moves the task to its next run, or deactivates it if it runs once.
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
