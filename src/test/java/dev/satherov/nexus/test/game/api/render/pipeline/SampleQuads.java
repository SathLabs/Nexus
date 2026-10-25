package dev.satherov.nexus.test.game.api.render.pipeline;

import dev.satherov.nexus.Nexus;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

///
/// The client side of the pipeline samples.
///
public final class SampleQuads {
    
    public static final String TINT_BLOCK = "SampleTint";
    
    private static final int RED = 0xFFFF0000;
    
    private static final int WHITE = 0xFFFFFFFF;
    
    private static final int TINT_SIZE = 16;
    
    private static final RenderPipeline SOLID = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "pipeline/pipeline_samples_solid"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "core/pipeline_samples_solid"))
            .withCull(false)
            .build();
    
    private static final RenderPipeline TINTED = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "pipeline/pipeline_samples_tinted"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "core/pipeline_samples_tinted"))
            .withUniform(SampleQuads.TINT_BLOCK, UniformType.UNIFORM_BUFFER)
            .withCull(false)
            .build();
    
    private static final RenderType SOLID_TYPE = RenderType.create("nexus_pipeline_samples_solid", RenderSetup.builder(SampleQuads.SOLID).createRenderSetup());
    
    private static final RenderType TINTED_TYPE = RenderType.create("nexus_pipeline_samples_tinted", RenderSetup.builder(SampleQuads.TINTED).createRenderSetup());
    
    private static final Map<Integer, GpuBuffer> TINT_BUFFERS = new HashMap<>();
    
    private static @Nullable RenderType shown;
    
    private static Vec3 center = Vec3.ZERO;
    
    private static int tint = SampleQuads.WHITE;
    
    private SampleQuads() { }
    
    private static GpuBufferSlice block(int argb) {
        return SampleQuads.TINT_BUFFERS.computeIfAbsent(argb, SampleQuads::upload).slice();
    }
    
    private static GpuBuffer upload(int argb) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, SampleQuads.TINT_SIZE)
                    .putVec4(ARGB.redFloat(argb), ARGB.greenFloat(argb), ARGB.blueFloat(argb), ARGB.alphaFloat(argb))
                    .get();
            return RenderSystem.getDevice().createBuffer(() -> "Nexus pipeline samples tint", GpuBuffer.USAGE_UNIFORM, data);
        }
    }
    
    public static RenderPipeline solid() {
        return SampleQuads.SOLID;
    }
    
    public static RenderPipeline tinted() {
        return SampleQuads.TINTED;
    }
    
    public static GpuBufferSlice tintBlock() {
        return SampleQuads.block(SampleQuads.tint);
    }
    
    public static GpuBufferSlice whiteBlock() {
        return SampleQuads.block(SampleQuads.WHITE);
    }
    
    public static void setTint(int argb) {
        SampleQuads.tint = argb;
    }
    
    public static void showSolid(Vec3 center) {
        SampleQuads.shown = SampleQuads.SOLID_TYPE;
        SampleQuads.center = center;
    }
    
    public static void showTinted(Vec3 center) {
        SampleQuads.shown = SampleQuads.TINTED_TYPE;
        SampleQuads.center = center;
    }
    
    public static void hide() {
        SampleQuads.shown = null;
    }
    
    public static void draw(PoseStack pose, MultiBufferSource buffers) {
        RenderType type = SampleQuads.shown;
        if (type == null) {
            return;
        }
        
        Vec3 offset = SampleQuads.center.subtract(Minecraft.getInstance().gameRenderer.getMainCamera().position());
        float x = (float) offset.x;
        float y = (float) offset.y;
        float z = (float) offset.z;
        Matrix4f matrix = pose.last().pose();
        VertexConsumer buffer = buffers.getBuffer(type);
        buffer.addVertex(matrix, x - 1.0F, y - 1.0F, z).setColor(SampleQuads.RED);
        buffer.addVertex(matrix, x + 1.0F, y - 1.0F, z).setColor(SampleQuads.RED);
        buffer.addVertex(matrix, x + 1.0F, y + 1.0F, z).setColor(SampleQuads.RED);
        buffer.addVertex(matrix, x - 1.0F, y + 1.0F, z).setColor(SampleQuads.RED);
    }
}
