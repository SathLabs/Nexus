///
/// The builder and the entry of potions.
/// - [PotionBuilder]: The builder, started through [Registrations#potion(String, String, Supplier...)].
/// - [PotionEntry]: The entry that the builder registers.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.registry.potion;

import dev.satherov.nexus.api.registry.Registrations;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;

import java.util.function.Supplier;
