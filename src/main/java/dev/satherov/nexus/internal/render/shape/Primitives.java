package dev.satherov.nexus.internal.render.shape;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.internal.render.client.ShapePipelines;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

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
    /// @param builders The buffer builders by render type, which should hold one for every render type of [ShapePipelines].
    /// @param origin   The position that the written positions are relative to.
    ///
    /// @throws NullPointerException If there is no buffer builder for a render type of these vertices.
    ///
    public void writeInto(Map<RenderType, BufferBuilder> builders, Vec3 origin) {
        this.verticesByType.forEach((type, vertices) -> {
            BufferBuilder builder = Objects.requireNonNull(builders.get(type), "There is no builder for render type '" + type + "'");
            VertexFormat format = type.pipeline().getVertexFormat();
            vertices.forEach(vertex -> vertex.writeInto(builder, format, origin));
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
    /// @param u         The horizontal texture coordinate in the atlas, or `0` if the vertex shows no sprite.
    /// @param v         The vertical texture coordinate in the atlas, or `0` if the vertex shows no sprite.
    /// @param normal    The direction of the line, or [Vec3#ZERO] if the vertex is not part of a line.
    /// @param lineWidth The width of the line in pixels, or `0` if the vertex is not part of a line.
    /// @param shape     The inputs of the shape program, or `null` if the vertex is not part of a disc, ring, or sphere.
    ///
    record Vertex(Vec3 position, int argb, float u, float v, Vec3 normal, float lineWidth, @Nullable ShapeInputs shape) {
        
        ///
        /// Creates a new vertex that is not part of a disc, ring, or sphere.
        ///
        Vertex(Vec3 position, int argb, float u, float v, Vec3 normal, float lineWidth) {
            this(position, argb, u, v, normal, lineWidth, null);
        }
        
        ///
        /// Writes this vertex into the given buffer builder, relative to the given origin.
        ///
        /// The buffer builder skips every element that its vertex format doesn't have.
        ///
        /// @param builder The buffer builder of the render type of the vertex.
        /// @param format  The vertex format of the pipeline of the render type.
        /// @param origin  The position that the written position is relative to.
        ///
        private void writeInto(BufferBuilder builder, VertexFormat format, Vec3 origin) {
            Vec3 relative = this.position.subtract(origin);
            builder.addVertex((float) relative.x, (float) relative.y, (float) relative.z)
                    .setColor(this.argb)
                    .setUv(this.u, this.v)
                    .setNormal((float) this.normal.x, (float) this.normal.y, (float) this.normal.z)
                    .setLineWidth(this.lineWidth);
            if (this.shape != null) {
                this.shape.writeInto(builder, format, origin);
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
            return Objects.requireNonNull(format.getElementMapping().get(input), "The vertex format '" + format + "' has no input '" + input + "'");
        }
        
        ///
        /// Converts the given `float`s into their raw bits.
        ///
        /// @param values The values to convert.
        ///
        /// @return The raw bits, one `int` per value.
        ///
        private static int[] raw(float... values) {
            int[] raw = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                raw[i] = Float.floatToRawIntBits(values[i]);
            }
            
            return raw;
        }
        
        ///
        /// Writes the inputs into the current vertex of the given buffer builder, with the center relative to the given origin.
        ///
        /// @param builder The buffer builder of the render type of the vertex.
        /// @param format  The vertex format of the pipeline of the render type.
        /// @param origin  The position that the written center is relative to.
        ///
        private void writeInto(BufferBuilder builder, VertexFormat format, Vec3 origin) {
            Vec3 relative = this.center.subtract(origin);
            builder.misc(ShapeInputs.element(format, "Center"), ShapeInputs.raw((float) relative.x, (float) relative.y, (float) relative.z))
                    .misc(ShapeInputs.element(format, "Axis"), ShapeInputs.raw((float) this.axis.x, (float) this.axis.y, (float) this.axis.z))
                    .misc(ShapeInputs.element(format, "Radius"), ShapeInputs.raw(this.radius))
                    .misc(ShapeInputs.element(format, "Width"), ShapeInputs.raw(this.width))
                    .misc(ShapeInputs.element(format, "Corner"), ShapeInputs.raw(this.corner.x, this.corner.y))
                    .misc(ShapeInputs.element(format, "Kind"), this.kind.ordinal());
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
