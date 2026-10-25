///
/// The tick of a ticker and the value it returns.
/// - [Tickable]: The function a ticker runs on every tick.
/// - [EntityTickable]: The function the ticker of a block entity declares through the block entity builder.
/// - [Next]: When the ticker will tick next, specified in the return value of the tick function.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.tick.ticker;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
