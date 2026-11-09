package dev.satherov.nexus.api.render.shape;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.internal.render.shape.SceneMesh;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

///
/// The drawings of one mod in the level of the client.
///
/// Every method throws an [IllegalStateException] off the client thread.
/// Every method but [#tick()] also throws one while the client is in no level.
///
/// Every drawing is removed once the level of the client unloads.
///
/// Can be accessed through [Render#getScene()].
///
public final class Scene {
    
    ///
    /// The mod the drawings belong to.
    ///
    private final NexusMod mod;
    
    ///
    /// Every active drawing.
    ///
    private final Set<Drawing> drawings = new LinkedHashSet<>();
    
    ///
    /// The active drawings under a key, by their key.
    ///
    private final Map<Object, Drawing> drawingByKey = new HashMap<>();
    
    ///
    /// The GPU buffers of the drawings.
    ///
    private final SceneMesh mesh = new SceneMesh();
    
    ///
    /// `true` if a drawing was added, changed, or removed since the buffers were last rebuilt.
    ///
    boolean changed;
    
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param mod The mod the drawings belong to.
    ///
    @ApiStatus.Internal
    public Scene(NexusMod mod) {
        this.mod = mod;
    }
    
    ///
    /// Draws the given shape with the given paint under the given key until the drawing is removed.
    ///
    /// If a drawing is already under an equal key, it will be removed first.
    ///
    /// @param key   The key to draw the shape under.
    /// @param shape The shape to draw.
    /// @param paint The paint to draw the shape with.
    ///
    /// @return The drawing of the shape.
    ///
    @CanIgnoreReturnValue
    public Drawing draw(Object key, Shape shape, Paint paint) {
        this.checkUsable();
        Drawing drawing = new Drawing(this, key, shape, paint);
        Drawing existing = this.drawingByKey.put(key, drawing);
        if (existing != null) {
            this.drop(existing);
        }
        
        this.drawings.add(drawing);
        this.changed = true;
        return drawing;
    }
    
    ///
    /// Draws the given shape with the given paint under no key until the drawing is removed.
    ///
    /// @param shape The shape to draw.
    /// @param paint The paint to draw the shape with.
    ///
    /// @return The drawing of the shape.
    ///
    @CanIgnoreReturnValue
    public Drawing draw(Shape shape, Paint paint) {
        this.checkUsable();
        Drawing drawing = new Drawing(this, null, shape, paint);
        this.drawings.add(drawing);
        this.changed = true;
        return drawing;
    }
    
    ///
    /// The drawing under the given key, or `null` if there is none.
    ///
    /// @param key The key of the drawing.
    ///
    /// @return The drawing under the key, or `null` if there is none.
    ///
    public @Nullable Drawing get(Object key) {
        this.checkUsable();
        return this.drawingByKey.get(key);
    }
    
    ///
    /// Removes the drawing under the given key.
    ///
    /// Does nothing if no drawing is under the key.
    ///
    /// @param key The key of the drawing.
    ///
    public void remove(Object key) {
        this.checkUsable();
        Drawing existing = this.drawingByKey.get(key);
        if (existing != null) {
            this.drop(existing);
        }
    }
    
    ///
    /// Removes every drawing.
    ///
    public void clear() {
        this.checkUsable();
        this.dropAll();
    }
    
    ///
    /// Ends and forgets every drawing.
    ///
    private void dropAll() {
        this.drawings.forEach(Drawing::end);
        this.drawings.clear();
        this.drawingByKey.clear();
        this.changed = true;
    }
    
    ///
    /// Ages every drawing by one client tick.
    ///
    /// Expired drawings and drawings whose entity left the level will be removed.
    ///
    /// Does nothing while the client is in no level.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    @ApiStatus.Internal
    public void tick() {
        this.checkThread();
        if (Minecraft.getInstance().level == null) {
            return;
        }
        
        for (Drawing drawing : List.copyOf(this.drawings)) {
            if (drawing.age()) {
                this.drop(drawing);
            }
        }
    }
    
    ///
    /// Removes the given drawing and ends it.
    ///
    /// @param drawing The drawing to remove.
    ///
    void drop(Drawing drawing) {
        this.drawings.remove(drawing);
        this.drawingByKey.remove(drawing.key, drawing);
        drawing.end();
        this.changed = true;
    }
    
    ///
    /// Removes every drawing and frees the GPU buffers.
    ///
    /// Works while the client is in no level, since the level may already be gone when it unloads.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @throws IllegalStateException If called off the client thread.
    ///
    @ApiStatus.Internal
    public void end() {
        this.checkThread();
        this.dropAll();
        this.mesh.close();
        this.changed = false;
    }
    
    ///
    /// Draws every drawing and its labels.
    ///
    /// If a drawing was added, changed, or removed since the last frame, the GPU buffers will be rebuilt first.
    /// If a shader pack is in use, the buffers of the discs, rings, and spheres will be held back for [#drawAfterLevel(Matrix4fc, float)].
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param pose        The pose stack of the frame, whose origin is the camera.
    /// @param partialTick The partial tick of the frame.
    /// @param buffers     The buffer source the labels are drawn into.
    ///
    @ApiStatus.Internal
    public void draw(PoseStack pose, float partialTick, MultiBufferSource.BufferSource buffers) {
        this.checkUsable();
        if (this.changed) {
            this.mesh.rebuild(this.drawings);
            this.changed = false;
        }
        
        this.mesh.draw(pose, partialTick);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        for (Drawing drawing : this.drawings) {
            drawing.drawLabels(pose, camera, partialTick, buffers);
        }
    }
    
    ///
    /// Draws the buffers of the discs, rings, and spheres after the level while a shader pack is in use.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param modelView   The model-view matrix of the frame.
    /// @param partialTick The partial tick of the frame.
    ///
    @ApiStatus.Internal
    public void drawAfterLevel(Matrix4fc modelView, float partialTick) {
        this.checkUsable();
        this.mesh.drawAfterLevel(modelView, partialTick);
    }
    
    ///
    /// Checks that the call is on the client thread and the client is in a level.
    ///
    /// @throws IllegalStateException If called off the client thread or while the client is in no level.
    ///
    private void checkUsable() {
        this.checkThread();
        if (Minecraft.getInstance().level == null) {
            throw new IllegalStateException("The scene of '" + this.mod.getModId() + "' was used while the client is in no level");
        }
    }
    
    ///
    /// Checks that the call is on the client thread.
    ///
    /// @throws IllegalStateException If called off the client thread.
    ///
    void checkThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("The scene of '" + this.mod.getModId() + "' was used on thread '" + Thread.currentThread().getName() + "'");
        }
    }
}
