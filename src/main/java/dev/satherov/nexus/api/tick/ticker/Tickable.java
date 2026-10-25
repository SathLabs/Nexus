package dev.satherov.nexus.api.tick.ticker;

import org.jetbrains.annotations.Range;

///
/// The tick of a ticker.
///
@FunctionalInterface
public interface Tickable {
    
    ///
    /// Runs one tick.
    ///
    /// @param elapsed The number of passes of the scope since the last tick call.
    ///
    /// @return The next tick.
    ///
    Next tick(@Range(from = 1, to = Integer.MAX_VALUE) int elapsed);
}
