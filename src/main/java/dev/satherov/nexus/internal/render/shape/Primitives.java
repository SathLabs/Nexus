package dev.satherov.nexus.internal.render.shape;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.render.shape.Sprite;
import dev.satherov.nexus.internal.render.client.ShapePipelines;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

///
/// The vertices and labels of one recorded shape.
///
/// Can be created through [MeshBrush#finish()].
///
@ApiStatus.Internal
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class Primitives {
    
    ///
    /// The vertices of every render type, in the order the mode of the render type reads them.
    ///
    private final Map<RenderType, List<Vertex>> verticesByType;
    
    ///
    /// The labels.
    ///
    private final List<Label> labels;
    
    ///
    /// Writes the vertices into the buffer builder of their render type, relative to the given origin.
    ///
    /// The sprites are looked up in the current stitch of their atlas.
    /// If the texture of an atlas is not a [TextureAtlas], the whole texture will be shown.
    ///
    /// @param builders The buffer builders by render type, which should hold one for every render type of [ShapePipelines].
    /// @param origin   The position that the written positions are relative to.
    ///
    /// @throws NullPointerException If there is no buffer builder for a render type of these vertices.
    ///
    public void writeInto(Map<RenderType, BufferBuilder> builders, Vec3 origin) {
        this.verticesByType.forEach((type, vertices) -> {
            BufferBuilder builder = Objects.requireNonNull(builders.get(type), () -> "There is no builder for render type '" + type + "'");
            VertexFormat format = type.pipeline().getVertexFormat();
            Supplier<ShapeInputs.Elements> elements = Suppliers.memoize(() -> ShapeInputs.Elements.of(format));
            vertices.forEach(vertex -> vertex.writeInto(builder, elements, origin));
        });
    }
    
    ///
    /// Draws every label through the font, facing the camera.
    ///
    /// @param pose    The pose stack of the frame, whose origin is the camera.
    /// @param origin  The origin of the pose stack, in the coordinates of the labels.
    /// @param buffers The buffer source the labels are drawn into.
    ///
    public void drawLabels(PoseStack pose, Vec3 origin, MultiBufferSource.BufferSource buffers) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Quaternionf rotation = minecraft.gameRenderer.getMainCamera().rotation();
        for (Label label : this.labels) {
            float scale = EntityRenderer.NAMETAG_SCALE * label.scale();
            pose.pushPose();
            pose.translate(label.at().subtract(origin));
            pose.mulPose(rotation);
            pose.scale(scale, -scale, scale);
            font.drawInBatch(
                    label.text(),
                    -font.width(label.text()) / 2.0F,
                    -font.lineHeight / 2.0F,
                    label.argb(),
                    false,
                    pose.last().pose(),
                    buffers,
                    label.mode(),
                    label.backdrop(),
                    LightCoordsUtil.FULL_BRIGHT
            );
            pose.popPose();
        }
    }
    
    ///
    /// A vertex with every element that the vertex formats of the render types of [ShapePipelines] have.
    ///
    /// @param position  The position of the vertex, in the coordinates of its shape.
    /// @param argb      The ARGB color of the vertex.
    /// @param sprite    The sprite the vertex shows, or `null` if it doesn't show one.
    /// @param u         The horizontal position on the sprite, from `0` to `1`, or `0` if the vertex shows no sprite.
    /// @param v         The vertical position on the sprite, from `0` to `1`, or `0` if the vertex shows no sprite.
    /// @param normal    The direction of the line, or [Vec3#ZERO] if the vertex is not part of a line.
    /// @param lineWidth The width of the line in pixels, or `0` if the vertex is not part of a line.
    /// @param shape     The inputs of the shape program, or `null` if the vertex is not part of a disc, ring, or sphere.
    ///
    record Vertex(Vec3 position, int argb, @Nullable Sprite sprite, float u, float v, Vec3 normal, float lineWidth, @Nullable ShapeInputs shape) {
        
        ///
        /// Creates a new vertex that is not part of a disc, ring, or sphere.
        ///
        Vertex(Vec3 position, int argb, @Nullable Sprite sprite, float u, float v, Vec3 normal, float lineWidth) {
            this(position, argb, sprite, u, v, normal, lineWidth, null);
        }
        
        ///
        /// Writes this vertex into the given buffer builder, relative to the given origin.
        ///
        /// The buffer builder skips every element that its vertex format doesn't have.
        /// If the vertex shows a sprite of a [TextureAtlas], its position on the sprite will be written as a position in the atlas.
        ///
        /// @param builder  The buffer builder of the render type of the vertex.
        /// @param elements The elements of the shape inputs in the vertex format of the render type, called only if the vertex has shape inputs.
        /// @param origin   The position that the written position is relative to.
        ///
        private void writeInto(BufferBuilder builder, Supplier<ShapeInputs.Elements> elements, Vec3 origin) {
            Vec3 relative = this.position.subtract(origin);
            float u = this.u;
            float v = this.v;
            if (this.sprite != null && Minecraft.getInstance().getTextureManager().getTexture(this.sprite.atlas()) instanceof TextureAtlas atlas) {
                TextureAtlasSprite resolved = atlas.getSprite(this.sprite.texture());
                u = resolved.getU(this.u);
                v = resolved.getV(this.v);
            }
            
            builder.addVertex((float) relative.x, (float) relative.y, (float) relative.z)
                    .setColor(this.argb)
                    .setUv(u, v)
                    .setNormal((float) this.normal.x, (float) this.normal.y, (float) this.normal.z)
                    .setLineWidth(this.lineWidth);
            if (this.shape != null) {
                this.shape.writeInto(builder, elements.get(), origin);
            }
        }
    }
    
    ///
    /// The inputs of the shape program at one corner of a disc, ring, or sphere.
    ///
    /// @param center The center, in the coordinates of its shape.
    /// @param axis   The unit normal of the plane of a disc or ring, or [Vec3#ZERO] for a sphere.
    /// @param radius The radius of the shape.
    /// @param width  The width of a ring in pixels, or `0` for a disc or sphere.
    /// @param corner The corner of the quad, with `-1` or `1` on each axis.
    /// @param kind   The kind of the shape.
    ///
    record ShapeInputs(Vec3 center, Vec3 axis, float radius, float width, Vec2 corner, Kind kind) {
        
        ///
        /// The arrays that the raw bits are written into, by the number of values minus one.
        ///
        /// Every conversion into raw bits reuses the array of its number of values.
        ///
        private static final int[][] RAW = { new int[1], new int[2], new int[3] };
        
        ///
        /// Puts the given `int` into an array as its raw bits.
        ///
        /// @param value The value to put.
        ///
        /// @return The raw bits, in the array that the next conversion of one value reuses.
        ///
        private static int[] raw(int value) {
            int[] raw = ShapeInputs.RAW[0];
            raw[0] = value;
            return raw;
        }
        
        ///
        /// Converts the given `float` into its raw bits.
        ///
        /// @param value The value to convert.
        ///
        /// @return The raw bits, in the array that the next conversion of one value reuses.
        ///
        private static int[] raw(float value) {
            return ShapeInputs.raw(Float.floatToRawIntBits(value));
        }
        
        ///
        /// Converts the given `float`s into their raw bits.
        ///
        /// @param x The first value to convert.
        /// @param y The second value to convert.
        ///
        /// @return The raw bits, in the array that the next conversion of two values reuses.
        ///
        private static int[] raw(float x, float y) {
            int[] raw = ShapeInputs.RAW[1];
            raw[0] = Float.floatToRawIntBits(x);
            raw[1] = Float.floatToRawIntBits(y);
            return raw;
        }
        
        ///
        /// Converts the given `float`s into their raw bits.
        ///
        /// @param x The first value to convert.
        /// @param y The second value to convert.
        /// @param z The third value to convert.
        ///
        /// @return The raw bits, in the array that the next conversion of three values reuses.
        ///
        private static int[] raw(float x, float y, float z) {
            int[] raw = ShapeInputs.RAW[2];
            raw[0] = Float.floatToRawIntBits(x);
            raw[1] = Float.floatToRawIntBits(y);
            raw[2] = Float.floatToRawIntBits(z);
            return raw;
        }
        
        ///
        /// Writes the inputs into the current vertex of the given buffer builder, with the center relative to the given origin.
        ///
        /// @param builder  The buffer builder of the render type of the vertex.
        /// @param elements The elements of the inputs in the vertex format of the render type.
        /// @param origin   The position that the written center is relative to.
        ///
        private void writeInto(BufferBuilder builder, Elements elements, Vec3 origin) {
            Vec3 relative = this.center.subtract(origin);
            // Each call writes its raw bits before the next one reuses the array.
            builder.misc(elements.center(), ShapeInputs.raw((float) relative.x, (float) relative.y, (float) relative.z))
                    .misc(elements.axis(), ShapeInputs.raw((float) this.axis.x, (float) this.axis.y, (float) this.axis.z))
                    .misc(elements.radius(), ShapeInputs.raw(this.radius))
                    .misc(elements.width(), ShapeInputs.raw(this.width))
                    .misc(elements.corner(), ShapeInputs.raw(this.corner.x, this.corner.y))
                    .misc(elements.kind(), ShapeInputs.raw(this.kind.ordinal()));
        }
        
        ///
        /// The elements of the inputs of the shape program in one vertex format.
        ///
        /// @param center The element of the `Center` input.
        /// @param axis   The element of the `Axis` input.
        /// @param radius The element of the `Radius` input.
        /// @param width  The element of the `Width` input.
        /// @param corner The element of the `Corner` input.
        /// @param kind   The element of the `Kind` input.
        ///
        private record Elements(VertexFormatElement center, VertexFormatElement axis, VertexFormatElement radius, VertexFormatElement width, VertexFormatElement corner, VertexFormatElement kind) {
            
            ///
            /// Gets the elements of the inputs in the given vertex format.
            ///
            /// @param format The vertex format of the pipeline of the render type.
            ///
            /// @return The elements.
            ///
            /// @throws NullPointerException If the vertex format has no element of an input.
            ///
            private static Elements of(VertexFormat format) {
                return new Elements(
                        Elements.element(format, "Center"),
                        Elements.element(format, "Axis"),
                        Elements.element(format, "Radius"),
                        Elements.element(format, "Width"),
                        Elements.element(format, "Corner"),
                        Elements.element(format, "Kind")
                );
            }
            
            ///
            /// Gets the element of the given input in the given vertex format.
            ///
            /// @param format The vertex format of the pipeline of the render type.
            /// @param input  The name of the input.
            ///
            /// @return The element.
            ///
            /// @throws NullPointerException If the vertex format has no element of that name.
            ///
            private static VertexFormatElement element(VertexFormat format, String input) {
                return Objects.requireNonNull(format.getElementMapping().get(input), () -> "The vertex format '" + format + "' has no input '" + input + "'");
            }
        }
        
        ///
        /// The kind of shape that the shape program draws, with the ordinal as the value of its `Kind` input.
        ///
        enum Kind {
            ///
            /// A filled circle.
            ///
            DISC,
            
            ///
            /// A circle outline.
            ///
            RING,
            
            ///
            /// A filled sphere.
            ///
            SPHERE
        }
    }
    
    ///
    /// A text drawn through the font, facing the camera.
    ///
    /// @param at       The point to center the text on, in the coordinates of its shape.
    /// @param text     The text to draw.
    /// @param scale    The scale of the text relative to a name tag.
    /// @param argb     The ARGB color of the text.
    /// @param backdrop The ARGB color of the backdrop, or `0` for none.
    /// @param mode     The display mode the font draws the text in.
    ///
    record Label(Vec3 at, Component text, float scale, int argb, int backdrop, Font.DisplayMode mode) { }
}
