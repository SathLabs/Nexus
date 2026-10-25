package dev.satherov.nexus.api.render.shape;

import lombok.With;

///
/// How a shape is colored and where it passes the depth test.
/// A color of `0` switches that part off.
///
/// @param stroke    The ARGB color of edges and text, or `0` for none.
/// @param lineWidth The width of edges in pixels, above `0`.
/// @param fill      The ARGB color of faces and backdrops, or `0` for none.
/// @param depth     Where the shape passes the depth test.
///
@With
public record Paint(int stroke, float lineWidth, int fill, Depth depth) {
    
    ///
    /// Vanilla's line width, `2.5` pixels.
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
    /// Creates a paint of edges only, at the default width, depth tested.
    ///
    /// @param argb The ARGB color of the edges.
    ///
    /// @return The paint of the edges.
    ///
    public static Paint stroke(int argb) {
        return new Paint(argb, Paint.DEFAULT_LINE_WIDTH, 0, Depth.VISIBLE);
    }
    
    ///
    /// Creates a paint of faces only, depth tested.
    ///
    /// @param argb The ARGB color of the faces.
    ///
    /// @return The paint of the faces.
    ///
    public static Paint fill(int argb) {
        return new Paint(0, Paint.DEFAULT_LINE_WIDTH, argb, Depth.VISIBLE);
    }
    
    ///
    /// Where a shape passes the depth test.
    ///
    public enum Depth {
        ///
        /// Only where nothing in the level is in front of it.
        ///
        VISIBLE,
        
        ///
        /// Only where something in the level is in front of it.
        ///
        HIDDEN,
        
        ///
        /// Everywhere.
        ///
        ALWAYS
    }
}
