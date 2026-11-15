package dev.satherov.nexus.test.game.api.render.pipeline;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.pipeline.Pipeline;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

///
/// The red sample pipeline and a mod's own render types over it, drawn by the sample itself after the translucent blocks.
///
public final class TargetQuads {
    
    private static final int RED = 0xFFFF0000;
    
    private static final float HALF_SIZE = 2.0F;
    
    private static final int BUFFER_SIZE = 256;
    
    private static final RenderPipeline RED_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "pipeline/pipeline_samples_red"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "core/pipeline_samples_red"))
            .withCull(false)
            .build();
    
    private static final RenderType MAIN_TYPE = RenderType.create("nexus_pipeline_samples_red_main", RenderSetup.builder(TargetQuads.RED_PIPELINE).createRenderSetup());
    
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new ByteBufferBuilder(TargetQuads.BUFFER_SIZE));
    
    private static @Nullable RenderType targetType;
    
    private static @Nullable RenderType shown;
    
    private static @Nullable RuntimeException failure;
    
    private static Vec3 center = Vec3.ZERO;
    
    private TargetQuads() { }
    
    private static void show(RenderType type, Vec3 center) {
        TargetQuads.shown = type;
        TargetQuads.center = center;
        TargetQuads.failure = null;
    }
    
    public static RenderPipeline red() {
        return TargetQuads.RED_PIPELINE;
    }
    
    public static void showOnMainTarget(Vec3 center) {
        TargetQuads.show(TargetQuads.MAIN_TYPE, center);
    }
    
    public static void showOnPipelineTarget(Pipeline pipeline, Vec3 center) {
        RenderType type = TargetQuads.targetType;
        if (type == null) {
            RenderSetup setup = RenderSetup.builder(TargetQuads.RED_PIPELINE)
                    .setOutputTarget(pipeline.getTarget().get().get())
                    .createRenderSetup();
            type = RenderType.create("nexus_pipeline_samples_red_target", setup);
            TargetQuads.targetType = type;
        }
        
        TargetQuads.show(type, center);
    }
    
    public static void hide() {
        TargetQuads.shown = null;
        TargetQuads.failure = null;
    }
    
    public static @Nullable RuntimeException failure() {
        return TargetQuads.failure;
    }
    
    public static void draw(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        RenderType type = TargetQuads.shown;
        if (type == null) {
            return;
        }
        
        try {
            Vec3 offset = TargetQuads.center.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().position());
            float x = (float) offset.x;
            float y = (float) offset.y;
            float z = (float) offset.z;
            Matrix4f matrix = event.getPoseStack().last().pose();
            VertexConsumer buffer = TargetQuads.BUFFERS.getBuffer(type);
            buffer.addVertex(matrix, x + TargetQuads.HALF_SIZE, y - TargetQuads.HALF_SIZE, z).setColor(TargetQuads.RED);
            buffer.addVertex(matrix, x - TargetQuads.HALF_SIZE, y - TargetQuads.HALF_SIZE, z).setColor(TargetQuads.RED);
            buffer.addVertex(matrix, x - TargetQuads.HALF_SIZE, y + TargetQuads.HALF_SIZE, z).setColor(TargetQuads.RED);
            buffer.addVertex(matrix, x + TargetQuads.HALF_SIZE, y + TargetQuads.HALF_SIZE, z).setColor(TargetQuads.RED);
            TargetQuads.BUFFERS.endBatch();
        } catch (RuntimeException exception) {
            TargetQuads.failure = exception;
        }
    }
}
