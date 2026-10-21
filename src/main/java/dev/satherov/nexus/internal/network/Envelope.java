package dev.satherov.nexus.internal.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import org.jetbrains.annotations.ApiStatus;

///
/// A mod payload that holds one encoded value and the identifier of its payload type.
///
/// @param type    The type of the payload, built from the identifier of its handle.
/// @param value   The value of the mod.
/// @param <VALUE> The type of the value.
///
@ApiStatus.Internal
public record Envelope<VALUE>(CustomPacketPayload.Type<Envelope<VALUE>> type, VALUE value) implements CustomPacketPayload {
    
    ///
    /// Creates the stream codec for envelopes of the given type using the stream codec of their values.
    ///
    /// @param type  The type of the envelopes.
    /// @param codec The stream codec of the values.
    ///
    /// @return The stream codec of the envelopes.
    ///
    public static <BUFFER extends FriendlyByteBuf, VALUE> StreamCodec<BUFFER, Envelope<VALUE>> codec(
            CustomPacketPayload.Type<Envelope<VALUE>> type,
            StreamCodec<? super BUFFER, VALUE> codec
    ) {
        return StreamCodec.composite(codec, Envelope::value, value -> new Envelope<>(type, value));
    }
}
