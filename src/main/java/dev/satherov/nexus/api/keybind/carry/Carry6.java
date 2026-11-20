package dev.satherov.nexus.api.keybind.carry;

import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.keybind.BindingBuilder;
import dev.satherov.nexus.api.registry.client.ClientOnly;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

///
/// A stage of a key builder that holds six carried values.
///
/// Can be started through [Carry5#carry(NexusCodec, ClientOnly)].
///
/// If [#onServer(KeyAction6)] is never called, the key will have no carried values.
///
/// @param <BUILDER> The type of the builder of the key.
/// @param <A>       The type of the first carried value.
/// @param <B>       The type of the second carried value.
/// @param <C>       The type of the third carried value.
/// @param <D>       The type of the fourth carried value.
/// @param <E>       The type of the fifth carried value.
/// @param <F>       The type of the sixth carried value.
///
@RequiredArgsConstructor(onConstructor_ = @ApiStatus.Internal)
public final class Carry6
        // @formatter:off
        <
            BUILDER extends BindingBuilder<BUILDER>,
            A,
            B,
            C,
            D,
            E,
            F
        >
        // @formatter:on
{
    
    ///
    /// The builder of the key.
    ///
    private final BUILDER builder;
    
    ///
    /// The codecs of the carried values.
    ///
    private final List<NexusCodec<?, ?>> codecs;
    
    ///
    /// The readers of the carried values.
    ///
    private final List<ClientOnly<? extends Supplier<?>>> readers;
    
    ///
    /// Adds one more carried value, read after the ones before it.
    ///
    /// The reader runs on the client thread when the key fires, and only while the client is connected to a server that has the key.
    /// If it returns `null`, nothing will be sent to the server for that firing.
    /// If it returns a mutable value such as an item stack, then the value must be a copy or a later change to it may reach the server.
    ///
    /// @param codec  The codec of the value.
    /// @param reader The reader of the value.
    /// @param <G>    The type of the value.
    ///
    /// @return The stage of seven carried values.
    ///
    public <G> Carry7<BUILDER, A, B, C, D, E, F, G> carry(NexusCodec<G, ?> codec, ClientOnly<Supplier<@Nullable G>> reader) {
        return new Carry7<>(
                this.builder,
                Stream.concat(this.codecs.stream(), Stream.of(codec)).toList(),
                Stream.concat(this.readers.stream(), Stream.of(reader)).toList()
        );
    }
    
    ///
    /// Sets the server action over the carried values and returns to the builder.
    ///
    /// @param action The server action, called with the player whose key fired and the carried values.
    ///
    /// @return The builder of the key.
    ///
    /// @throws IllegalStateException If a server action is already set.
    ///
    public BUILDER onServer(KeyAction6<A, B, C, D, E, F> action) {
        //noinspection unchecked Each value was decoded by the codec of its own type.
        return this.builder.finishCarry(this.codecs, this.readers, (player, values) -> action.run(
                player,
                (A) values.get(0),
                (B) values.get(1),
                (C) values.get(2),
                (D) values.get(3),
                (E) values.get(4),
                (F) values.get(5)
        ));
    }
}
