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
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Quaternionf;

import java.util.List;
import java.util.Map;
import java.util.Objects;

///
/// What one drawing emitted: vertex data per render type and its labels, kept until the drawing ends.
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
    /// The labels, in the order they were drawn.
    ///
    private final List<Label> labels;
    
    ///
    /// Appends the vertex data, offset by the origin, into the builders by render type.
    /// Every position is written relative to the origin.
    ///
    /// @param builders The builders by render type, which should hold one for every render type of [ShapePipelines].
    /// @param origin   The position that the written positions are relative to.
    ///
    /// @throws NullPointerException If the builders don't hold one for a render type of these primitives.
    ///
    public void writeInto(Map<RenderType, BufferBuilder> builders, Vec3 origin) {
        this.verticesByType.forEach((type, vertices) -> {
            BufferBuilder builder = Objects.requireNonNull(builders.get(type), "There is no builder for render type '" + type + "'");
            vertices.forEach(vertex -> vertex.writeInto(builder, origin));
        });
    }
    
    ///
    /// Draws the labels through the font at the origin.
    /// Every label faces the camera and is drawn relative to the origin.
    ///
    /// @param pose    The pose stack of the frame, whose origin is the camera.
    /// @param origin  The position of the labels' coordinates that lies at the origin of the pose stack.
    /// @param buffers The buffer source the font draws into.
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
    /// One vertex, with every element that the vertex formats of the shape render types have.
    ///
    /// @param position  The position of the vertex, in the coordinates of its drawing.
    /// @param argb      The ARGB color of the vertex.
    /// @param u         The horizontal texture coordinate in the atlas, or `0` if the vertex shows no sprite.
    /// @param v         The vertical texture coordinate in the atlas, or `0` if the vertex shows no sprite.
    /// @param normal    The direction of the line, or [Vec3#ZERO] if the vertex is not part of a line.
    /// @param lineWidth The width of the line in pixels, or `0` if the vertex is not part of a line.
    ///
    record Vertex(Vec3 position, int argb, float u, float v, Vec3 normal, float lineWidth) {
        
        ///
        /// Writes this vertex into the given builder, relative to the given origin.
        /// The builder leaves out every element that its vertex format doesn't have.
        ///
        /// @param builder The builder of the render type of the vertex.
        /// @param origin  The position that the written position is relative to.
        ///
        private void writeInto(BufferBuilder builder, Vec3 origin) {
            Vec3 relative = this.position.subtract(origin);
            builder.addVertex((float) relative.x, (float) relative.y, (float) relative.z)
                    .setColor(this.argb)
                    .setUv(this.u, this.v)
                    .setNormal((float) this.normal.x, (float) this.normal.y, (float) this.normal.z)
                    .setLineWidth(this.lineWidth);
        }
    }
    
    ///
    /// One text drawn through the font each frame.
    ///
    /// @param at       The point to center the text on, in the coordinates of its drawing.
    /// @param text     The text to draw.
    /// @param scale    The scale of the text, where `1` is name tag size.
    /// @param argb     The ARGB color of the text.
    /// @param backdrop The ARGB color of the backdrop, or `0` for none.
    /// @param mode     The display mode the font draws the text in.
    ///
    record Label(Vec3 at, Component text, float scale, int argb, int backdrop, Font.DisplayMode mode) { }
}
