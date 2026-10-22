package dev.satherov.nexus.api.tick.ticker;

import org.jetbrains.annotations.Range;

///
/// The next tick of a ticker, returned from every tick.
///
public sealed interface Next {
    
    ///
    /// A tick on the next pass.
    ///
    Next TICK = new In(1);
    
    ///
    /// No tick until woken.
    ///
    Next SLEEP = new Sleep();
    
    ///
    /// A tick on the pass the given number of passes from now.
    ///
    /// @param ticks The number of passes until the tick.
    ///
    /// @return The next tick.
    ///
    /// @throws IllegalArgumentException If the ticks are below 1.
    ///
    static Next in(@Range(from = 1, to = Integer.MAX_VALUE) int ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("Tick count '" + ticks + "' is below 1");
        }
        
        return new In(ticks);
    }
    
    ///
    /// A tick a fixed number of passes from now.
    ///
    /// @param ticks The passes until the tick, at least 1.
    ///
    record In(int ticks) implements Next { }
    
    ///
    /// No tick until woken.
    ///
    record Sleep() implements Next { }
}
