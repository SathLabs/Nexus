///
/// Geometry drawn into the level with a paint.
///
/// A [Shape] emits its lines, faces, and text into a [Brush], which holds the [Paint] of the drawing.
/// - [Shape]: The built-in geometry and the base of a custom one.
/// - [Paint]: The colors, line width, and depth mode of a drawing.
/// - [Quad]: A face of a shape, with the edges a sprite follows.
/// - [Sprite]: A texture of an atlas that a face shows.
/// - [Brush]: What a custom shape draws with.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.render.shape;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
