package dev.satherov.nexus.internal.codec.vanilla;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.internal.codec.Traversal;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// A DFU codec that encodes and decodes with a codec, created by [NexusCodec#asDfu()].
///
/// Fails with the message of a [NexusCodecException] and without a partial result.
///
/// @param codec The codec to encode and decode with.
/// @param <T>   The type of value the codec encodes and decodes.
///
@ApiStatus.Internal
public record DfuAdapter<T>(Traversal<T, ?> codec) implements Codec<T> {
    
    ///
    /// Decodes the input with the codec and returns the empty value of the ops as the rest of the input.
    ///
    @Override
    public <V> DataResult<Pair<T, V>> decode(DynamicOps<V> ops, @Nullable V input) {
        try {
            return DataResult.success(Pair.of(VanillaAdapters.decode(this.codec, ops, input), ops.empty()));
        } catch (NexusCodecException failure) {
            return DataResult.error(failure::getMessage);
        }
    }
    
    ///
    /// Encodes the value with the codec and fails if the prefix isn't the empty value of the ops.
    ///
    @Override
    public <V> DataResult<V> encode(T input, DynamicOps<V> ops, V prefix) {
        try {
            return ops.mergeToPrimitive(prefix, VanillaAdapters.toOps(ops, VanillaAdapters.encodeFor(this.codec, ops, input)));
        } catch (NexusCodecException failure) {
            return DataResult.error(failure::getMessage);
        }
    }
}
