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
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

///
/// The drawings of one mod in the client's level.
///
/// Every method throws an [IllegalStateException] off the client thread,
/// and every method but [#tick()] also while the client is in no level.
///
/// Every drawing is dropped whenever the level of the client unloads, on a dimension change and on logout alike.
///
/// Can be accessed through [Render#getScene()].
///
public final class Scene {
    
    ///
    /// The handle of the mod.
    ///
    private final NexusMod mod;
    
    ///
    /// Every active drawing, keyed or not, in the order they were drawn.
    ///
    private final Set<Drawing> drawings = new LinkedHashSet<>();
    
    ///
    /// The active drawings under a key, by key.
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
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Scene(NexusMod mod) {
        this.mod = mod;
    }
    
    ///
    /// Draws the shape until removed.
    ///
    /// A drawing of this mod under an equal key is removed first.
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
    /// Draws the shape until removed, under no key, so only its handle reaches it.
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
    /// The active drawing under the key.
    ///
    /// @param key The key of the drawing.
    ///
    /// @return The active drawing under the key, or `null` if there is none.
    ///
    public @Nullable Drawing get(Object key) {
        this.checkUsable();
        return this.drawingByKey.get(key);
    }
    
    ///
    /// Removes the drawing under the key.
    ///
    /// Does nothing without one.
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
    /// Removes every drawing of this mod, keyed or not.
    ///
    public void clear() {
        this.checkUsable();
        this.drawings.forEach(Drawing::end);
        this.drawings.clear();
        this.drawingByKey.clear();
        this.changed = true;
    }
    
    ///
    /// Ages every drawing by one tick and removes the expired and the entity-less ones.
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
    /// Removes the given drawing from this scene and marks it as removed.
    ///
    /// @param drawing The active drawing to remove.
    ///
    void drop(Drawing drawing) {
        this.drawings.remove(drawing);
        this.drawingByKey.remove(drawing.key, drawing);
        drawing.end();
        this.changed = true;
    }
    
    ///
    /// Ends every drawing and frees the buffers.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    @ApiStatus.Internal
    public void end() {
        this.clear();
        this.mesh.close();
        this.changed = false;
    }
    
    ///
    /// Draws the scene's buffers, rebuilding them first if a drawing changed, and its labels.
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
    /// Throws an [IllegalStateException] if called off the client thread or while the client is in no level.
    ///
    private void checkUsable() {
        this.checkThread();
        if (Minecraft.getInstance().level == null) {
            throw new IllegalStateException("The scene of '" + this.mod.getModId() + "' was used while the client is in no level");
        }
    }
    
    ///
    /// Throws an [IllegalStateException] if called off the client thread.
    ///
    void checkThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("The scene of '" + this.mod.getModId() + "' was used on thread '" + Thread.currentThread().getName() + "'");
        }
    }
}
