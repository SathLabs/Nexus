package dev.satherov.nexus.api.event.keybind;

import lombok.Getter;

import dev.satherov.nexus.api.keybind.Keybind;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import org.jetbrains.annotations.ApiStatus;

///
/// An event that is posted on the game bus on the client each time a key fires, before its client action runs and before its firing is sent to the server.
///
/// Cancelling the event skips the client action, the readers of the carried values and the send for this firing.
///
/// If the key syncs its held state, its going down and up will still be sent while the event is cancelled.
///
public final class KeyActivatedEvent extends Event implements ICancellableEvent {

    ///
    /// The key that fired.
    ///
    @Getter
    private final Keybind keybind;

    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param keybind The key that fired.
    ///
    @ApiStatus.Internal
    public KeyActivatedEvent(Keybind keybind) {
        this.keybind = keybind;
    }
}
