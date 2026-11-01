package dev.satherov.nexus.api.render.outline;

import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.api.render.shape.Shape;

///
/// What a rule draws for the targeted block.
///
/// @param shape The shape, in coordinates relative to the position of the block.
/// @param paint The paint the shape is drawn with.
///
public record Outline(Shape shape, Paint paint) { }
