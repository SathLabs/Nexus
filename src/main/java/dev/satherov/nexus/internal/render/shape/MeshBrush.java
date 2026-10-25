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
/// The brush a shape draws into: records lines, triangles, quads and labels per render type into a drawing's primitive list, with the
/// current paint swapped around a painted part.
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
    /// The current paint, which is the paint of the drawing outside a painted part.
    ///
    private Paint paint;
    
    ///
    /// A brush for the paint.
    ///
    /// @param paint The paint of the drawing.
    ///
    public MeshBrush(Paint paint) {
        this.paint = paint;
    }
    
    ///
    /// The current paint, which is the paint of the drawing outside a painted part.
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
    /// Records the triangle as a quad with its last corner repeated, in the fill render type of the current depth mode.
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
    /// Records the quad between the given corners, in order, in the fill render type of the current depth mode.
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
    /// The sprite is resolved now, and a texture that is no atlas is shown whole.
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
    private List<Primitives.Vertex> vertices(RenderType type) {
        return this.verticesByType.computeIfAbsent(type, _ -> new ArrayList<>());
    }
    
    ///
    /// Records the text as a label, see-through under every depth mode other than [Paint.Depth#VISIBLE].
    ///
    /// @param at       The point to center the text on.
    /// @param text     The text to draw.
    /// @param scale    The scale of the text, where `1` is name tag size.
    /// @param argb     The ARGB color of the text.
    /// @param backdrop The ARGB color of the backdrop, or `0` for none.
    ///
    @Override
    public void text(Vec3 at, Component text, float scale, int argb, int backdrop) {
        Font.DisplayMode mode = this.paint.depth() == Paint.Depth.VISIBLE ? Font.DisplayMode.NORMAL : Font.DisplayMode.SEE_THROUGH;
        this.labels.add(new Primitives.Label(at, text, scale, argb, backdrop, mode));
    }
    
    ///
    /// Draws the given part with the given paint as the current one and then restores the current one, even if the part throws.
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
    /// The recorded primitives.
    ///
    /// @return The primitives recorded so far.
    ///
    public Primitives finish() {
        return new Primitives(this.verticesByType, this.labels);
    }
}
