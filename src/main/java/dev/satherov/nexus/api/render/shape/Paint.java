package dev.satherov.nexus.api.render.shape;

import lombok.With;

///
/// The style that a shape is drawn with.
///
/// @param stroke    The ARGB color of lines and text, or `0` to draw neither.
/// @param lineWidth The width of lines in pixels.
/// @param fill      The ARGB color of faces and backdrops, or `0` to draw neither.
/// @param depth     The depth test the shape is drawn with.
///
@With
public record Paint(int stroke, float lineWidth, int fill, Depth depth) {
    
    ///
    /// The line width vanilla draws its block outline with, which it scales up on windows wider than 1920 pixels.
    ///
    public static final float DEFAULT_LINE_WIDTH = 2.5F;
    
    ///
    /// Creates a new paint.
    ///
    /// @throws IllegalArgumentException If the line width is not above `0`.
    ///
    public Paint {
        if (!(lineWidth > 0.0F)) {
            throw new IllegalArgumentException("Line width '" + lineWidth + "' is not above 0");
        }
    }
    
    ///
    /// Creates a new paint with the given stroke and no fill.
    ///
    /// The line width will be {@value #DEFAULT_LINE_WIDTH} and the depth test [Depth#VISIBLE].
    ///
    /// @param argb The ARGB color of the stroke.
    ///
    /// @return The paint.
    ///
    public static Paint stroke(int argb) {
        return new Paint(argb, Paint.DEFAULT_LINE_WIDTH, 0, Depth.VISIBLE);
    }
    
    ///
    /// Creates a new paint with the given fill and no stroke.
    ///
    /// The line width will be {@value #DEFAULT_LINE_WIDTH} and the depth test [Depth#VISIBLE].
    ///
    /// @param argb The ARGB color of the fill.
    ///
    /// @return The paint.
    ///
    public static Paint fill(int argb) {
        return new Paint(0, Paint.DEFAULT_LINE_WIDTH, argb, Depth.VISIBLE);
    }
    
    ///
    /// The depth test that a shape may be drawn with.
    ///
    public enum Depth {
        ///
        /// The shape is drawn only where nothing in the level is in front of it.
        ///
        VISIBLE,
        
        ///
        /// The shape is drawn only where something in the level is in front of it.
        ///
        HIDDEN,
        
        ///
        /// The shape is drawn everywhere.
        ///
        ALWAYS
    }
}
