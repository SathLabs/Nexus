///
/// The builder and the entry of mob effects.
/// - [EffectBuilder]: The builder, started through [Registrations#effect(String, String, Supplier)].
/// - [EffectEntry]: The entry that the builder registers.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.registry.effect;

import dev.satherov.nexus.api.registry.Registrations;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;
