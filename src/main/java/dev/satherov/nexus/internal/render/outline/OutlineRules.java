package dev.satherov.nexus.internal.render.outline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import dev.satherov.nexus.api.render.outline.Outline;
import dev.satherov.nexus.api.render.outline.OutlineRule;
import dev.satherov.nexus.api.render.outline.Outlines;
import dev.satherov.nexus.api.render.outline.TargetedBlock;
import dev.satherov.nexus.internal.render.client.RenderDispatch;
import dev.satherov.nexus.internal.render.client.ShapePipelines;
import dev.satherov.nexus.internal.render.shader.Programs;
import dev.satherov.nexus.internal.render.shape.MeshBrush;
import dev.satherov.nexus.internal.render.shape.Primitives;

import net.neoforged.neoforge.client.CustomBlockOutlineRenderer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

///
/// A custom block outline renderer that draws the outlines the rules of every mod return.
///
@ApiStatus.Internal
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OutlineRules implements CustomBlockOutlineRenderer {
    
    ///
    /// The only instance.
    ///
    public static final OutlineRules INSTANCE = new OutlineRules();
    
    ///
    /// The order the render types are drawn in, lines last.
    ///
    private static final Comparator<RenderType> DRAW_ORDER = Comparator.comparing(type -> type.mode() == VertexFormat.Mode.LINES);
    
    ///
    /// Every replacing rule.
    ///
    private final List<OutlineRule> replacing = new CopyOnWriteArrayList<>();
    
    ///
    /// Every adding rule.
    ///
    private final List<OutlineRule> adding = new CopyOnWriteArrayList<>();
    
    ///
    /// The byte buffer builders by render type, kept across frames.
    ///
    private final Map<RenderType, ByteBufferBuilder> memoryByType = new HashMap<>();
    
    ///
    /// The meshes held back for [#drawAfterLevel(Matrix4fc)], by render type.
    ///
    private final Map<RenderType, MeshData> heldByType = new LinkedHashMap<>();
    
    ///
    /// The targeted block the kept primitives are for, or `null` if there is none.
    ///
    private @Nullable TargetedBlock target;
    
    ///
    /// The primitives of every outline the rules returned for the targeted block, the replacement first.
    ///
    private List<Primitives> primitives = List.of();
    
    ///
    /// If a replacing rule returned an outline for the targeted block.
    ///
    private boolean replaced;
    
    ///
    /// Records the shape of the given outline with the paint of the outline.
    ///
    /// @param outline The outline to record.
    ///
    /// @return The primitives of the outline.
    ///
    private static Primitives primitivesOf(Outline outline) {
        MeshBrush brush = new MeshBrush(outline.paint());
        outline.shape().draw(brush);
        return brush.finish();
    }
    
    ///
    /// Adds the given rule as a replacing rule.
    ///
    /// Should only ever be called from [Outlines#replace(OutlineRule)].
    ///
    /// @param rule The rule to add.
    ///
    public void replace(OutlineRule rule) {
        this.replacing.add(rule);
    }
    
    ///
    /// Adds the given rule as an adding rule.
    ///
    /// Should only ever be called from [Outlines#add(OutlineRule)].
    ///
    /// @param rule The rule to add.
    ///
    public void add(OutlineRule rule) {
        this.adding.add(rule);
    }
    
    ///
    /// Collects the outlines every rule returns for the given targeted block and records their primitives.
    ///
    /// If the targeted block equals the one of the last call, the kept primitives will be reused.
    ///
    /// @param target The targeted block.
    ///
    /// @return `true` if any rule returned an outline for the targeted block.
    ///
    public boolean update(TargetedBlock target) {
        if (target.equals(this.target)) {
            return !this.primitives.isEmpty();
        }
        
        Optional<Outline> replacement = this.replacing.stream()
                .flatMap(rule -> Stream.ofNullable(rule.outline(target)))
                .findFirst();
        // noinspection RedundantTypeArguments
        List<Outline> additions = this.adding.stream()
                // yeah, don't question why we need this, intelliJ says it's pointless, yet if we remove it,
                // it'll cry about nullability issues that aren't real.
                .<Outline>flatMap(rule -> Stream.ofNullable(rule.outline(target)))
                .toList();
        
        this.target = target;
        this.replaced = replacement.isPresent();
        this.primitives = Stream.concat(replacement.stream(), additions.stream()).map(OutlineRules::primitivesOf).toList();
        return !this.primitives.isEmpty();
    }
    
    ///
    /// Drops the kept primitives and the targeted block they are for.
    ///
    public void end() {
        this.target = null;
        this.primitives = List.of();
        this.replaced = false;
    }
    
    ///
    /// Draws the kept primitives and labels at the targeted block.
    ///
    /// Does nothing if the pass does not match the translucency of the targeted block.
    ///
    /// @param state           The block outline render-state of the frame.
    /// @param buffers         The buffer source the labels are drawn into.
    /// @param pose            The pose stack of the frame, whose origin is the camera.
    /// @param translucentPass If this is the pass of the translucent blocks.
    /// @param levelState      The level render-state of the frame.
    ///
    /// @return `true` if a replacing rule returned an outline.
    ///
    @Override
    public boolean render(BlockOutlineRenderState state, MultiBufferSource.BufferSource buffers, PoseStack pose, boolean translucentPass, LevelRenderState levelState) {
        if (state.isTranslucent() == translucentPass) {
            Vec3 origin = levelState.cameraRenderState.pos.subtract(Vec3.atLowerCornerOf(state.pos()));
            this.draw(origin);
            for (Primitives outline : this.primitives) {
                outline.drawLabels(pose, origin, buffers);
            }
        }
        
        return this.replaced;
    }
    
    ///
    /// Writes the kept primitives into one buffer per render type, relative to the given origin, and then draws every buffer, lines last.
    ///
    /// If the program of a render type could not be compiled, its buffer will be dropped.
    /// If a shader pack is in use, the buffers of the own-shaded render types will be held back for [#drawAfterLevel(Matrix4fc)].
    ///
    /// @param origin The position of the camera, in the coordinates of the outlines.
    ///
    private void draw(Vec3 origin) {
        List<RenderType> types = ShapePipelines.getAllRenderTypes().stream().sorted(OutlineRules.DRAW_ORDER).toList();
        Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        for (RenderType type : types) {
            builders.put(type, new BufferBuilder(this.memory(type), type.mode(), type.format()));
        }
        
        for (Primitives outline : this.primitives) {
            outline.writeInto(builders, origin);
        }
        
        builders.forEach((type, builder) -> {
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
            
            if (RenderDispatch.isDrawnAfterLevel(type.pipeline())) {
                this.heldByType.put(type, mesh);
                return;
            }
            
            type.draw(mesh);
        });
    }
    
    ///
    /// Applies the given model-view matrix and then draws every held back mesh.
    ///
    /// If a draw throws, every held back mesh will be dropped.
    ///
    /// @param modelView The model-view matrix of the frame.
    ///
    public void drawAfterLevel(Matrix4fc modelView) {
        if (this.heldByType.isEmpty()) {
            return;
        }
        
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.mul(modelView);
        try {
            this.heldByType.forEach(RenderType::draw);
        } finally {
            stack.popMatrix();
            this.heldByType.values().forEach(MeshData::close);
            this.heldByType.clear();
        }
    }
    
    ///
    /// The byte buffer builder of the given render type, created on first use.
    ///
    /// @param type The render type of the byte buffer builder.
    ///
    /// @return The byte buffer builder of the render type.
    ///
    private ByteBufferBuilder memory(RenderType type) {
        return this.memoryByType.computeIfAbsent(type, _ -> new ByteBufferBuilder(type.bufferSize()));
    }
}
