package dev.satherov.nexus.api.render.shape;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

///
/// What a shape draws with.
/// Width and depth come from the paint, and every face is drawn from both sides.
///
/// Only valid during [Shape#draw(Brush)].
///
public interface Brush {
    
    ///
    /// The paint of the shape being drawn.
    ///
    /// @return The current paint.
    ///
    Paint getPaint();
    
    ///
    /// Draws a line between the given points.
    ///
    /// @param from The start of the line.
    /// @param to   The end of the line.
    /// @param argb The ARGB color of the line.
    ///
    void line(Vec3 from, Vec3 to, int argb);
    
    ///
    /// Draws a filled triangle between the given corners.
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
    /// Draws the given quad showing the given sprite, tinted by the color, with `u` along the quad's first edge and `v` along its second.
    ///
    /// @param quad   The quad to show the sprite on.
    /// @param sprite The sprite to show.
    /// @param argb   The ARGB color to tint the sprite with.
    ///
    void quad(Quad quad, Sprite sprite, int argb);
    
    ///
    /// Draws the given text facing the camera, centered on the given point and scaled from name tag size,
    /// over a backdrop in the second color or none for `0`.
    ///
    /// The text is drawn through the font each frame and passes the depth test under [Paint.Depth#VISIBLE] only.
    ///
    /// @param at       The point to center the text on.
    /// @param text     The text to draw.
    /// @param scale    The scale of the text, where `1` is name tag size.
    /// @param argb     The ARGB color of the text.
    /// @param backdrop The ARGB color of the backdrop, or `0` for none.
    ///
    void text(Vec3 at, Component text, float scale, int argb, int backdrop);
    
    ///
    /// Draws the given part with the given paint in place of the current one, colors, width, and depth alike,
    /// and then restores the current one.
    ///
    /// @param part  The shape to draw.
    /// @param paint The paint to draw the part with.
    ///
    void draw(Shape part, Paint paint);
}
