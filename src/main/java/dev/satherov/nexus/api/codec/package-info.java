///
/// Codecs that encode values into JSON, NBT, and network buffers and decode them back.
/// - [NexusCodec]: The codec of one type, with the codec constants and factories as its static members.
/// - [Access]: The last type argument of every codec and format.
///
/// The formats are in [dev.satherov.nexus.api.codec.format].
/// The results and failures of encoding and decoding are in [dev.satherov.nexus.api.codec.result].
/// The struct codecs and their fields are in [dev.satherov.nexus.api.codec.struct].
/// The keyed codecs and the keys of map codecs are in [dev.satherov.nexus.api.codec.key].
/// The codecs of vanilla and neoforge types are in [dev.satherov.nexus.api.codec.vanilla].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.codec;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
