package dev.satherov.nexus.test.game.api.render;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.test.game.api.render.pipeline.SampleQuads;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

///
/// The sample pipeline that is built anew on every call and the draw hooks that move the pose stack and draw a quad over it.
///
public final class SampleHooks {
    
    private static final int WHITE = 0xFFFFFFFF;
    
    private static final float SHIFT = 5.0F;
    
    private static @Nullable Pipeline shown;
    
    private static @Nullable RenderType type;
    
    private static Vec3 center = Vec3.ZERO;
    
    private static Shift shift = Shift.NONE;
    
    private SampleHooks() { }
    
    public static RenderPipeline fresh() {
        return RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "pipeline/render_samples_fresh"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "core/pipeline_samples_tinted"))
                .withUniform(SampleQuads.TINT_BLOCK, UniformType.UNIFORM_BUFFER)
                .withCull(false)
                .build();
    }
    
    public static void show(Pipeline pipeline, Vec3 center) {
        SampleHooks.shown = pipeline;
        SampleHooks.center = center;
    }
    
    public static void translate() {
        SampleHooks.shift = Shift.TRANSLATE;
    }
    
    public static void push() {
        SampleHooks.shift = Shift.PUSH;
    }
    
    public static void hide() {
        SampleHooks.shown = null;
        SampleHooks.shift = Shift.NONE;
    }
    
    public static void shift(PoseStack pose, MultiBufferSource buffers) {
        switch (SampleHooks.shift) {
            case NONE -> {
            }
            case TRANSLATE -> pose.translate(SampleHooks.SHIFT, 0.0F, 0.0F);
            case PUSH -> {
                pose.pushPose();
                pose.translate(SampleHooks.SHIFT, 0.0F, 0.0F);
            }
        }
    }
    
    public static void draw(PoseStack pose, MultiBufferSource buffers) {
        Pipeline pipeline = SampleHooks.shown;
        if (pipeline == null) {
            return;
        }
        
        RenderType type = SampleHooks.type;
        if (type == null) {
            type = RenderType.create("nexus_render_samples_fresh", RenderSetup.builder(pipeline.getPipeline().get().get()).createRenderSetup());
            SampleHooks.type = type;
        }
        
        Vec3 offset = SampleHooks.center.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().position());
        float x = (float) offset.x;
        float y = (float) offset.y;
        float z = (float) offset.z;
        Matrix4f matrix = pose.last().pose();
        VertexConsumer buffer = buffers.getBuffer(type);
        buffer.addVertex(matrix, x + 1.0F, y - 1.0F, z).setColor(SampleHooks.WHITE);
        buffer.addVertex(matrix, x - 1.0F, y - 1.0F, z).setColor(SampleHooks.WHITE);
        buffer.addVertex(matrix, x - 1.0F, y + 1.0F, z).setColor(SampleHooks.WHITE);
        buffer.addVertex(matrix, x + 1.0F, y + 1.0F, z).setColor(SampleHooks.WHITE);
    }
    
    private enum Shift {
        NONE,
        TRANSLATE,
        PUSH
    }
}
