package dev.satherov.nexus.api.keybind.carry;

import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.keybind.BindingBuilder;
import dev.satherov.nexus.api.registry.client.ClientOnly;

import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

///
/// A stage of a key builder that holds one carried value.
///
/// Can be started through [BindingBuilder#carry(NexusCodec, ClientOnly)].
///
/// If [#onServer(BiConsumer)] is never called, the key will have no carried values.
///
/// @param <BUILDER> The type of the builder of the key.
/// @param <A>       The type of the carried value.
///
@RequiredArgsConstructor(onConstructor_ = @ApiStatus.Internal)
public final class Carry1<BUILDER extends BindingBuilder<BUILDER>, A> {
    
    ///
    /// The builder of the key.
    ///
    private final BUILDER builder;
    
    ///
    /// The codec of the carried value.
    ///
    private final NexusCodec<A, ?> codec;
    
    ///
    /// The reader of the carried value.
    ///
    private final ClientOnly<Supplier<@Nullable A>> reader;
    
    ///
    /// Adds one more carried value, read after the one before it.
    ///
    /// The reader runs on the client thread when the key fires, and only while the client is connected to a server that has the key.
    /// If it returns `null`, nothing will be sent to the server for that firing.
    /// If it returns a mutable value such as an item stack, then the value must be a copy or a later change to it may reach the server.
    ///
    /// @param codec  The codec of the value.
    /// @param reader The reader of the value.
    /// @param <B>    The type of the value.
    ///
    /// @return The stage of two carried values.
    ///
    public <B> Carry2<BUILDER, A, B> carry(NexusCodec<B, ?> codec, ClientOnly<Supplier<@Nullable B>> reader) {
        return new Carry2<>(this.builder, List.of(this.codec, codec), List.of(this.reader, reader));
    }
    
    ///
    /// Sets the server action over the carried values and returns to the builder.
    ///
    /// @param action The server action, called with the player whose key fired and the carried value.
    ///
    /// @return The builder of the key.
    ///
    /// @throws IllegalStateException If a server action is already set.
    ///
    public BUILDER onServer(BiConsumer<ServerPlayer, A> action) {
        //noinspection unchecked The value was decoded by the codec of its own type.
        return this.builder.finishCarry(List.of(this.codec), List.of(this.reader), (player, values) -> action.accept(player, (A) values.getFirst()));
    }
}
