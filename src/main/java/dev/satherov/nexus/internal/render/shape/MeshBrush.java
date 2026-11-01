package dev.satherov.nexus.internal.render.shape;

import dev.satherov.nexus.api.render.shape.Brush;
import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.api.render.shape.Quad;
import dev.satherov.nexus.api.render.shape.Shape;
import dev.satherov.nexus.api.render.shape.Sprite;
import dev.satherov.nexus.internal.render.client.ShapePipelines;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

///
/// A brush that records the lines, quads, and labels of a shape into [Primitives].
///
@ApiStatus.Internal
public final class MeshBrush implements Brush {
    
    ///
    /// The vertices recorded so far, by render type.
    ///
    private final Map<RenderType, List<Primitives.Vertex>> verticesByType = new LinkedHashMap<>();
    
    ///
    /// The labels recorded so far.
    ///
    private final List<Primitives.Label> labels = new ArrayList<>();
    
    ///
    /// The paint of the painted part being drawn, or the paint of the whole shape outside one.
    ///
    private Paint paint;
    
    ///
    /// Creates a new brush with the given paint as the current one.
    ///
    /// @param paint The paint of the whole shape.
    ///
    public MeshBrush(Paint paint) {
        this.paint = paint;
    }
    
    ///
    /// The current paint.
    ///
    /// @return The current paint.
    ///
    @Override
    public Paint getPaint() {
        return this.paint;
    }
    
    ///
    /// Records the line in the line render type of the current depth mode, at the current line width.
    ///
    /// @param from The start of the line.
    /// @param to   The end of the line.
    /// @param argb The ARGB color of the line.
    ///
    @Override
    public void line(Vec3 from, Vec3 to, int argb) {
        Vec3 normal = to.subtract(from).normalize();
        List<Primitives.Vertex> vertices = this.vertices(ShapePipelines.lines(this.paint.depth()));
        vertices.add(new Primitives.Vertex(from, argb, 0.0F, 0.0F, normal, this.paint.lineWidth()));
        vertices.add(new Primitives.Vertex(to, argb, 0.0F, 0.0F, normal, this.paint.lineWidth()));
    }
    
    ///
    /// Records the triangle as a quad that repeats the third corner, in the fill render type of the current depth mode.
    ///
    /// @param a    The first corner.
    /// @param b    The second corner.
    /// @param c    The third corner.
    /// @param argb The ARGB color of the triangle.
    ///
    @Override
    public void triangle(Vec3 a, Vec3 b, Vec3 c, int argb) {
        this.fill(argb, a, b, c, c);
    }
    
    ///
    /// Records the quad in the fill render type of the current depth mode.
    ///
    /// @param quad The quad to fill.
    /// @param argb The ARGB color of the quad.
    ///
    @Override
    public void quad(Quad quad, int argb) {
        Vec3 cornerU = quad.origin().add(quad.edgeU());
        this.fill(argb, quad.origin(), cornerU, cornerU.add(quad.edgeV()), quad.origin().add(quad.edgeV()));
    }
    
    ///
    /// Records the quad between the given corners in the fill render type of the current depth mode.
    ///
    /// @param argb   The ARGB color of the quad.
    /// @param first  The first corner.
    /// @param second The second corner.
    /// @param third  The third corner.
    /// @param fourth The fourth corner.
    ///
    private void fill(int argb, Vec3 first, Vec3 second, Vec3 third, Vec3 fourth) {
        List<Primitives.Vertex> vertices = this.vertices(ShapePipelines.fill(this.paint.depth()));
        vertices.add(new Primitives.Vertex(first, argb, 0.0F, 0.0F, Vec3.ZERO, 0.0F));
        vertices.add(new Primitives.Vertex(second, argb, 0.0F, 0.0F, Vec3.ZERO, 0.0F));
        vertices.add(new Primitives.Vertex(third, argb, 0.0F, 0.0F, Vec3.ZERO, 0.0F));
        vertices.add(new Primitives.Vertex(fourth, argb, 0.0F, 0.0F, Vec3.ZERO, 0.0F));
    }
    
    ///
    /// Records the quad in the sprite render type of the sprite's atlas and the current depth mode.
    ///
    /// The texture coordinates of the sprite are looked up while recording.
    /// If the texture of the atlas is not a [TextureAtlas], the whole texture will be shown.
    ///
    /// @param quad   The quad to show the sprite on.
    /// @param sprite The sprite to show.
    /// @param argb   The ARGB color to tint the sprite with.
    ///
    @Override
    public void quad(Quad quad, Sprite sprite, int argb) {
        float u0 = 0.0F;
        float v0 = 0.0F;
        float u1 = 1.0F;
        float v1 = 1.0F;
        if (Minecraft.getInstance().getTextureManager().getTexture(sprite.atlas()) instanceof TextureAtlas atlas) {
            TextureAtlasSprite resolved = atlas.getSprite(sprite.texture());
            u0 = resolved.getU0();
            v0 = resolved.getV0();
            u1 = resolved.getU1();
            v1 = resolved.getV1();
        }
        
        Vec3 cornerU = quad.origin().add(quad.edgeU());
        List<Primitives.Vertex> vertices = this.vertices(ShapePipelines.sprite(sprite.atlas(), this.paint.depth()));
        vertices.add(new Primitives.Vertex(quad.origin(), argb, u0, v0, Vec3.ZERO, 0.0F));
        vertices.add(new Primitives.Vertex(cornerU, argb, u1, v0, Vec3.ZERO, 0.0F));
        vertices.add(new Primitives.Vertex(cornerU.add(quad.edgeV()), argb, u1, v1, Vec3.ZERO, 0.0F));
        vertices.add(new Primitives.Vertex(quad.origin().add(quad.edgeV()), argb, u0, v1, Vec3.ZERO, 0.0F));
    }
    
    ///
    /// The vertices recorded so far for the given render type, created empty on first use.
    ///
    /// @param type The render type of the vertices.
    ///
    /// @return The vertices of the render type.
    ///
    private List<Primitives.Vertex> vertices(RenderType type) {
        return this.verticesByType.computeIfAbsent(type, _ -> new ArrayList<>());
    }
    
    ///
    /// Records the text as a label, drawn see-through unless the current depth mode is [Paint.Depth#VISIBLE].
    ///
    /// @param at       The point to center the text on.
    /// @param text     The text to draw.
    /// @param scale    The scale of the text relative to a name tag.
    /// @param argb     The ARGB color of the text.
    /// @param backdrop The ARGB color of the backdrop, or `0` for none.
    ///
    @Override
    public void text(Vec3 at, Component text, float scale, int argb, int backdrop) {
        Font.DisplayMode mode = this.paint.depth() == Paint.Depth.VISIBLE ? Font.DisplayMode.NORMAL : Font.DisplayMode.SEE_THROUGH;
        this.labels.add(new Primitives.Label(at, text, scale, argb, backdrop, mode));
    }
    
    ///
    /// Draws the given part with the given paint as the current one and then restores the previous one, even if the part throws.
    ///
    /// @param part  The shape to draw.
    /// @param paint The paint to draw the part with.
    ///
    @Override
    public void draw(Shape part, Paint paint) {
        Paint current = this.paint;
        this.paint = paint;
        try {
            part.draw(this);
        } finally {
            this.paint = current;
        }
    }
    
    ///
    /// Creates the primitives of everything recorded so far.
    ///
    /// The primitives share their vertices and labels with this brush, so this should be the last call on it.
    ///
    /// @return The primitives recorded so far.
    ///
    public Primitives finish() {
        return new Primitives(this.verticesByType, this.labels);
    }
}
