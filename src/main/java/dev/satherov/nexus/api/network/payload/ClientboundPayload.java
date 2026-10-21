package dev.satherov.nexus.api.network.payload;

import dev.satherov.nexus.api.network.ClientRecipients;

import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

///
/// A payload that the server sends to clients.
///
/// @param <VALUE> The type of the values that the payload sends.
///
public interface ClientboundPayload<VALUE> {
    
    ///
    /// The identifier the payload is registered under.
    ///
    /// @return The identifier of the payload.
    ///
    Identifier getId();
    
    ///
    /// Wraps the given value and delivers it to the given recipients on the calling thread.
    ///
    /// If the payload is optional, every recipient that doesn't have it will be skipped.
    ///
    /// @param value      The value to send.
    /// @param recipients The recipients to deliver the value to.
    ///
    void send(VALUE value, ClientRecipients recipients);
    
    ///
    /// If the client of the given player registered the payload for the play protocol.
    ///
    /// Will always be `true` for a required payload.
    ///
    /// @param player The player to check the client of.
    ///
    /// @return `true` if the client of the player registered the payload for the play protocol.
    ///
    boolean canSend(ServerPlayer player);
    
    ///
    /// If the other side of the given listener registered the payload for the current protocol of the connection.
    ///
    /// Will always be `true` for a required payload.
    ///
    /// @param listener The listener of the connection to check.
    ///
    /// @return `true` if the other side of the listener registered the payload.
    ///
    boolean canSend(ICommonPacketListener listener);
}
