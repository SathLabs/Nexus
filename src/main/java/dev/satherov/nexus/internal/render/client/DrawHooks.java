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
/// Runs the draw hooks of the mods and then draws what they drew, one batch per render type.
///
@ApiStatus.Internal
public final class DrawHooks {
    
    ///
    /// The draw hooks of the mods.
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
    /// The open buffer builder of this frame by render type.
    ///
    private final Map<RenderType, BufferBuilder> builderByType = new LinkedHashMap<>();
    
    ///
    /// The byte buffer builder by render type, kept across frames.
    ///
    private final Map<RenderType, ByteBufferBuilder> memoryByType = new HashMap<>();
    
    ///
    /// The batches held back for [#drawAfterLevel(Matrix4fc)].
    ///
    private final List<Batch> held = new ArrayList<>();
    
    ///
    /// Should only ever be called from [RenderDispatch].
    ///
    /// @param hooks     The draw hooks of the mods.
    /// @param pipelines The pipelines of the mods by their render pipeline.
    ///
    public DrawHooks(List<BiConsumer<PoseStack, MultiBufferSource>> hooks, Map<RenderPipeline, Pipeline> pipelines) {
        this.hooks = hooks;
        this.pipelines = pipelines;
    }
    
    ///
    /// Runs every draw hook and then draws or holds back the batch of every render type they drew into.
    ///
    /// @param pose The pose stack of the frame.
    ///
    public void drawInLevel(PoseStack pose) {
        for (BiConsumer<PoseStack, MultiBufferSource> hook : this.hooks) {
            hook.accept(pose, this.buffers);
        }
        
        this.builderByType.forEach(this::end);
        this.builderByType.clear();
    }
    
    ///
    /// Starts a new buffer builder for the given render type or
    /// continues the open one if the render type can consolidate consecutive geometry.
    ///
    /// @param type The render type a hook draws with.
    ///
    /// @return The buffer builder to draw into.
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
    /// Builds the mesh of the given buffer builder and then draws it as a batch.
    ///
    /// If the shading of its pipeline is [Shading#OWN] and a shader pack is in use, the batch will be held back for [#drawAfterLevel(Matrix4fc)].
    ///
    /// @param type    The render type of the buffer builder.
    /// @param builder The buffer builder to build the mesh of.
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
    /// Gets the byte buffer builder of the given render type, creating it on first use.
    ///
    /// @param type The render type of the byte buffer builder.
    ///
    /// @return The byte buffer builder.
    ///
    private ByteBufferBuilder memory(RenderType type) {
        return this.memoryByType.computeIfAbsent(type, _ -> new ByteBufferBuilder(type.bufferSize()));
    }
    
    ///
    /// Applies the given model-view matrix and then draws every held back batch.
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
    /// A batch of one render type, ready to draw.
    ///
    /// @param type     The render type of the batch.
    /// @param mesh     The mesh of the batch, closed once drawn.
    /// @param pipeline The mod pipeline of the render type, or `null` if none exists.
    ///
    private record Batch(RenderType type, MeshData mesh, @Nullable Pipeline pipeline) {
        
        ///
        /// Draws the mesh through the render type, or through a render pass of our own if the pipeline binds any uniform block.
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
        /// Reads the buffer slice of every uniform block the pipeline binds.
        ///
        /// @return The buffer slices by the name of their uniform block, or an empty map if the batch has no mod pipeline.
        ///
        /// @throws IllegalStateException If the render pipeline does not declare one of the bound uniform blocks.
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
        /// Draws the mesh the way [RenderType#draw(MeshData)] does and binds the given uniform blocks as well.
        ///
        /// @param uniforms The buffer slices of the uniform blocks to bind, by the name of their uniform block.
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
