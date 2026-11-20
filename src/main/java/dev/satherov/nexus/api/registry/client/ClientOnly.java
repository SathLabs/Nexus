package dev.satherov.nexus.api.registry.client;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.function.Supplier;

///
/// A supplier of client-only code.
///
/// The body may use client classes, in a class that the server loads too.
/// It should be a constructor reference, a method reference, or a plain `new`, and a body with statements should live in a client class of its own.
///
/// A lambda body may pass a client value on only as an `Object`, or as a JDK or Nexus interface such as a [Supplier] or a [ScreenFactory].
/// If the body passes the value on as any other type, such as a [LocalPlayer] as a [Player] or a provider as a [ParticleProvider], the verifier of the class that holds the lambda will load a client class to check it,
/// and a dedicated server will crash when that class loads.
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
