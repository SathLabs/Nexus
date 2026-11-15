package dev.satherov.nexus.api.keybind.carry;

import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.keybind.BindingBuilder;
import dev.satherov.nexus.api.registry.client.ClientOnly;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.function.Supplier;

///
/// A stage of a key builder that holds eight carried values.
///
/// Can be started through [Carry7#carry(NexusCodec, ClientOnly)].
///
/// If [#onServer(KeyAction8)] is never called, the key will have no carried values.
///
/// @param <BUILDER> The type of the builder of the key.
/// @param <A>       The type of the first carried value.
/// @param <B>       The type of the second carried value.
/// @param <C>       The type of the third carried value.
/// @param <D>       The type of the fourth carried value.
/// @param <E>       The type of the fifth carried value.
/// @param <F>       The type of the sixth carried value.
/// @param <G>       The type of the seventh carried value.
/// @param <H>       The type of the eighth carried value.
///
@RequiredArgsConstructor(onConstructor_ = @ApiStatus.Internal)
public final class Carry8
        // @formatter:off
        <
            BUILDER extends BindingBuilder<BUILDER>,
            A,
            B,
            C,
            D,
            E,
            F,
            G,
            H
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
    /// Sets the server action over the carried values and returns to the builder.
    ///
    /// @param action The server action, called with the player whose key fired and the carried values.
    ///
    /// @return The builder of the key.
    ///
    /// @throws IllegalStateException If a server action is already set.
    ///
    public BUILDER onServer(KeyAction8<A, B, C, D, E, F, G, H> action) {
        //noinspection unchecked Each value was decoded by the codec of its own type.
        return this.builder.finishCarry(this.codecs, this.readers, (player, values) -> action.run(
                player,
                (A) values.get(0),
                (B) values.get(1),
                (C) values.get(2),
                (D) values.get(3),
                (E) values.get(4),
                (F) values.get(5),
                (G) values.get(6),
                (H) values.get(7)
        ));
    }
}
