package dev.satherov.nexus.api.codec.format;

import dev.satherov.nexus.api.codec.Access;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.jetbrains.annotations.ApiStatus;

///
/// A format whose values are the buffer it was built on.
///
/// Encoding appends to the buffer and decoding reads from it, so decoding needs no input.
///
/// @param <V> The type of the buffer.
/// @param <A> The access of this format, which is [Access.Registries] on a [RegistryFriendlyByteBuf].
///
@ApiStatus.NonExtendable
public interface BufferFormat<V extends FriendlyByteBuf, A extends Access.Plain> extends CodecFormat<V, A> {
    
    ///
    /// The buffer this format reads from and appends to.
    ///
    /// @return The buffer of this format.
    ///
    V buffer();
}
