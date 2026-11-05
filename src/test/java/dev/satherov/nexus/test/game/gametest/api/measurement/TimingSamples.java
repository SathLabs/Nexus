package dev.satherov.nexus.test.game.gametest.api.measurement;

import dev.satherov.nexus.gametest.api.measurement.Timing;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

///
/// The sample that records one series of durations, which RunReportTest checks.
///
public class TimingSamples {
    
    @ServerTest
    public static void recordsASeries(GameTestHelper helper) {
        long[] nanos = new long[4];
        for (int i = 0; i < nanos.length; i++) {
            long start = System.nanoTime();
            helper.setBlock(BlockPos.ZERO, i % 2 == 0 ? Blocks.STONE : Blocks.AIR);
            nanos[i] = System.nanoTime() - start;
        }
        
        Timing.record(helper, "set_block", nanos);
        helper.succeed();
    }
}
