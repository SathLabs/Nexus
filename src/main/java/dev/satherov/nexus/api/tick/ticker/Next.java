package dev.satherov.nexus.api.tick.ticker;

import dev.satherov.nexus.api.tick.Ticker;

import org.jetbrains.annotations.Range;

///
/// When to run the next tick of a [Ticker].
///
/// The return value of every [EntityTickable#tick]
///
public sealed interface Next {
    
    ///
    /// Will be called again in the next tick.
    ///
    Next TICK = new In(1);
    
    ///
    /// Will never tick again until woken.
    ///
    Next SLEEP = new Sleep();
    
    ///
    /// Sleeps for the given number of ticks before running the next tick.
    ///
    /// @param ticks The number of passes until the tick.
    ///
    /// @return The next tick.
    ///
    /// @throws IllegalArgumentException If the ticks are below 1.
    ///
    static Next in(@Range(from = 1, to = Integer.MAX_VALUE) int ticks) {
        //noinspection ConstantValue Yes intelliJ, technically this should never happen, but we know devs are stupid.
        if (ticks < 1) {
            throw new IllegalArgumentException("Tick count '" + ticks + "' is below 1");
        }
        
        return new In(ticks);
    }
    
    ///
    /// A tick in a fixed number of passes from now.
    ///
    /// @param ticks The passes until the tick, at least 1.
    ///
    record In(int ticks) implements Next { }
    
    ///
    /// No tick until woken.
    ///
    record Sleep() implements Next { }
}
