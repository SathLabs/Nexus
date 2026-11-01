///
/// Shapes drawn into the level on the client.
///
/// A [Drawing] is one [Shape] drawn with one [Paint] in a [Scene].
/// A [Shape] draws its lines, faces, and text with a [Brush].
/// - [Scene]: The entry point for drawing shapes.
/// - [Shape]: The built-in geometry and the base of a custom one.
/// - [Paint]: The colors, line width, and depth test a shape is drawn with.
/// - [Drawing]: A drawn shape, which can be repainted, follow an entity, or expire.
/// - [Brush]: The tool a custom shape draws with.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.render.shape;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
