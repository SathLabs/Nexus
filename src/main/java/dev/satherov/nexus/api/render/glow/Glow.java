package dev.satherov.nexus.api.render.glow;

import dev.satherov.nexus.internal.render.glow.GlowingBlock;
import dev.satherov.nexus.internal.tick.client.ClientTicks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;

///
/// One glowing outline of a mod, on an entity or a block.
///
/// Every method throws an [IllegalStateException] off the client thread.
///
/// Can be started through [Glows#entity(Object, Entity, int)] and [Glows#block(Object, BlockPos, int)].
///
public final class Glow {
    
    ///
    /// The glows of the mod that the glow belongs to.
    ///
    private final Glows glows;
    
    ///
    /// The key the glow is under.
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
    /// The cached model of the outlined block, or `null` if the glow outlines an entity.
    ///
    final @Nullable GlowingBlock block;
    
    ///
    /// How many client ticks are left until the glow expires, or `-1` if it never does.
    ///
    private int ticksLeft = -1;
    
    ///
    /// `true` until the glow is removed.
    ///
    private boolean active = true;
    
    ///
    /// Should only ever be called from [Glows#entity(Object, Entity, int)].
    ///
    /// @param glows  The glows of the mod that the glow belongs to.
    /// @param key    The key the glow is under.
    /// @param argb   The ARGB color of the outline.
    /// @param entity The outlined entity.
    ///
    Glow(Glows glows, Object key, int argb, Entity entity) {
        this.glows = glows;
        this.key = key;
        this.argb = argb;
        this.entity = entity;
        this.block = null;
    }
    
    ///
    /// Should only ever be called from [Glows#block(Object, BlockPos, int)].
    ///
    /// @param glows The glows of the mod that the glow belongs to.
    /// @param key   The key the glow is under.
    /// @param argb  The ARGB color of the outline.
    /// @param block The cached model of the outlined block.
    ///
    Glow(Glows glows, Object key, int argb, GlowingBlock block) {
        this.glows = glows;
        this.key = key;
        this.argb = argb;
        this.entity = null;
        this.block = block;
    }
    
    ///
    /// @return `true` until removed, expired, replaced under its key, dropped with its entity, or dropped on leaving the level.
    ///
    public boolean isActive() {
        this.glows.checkThread();
        return this.active;
    }
    
    ///
    /// Removes the glow once the given number of client ticks passed, counted from now.
    ///
    /// A second call restarts the count.
    ///
    /// @param ticks The number of client ticks until the glow is removed.
    ///
    /// @return This glow for chaining.
    ///
    /// @throws IllegalArgumentException If the ticks are below `1`.
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
    /// Does nothing once inactive.
    ///
    public void remove() {
        this.glows.checkThread();
        if (this.active) {
            this.glows.drop(this);
        }
    }
    
    ///
    /// Marks the glow as removed.
    ///
    void end() {
        this.active = false;
    }
    
    ///
    /// Ages the glow by one client tick.
    ///
    /// @return `true` if the glow expired or the entity it outlines left the level of the client.
    ///
    boolean age() {
        if (this.ticksLeft > 0) {
            this.ticksLeft--;
        }
        
        Entity outlined = this.entity;
        return this.ticksLeft == 0 || (outlined != null && (outlined.isRemoved() || !ClientTicks.isCurrentLevel(outlined.level())));
    }
}
