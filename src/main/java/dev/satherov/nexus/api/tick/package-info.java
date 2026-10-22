///
/// Tasks and tickers that a mod runs after vanilla's own tick.
/// - [Scheduler]: The tick scopes of one mod, reached through [NexusMod#getScheduler()].
/// - [TickScope]: One tick loop, with the tasks and tickers it runs.
/// - [Task]: A task scheduled once or repeating.
/// - [Ticker]: A ticker that ticks at the rate its own tick returns.
///
/// The tick of a ticker and the value it returns are in [dev.satherov.nexus.api.tick.ticker].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.tick;

import dev.satherov.nexus.api.mod.NexusMod;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
