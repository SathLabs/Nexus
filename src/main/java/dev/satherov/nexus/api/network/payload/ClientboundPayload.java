package dev.satherov.nexus.api.network.payload;

import dev.satherov.nexus.api.network.Recipients;

import net.minecraft.resources.Identifier;

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
    /// If the channel of the payload is optional, every recipient that doesn't have it will be skipped.
    ///
    /// @param value      The value to send.
    /// @param recipients The recipients to deliver the value to.
    ///
    void send(VALUE value, Recipients recipients);
}
