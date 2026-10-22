///
/// Tasks, tickers, and jobs that a mod runs after vanilla's own tick.
/// - [Scheduler]: The tick scopes and budgets of one mod, reached through [NexusMod#getScheduler()].
/// - [TickScope]: One tick loop, with the tasks, tickers, and jobs it runs.
/// - [Task]: A task scheduled once or repeating.
/// - [Ticker]: A ticker that ticks at the rate its own tick returns.
/// - [Job]: A job that steps in turn with the other jobs of its budget until it finishes.
///
/// The tick of a ticker and the value it returns are in [dev.satherov.nexus.api.tick.ticker].
/// The budget that jobs share is in [dev.satherov.nexus.api.tick.budget].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.tick;

import dev.satherov.nexus.api.mod.NexusMod;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
