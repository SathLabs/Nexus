///
/// The builders and the entries of blocks.
///
/// A block with an item is built by a [BlockBuilder] and held by a [BlockEntry].
/// - [PlainBlockBuilder]: The builder, started through [Registrations#block(String, String, Function)].
/// - [PlainBlockEntry]: The entry that the builder registers.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.registry.block;

import dev.satherov.nexus.api.registry.Registrations;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;

import java.util.function.Function;
