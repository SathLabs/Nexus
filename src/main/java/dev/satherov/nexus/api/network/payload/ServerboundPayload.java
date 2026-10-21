package dev.satherov.nexus.api.network.payload;

import dev.satherov.nexus.api.network.context.ClientHandlerContext;

import net.minecraft.resources.Identifier;

///
/// A payload that a client sends to the server.
///
/// @param <VALUE> The type of the values that the payload sends.
///
public interface ServerboundPayload<VALUE> {
    
    ///
    /// The identifier the payload is registered under.
    ///
    /// @return The identifier of the payload.
    ///
    Identifier getId();
    
    ///
    /// Wraps the given value and sends it over the game connection of this client.
    ///
    /// @param value The value to send.
    ///
    /// @throws IllegalStateException If this is a dedicated server or the client is not in play.
    ///
    void sendToServer(VALUE value);
    
    ///
    /// If the server that this client is connected to has registered the payload for the play protocol.
    ///
    /// Will always be `true` for a required payload.
    ///
    /// @return `true` if the server registered the payload.
    ///
    /// @throws IllegalStateException If this is a dedicated server or the client is not in play.
    ///
    boolean canSendToServer();
    
    ///
    /// Wraps the given value and sends it back over the connection that a client handler received on, in play or configuration.
    ///
    /// @param value   The value to send.
    /// @param context The context of the client handler.
    ///
    /// @throws IllegalStateException If this is a dedicated server.
    ///
    void reply(VALUE value, ClientHandlerContext context);
}
