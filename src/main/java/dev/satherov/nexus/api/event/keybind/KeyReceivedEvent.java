package dev.satherov.nexus.api.event.keybind;

import lombok.Getter;

import dev.satherov.nexus.api.keybind.Keybind;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.ApiStatus;

///
/// An event that is posted on the game bus on the server thread each time a firing of a key arrives, before the server action runs.
///
/// Cancelling the event skips the server action and leaves the held state of the key as it is.
///
public final class KeyReceivedEvent extends Event implements ICancellableEvent {

    ///
    /// The key that fired.
    ///
    @Getter
    private final Keybind keybind;

    ///
    /// The player who fired the key.
    ///
    @Getter
    private final ServerPlayer player;

    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param keybind The key that fired.
    /// @param player  The player who fired the key.
    ///
    @ApiStatus.Internal
    public KeyReceivedEvent(Keybind keybind, ServerPlayer player) {
        this.keybind = keybind;
        this.player = player;
    }
}
