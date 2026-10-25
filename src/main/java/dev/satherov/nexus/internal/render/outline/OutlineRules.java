package dev.satherov.nexus.internal.render.outline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import dev.satherov.nexus.api.render.outline.Outline;
import dev.satherov.nexus.api.render.outline.OutlineRule;
import dev.satherov.nexus.api.render.outline.Outlines;
import dev.satherov.nexus.api.render.outline.TargetedBlock;
import dev.satherov.nexus.internal.render.client.ShapePipelines;
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
/// Every outline rule of every mod, in registration order: asks them at the outline extraction when the target changed, keeps the results
/// and their primitives, and is the custom outline renderer added to the extraction when any rule answered.
///
@ApiStatus.Internal
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OutlineRules implements CustomBlockOutlineRenderer {
    
    ///
    /// The rules.
    ///
    public static final OutlineRules INSTANCE = new OutlineRules();
    
    ///
    /// The order the render types are drawn in, lines last.
    ///
    private static final Comparator<RenderType> DRAW_ORDER = Comparator.comparing(type -> type.mode() == VertexFormat.Mode.LINES);
    
    ///
    /// Every replacing rule, in the order they were added.
    ///
    private final List<OutlineRule> replacing = new CopyOnWriteArrayList<>();
    
    ///
    /// Every adding rule, in the order they were added.
    ///
    private final List<OutlineRule> adding = new CopyOnWriteArrayList<>();
    
    ///
    /// The byte buffer builder of every render type drawn so far, kept for the next frame.
    ///
    private final Map<RenderType, ByteBufferBuilder> memoryByType = new HashMap<>();
    
    ///
    /// The target the kept results are for, or `null` if no block was targeted yet.
    ///
    private @Nullable TargetedBlock target;
    
    ///
    /// The primitives of every outline the rules answered with for the target, the replacement first.
    ///
    private List<Primitives> primitives = List.of();
    
    ///
    /// If a replacing rule answered for the target.
    ///
    private boolean replaced;
    
    ///
    /// Records the shape of the given outline with its paint.
    ///
    private static Primitives primitivesOf(Outline outline) {
        MeshBrush brush = new MeshBrush(outline.paint());
        outline.shape().draw(brush);
        return brush.finish();
    }
    
    ///
    /// Adds a replacing rule after every rule added so far.
    ///
    /// Should only ever be called from [Outlines#replace(OutlineRule)].
    ///
    /// @param rule The rule to add.
    ///
    public void replace(OutlineRule rule) {
        this.replacing.add(rule);
    }
    
    ///
    /// Adds an adding rule after every rule added so far.
    ///
    /// Should only ever be called from [Outlines#add(OutlineRule)].
    ///
    /// @param rule The rule to add.
    ///
    public void add(OutlineRule rule) {
        this.adding.add(rule);
    }
    
    ///
    /// Updates the kept results for the target, asking the rules only when it changed.
    ///
    /// @param target The block under the crosshair.
    ///
    /// @return `true` if any rule answered for the target.
    ///
    public boolean update(TargetedBlock target) {
        if (target.equals(this.target)) {
            return !this.primitives.isEmpty();
        }
        
        Optional<Outline> replacement = this.replacing.stream()
                .flatMap(rule -> Stream.ofNullable(rule.outline(target)))
                .findFirst();
        List<Outline> additions = this.adding.stream()
                .flatMap(rule -> Stream.ofNullable(rule.outline(target)))
                .toList();
        
        this.target = target;
        this.replaced = replacement.isPresent();
        this.primitives = Stream.concat(replacement.stream(), additions.stream()).map(OutlineRules::primitivesOf).toList();
        return !this.primitives.isEmpty();
    }
    
    ///
    /// Drops the kept results and their primitives. Called whenever the level of the client unloads, on a dimension change and on logout alike.
    ///
    public void end() {
        this.target = null;
        this.primitives = List.of();
        this.replaced = false;
    }
    
    ///
    /// Draws the kept outlines at the block's position in the pass that matches the block.
    ///
    /// @param state           The block outline render state of the frame.
    /// @param buffers         The buffer source the labels are drawn into.
    /// @param pose            The pose stack of the frame, whose origin is the camera.
    /// @param translucentPass If this is the pass of the translucent blocks.
    /// @param levelState      The level render state of the frame.
    ///
    /// @return `true` if a replacing rule answered, which hides vanilla's outline.
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
    /// Writes the kept primitives into one buffer per render type, relative to the given origin, and then draws each buffer, lines last.
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
            
            if (type.sortOnUpload()) {
                mesh.sortQuads(this.memory(type), RenderSystem.getProjectionType().vertexSorting());
            }
            
            type.draw(mesh);
        });
    }
    
    ///
    /// The byte buffer builder of the given render type, created on first use.
    ///
    private ByteBufferBuilder memory(RenderType type) {
        return this.memoryByType.computeIfAbsent(type, _ -> new ByteBufferBuilder(type.bufferSize()));
    }
}
