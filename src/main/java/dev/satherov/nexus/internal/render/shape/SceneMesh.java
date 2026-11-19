package dev.satherov.nexus.internal.render.shape;

import dev.satherov.nexus.api.render.shape.Drawing;
import dev.satherov.nexus.internal.render.client.PackOverlay;
import dev.satherov.nexus.internal.render.client.ShapePipelines;
import dev.satherov.nexus.internal.render.iris.IrisBridge;
import dev.satherov.nexus.internal.render.shader.Programs;
import dev.satherov.nexus.internal.render.shader.VanillaBlock;

import net.neoforged.fml.ModList;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

///
/// The GPU buffers of one scene, one group per followed drawing plus one for the drawings that follow no entity.
///
/// Every group is drawn at its own origin into the render target of each render type, the unfollowed one first within each render type.
///
@ApiStatus.Internal
public final class SceneMesh implements AutoCloseable {
    
    ///
    /// The layering transform applied to the model view stack before drawing.
    ///
    private static final Consumer<Matrix4fStack> LAYERING = Objects.requireNonNull(LayeringTransform.VIEW_OFFSET_Z_LAYERING.getModifier());
    
    ///
    /// The distance in blocks that the camera may move away from the camera position of the last rebuild before the vertices lose precision.
    ///
    private static final double REBUILD_DISTANCE = 512.0D;
    
    ///
    /// The groups of buffers, in the order they are drawn.
    ///
    private final List<Group> groups = new ArrayList<>();
    
    ///
    /// Uploads the vertex data of the given drawings, relative to the given origin, into one buffer per render type that has any.
    ///
    /// @param drawings The drawings of the group.
    /// @param origin   The position that the vertices are relative to, in the coordinates of the drawings.
    /// @param memory   The byte buffer builders by render type, in the order they are drawn, their results closed.
    ///
    /// @return The buffers by render type, of the render types that have any vertex data.
    ///
    private static Map<RenderType, Buffer> upload(Collection<Drawing> drawings, Vec3 origin, Map<RenderType, ByteBufferBuilder> memory) {
        Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        memory.forEach((type, bytes) -> builders.put(type, new BufferBuilder(bytes, type.mode(), type.format())));
        
        for (Drawing drawing : drawings) {
            drawing.writeInto(builders, origin);
        }
        
        Map<RenderType, Buffer> buffers = new LinkedHashMap<>();
        builders.forEach((type, builder) -> {
            try (MeshData mesh = builder.build()) {
                if (mesh != null) {
                    GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> "Nexus scene " + type, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                    buffers.put(type, new Buffer(vertices, mesh.drawState().indexCount()));
                }
            }
        });
        return buffers;
    }
    
    ///
    /// Frees the buffers and then uploads the given drawings again.
    ///
    /// The drawings that follow no entity are written relative to the camera, and each followed drawing relative to its entity.
    ///
    /// @param drawings The drawings of the scene.
    ///
    public void rebuild(Collection<Drawing> drawings) {
        this.close();
        List<RenderType> types = ShapePipelines.getAllRenderTypes();
        Map<RenderType, ByteBufferBuilder> memory = new LinkedHashMap<>();
        try {
            for (RenderType type : types) {
                memory.put(type, new ByteBufferBuilder(type.bufferSize()));
            }
            
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            List<Drawing> unfollowed = drawings.stream().filter(drawing -> drawing.getEntity() == null).toList();
            this.groups.add(new Group(null, camera, SceneMesh.upload(unfollowed, camera, memory)));
            for (Drawing drawing : drawings) {
                Entity entity = drawing.getEntity();
                if (entity != null) {
                    this.groups.add(new Group(entity, Vec3.ZERO, SceneMesh.upload(List.of(drawing), Vec3.ZERO, memory)));
                }
            }
        } finally {
            memory.values().forEach(ByteBufferBuilder::close);
        }
    }
    
    ///
    /// If the vertices of the drawings that follow no entity lose too much precision at the given camera position.
    ///
    /// They are written relative to the camera position of the last rebuild.
    ///
    /// @param camera The position of the camera.
    ///
    /// @return `true` if the mesh should be rebuilt before it is drawn.
    ///
    public boolean isFarFrom(Vec3 camera) {
        return !this.groups.isEmpty() && !this.groups.getFirst().origin().closerThan(camera, SceneMesh.REBUILD_DISTANCE);
    }
    
    ///
    /// Gets the position of the given entity, interpolated with the partial tick that vanilla renders the entity with.
    ///
    /// If the frozen tick rate stops the entity, the position will be the one of its last tick.
    ///
    /// @param entity       The entity.
    /// @param deltaTracker The delta tracker of the frame.
    ///
    /// @return The position of the entity.
    ///
    public static Vec3 positionOf(Entity entity, DeltaTracker deltaTracker) {
        return entity.getPosition(deltaTracker.getGameTimeDeltaPartialTick(!entity.level().tickRateManager().isEntityFrozen(entity)));
    }
    
    ///
    /// Draws every buffer, each group at its interpolated origin, through one render pass per render target that the render types draw into.
    ///
    /// The buffers are drawn in the order of [ShapePipelines#getAllRenderTypes()], with the groups in their order within each render type.
    /// The shape render types draw into the [PackOverlay] while a shader pack is in use.
    /// Looking up the overlay seeds it, so the targets are looked up before any pass opens.
    /// A buffer whose program could not be compiled is skipped.
    ///
    /// A line buffer gets the offset of its group as its model offset, and every other buffer gets it in its model-view matrix.
    /// If a shader pack is in use, a line buffer will get it in its model-view matrix too.
    ///
    /// @param pose         The pose stack of the frame, whose origin is the camera.
    /// @param deltaTracker The delta tracker of the frame.
    ///
    public void draw(PoseStack pose, DeltaTracker deltaTracker) {
        Map<RenderTarget, List<RenderType>> typesByTarget = new LinkedHashMap<>();
        Map<VertexFormat.Mode, Integer> indexCounts = new EnumMap<>(VertexFormat.Mode.class);
        Map<RenderType, Map<String, RenderSetup.TextureAndSampler>> texturesByType = new HashMap<>();
        for (RenderType type : ShapePipelines.getAllRenderTypes()) {
            if (Programs.isBroken(type.pipeline()) || this.groups.stream().noneMatch(group -> group.buffers().containsKey(type))) {
                continue;
            }
            
            typesByTarget.computeIfAbsent(type.outputTarget().getRenderTarget(), _ -> new ArrayList<>()).add(type);
            // Looking up a missing texture loads and uploads it, which isn't allowed inside an open render pass, so we look up every one here first.
            texturesByType.put(type, ShapePipelines.getTextures(type));
            for (Group group : this.groups) {
                Buffer buffer = group.buffers().get(type);
                if (buffer != null) {
                    indexCounts.merge(type.mode(), buffer.indexCount(), Math::max);
                }
            }
        }
        
        if (indexCounts.isEmpty()) {
            return;
        }
        
        // Growing an index buffer uploads it, which isn't allowed inside an open render pass, so we grow every one here first.
        indexCounts.forEach((mode, count) -> RenderSystem.getSequentialBuffer(mode).getBuffer(count));
        
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        // A shader pack draws the lines with its own program, which might ignore the model offset.
        boolean offsetLines = !(ModList.get().isLoaded("iris") && IrisBridge.isShaderPackInUse());
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        SceneMesh.LAYERING.accept(modelView);
        modelView.mul(pose.last().pose());
        List<GpuBufferSlice> transforms = new ArrayList<>();
        List<GpuBufferSlice> lineTransforms = new ArrayList<>();
        for (Group group : this.groups) {
            Vector3f offset = group.at(deltaTracker).subtract(camera).toVector3f();
            Matrix4f transform = new Matrix4f(modelView).translate(offset);
            transforms.add(RenderSystem.getDynamicUniforms().writeTransform(transform, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f()));
            if (offsetLines) {
                lineTransforms.add(RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), offset, new Matrix4f()));
            }
        }
        
        modelView.popMatrix();
        
        typesByTarget.forEach((target, types) -> {
            //noinspection DataFlowIssue Yes, target#getColorTextureView might be null, but only after the buffer has already been destroyed, so it doesn't matter.
            try (RenderPass pass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(() -> "Nexus scene", target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                RenderSystem.bindDefaultUniforms(pass);
                for (RenderType type : types) {
                    // The line shader adds the model offset itself, so it gets the model-view matrix without the offset of the group.
                    List<GpuBufferSlice> placements = offsetLines && type.mode() == VertexFormat.Mode.LINES ? lineTransforms : transforms;
                    RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(type.mode());
                    for (int i = 0; i < this.groups.size(); i++) {
                        Buffer buffer = this.groups.get(i).buffers().get(type);
                        if (buffer != null) {
                            pass.setUniform(VanillaBlock.DYNAMIC_TRANSFORMS.getGlslName(), placements.get(i));
                            pass.setPipeline(type.pipeline());
                            for (Map.Entry<String, RenderSetup.TextureAndSampler> texture : texturesByType.get(type).entrySet()) {
                                pass.bindTexture(texture.getKey(), texture.getValue().textureView(), texture.getValue().sampler());
                            }
                            
                            pass.setVertexBuffer(0, buffer.vertices());
                            pass.setIndexBuffer(indices.getBuffer(buffer.indexCount()), indices.type());
                            pass.drawIndexed(0, 0, buffer.indexCount(), 1);
                        }
                    }
                }
            }
        });
    }
    
    ///
    /// Frees the GPU buffers.
    ///
    /// The mesh draws nothing until it is rebuilt.
    ///
    @Override
    public void close() {
        for (Group group : this.groups) {
            group.buffers().values().forEach(buffer -> buffer.vertices().close());
        }
        
        this.groups.clear();
    }
    
    ///
    /// The buffers of the drawings that share one origin.
    ///
    /// @param entity  The entity the origin is relative to, or `null` if the origin is a world position.
    /// @param origin  The position that the vertices are relative to, relative to the entity if there is one.
    /// @param buffers The buffers by render type, of every render type that has vertex data.
    ///
    private record Group(@Nullable Entity entity, Vec3 origin, Map<RenderType, Buffer> buffers) {
        
        ///
        /// The world position of the origin, interpolated between ticks as vanilla renders the entity if the group follows one.
        ///
        /// @param deltaTracker The delta tracker of the frame.
        ///
        /// @return The world position that the vertices are relative to.
        ///
        private Vec3 at(DeltaTracker deltaTracker) {
            return this.entity == null ? this.origin : SceneMesh.positionOf(this.entity, deltaTracker).add(this.origin);
        }
    }
    
    ///
    /// The vertex buffer of one render type.
    ///
    /// @param vertices   The vertex buffer on the GPU.
    /// @param indexCount The number of indices the vertices are drawn with.
    ///
    private record Buffer(GpuBuffer vertices, int indexCount) { }
}
