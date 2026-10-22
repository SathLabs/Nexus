///
/// The builder and the entry of entity attributes.
/// - [AttributeBuilder]: The builder, started through [Registrations#attribute(String, String, Function)].
/// - [AttributeEntry]: The entry that the builder registers.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.registry.attribute;

import dev.satherov.nexus.api.registry.Registrations;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;

import java.util.function.Function;
