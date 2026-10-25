package dev.satherov.nexus.api.render.glow;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.internal.render.glow.GlowingBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

///
/// The glowing outlines one mod gives entities and blocks.
///
/// Every method throws an [IllegalStateException] off the client thread,
/// and every method but [#tick()] and [#hasAny()] also while the client is in no level.
///
/// Every glow is dropped whenever the level of the client unloads, on a dimension change and on logout alike.
///
/// Can be accessed through [Render#getGlows()].
///
public final class Glows {
    
    ///
    /// The handle of the mod.
    ///
    private final NexusMod mod;
    
    ///
    /// The active glows by key, in the order they were made.
    ///
    private final Map<Object, Glow> glowByKey = new LinkedHashMap<>();
    
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Glows(NexusMod mod) {
        this.mod = mod;
    }
    
    ///
    /// Outlines the entity in the color until removed, over its team color.
    ///
    /// A glow of this mod under an equal key is removed first.
    ///
    /// The glow ends when the entity leaves the client's level.
    ///
    /// @param key    The key to make the glow under.
    /// @param entity The entity to outline.
    /// @param argb   The ARGB color of the outline.
    ///
    /// @return The glow of the entity.
    ///
    @CanIgnoreReturnValue
    public Glow entity(Object key, Entity entity, int argb) {
        this.checkUsable();
        return this.put(new Glow(this, key, argb, entity));
    }
    
    ///
    /// Outlines the block at the position in the color until removed, following its state.
    ///
    /// A glow of this mod under an equal key is removed first.
    ///
    /// The outline covers the block's model, and the models and items that its block entity renderer submits in each frame.
    /// Custom geometry that the block entity renderer submits will not glow.
    ///
    /// If the position holds air, the glow will stay active and draw nothing.
    ///
    /// @param key  The key to make the glow under.
    /// @param pos  The position of the block to outline.
    /// @param argb The ARGB color of the outline.
    ///
    /// @return The glow of the block.
    ///
    @CanIgnoreReturnValue
    public Glow block(Object key, BlockPos pos, int argb) {
        this.checkUsable();
        return this.put(new Glow(this, key, argb, new GlowingBlock(pos.immutable())));
    }
    
    ///
    /// Puts the given glow under its key, after removing the glow under an equal key.
    ///
    private Glow put(Glow glow) {
        Glow existing = this.glowByKey.get(glow.key);
        if (existing != null) {
            this.drop(existing);
        }
        
        this.glowByKey.put(glow.key, glow);
        return glow;
    }
    
    ///
    /// Removes the glow under the key.
    ///
    /// Does nothing without one.
    ///
    /// @param key The key of the glow.
    ///
    public void remove(Object key) {
        this.checkUsable();
        Glow existing = this.glowByKey.get(key);
        if (existing != null) {
            this.drop(existing);
        }
    }
    
    ///
    /// Removes every glow of this mod.
    ///
    public void clear() {
        this.checkUsable();
        this.glowByKey.values().forEach(Glow::end);
        this.glowByKey.clear();
    }
    
    ///
    /// If this mod has any glow.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @return `true` if this mod has any active glow.
    ///
    @ApiStatus.Internal
    public boolean hasAny() {
        this.checkThread();
        return !this.glowByKey.isEmpty();
    }
    
    ///
    /// The color this mod gave the entity.
    ///
    /// If more than one glow of this mod outlines the entity, the color will be the one of the latest.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param entity The entity to look up.
    ///
    /// @return The ARGB color of the outline, or `0` if this mod didn't glow the entity.
    ///
    @ApiStatus.Internal
    public int colorOf(Entity entity) {
        this.checkUsable();
        int color = 0;
        for (Glow glow : this.glowByKey.values()) {
            if (glow.entity == entity) {
                color = glow.argb;
            }
        }
        
        return color;
    }
    
    ///
    /// Ages every glow by one tick and removes the expired and the entity-less ones.
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
        
        for (Glow glow : List.copyOf(this.glowByKey.values())) {
            if (glow.age()) {
                this.drop(glow);
            }
        }
    }
    
    ///
    /// Removes the given glow from this mod's glows and marks it as removed.
    ///
    /// @param glow The active glow to remove.
    ///
    void drop(Glow glow) {
        this.glowByKey.remove(glow.key, glow);
        glow.end();
    }
    
    ///
    /// Submits every glowing block's cached model with its outline color.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param pose      The pose stack of the frame, whose origin is the camera.
    /// @param collector The collector of the frame that the models are submitted to.
    /// @param camera    The position of the camera.
    ///
    @ApiStatus.Internal
    public void submitBlocks(PoseStack pose, SubmitNodeCollector collector, Vec3 camera) {
        this.checkUsable();
        for (Glow glow : this.glowByKey.values()) {
            GlowingBlock block = glow.block;
            if (block != null) {
                block.submit(pose, collector, camera, glow.argb);
            }
        }
    }
    
    ///
    /// Ends every glow.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    @ApiStatus.Internal
    public void end() {
        this.clear();
    }
    
    ///
    /// Throws an [IllegalStateException] if called off the client thread or while the client is in no level.
    ///
    private void checkUsable() {
        this.checkThread();
        if (Minecraft.getInstance().level == null) {
            throw new IllegalStateException("The glows of '" + this.mod.getModId() + "' were used while the client is in no level");
        }
    }
    
    ///
    /// Throws an [IllegalStateException] if called off the client thread.
    ///
    void checkThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("The glows of '" + this.mod.getModId() + "' were used on thread '" + Thread.currentThread().getName() + "'");
        }
    }
}
