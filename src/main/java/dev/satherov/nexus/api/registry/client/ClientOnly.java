package dev.satherov.nexus.api.registry.client;

///
/// A supplier of client-only code.
///
/// The body may use client classes, in a class that the server loads too.
/// It should be a constructor reference, a method reference, or a plain `new`, and a body with statements should live in a client class of its own.
///
/// Should only ever be called on the client.
///
/// @param <VALUE> The type of the client code.
///
@FunctionalInterface
public interface ClientOnly<VALUE> {
    
    ///
    /// The client code.
    ///
    /// Never called on a dedicated server.
    ///
    /// @return The client code.
    ///
    VALUE get();
}
