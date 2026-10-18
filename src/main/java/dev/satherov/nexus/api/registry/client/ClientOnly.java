package dev.satherov.nexus.api.registry.client;

///
/// A supplier for client-only code.
///
/// Due to the way lambda expressions are created and loaded by the JVM,
/// we can't just have client-code inside one and just not call it on the server side.
///
/// Because of that we have this class to wrap everything inside another lambda itself, so any code, even another lambda expression
/// must be called through this as to not get loaded. Inside the supplier body any client code can freely be used,
/// even in classes that are also loaded on the server side.
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
