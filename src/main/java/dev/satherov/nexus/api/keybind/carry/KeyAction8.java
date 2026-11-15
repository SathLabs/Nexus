package dev.satherov.nexus.api.keybind.carry;

import net.minecraft.server.level.ServerPlayer;

///
/// A server action over eight carried values.
///
/// @param <A> The type of the first carried value.
/// @param <B> The type of the second carried value.
/// @param <C> The type of the third carried value.
/// @param <D> The type of the fourth carried value.
/// @param <E> The type of the fifth carried value.
/// @param <F> The type of the sixth carried value.
/// @param <G> The type of the seventh carried value.
/// @param <H> The type of the eighth carried value.
///
@FunctionalInterface
public interface KeyAction8<A, B, C, D, E, F, G, H> {
    
    ///
    /// Runs the action for the player whose key fired.
    ///
    /// @param player  The player whose key fired.
    /// @param first   The first carried value.
    /// @param second  The second carried value.
    /// @param third   The third carried value.
    /// @param fourth  The fourth carried value.
    /// @param fifth   The fifth carried value.
    /// @param sixth   The sixth carried value.
    /// @param seventh The seventh carried value.
    /// @param eighth  The eighth carried value.
    ///
    void run(ServerPlayer player, A first, B second, C third, D fourth, E fifth, F sixth, G seventh, H eighth);
}
