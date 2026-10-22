///
/// The tick of a ticker and the value it returns.
/// - [Tickable]: The function a ticker runs on every tick.
/// - [EntityTickable]: The function the ticker of a block entity runs on every tick, declared on the block entity builder.
/// - [Next]: When the ticker ticks next, returned from every tick.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.tick.ticker;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
