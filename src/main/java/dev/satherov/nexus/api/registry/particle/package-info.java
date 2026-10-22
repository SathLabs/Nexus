///
/// The builder and the entry of particle types.
/// - [ParticleBuilder]: The builder, started through [Registrations#particle(String, Supplier)].
/// - [ParticleEntry]: The entry that the builder registers.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.registry.particle;

import dev.satherov.nexus.api.registry.Registrations;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;
