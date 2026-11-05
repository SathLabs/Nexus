///
/// Filters over the data components of a holder, and the paths to the values inside a component.
///
/// A [ComponentPath] creates the comparison of its value against a [Literal] with an [Operator].
/// The comparison is one case of a [ComponentFilter].
///
/// - [ComponentFilter]: The filter tree, with a factory per case.
/// - [ComponentPath]: The path to a value inside a component.
/// - [Operator]: The comparison of a value against a literal.
/// - [Literal]: The number, string, or boolean a value is compared against.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.component;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
