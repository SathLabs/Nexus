package dev.satherov.nexus.api.codec;

import dev.satherov.nexus.internal.codec.JsonOperations;
import dev.satherov.nexus.internal.codec.NbtOperations;
import dev.satherov.nexus.internal.codec.NettyOperations;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

import com.google.gson.JsonElement;

import org.jetbrains.annotations.ApiStatus;

///
/// A format that codecs encode values into and decode values from.
///
/// @param <V> The type of the values of this format.
/// @param <A> The access of this format, which is [Access.Registries] if it was built with registries.
///
@ApiStatus.NonExtendable
public interface CodecFormat<V, A extends Access.Plain> {

    ///
    /// The json format, over gson's [JsonElement].
    ///
    CodecFormat<JsonElement, Access.Plain> JSON = new JsonOperations<>(false, null);

    ///
    /// The NBT format, over [Tag].
    ///
    CodecFormat<Tag, Access.Plain> NBT = new NbtOperations<>(false, null);

    ///
    /// Creates the netty format over the given buffer.
    ///
    /// Encoding appends to the buffer and decoding reads from it.
    ///
    /// @param buffer The buffer to read from and append to.
    ///
    /// @return The netty format over the buffer.
    ///
    /// @see #netty(RegistryFriendlyByteBuf)
    ///
    static BufferFormat<FriendlyByteBuf, Access.Plain> netty(FriendlyByteBuf buffer) {
        return new NettyOperations<>(buffer, null);
    }

    ///
    /// Creates the netty format over the given buffer, with the registries of the buffer.
    ///
    /// Encoding appends to the buffer and decoding reads from it.
    ///
    /// @param buffer The buffer to read from and append to.
    ///
    /// @return The netty format over the buffer.
    ///
    /// @see #netty(FriendlyByteBuf)
    ///
    static BufferFormat<RegistryFriendlyByteBuf, Access.Registries> netty(RegistryFriendlyByteBuf buffer) {
        return new NettyOperations<>(buffer, buffer.registryAccess());
    }

    ///
    /// The name of this format, used in failure messages.
    ///
    /// @return `JSON`, `NBT`, or `netty`.
    ///
    String name();

    ///
    /// Creates a copy of this format that refuses unknown keys when decoding.
    ///
    /// If this is a [BufferFormat], it will be returned as is.
    ///
    /// @return The strict copy of this format.
    ///
    CodecFormat<V, A> strict();

    ///
    /// Creates a copy of this format with the given registries.
    ///
    /// @param registries The registry lookup provider, such as a [RegistryAccess].
    ///
    /// @return The copy of this format with the registries.
    ///
    /// @throws UnsupportedOperationException If this is a [BufferFormat].
    /// @see #netty(RegistryFriendlyByteBuf)
    ///
    CodecFormat<V, Access.Registries> withRegistries(HolderLookup.Provider registries);
}
