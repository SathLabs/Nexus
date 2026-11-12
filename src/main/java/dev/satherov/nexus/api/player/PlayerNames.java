package dev.satherov.nexus.api.player;

import lombok.experimental.UtilityClass;

import net.neoforged.neoforge.common.UsernameCache;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;

import java.util.Optional;
import java.util.UUID;

///
/// Utility for looking up the names of players.
///
@UtilityClass
public class PlayerNames {

    ///
    /// Looks up the last known name of the given player.
    ///
    /// Checks [UsernameCache] first and then the name cache of the server.
    ///
    /// @param server The server that holds the name cache.
    /// @param player The UUID of the player.
    ///
    /// @return The UUID and the last known name of the player, or an empty optional if neither cache holds the player.
    ///
    public static Optional<NameAndId> find(MinecraftServer server, UUID player) {
        String name = UsernameCache.getLastKnownUsername(player);
        if (name != null) {
            return Optional.of(new NameAndId(player, name));
        }

        return server.services().nameToIdCache().get(player);
    }
}
