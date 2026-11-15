package dev.satherov.nexus.api.keybind.carry;

import net.minecraft.server.level.ServerPlayer;

///
/// A server action over five carried values.
///
/// @param <A> The type of the first carried value.
/// @param <B> The type of the second carried value.
/// @param <C> The type of the third carried value.
/// @param <D> The type of the fourth carried value.
/// @param <E> The type of the fifth carried value.
///
@FunctionalInterface
public interface KeyAction5<A, B, C, D, E> {
    
    ///
    /// Runs the action for the player whose key fired.
    ///
    /// @param player The player whose key fired.
    /// @param first  The first carried value.
    /// @param second The second carried value.
    /// @param third  The third carried value.
    /// @param fourth The fourth carried value.
    /// @param fifth  The fifth carried value.
    ///
    void run(ServerPlayer player, A first, B second, C third, D fourth, E fifth);
}
