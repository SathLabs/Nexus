package dev.satherov.nexus.test.gametest.internal.level;

import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

///
/// The server test that checks the level a run takes place in is void at the test's own position.
///
public class VoidLevelSamples {

    @ServerTest
    public static void isVoid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);

        // The framework encases the structure in barriers, so the column starts under that floor.
        for (int y = level.getMinY(); y < origin.getY() - 1; y++) {
            BlockPos pos = new BlockPos(origin.getX(), y, origin.getZ());
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                helper.fail("'" + state + "' at " + pos.toShortString() + " is not void");
            }
        }

        helper.succeed();
    }
}
