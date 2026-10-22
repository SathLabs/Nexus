///
/// The builders and the entries of fluids.
///
/// A fluid with a block and a bucket is built by a [FluidBuilder] and held by a [FluidEntry].
/// - [PlainFluidBuilder]: The builder, started through [Registrations#fluid(String, String)].
/// - [PlainFluidEntry]: The entry that the builder registers.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.registry.fluid;

import dev.satherov.nexus.api.registry.Registrations;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
