package dev.satherov.nexus.api.render.outline;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

///
/// The block under the crosshair as the client sees it.
///
/// A rule is asked again once any component changes.
///
/// @param level The client's level.
/// @param pos   The position of the block.
/// @param state The state of the block.
/// @param side  The side the crosshair hits.
///
public record TargetedBlock(Level level, BlockPos pos, BlockState state, Direction side) { }
