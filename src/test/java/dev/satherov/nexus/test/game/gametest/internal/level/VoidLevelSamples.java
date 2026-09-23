package dev.satherov.nexus.test.game.gametest.internal.level;

import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

///
/// Checks that the level is air around and far below the structure of a server test.
///
public class VoidLevelSamples {

    @ServerTest
    public static void airAroundAndBelow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AABB bounds = helper.getBounds();
        BlockPos low = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ).offset(-1, -1, -1);
        BlockPos high = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        BoundingBox encased = BoundingBox.fromCorners(low, high);
        BlockPos bottom = new BlockPos(low.getX() - 1, level.getMinY(), low.getZ() - 1);
        for (BlockPos pos : BlockPos.betweenClosed(bottom, high.offset(1, 0, 1))) {
            BlockState state = level.getBlockState(pos);
            if (!encased.isInside(pos) && !state.isAir()) {
                helper.fail("The block at '" + pos.toShortString() + "' is '" + state + "' instead of air");
            }
        }

        helper.succeed();
    }
}
