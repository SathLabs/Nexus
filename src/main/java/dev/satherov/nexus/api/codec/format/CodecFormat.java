package dev.satherov.nexus.api.codec.format;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.internal.codec.format.JsonOperations;
import dev.satherov.nexus.internal.codec.format.NbtOperations;
import dev.satherov.nexus.internal.codec.format.NettyOperations;

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
/// @param <A> The access of this format, either [Access.Plain] or [Access.Registries] if it was built with registry access.
///
@ApiStatus.NonExtendable
public interface CodecFormat<V, A extends Access.Plain> {
    
    ///
    /// The JSON format, built on gson's [JsonElement].
    ///
    CodecFormat<JsonElement, Access.Plain> JSON = new JsonOperations<>(false, null);
    
    ///
    /// The NBT format, built on vanilla's [Tag].
    ///
    CodecFormat<Tag, Access.Plain> NBT = new NbtOperations<>(false, null);
    
    ///
    /// Creates the netty format over the given byte buffer.
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
    /// Creates the netty format over the given buffer, with the registry access of the buffer.
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
    /// @return e.g. `JSON`, `NBT`, or `netty`.
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
