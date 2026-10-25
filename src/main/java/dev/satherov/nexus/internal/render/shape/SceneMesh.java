package dev.satherov.nexus.internal.render.shape;

import dev.satherov.nexus.api.render.shape.Drawing;
import dev.satherov.nexus.internal.render.client.ShapePipelines;

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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

///
/// The GPU buffers of one scene, one per render type, plus one group per followed drawing. Rebuilt when the scene marks itself changed.
///
/// Every group is drawn at its own origin, the drawings that follow no entity first.
///
@ApiStatus.Internal
public final class SceneMesh implements AutoCloseable {
    
    ///
    /// The layering transform that every render type of [ShapePipelines] is set up with.
    ///
    private static final Consumer<Matrix4fStack> LAYERING = Objects.requireNonNull(LayeringTransform.VIEW_OFFSET_Z_LAYERING.getModifier());
    
    ///
    /// The order the render types of a group are drawn in, lines last.
    ///
    private static final Comparator<RenderType> DRAW_ORDER = Comparator.comparing(type -> type.mode() == VertexFormat.Mode.LINES);
    
    ///
    /// The groups of buffers, in the order they are drawn.
    ///
    private final List<Group> groups = new ArrayList<>();
    
    ///
    /// Uploads the vertex data of the given drawings, relative to the given origin, into one buffer per render type that has any.
    ///
    /// @param drawings The drawings of the group.
    /// @param origin   The position that the vertices are relative to, in the coordinates of the drawings.
    /// @param memory   The byte buffer builder of every render type, in the order they are drawn, each without an open result.
    ///
    /// @return The buffers of the render types that have any vertex data.
    ///
    private static List<Buffer> upload(Collection<Drawing> drawings, Vec3 origin, Map<RenderType, ByteBufferBuilder> memory) {
        Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        memory.forEach((type, bytes) -> builders.put(type, new BufferBuilder(bytes, type.mode(), type.format())));
        
        for (Drawing drawing : drawings) {
            drawing.writeInto(builders, origin);
        }
        
        List<Buffer> buffers = new ArrayList<>();
        builders.forEach((type, builder) -> {
            try (MeshData mesh = builder.build()) {
                if (mesh != null) {
                    GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> "Nexus scene " + type, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
                    buffers.add(new Buffer(type, vertices, mesh.drawState().indexCount()));
                }
            }
        });
        return buffers;
    }
    
    ///
    /// Rebuilds every buffer from the drawings' primitives.
    ///
    /// The drawings that follow no entity are written relative to the camera, and each followed drawing relative to its entity.
    ///
    /// @param drawings The active drawings of the scene.
    ///
    public void rebuild(Collection<Drawing> drawings) {
        this.close();
        List<RenderType> types = ShapePipelines.getAllRenderTypes().stream().sorted(SceneMesh.DRAW_ORDER).toList();
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
    /// Draws every buffer through a render pass on the main target, the followed groups at their interpolated origin.
    ///
    /// @param pose        The pose stack of the frame, whose origin is the camera.
    /// @param partialTick The partial tick of the frame.
    ///
    public void draw(PoseStack pose, float partialTick) {
        Map<VertexFormat.Mode, Integer> indexCounts = new EnumMap<>(VertexFormat.Mode.class);
        for (Group group : this.groups) {
            for (Buffer buffer : group.buffers()) {
                indexCounts.merge(buffer.type().mode(), buffer.indexCount(), Math::max);
            }
        }
        
        if (indexCounts.isEmpty()) {
            return;
        }
        
        // Growing an index buffer uploads it, which an open render pass doesn't allow, so every one is grown here.
        indexCounts.forEach((mode, count) -> RenderSystem.getSequentialBuffer(mode).getBuffer(count));
        
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        SceneMesh.LAYERING.accept(modelView);
        modelView.mul(pose.last().pose());
        List<GpuBufferSlice> transforms = new ArrayList<>();
        for (Group group : this.groups) {
            Vec3 offset = group.at(partialTick).subtract(camera);
            Matrix4f transform = new Matrix4f(modelView).translate((float) offset.x, (float) offset.y, (float) offset.z);
            transforms.add(RenderSystem.getDynamicUniforms().writeTransform(transform, new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f()));
        }
        
        modelView.popMatrix();
        
        RenderTarget target = minecraft.getMainRenderTarget();
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Nexus scene", target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            for (int i = 0; i < this.groups.size(); i++) {
                pass.setUniform("DynamicTransforms", transforms.get(i));
                for (Buffer buffer : this.groups.get(i).buffers()) {
                    RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(buffer.type().mode());
                    pass.setPipeline(buffer.type().pipeline());
                    for (Map.Entry<String, RenderSetup.TextureAndSampler> texture : ShapePipelines.getTextures(buffer.type()).entrySet()) {
                        pass.bindTexture(texture.getKey(), texture.getValue().textureView(), texture.getValue().sampler());
                    }
                    
                    pass.setVertexBuffer(0, buffer.vertices());
                    pass.setIndexBuffer(indices.getBuffer(buffer.indexCount()), indices.type());
                    pass.drawIndexed(0, 0, buffer.indexCount(), 1);
                }
            }
        }
    }
    
    ///
    /// Frees the GPU buffers.
    ///
    /// The mesh draws nothing until it is rebuilt.
    ///
    @Override
    public void close() {
        for (Group group : this.groups) {
            group.buffers().forEach(buffer -> buffer.vertices().close());
        }
        
        this.groups.clear();
    }
    
    ///
    /// The buffers of the drawings that share one origin.
    ///
    /// @param entity  The entity the origin is relative to, or `null` if the origin is a world position.
    /// @param origin  The position that the vertices are relative to, relative to the entity if there is one.
    /// @param buffers The buffers of every render type that has vertex data, in the order they are drawn.
    ///
    private record Group(@Nullable Entity entity, Vec3 origin, List<Buffer> buffers) {
        
        ///
        /// The world position of the origin, interpolated between ticks if the group follows an entity.
        ///
        /// @param partialTick The partial tick of the frame.
        ///
        /// @return The world position that the vertices are relative to.
        ///
        private Vec3 at(float partialTick) {
            return this.entity == null ? this.origin : this.entity.getPosition(partialTick).add(this.origin);
        }
    }
    
    ///
    /// The vertex buffer of one render type.
    ///
    /// @param type       The render type the vertices are drawn with.
    /// @param vertices   The vertex buffer on the GPU.
    /// @param indexCount The number of indices the vertices are drawn with.
    ///
    private record Buffer(RenderType type, GpuBuffer vertices, int indexCount) { }
}
