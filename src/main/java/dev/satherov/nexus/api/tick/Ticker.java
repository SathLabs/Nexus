package dev.satherov.nexus.api.tick;

import lombok.Getter;

import dev.satherov.nexus.api.tick.ticker.Next;
import dev.satherov.nexus.api.tick.ticker.Tickable;

///
/// A ticker that a scope ticks at the rate its own tick returns.
///
/// Can be started through [TickScope#ticker(String, Tickable)].
///
public final class Ticker {
    
    ///
    /// The scope that ticks the ticker.
    ///
    private final TickScope scope;
    
    ///
    /// The name that the profiler section, the statistics, and the crash report use.
    ///
    /// Will be `<modid>:<name>`, or the identifier of the block entity type if bound.
    ///
    @Getter
    private final String name;
    
    ///
    /// The tick of the ticker.
    ///
    private final Tickable tickable;
    
    ///
    /// The pass of the last tick, or the last pass before the start if it never ticked.
    ///
    private long lastTick;
    
    ///
    /// The pass the ticker ticks on next, or [Long#MAX_VALUE] while it sleeps.
    ///
    long due;
    
    ///
    /// `true` until the ticker was cancelled or dropped by its scope.
    ///
    @Getter
    private boolean active = true;
    
    ///
    /// Should only ever be called from [TickScope#ticker(String, Tickable)].
    ///
    /// @param scope    The scope that ticks the ticker.
    /// @param name     The name of the ticker.
    /// @param tickable The tick of the ticker.
    ///
    Ticker(TickScope scope, String name, Tickable tickable) {
        this.scope = scope;
        this.name = name;
        this.tickable = tickable;
        
        this.lastTick = scope.passes;
        this.due = scope.passes + 1;
    }
    
    ///
    /// Ticks the ticker on the next pass at the latest, whatever it waits for.
    /// If the ticker is still due on the current pass, it will tick on that pass.
    ///
    /// If called during its own tick, this wins over what that tick returns.
    ///
    /// Does nothing once the ticker is inactive.
    ///
    public void wake() {
        this.due = Math.min(this.due, this.scope.passes + 1);
    }
    
    ///
    /// Drops the ticker.
    ///
    /// If called from inside its own tick, the ticker will be dropped after that tick, ignoring what it returns.
    ///
    /// Does nothing once the ticker is inactive.
    ///
    public void cancel() {
        this.active = false;
    }
    
    ///
    /// Ticks the ticker on the current pass of its scope and then sets the pass of its next tick.
    ///
    void tick() {
        long pass = this.scope.passes;
        int elapsed = Math.clamp(pass - this.lastTick, 1, Integer.MAX_VALUE);
        this.lastTick = pass;
        
        // A wake during the tick sets the next pass, and no tick can return an earlier one.
        this.due = Long.MAX_VALUE;
        long requested = switch (this.tickable.tick(elapsed)) {
            case Next.In(int ticks) -> pass + ticks;
            case Next.Sleep() -> Long.MAX_VALUE;
        };
        this.due = Math.min(this.due, requested);
    }
}
