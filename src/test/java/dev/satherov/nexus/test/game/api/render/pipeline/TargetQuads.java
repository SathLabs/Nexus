package dev.satherov.nexus.test.game.api.render.pipeline;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.pipeline.Pipeline;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

///
/// The sample pipelines and a mod's own render types over them, drawn by the sample itself at several stages of the frame.
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
    
    private static final RenderPipeline COVER_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "pipeline/pipeline_samples_cover"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();
    
    private static final RenderPipeline.Snippet ADDITIVE_STATE = RenderPipeline.builder()
            .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
            .withCull(false)
            .buildSnippet();
    
    private static final RenderPipeline.Snippet LIGHTNING_STATE = RenderPipeline.builder()
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withCull(false)
            .buildSnippet();
    
    private static final RenderType MAIN_TYPE = RenderType.create("nexus_pipeline_samples_red_main", RenderSetup.builder(TargetQuads.RED_PIPELINE).createRenderSetup());
    
    private static final RenderType COVER_TYPE = RenderType.create("nexus_pipeline_samples_cover", RenderSetup.builder(TargetQuads.COVER_PIPELINE).createRenderSetup());
    
    private static final Map<Pipeline, RenderType> TARGET_TYPES = new HashMap<>();
    
    private static final Map<Pipeline, RenderType> ITEM_ENTITY_TYPES = new HashMap<>();
    
    private static @Nullable Quad shown;
    
    private static @Nullable Quad early;
    
    private static @Nullable Quad submitted;
    
    private static @Nullable Quad cover;
    
    private static @Nullable Quad hooked;
    
    private static @Nullable RenderTarget drawnInto;
    
    private static @Nullable RuntimeException failure;
    
    private TargetQuads() { }
    
    private static Quad onTarget(Pipeline pipeline, int color, Vec3 center) {
        RenderType type = TargetQuads.TARGET_TYPES.computeIfAbsent(pipeline, TargetQuads::targetType);
        return new Quad(type, center, color);
    }
    
    private static RenderType targetType(Pipeline pipeline) {
        return TargetQuads.type(pipeline, pipeline.getTarget().get().get(), "_target");
    }
    
    private static RenderType itemEntityType(Pipeline pipeline) {
        return TargetQuads.type(pipeline, OutputTarget.ITEM_ENTITY_TARGET, "_item_entity");
    }
    
    private static RenderType type(Pipeline pipeline, OutputTarget target, String suffix) {
        RenderPipeline renderPipeline = pipeline.getPipeline().get().get();
        RenderSetup setup = RenderSetup.builder(renderPipeline)
                .setOutputTarget(target)
                .createRenderSetup();
        return RenderType.create("nexus_" + renderPipeline.getLocation().getPath().replace('/', '_') + suffix, setup);
    }
    
    private static void write(VertexConsumer buffer, Matrix4f matrix, Quad quad) {
        Vec3 offset = quad.center().subtract(Minecraft.getInstance().gameRenderer.getMainCamera().position());
        float x = (float) offset.x;
        float y = (float) offset.y;
        float z = (float) offset.z;
        buffer.addVertex(matrix, x + TargetQuads.HALF_SIZE, y - TargetQuads.HALF_SIZE, z).setColor(quad.color());
        buffer.addVertex(matrix, x - TargetQuads.HALF_SIZE, y - TargetQuads.HALF_SIZE, z).setColor(quad.color());
        buffer.addVertex(matrix, x - TargetQuads.HALF_SIZE, y + TargetQuads.HALF_SIZE, z).setColor(quad.color());
        buffer.addVertex(matrix, x + TargetQuads.HALF_SIZE, y + TargetQuads.HALF_SIZE, z).setColor(quad.color());
    }
    
    private static void drawNow(@Nullable Quad quad, Matrix4f matrix) {
        if (quad == null) {
            return;
        }
        
        try (ByteBufferBuilder memory = new ByteBufferBuilder(TargetQuads.BUFFER_SIZE)) {
            MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(memory);
            TargetQuads.write(buffers.getBuffer(quad.type()), matrix, quad);
            buffers.endBatch();
            TargetQuads.drawnInto = quad.type().outputTarget().getRenderTarget();
        } catch (RuntimeException exception) {
            TargetQuads.failure = exception;
        }
    }
    
    public static RenderPipeline red() {
        return TargetQuads.RED_PIPELINE;
    }
    
    public static RenderPipeline cover() {
        return TargetQuads.COVER_PIPELINE;
    }
    
    public static RenderPipeline.Snippet additiveState() {
        return TargetQuads.ADDITIVE_STATE;
    }
    
    public static RenderPipeline.Snippet lightningState() {
        return TargetQuads.LIGHTNING_STATE;
    }
    
    public static void showOnMainTarget(Vec3 center) {
        TargetQuads.shown = new Quad(TargetQuads.MAIN_TYPE, center, TargetQuads.RED);
        TargetQuads.failure = null;
    }
    
    public static void showOnPipelineTarget(Pipeline pipeline, int color, Vec3 center) {
        TargetQuads.shown = TargetQuads.onTarget(pipeline, color, center);
        TargetQuads.failure = null;
    }
    
    public static void showEarlyOnPipelineTarget(Pipeline pipeline, Vec3 center) {
        TargetQuads.early = TargetQuads.onTarget(pipeline, TargetQuads.RED, center);
        TargetQuads.failure = null;
    }
    
    public static void submitOnPipelineTarget(Pipeline pipeline, Vec3 center) {
        TargetQuads.submitted = TargetQuads.onTarget(pipeline, TargetQuads.RED, center);
        TargetQuads.failure = null;
    }
    
    public static void showAfterLevel(int color, Vec3 center) {
        TargetQuads.cover = new Quad(TargetQuads.COVER_TYPE, center, color);
        TargetQuads.failure = null;
    }
    
    public static void showThroughDrawHook(Pipeline pipeline, int color, Vec3 center) {
        RenderType type = TargetQuads.ITEM_ENTITY_TYPES.computeIfAbsent(pipeline, TargetQuads::itemEntityType);
        TargetQuads.hooked = new Quad(type, center, color);
        TargetQuads.failure = null;
    }
    
    public static void hide() {
        TargetQuads.shown = null;
        TargetQuads.early = null;
        TargetQuads.submitted = null;
        TargetQuads.cover = null;
        TargetQuads.hooked = null;
        TargetQuads.drawnInto = null;
        TargetQuads.failure = null;
    }
    
    public static @Nullable RuntimeException failure() {
        return TargetQuads.failure;
    }
    
    public static @Nullable RenderTarget drawnInto() {
        return TargetQuads.drawnInto;
    }
    
    public static void draw(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        TargetQuads.drawNow(TargetQuads.shown, event.getPoseStack().last().pose());
    }
    
    public static void drawEarly(RenderLevelStageEvent.AfterTranslucentFeatures event) {
        TargetQuads.drawNow(TargetQuads.early, event.getPoseStack().last().pose());
    }
    
    public static void submit(SubmitCustomGeometryEvent event) {
        Quad quad = TargetQuads.submitted;
        if (quad != null) {
            event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), quad.type(), (pose, buffer) -> TargetQuads.write(buffer, pose.pose(), quad));
        }
    }
    
    public static void drawAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        if (TargetQuads.cover == null) {
            return;
        }
        
        // The level pops its view rotation off the model view stack before this stage, so we push it again like the level does.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        TargetQuads.drawNow(TargetQuads.cover, event.getPoseStack().last().pose());
        modelView.popMatrix();
    }
    
    public static void drawHook(PoseStack pose, MultiBufferSource buffers) {
        Quad quad = TargetQuads.hooked;
        if (quad == null) {
            return;
        }
        
        try {
            TargetQuads.write(buffers.getBuffer(quad.type()), pose.last().pose(), quad);
        } catch (RuntimeException exception) {
            TargetQuads.failure = exception;
        }
    }
    
    private record Quad(RenderType type, Vec3 center, int color) { }
}
