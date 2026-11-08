///
/// Paths into the values of data components and the comparisons at their end.
/// - [ComponentPath]: The path to the compared part of a component value.
/// - [Comparison]: The operator and the literal that the part at a path is compared against.
/// - [PathReader]: The reader of one path, registered through [RegisterPathReadersEvent].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.component;

import dev.satherov.nexus.api.event.component.RegisterPathReadersEvent;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
