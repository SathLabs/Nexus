package dev.satherov.nexus.internal.render.client;

import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.internal.render.shader.Programs;
import dev.satherov.nexus.internal.render.shader.VanillaBlock;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.OutputTarget;
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
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

///
/// Runs the draw hooks of the mods and then draws what they drew, one batch per render type.
///
@Slf4j
@ApiStatus.Internal
public final class DrawHooks {
    
    ///
    /// The draw hooks of the mods.
    ///
    private final List<Hook> hooks;
    
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
    /// The uniform blocks that each mod pipeline had bound when [#checkBindings(RenderType)] last passed for it.
    ///
    private final Map<Pipeline, Set<String>> checkedBindings = new HashMap<>();
    
    ///
    /// The byte buffer builder by render type, kept across frames.
    ///
    private final Map<RenderType, ByteBufferBuilder> memoryByType = new HashMap<>();
    
    ///
    /// The draw hooks that were already logged for leaving a pose pushed.
    ///
    private final Set<Hook> leaking = new HashSet<>();
    
    ///
    /// Should only ever be called from [RenderDispatch].
    ///
    /// @param hooks     The draw hooks of the mods.
    /// @param pipelines The pipelines of the mods by their render pipeline.
    ///
    public DrawHooks(List<Hook> hooks, Map<RenderPipeline, Pipeline> pipelines) {
        this.hooks = hooks;
        this.pipelines = pipelines;
    }
    
    ///
    /// Runs every draw hook and then draws the batch of every render type they drew into.
    ///
    /// The pose stack will be reset to its pose on entry after every hook, and a hook that leaves a pose pushed will be logged the first time.
    ///
    /// If a hook or a batch throws, every open buffer builder will be dropped and every byte buffer builder freed.
    ///
    /// @param pose The pose stack of the frame, with no pose pushed.
    ///
    public void drawInLevel(PoseStack pose) {
        PoseStack.Pose entry = pose.last().copy();
        boolean drawn = false;
        try {
            for (Hook hook : this.hooks) {
                hook.drawer().accept(pose, this.buffers);
                if (!pose.isEmpty()) {
                    if (this.leaking.add(hook)) {
                        DrawHooks.log.error("The draw hook of '{}' left a pose pushed on the pose stack", hook.mod().getModId());
                    }
                    
                    while (!pose.isEmpty()) {
                        pose.popPose();
                    }
                }
                
                pose.last().set(entry);
            }
            
            this.builderByType.forEach(this::end);
            drawn = true;
        } finally {
            this.builderByType.clear();
            if (!drawn) {
                this.drop();
            }
        }
    }
    
    ///
    /// Frees every byte buffer builder.
    ///
    /// A byte buffer builder may still hold the vertices of a buffer builder that never built its mesh, which would end up in the next mesh built from it.
    ///
    private void drop() {
        this.memoryByType.values().forEach(ByteBufferBuilder::close);
        this.memoryByType.clear();
    }
    
    ///
    /// Starts a new buffer builder for the given render type or
    /// continues the open one if the render type can consolidate consecutive geometry.
    ///
    /// @param type The render type a hook draws with.
    ///
    /// @return The buffer builder to draw into.
    ///
    /// @throws IllegalStateException If a new buffer builder is started and the mod pipeline of the render type fails [#checkBindings(RenderType)].
    ///
    private VertexConsumer begin(RenderType type) {
        BufferBuilder builder = this.builderByType.get(type);
        if (builder != null && type.canConsolidateConsecutiveGeometry()) {
            return builder;
        }
        
        if (builder != null) {
            this.end(type, builder);
        }
        
        this.checkBindings(type);
        BufferBuilder started = new BufferBuilder(this.memory(type), type.mode(), type.format());
        this.builderByType.put(type, started);
        return started;
    }
    
    ///
    /// Checks the bindings of the mod pipeline of the given render type, if it has one.
    ///
    /// Checks again only once the bound blocks differ from the last check that passed.
    ///
    /// @param type The render type a hook draws with.
    ///
    /// @throws IllegalStateException If the mod pipeline binds a uniform block that the render pipeline does not declare,
    /// or if its program file declares a uniform block other than a [VanillaBlock] that is never bound.
    ///
    private void checkBindings(RenderType type) {
        RenderPipeline renderPipeline = type.pipeline();
        Pipeline pipeline = this.pipelines.get(renderPipeline);
        if (pipeline == null) {
            return;
        }
        
        Set<String> bound = pipeline.getBindings().keySet();
        if (bound.equals(this.checkedBindings.get(pipeline))) {
            return;
        }
        
        Set<String> declared = new HashSet<>();
        renderPipeline.getUniforms().forEach(uniform -> declared.add(uniform.name()));
        for (String block : bound) {
            if (!declared.contains(block)) {
                throw new IllegalStateException("The pipeline '" + renderPipeline.getLocation() + "' does not declare the uniform '" + block + "'");
            }
        }
        
        for (String block : Programs.blocks(renderPipeline)) {
            if (!bound.contains(block) && VanillaBlock.byName(block) == null) {
                throw new IllegalStateException("The pipeline '" + renderPipeline.getLocation() + "' does not bind the uniform '" + block + "' that its program declares");
            }
        }
        
        this.checkedBindings.put(pipeline, Set.copyOf(bound));
    }
    
    ///
    /// Builds the mesh of the given buffer builder and then draws it as a batch.
    ///
    /// If the program of its pipeline could not be compiled, the batch will be dropped.
    ///
    /// @param type    The render type of the buffer builder.
    /// @param builder The buffer builder to build the mesh of.
    ///
    private void end(RenderType type, BufferBuilder builder) {
        MeshData mesh = builder.build();
        if (mesh == null) {
            return;
        }
        
        if (Programs.isBroken(type.pipeline())) {
            mesh.close();
            return;
        }
        
        if (type.sortOnUpload()) {
            mesh.sortQuads(this.memory(type), RenderSystem.getProjectionType().vertexSorting());
        }
        
        new Batch(type, mesh, this.pipelines.get(type.pipeline())).draw();
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
    /// A draw hook of a mod.
    ///
    /// @param mod    The mod that registered the draw hook.
    /// @param drawer The draw hook, called with the pose stack and the buffer source.
    ///
    public record Hook(NexusMod mod, BiConsumer<PoseStack, MultiBufferSource> drawer) { }
    
    ///
    /// A batch of one render type, ready to draw.
    ///
    /// @param type     The render type of the batch.
    /// @param mesh     The mesh of the batch, closed once drawn.
    /// @param pipeline The mod pipeline of the render type, or `null` if none exists.
    ///
    private record Batch(RenderType type, MeshData mesh, @Nullable Pipeline pipeline) {
        
        ///
        /// Draws the mesh through the render type, or through a render pass of our own
        /// if the pipeline binds any uniform block or is [Shading#OWN] while the [PackOverlay] is armed.
        ///
        /// The pass of an own-shaded pipeline draws into the overlay instead of the target of its render type, unless that is the outline target.
        /// The mesh will be closed even if the draw throws.
        ///
        private void draw() {
            try (this.mesh) {
                Map<String, GpuBufferSlice> uniforms = this.readUniforms();
                boolean redirected = this.pipeline != null && this.pipeline.getShading() == Shading.OWN && PackOverlay.isArmed() && this.type.outputTarget() != OutputTarget.OUTLINE_TARGET;
                if (uniforms.isEmpty() && !redirected) {
                    this.type.draw(this.mesh); // Legal because closing the mesh a second time does nothing
                    return;
                }
                
                this.drawThroughPass(uniforms, redirected ? PackOverlay.target() : this.type.outputTarget().getRenderTarget());
            }
        }
        
        ///
        /// Reads the buffer slice of every uniform block the pipeline binds.
        ///
        /// @return The buffer slices by the name of their uniform block, or an empty map if the batch has no mod pipeline.
        ///
        private Map<String, GpuBufferSlice> readUniforms() {
            if (this.pipeline == null) {
                return Map.of();
            }
            
            Map<String, GpuBufferSlice> uniforms = new HashMap<>();
            this.pipeline.getBindings().forEach((block, value) -> uniforms.put(block, value.get().get()));
            return uniforms;
        }
        
        ///
        /// Draws the mesh the way [RenderType#draw(MeshData)] does, onto the given target, and binds the given uniform blocks as well.
        ///
        /// @param uniforms The buffer slices of the uniform blocks to bind, by the name of their uniform block.
        /// @param target   The render target to draw onto.
        ///
        private void drawThroughPass(Map<String, GpuBufferSlice> uniforms, RenderTarget target) {
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
            
            // Read the textures before the pass opens, because looking one up uploads it if it isn't loaded yet, and an upload inside an open render pass fails.
            Map<String, RenderSetup.TextureAndSampler> textures = setup.getTextures();
            
            VertexFormat format = this.type.pipeline().getVertexFormat();
            GpuBuffer vertices = format.uploadImmediateVertexBuffer(this.mesh.vertexBuffer());
            ByteBuffer sortedIndices = this.mesh.indexBuffer();
            RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(this.mesh.drawState().mode());
            GpuBuffer indices = sortedIndices == null ? sequential.getBuffer(this.mesh.drawState().indexCount()) : format.uploadImmediateIndexBuffer(sortedIndices);
            VertexFormat.IndexType indexType = sortedIndices == null ? sequential.type() : this.mesh.drawState().indexType();
            
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
                pass.setUniform(VanillaBlock.DYNAMIC_TRANSFORMS.getGlslName(), transforms);
                uniforms.forEach(pass::setUniform);
                pass.setVertexBuffer(0, vertices);
                textures.forEach((sampler, texture) -> pass.bindTexture(sampler, texture.textureView(), texture.sampler()));
                pass.setIndexBuffer(indices, indexType);
                pass.drawIndexed(0, 0, this.mesh.drawState().indexCount(), 1);
            }
        }
    }
}
