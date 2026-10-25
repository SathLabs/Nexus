package dev.satherov.nexus.api.render.outline;

import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.api.render.shape.Shape;

///
/// What a rule draws for a targeted block.
///
/// @param shape The shape, in coordinates relative to the block's position.
/// @param paint The paint it is drawn with.
///
public record Outline(Shape shape, Paint paint) { }
