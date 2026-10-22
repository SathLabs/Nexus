///
/// The results and failures of encoding and decoding.
///
/// A failed [CodecResult] holds a [NexusCodecException].
/// The exception holds a [CodecError] for each value at fault.
/// - [NexusCodecException]: The failure that `encode` and `decode` throw.
/// - [CodecResult]: The value or the failure that `tryEncode` and `tryDecode` return.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.codec.result;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
