package dev.satherov.nexus.test.game.api.render.pipeline;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.internal.render.shader.Programs;
import dev.satherov.nexus.internal.render.shader.VanillaSampler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

///
/// The sample pipelines and the draw hook that draws a quad in the shown one.
///
public final class SampleQuads {
    
    public static final String TINT_BLOCK = "SampleTint";
    
    public static final String MID_VERTEX_FAILURE = "Thrown by the sample in the middle of a vertex";
    
    private static final int WHITE = 0xFFFFFFFF;
    
    private static final int TINT_SIZE = 16;
    
    private static final String BROKEN_PROGRAM = "pipeline_samples_broken";
    
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    
    private static final Map<String, int[]> INPUTS = Map.of(
            "Strength", new int[]{ Float.floatToRawIntBits(1.0F) },
            "Weights", new int[]{ Float.floatToRawIntBits(1.0F), Float.floatToRawIntBits(2.0F) },
            "Pick", new int[]{ 3 }
    );
    
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
    
    private static final RenderPipeline.Snippet TEXTURED_STATE = RenderPipeline.builder().withCull(false).buildSnippet();
    
    private static final RenderType SOLID_TYPE = RenderType.create("nexus_pipeline_samples_solid", RenderSetup.builder(SampleQuads.SOLID).createRenderSetup());
    
    private static final RenderType TINTED_TYPE = RenderType.create("nexus_pipeline_samples_tinted", RenderSetup.builder(SampleQuads.TINTED).createRenderSetup());
    
    private static final Map<Integer, GpuBuffer> TINT_BUFFERS = new HashMap<>();
    
    private static final Map<RenderPipeline, RenderType> PROGRAM_TYPES = new HashMap<>();
    
    private static final List<String> BROKEN_LOGS = new CopyOnWriteArrayList<>();
    
    private static @Nullable RenderType shown;
    
    private static @Nullable RuntimeException failure;
    
    private static boolean throwMidVertex;
    
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
    
    private static RenderType programType(RenderPipeline pipeline) {
        RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline).withTexture(VanillaSampler.TEXTURE.getGlslName(), SampleQuads.TEXTURE);
        if (pipeline.getVertexFormat().contains(VertexFormatElement.UV2)) {
            setup.useLightmap().useOverlay();
        }
        
        return RenderType.create("nexus_" + pipeline.getLocation().getPath().replace('/', '_'), setup.createRenderSetup());
    }
    
    private static void vertex(VertexConsumer buffer, RenderType type, Matrix4f matrix, float x, float y, float z) {
        VertexConsumer vertex = buffer.addVertex(matrix, x, y, z)
                .setColor(SampleQuads.WHITE)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, -1.0F);
        
        Map<String, VertexFormatElement> elements = type.format().getElementMapping();
        SampleQuads.INPUTS.forEach((name, raw) -> {
            VertexFormatElement element = elements.get(name);
            if (element != null) {
                vertex.misc(element, raw);
            }
        });
    }
    
    public static RenderPipeline solid() {
        return SampleQuads.SOLID;
    }
    
    public static RenderPipeline tinted() {
        return SampleQuads.TINTED;
    }
    
    public static RenderPipeline.Snippet texturedState() {
        return SampleQuads.TEXTURED_STATE;
    }
    
    public static RenderPipeline.Snippet entityState() {
        return RenderPipelines.ENTITY_SNIPPET;
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
    
    public static void showProgram(Pipeline pipeline, Vec3 center) {
        SampleQuads.shown = SampleQuads.PROGRAM_TYPES.computeIfAbsent(pipeline.getPipeline().get().get(), SampleQuads::programType);
        SampleQuads.center = center;
    }
    
    public static void throwMidVertex() {
        SampleQuads.throwMidVertex = true;
    }
    
    public static void hide() {
        SampleQuads.shown = null;
        SampleQuads.failure = null;
    }
    
    public static @Nullable RuntimeException failure() {
        return SampleQuads.failure;
    }
    
    public static List<String> brokenLogs() {
        return SampleQuads.BROKEN_LOGS;
    }
    
    public static void captureBrokenLogs() {
        Appender appender = new AbstractAppender("nexus_pipeline_samples", null, null, true, Property.EMPTY_ARRAY) {
            
            @Override
            public void append(LogEvent event) {
                String message = event.getMessage().getFormattedMessage();
                if (message.contains(SampleQuads.BROKEN_PROGRAM)) {
                    SampleQuads.BROKEN_LOGS.add(message);
                }
            }
        };
        appender.start();
        Logger logger = (Logger) LogManager.getLogger(Programs.class);
        logger.addAppender(appender);
        // The logger config created for the appender would otherwise keep the events from reaching the log files.
        logger.setAdditive(true);
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
        if (SampleQuads.throwMidVertex) {
            SampleQuads.throwMidVertex = false;
            buffers.getBuffer(type).addVertex(matrix, x, y, z);
            throw new IllegalStateException(SampleQuads.MID_VERTEX_FAILURE);
        }
        
        try {
            VertexConsumer buffer = buffers.getBuffer(type);
            SampleQuads.vertex(buffer, type, matrix, x + 1.0F, y - 1.0F, z);
            SampleQuads.vertex(buffer, type, matrix, x - 1.0F, y - 1.0F, z);
            SampleQuads.vertex(buffer, type, matrix, x - 1.0F, y + 1.0F, z);
            SampleQuads.vertex(buffer, type, matrix, x + 1.0F, y + 1.0F, z);
        } catch (RuntimeException exception) {
            SampleQuads.failure = exception;
        }
    }
}
