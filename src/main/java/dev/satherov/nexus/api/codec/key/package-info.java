///
/// The keys of map codecs and the codecs of values stored under a key.
/// - [MapKey]: The type of every map key, with the string, identifier, UUID, `int`, and `long` keys as constants.
/// - [KeyedCodec]: The codec of one value under a key of a compound tag, a value input, or a value output.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.codec.key;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
