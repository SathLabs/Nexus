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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

///
/// The glowing outlines one mod gives to entities and blocks.
///
/// A new glow under a key this mod already uses removes the earlier glow first.
///
/// Every method throws an [IllegalStateException] off the client thread,
/// and every method but [#tick()] and [#hasAny()] also while the client is in no level.
/// Every glow is dropped once the level of the client unloads, on a dimension change and on logout alike.
///
/// Can be accessed through [Render#getGlows()].
///
public final class Glows {
    
    ///
    /// The handle of the mod.
    ///
    private final NexusMod mod;
    
    ///
    /// The active glows by key.
    ///
    private final Map<Object, Glow> glowByKey = new LinkedHashMap<>();
    
    ///
    /// The latest active glow of every outlined entity.
    ///
    private final Map<Entity, Glow> glowByEntity = new HashMap<>();
    
    ///
    /// The glowing block of every active block glow.
    ///
    private final Map<Glow, GlowingBlock> blockByGlow = new LinkedHashMap<>();
    
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
    /// Outlines the given entity in the given color until removed.
    /// The color replaces the team color of the entity.
    ///
    /// The glow ends once the entity leaves the level of the client.
    ///
    /// @param key    The key of the glow instance.
    /// @param entity The entity to outline.
    /// @param argb   The ARGB color of the outline.
    ///
    /// @return The glow of the entity.
    ///
    @CanIgnoreReturnValue
    public Glow entity(Object key, Entity entity, int argb) {
        this.checkUsable();
        Glow glow = this.put(new Glow(this, key, argb, entity));
        this.glowByEntity.put(entity, glow);
        return glow;
    }
    
    ///
    /// Outlines the block at the given position in the given color until removed, following its state.
    ///
    /// The outline covers the model of the block and the models and items its block entity renderer submits.
    /// Custom geometry of a block entity renderer will not glow.
    ///
    /// If the position holds air, the glow will stay active and draw nothing.
    ///
    /// @param key  The key of the glow instance.
    /// @param pos  The position of the block to outline.
    /// @param argb The ARGB color of the outline.
    ///
    /// @return The glow of the block.
    ///
    @CanIgnoreReturnValue
    public Glow block(Object key, BlockPos pos, int argb) {
        this.checkUsable();
        Glow glow = this.put(new Glow(this, key, argb));
        this.blockByGlow.put(glow, new GlowingBlock(pos.immutable()));
        return glow;
    }
    
    ///
    /// Puts the given glow under its key, after removing the glow under an equal key.
    ///
    /// @param glow The glow to put.
    ///
    /// @return The given glow.
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
    /// Removes the glow under the given key.
    ///
    /// Does nothing if there is none.
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
        this.glowByEntity.clear();
        this.blockByGlow.clear();
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
    /// Gets the color this mod gave the given entity.
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
        Glow glow = this.glowByEntity.get(entity);
        return glow == null ? 0 : glow.argb;
    }
    
    ///
    /// Ages every glow by one client tick and removes every glow that expired or whose entity left the level of the client.
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
    /// Removes the given glow and marks it as inactive.
    ///
    /// @param glow The active glow to remove.
    ///
    void drop(Glow glow) {
        this.glowByKey.remove(glow.key, glow);
        this.blockByGlow.remove(glow);
        Entity entity = glow.entity;
        if (entity != null && this.glowByEntity.remove(entity, glow)) {
            // The latest remaining glow of the entity takes over, or the entity would lose its outline while a glow still outlines it.
            for (Glow remaining : this.glowByKey.values()) {
                if (remaining.entity == entity) {
                    this.glowByEntity.put(entity, remaining);
                }
            }
        }
        
        glow.end();
    }
    
    ///
    /// Submits the model of every glowing block with its outline color.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param pose      The pose stack of the frame, whose origin is the camera.
    /// @param collector The submit node collector of the frame.
    /// @param camera    The position of the camera.
    ///
    @ApiStatus.Internal
    public void submitBlocks(PoseStack pose, SubmitNodeCollector collector, Vec3 camera) {
        this.checkUsable();
        this.blockByGlow.forEach((glow, block) -> block.submit(pose, collector, camera, glow.argb));
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
    /// Checks that the call is on the client thread and the client is in a level.
    ///
    /// @throws IllegalStateException If called off the client thread or while the client is in no level.
    ///
    private void checkUsable() {
        this.checkThread();
        if (Minecraft.getInstance().level == null) {
            throw new IllegalStateException("The glows of '" + this.mod.getModId() + "' were used while the client is in no level");
        }
    }
    
    ///
    /// Checks that the call is on the client thread.
    ///
    /// @throws IllegalStateException If called off the client thread.
    ///
    void checkThread() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("The glows of '" + this.mod.getModId() + "' were used on thread '" + Thread.currentThread().getName() + "'");
        }
    }
}
