package dev.satherov.nexus.api.tick.ticker;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Range;

///
/// The tick function of a block entity using a scheduler.
///
/// Declared in the block entity builder and bound once the block entity is loaded.
///
/// @param <ENTITY> The class of the block entity.
///
@FunctionalInterface
public interface EntityTickable<ENTITY extends BlockEntity> {
    
    ///
    /// Runs one tick of the given block entity.
    ///
    /// @param entity  The block entity.
    /// @param elapsed The number of passes since the last tick, as in [Tickable#tick(int)].
    ///
    /// @return The next tick.
    ///
    Next tick(ENTITY entity, @Range(from = 1, to = Integer.MAX_VALUE) int elapsed);
}
