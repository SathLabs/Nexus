package dev.satherov.nexus.api.render.glow;

import dev.satherov.nexus.internal.tick.client.ClientTicks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;

///
/// A glowing outline a mod gives an entity or a block.
///
/// Can be created through [Glows#entity(Object, Entity, int)] and [Glows#block(Object, BlockPos, int)].
///
public final class Glow {
    
    ///
    /// The key of the glow instance.
    ///
    final Object key;
    
    ///
    /// The ARGB color of the outline.
    ///
    final int argb;
    
    ///
    /// The outlined entity, or `null` if the glow outlines a block.
    ///
    final @Nullable Entity entity;
    
    ///
    /// The glows of the mod that the glow belongs to.
    ///
    private final Glows glows;
    
    ///
    /// How many client ticks are left until the glow expires, or `-1` if it never does.
    ///
    private int ticksLeft = -1;
    
    ///
    /// If the glow is still active.
    ///
    private boolean active = true;
    
    ///
    /// Should only ever be called from [Glows#entity(Object, Entity, int)].
    ///
    /// @param glows  The glows of the mod that the glow belongs to.
    /// @param key    The key of the glow instance.
    /// @param argb   The ARGB color of the outline.
    /// @param entity The entity to outline.
    ///
    Glow(Glows glows, Object key, int argb, Entity entity) {
        this.glows = glows;
        this.key = key;
        this.argb = argb;
        this.entity = entity;
    }
    
    ///
    /// Should only ever be called from [Glows#block(Object, BlockPos, int)].
    ///
    /// @param glows The glows of the mod that the glow belongs to.
    /// @param key   The key of the glow instance.
    /// @param argb  The ARGB color of the outline.
    ///
    Glow(Glows glows, Object key, int argb) {
        this.glows = glows;
        this.key = key;
        this.argb = argb;
        this.entity = null;
    }
    
    ///
    /// If the glow is still active.
    ///
    /// The glow will be inactive once any of the following happened:
    /// - It was removed.
    /// - It expired.
    /// - Its mod made another glow under an equal key.
    /// - Its entity left the level of the client.
    /// - The level of the client unloaded.
    ///
    /// @return `true` if the glow is still active.
    ///
    /// @throws IllegalStateException If called off the client thread.
    ///
    public boolean isActive() {
        this.glows.checkThread();
        return this.active;
    }
    
    ///
    /// Removes the glow once the given number of client ticks has passed.
    ///
    /// Replaces previous calls.
    ///
    /// @param ticks The number of client ticks until the glow is removed.
    ///
    /// @return The glow for chaining.
    ///
    /// @throws IllegalArgumentException If the number of ticks is below `1`.
    /// @throws IllegalStateException    If called off the client thread.
    ///
    @CanIgnoreReturnValue
    public Glow expire(@Range(from = 1, to = Integer.MAX_VALUE) int ticks) {
        this.glows.checkThread();
        //noinspection ConstantValue Nothing enforces the range at runtime.
        if (ticks < 1) {
            throw new IllegalArgumentException("Tick count '" + ticks + "' is below 1");
        }
        
        this.ticksLeft = ticks;
        return this;
    }
    
    ///
    /// Removes the glow.
    ///
    /// Does nothing if the glow is already inactive.
    ///
    /// @throws IllegalStateException If called off the client thread.
    ///
    public void remove() {
        this.glows.checkThread();
        if (this.active) {
            this.glows.drop(this);
        }
    }
    
    ///
    /// Marks the glow as inactive.
    ///
    void end() {
        this.active = false;
    }
    
    ///
    /// Ages the glow by one client tick.
    ///
    /// @return `true` if the glow expired or its entity left the level of the client.
    ///
    boolean age() {
        if (this.ticksLeft > 0) {
            this.ticksLeft--;
        }
        
        Entity outlined = this.entity;
        return this.ticksLeft == 0 || (outlined != null && (outlined.isRemoved() || !ClientTicks.isCurrentLevel(outlined.level())));
    }
}
