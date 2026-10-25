package dev.satherov.nexus.internal.render.client;

import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.internal.render.iris.IrisBridge;

import net.neoforged.fml.ModList;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

///
/// Runs the mods' draw hooks into one buffer source, ends the batches of pack-shaded types in the level and
/// holds the own-shaded ones for the after-level slot while a pack is active,
/// drawing a type with bound uniforms through a render pass of its own.
///
@ApiStatus.Internal
public final class DrawHooks {
    
    ///
    /// The draw hooks of the mods, in the order they were registered.
    ///
    private final List<BiConsumer<PoseStack, MultiBufferSource>> hooks;
    
    ///
    /// The pipelines of the mods by their render pipeline.
    ///
    private final Map<RenderPipeline, Pipeline> pipelines;
    
    ///
    /// The buffer source the hooks draw into.
    ///
    private final MultiBufferSource buffers = this::begin;
    
    ///
    /// The buffer builder of every render type the hooks drew into this frame, in the order they were first asked for.
    ///
    private final Map<RenderType, BufferBuilder> builderByType = new LinkedHashMap<>();
    
    ///
    /// The byte buffer builder of every render type drawn so far, kept for the next frame.
    ///
    private final Map<RenderType, ByteBufferBuilder> memoryByType = new HashMap<>();
    
    ///
    /// The batches held for the after-level slot, in the order they were ended.
    ///
    private final List<Batch> held = new ArrayList<>();
    
    ///
    /// Should only ever be called from [RenderDispatch].
    ///
    /// @param hooks     The draw hooks of the mods, in the order they were registered.
    /// @param pipelines The pipelines of the mods by their render pipeline.
    ///
    public DrawHooks(List<BiConsumer<PoseStack, MultiBufferSource>> hooks, Map<RenderPipeline, Pipeline> pipelines) {
        this.hooks = hooks;
        this.pipelines = pipelines;
    }
    
    ///
    /// Runs the hooks and ends what belongs in the level.
    ///
    /// @param pose The pose stack of the frame, whose origin is the camera.
    ///
    public void drawInLevel(PoseStack pose) {
        for (BiConsumer<PoseStack, MultiBufferSource> hook : this.hooks) {
            hook.accept(pose, this.buffers);
        }
        
        this.builderByType.forEach(this::end);
        this.builderByType.clear();
    }
    
    ///
    /// Starts a new buffer builder for the given render type,
    /// or continues the one of this frame if the render type can join consecutive geometry.
    ///
    private VertexConsumer begin(RenderType type) {
        BufferBuilder builder = this.builderByType.get(type);
        if (builder != null && type.canConsolidateConsecutiveGeometry()) {
            return builder;
        }
        
        if (builder != null) {
            this.end(type, builder);
        }
        
        BufferBuilder started = new BufferBuilder(this.memory(type), type.mode(), type.format());
        this.builderByType.put(type, started);
        return started;
    }
    
    ///
    /// Ends the batch of the given builder and then draws it,
    /// or holds it for the after-level slot if its pipeline is own-shaded and a pack is active.
    ///
    private void end(RenderType type, BufferBuilder builder) {
        MeshData mesh = builder.build();
        if (mesh == null) {
            return;
        }
        
        if (type.sortOnUpload()) {
            mesh.sortQuads(this.memory(type), RenderSystem.getProjectionType().vertexSorting());
        }
        
        Pipeline pipeline = this.pipelines.get(type.pipeline());
        Batch batch = new Batch(type, mesh, pipeline);
        if (pipeline != null && pipeline.getShading() == Shading.OWN && ModList.get().isLoaded("iris") && IrisBridge.isShaderPackInUse()) {
            this.held.add(batch);
            return;
        }
        
        batch.draw();
    }
    
    ///
    /// The byte buffer builder of the given render type, created on first use.
    ///
    private ByteBufferBuilder memory(RenderType type) {
        return this.memoryByType.computeIfAbsent(type, _ -> new ByteBufferBuilder(type.bufferSize()));
    }
    
    ///
    /// Ends what was held, with the stage's model-view matrix since that stage has no pose stack.
    ///
    /// @param modelView The model-view matrix of the frame.
    ///
    public void drawAfterLevel(Matrix4fc modelView) {
        if (this.held.isEmpty()) {
            return;
        }
        
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.mul(modelView);
        this.held.forEach(Batch::draw);
        stack.popMatrix();
        this.held.clear();
    }
    
    ///
    /// A batch of one render type, ended and ready to draw.
    ///
    /// @param type     The render type of the batch.
    /// @param mesh     The mesh of the batch, closed once drawn.
    /// @param pipeline The pipeline of a mod that the render type draws with, or `null` if no mod registered its render pipeline here.
    ///
    private record Batch(RenderType type, MeshData mesh, @Nullable Pipeline pipeline) {
        
        ///
        /// Draws the mesh like vanilla, or through a render pass of our own if the pipeline has bound uniforms.
        ///
        private void draw() {
            Map<String, GpuBufferSlice> uniforms = this.readUniforms();
            if (uniforms.isEmpty()) {
                this.type.draw(this.mesh);
                return;
            }
            
            try (this.mesh) {
                this.drawThroughPass(uniforms);
            }
        }
        
        ///
        /// Reads every bound uniform block of the pipeline once and throws on a block that the render pipeline does not declare.
        ///
        private Map<String, GpuBufferSlice> readUniforms() {
            if (this.pipeline == null) {
                return Map.of();
            }
            
            RenderPipeline renderPipeline = this.type.pipeline();
            Map<String, GpuBufferSlice> uniforms = new HashMap<>();
            this.pipeline.getBindings().forEach((block, value) -> {
                if (renderPipeline.getUniforms().stream().noneMatch(uniform -> uniform.name().equals(block))) {
                    throw new IllegalStateException("The pipeline '" + renderPipeline.getLocation() + "' does not declare the uniform '" + block + "'");
                }
                
                uniforms.put(block, value.get().get());
            });
            return uniforms;
        }
        
        ///
        /// Draws the mesh like [RenderType#draw(MeshData)] does, with the given uniform blocks bound as well.
        ///
        private void drawThroughPass(Map<String, GpuBufferSlice> uniforms) {
            RenderSetup setup = this.type.state;
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            Consumer<Matrix4fStack> layering = setup.layeringTransform.getModifier();
            if (layering != null) {
                layering.accept(modelView);
            }
            
            Matrix4f textureMatrix = setup.textureTransform.getMatrix();
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), textureMatrix);
            modelView.popMatrix();
            
            VertexFormat format = this.type.pipeline().getVertexFormat();
            GpuBuffer vertices = format.uploadImmediateVertexBuffer(this.mesh.vertexBuffer());
            ByteBuffer sortedIndices = this.mesh.indexBuffer();
            RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(this.mesh.drawState().mode());
            GpuBuffer indices = sortedIndices == null ? sequential.getBuffer(this.mesh.drawState().indexCount()) : format.uploadImmediateIndexBuffer(sortedIndices);
            VertexFormat.IndexType indexType = sortedIndices == null ? sequential.type() : this.mesh.drawState().indexType();
            
            RenderTarget target = this.type.outputTarget().getRenderTarget();
            GpuTextureView color = Objects.requireNonNullElseGet(RenderSystem.outputColorTextureOverride, () -> Objects.requireNonNull(target.getColorTextureView()));
            GpuTextureView depth = null;
            if (target.useDepth) {
                depth = Objects.requireNonNullElseGet(RenderSystem.outputDepthTextureOverride, () -> Objects.requireNonNull(target.getDepthTextureView()));
            }
            
            try (RenderPass pass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "Nexus draw hook for " + this.type, color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
                pass.setPipeline(this.type.pipeline());
                ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
                if (scissor.enabled()) {
                    pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
                }
                
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                uniforms.forEach(pass::setUniform);
                pass.setVertexBuffer(0, vertices);
                setup.getTextures().forEach((sampler, texture) -> pass.bindTexture(sampler, texture.textureView(), texture.sampler()));
                pass.setIndexBuffer(indices, indexType);
                pass.drawIndexed(0, 0, this.mesh.drawState().indexCount(), 1);
            }
        }
    }
}
