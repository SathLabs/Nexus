package dev.satherov.nexus.api.render.shape;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

///
/// The tool a shape draws itself with.
///
/// Width and depth come from the paint.
///
/// Every face is drawn from both sides.
///
/// Should only ever be used during [Shape#draw(Brush)].
///
public interface Brush {
    
    ///
    /// The paint of the shape being drawn.
    ///
    /// @return The paint of the shape.
    ///
    Paint getPaint();
    
    ///
    /// Draws a line from the given start to the given end.
    ///
    /// @param from The start of the line.
    /// @param to   The end of the line.
    /// @param argb The ARGB color of the line.
    ///
    void line(Vec3 from, Vec3 to, int argb);
    
    ///
    /// Draws a filled triangle with the given corners.
    ///
    /// @param a    The first corner.
    /// @param b    The second corner.
    /// @param c    The third corner.
    /// @param argb The ARGB color of the triangle.
    ///
    void triangle(Vec3 a, Vec3 b, Vec3 c, int argb);
    
    ///
    /// Draws the given quad filled.
    ///
    /// @param quad The quad to fill.
    /// @param argb The ARGB color of the quad.
    ///
    void quad(Quad quad, int argb);
    
    ///
    /// Draws the given sprite on the given quad.
    ///
    /// `u` runs along the first edge of the quad and `v` along its second.
    ///
    /// @param quad   The quad to draw the sprite on.
    /// @param sprite The sprite to draw.
    /// @param argb   The ARGB color to tint the sprite with.
    ///
    void quad(Quad quad, Sprite sprite, int argb);
    
    ///
    /// Draws the given text centered on the given point, facing the camera.
    ///
    /// The text is depth tested under [Paint.Depth#VISIBLE] only.
    ///
    /// @param at       The point to center the text on.
    /// @param text     The text to draw.
    /// @param scale    The scale of the text relative to name tag size.
    /// @param argb     The ARGB color of the text.
    /// @param backdrop The ARGB color of the backdrop, or `0` to draw no backdrop.
    ///
    void text(Vec3 at, Component text, float scale, int argb, int backdrop);
    
    ///
    /// Draws the given part with the given paint and then restores the current one.
    ///
    /// @param part  The shape to draw.
    /// @param paint The paint to draw the part with.
    ///
    void draw(Shape part, Paint paint);
}
