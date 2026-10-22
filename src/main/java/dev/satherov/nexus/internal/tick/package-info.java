///
/// The game bus listeners that pass and end the tick scopes of each scheduler, and the tickers of block entities.
/// - [Ticks]: The listeners and scopes of one scheduler.
/// - [EntityTickables]: The tickables declared for each block entity type.
/// - [Bindings]: The tickers bound to block entities, across all mods.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.internal.tick;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
