package dev.satherov.nexus.api.keybind.carry;

import net.minecraft.server.level.ServerPlayer;

///
/// A server action over two carried values.
///
/// @param <A> The type of the first carried value.
/// @param <B> The type of the second carried value.
///
@FunctionalInterface
public interface KeyAction2<A, B> {
    
    ///
    /// Runs the action for the player whose key fired.
    ///
    /// @param player The player whose key fired.
    /// @param first  The first carried value.
    /// @param second The second carried value.
    ///
    void run(ServerPlayer player, A first, B second);
}
