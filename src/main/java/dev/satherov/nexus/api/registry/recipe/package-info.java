///
/// The builder and the entry of recipe types.
/// - [RecipeTypeBuilder]: The builder, started through [Registrations#recipe(String, StructCodec)].
/// - [RecipeTypeEntry]: The entry that the builder registers.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.registry.recipe;

import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.registry.Registrations;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
