///
/// Tasks, tickers, and jobs that a mod runs after vanilla's own tick.
/// - [Scheduler]: The tick scopes and budgets of one mod, accessed through [NexusMod#getScheduler()].
/// - [TickScope]: One tick loop, with the tasks, tickers, and jobs it runs.
/// - [Task]: A task that a scope can run.
/// - [Ticker]: A ticker ticking at the specified rate of its return method or the one of the block entity it is bound to.
/// - [Job]: A job that a scope can progress together with other jobs of the same budget.
///
/// For the tickable and its return value of a block entity see [dev.satherov.nexus.api.tick.ticker].
///
/// For budgets of the jobs see [dev.satherov.nexus.api.tick.budget].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.tick;

import dev.satherov.nexus.api.mod.NexusMod;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
