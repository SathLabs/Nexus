package dev.satherov.nexus.internal.keybind;

import dev.satherov.zelqro.mapping.ObjectMapping;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

///
/// The server tick each key of one mod went down on, per player.
///
/// A player's keys are removed once the player logs out, and every key once the server stopped.
///
@ApiStatus.Internal
public final class HeldKeys {
    
    ///
    /// The server tick each key that is down went down on, by the player's UUID and then the key's mapping name.
    ///
    private final Map<UUID, Map<String, Integer>> downTicksByPlayer = new HashMap<>();
    
    ///
    /// Creates the held keys of one mod and listens on the game bus for players logging out and the server stopping.
    ///
    public HeldKeys() {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, this::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, this::onServerStopped);
    }
    
    ///
    /// Marks the key of the given mapping name as down for the given player from the current server tick on.
    ///
    /// Does nothing if the key is already down.
    ///
    /// @param player The player whose key went down.
    /// @param name   The mapping name of the key.
    ///
    public void press(ServerPlayer player, String name) {
        this.downTicksByPlayer.computeIfAbsent(player.getUUID(), _ -> new HashMap<>()).putIfAbsent(name, player.level().getServer().getTickCount());
    }
    
    ///
    /// Marks the key of the given mapping name as up for the given player.
    ///
    /// @param player The player whose key went up.
    /// @param name   The mapping name of the key.
    ///
    public void release(ServerPlayer player, String name) {
        Map<String, Integer> downTicks = this.downTicksByPlayer.get(player.getUUID());
        if (downTicks != null) {
            downTicks.remove(name);
        }
    }
    
    ///
    /// The number of server ticks since the given player's key went down, `1` on the tick it went down and `0` while it is up.
    ///
    /// @param player The player whose key is read.
    /// @param name   The mapping name of the key.
    ///
    /// @return The number of server ticks the key has been down.
    ///
    public int getHeldTicks(ServerPlayer player, String name) {
        Integer downTick = this.downTicksByPlayer.getOrDefault(player.getUUID(), Map.of()).get(name);
        return ObjectMapping.mapToIntOrElse(downTick, tick -> player.level().getServer().getTickCount() - tick + 1, 0);
    }
    
    ///
    /// Removes every key of the player that logged out.
    ///
    /// @param event The event of the player logging out.
    ///
    private void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        this.downTicksByPlayer.remove(event.getEntity().getUUID());
    }
    
    ///
    /// Removes every key, since an integrated server stops without logging its players out.
    ///
    /// @param event The event of the stopped server.
    ///
    private void onServerStopped(ServerStoppedEvent event) {
        this.downTicksByPlayer.clear();
    }
}
