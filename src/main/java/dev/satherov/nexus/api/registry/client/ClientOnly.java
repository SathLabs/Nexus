package dev.satherov.nexus.api.registry.client;

///
/// Client code behind a supplier, written `() -> CrusherScreen::new`.
///
/// The body runs on the client only, so the classes it names load there only.
/// The type of the client code should erase to a class that both sides have, such as a JDK functional interface or one
/// in this package.
/// The body may then name client classes in a constructor reference, a method reference, or a plain `new`, since they
/// resolve only once [#get()] runs on the client.
///
/// A body with statements goes into a separate client class and is called as `() -> OilClient.extensions()`.
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
