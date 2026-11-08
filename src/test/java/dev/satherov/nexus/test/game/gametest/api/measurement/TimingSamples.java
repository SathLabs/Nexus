package dev.satherov.nexus.test.game.gametest.api.measurement;

import dev.satherov.nexus.gametest.api.measurement.Timing;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.gametest.framework.GameTestHelper;

import org.assertj.core.api.Assertions;

import java.util.List;

///
/// Checks the series that server tests record with [Timing] and the names it refuses.
///
public class TimingSamples {
    
    @ServerTest
    public static void recordsASeries(GameTestHelper helper) {
        Timing.record(helper, "sample", new long[]{ 30L, 10L, 20L });
        helper.succeed();
    }
    
    @ServerTest
    public static void refusesAnInvalidName(GameTestHelper helper) {
        for (String name : List.of("", "Sample", "a-sample", "a.sample", "a/sample")) {
            Assertions.assertThatIllegalArgumentException()
                    .as("Recording under '%s'", name)
                    .isThrownBy(() -> Timing.record(helper, name, new long[]{ 10L }));
        }
        
        helper.succeed();
    }
    
    @ServerTest
    public static void refusesADuplicateName(GameTestHelper helper) {
        Timing.record(helper, "sample", new long[]{ 10L, 20L });
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> Timing.record(helper, "sample", new long[]{ 30L }));
        helper.succeed();
    }
    
    @ServerTest
    public static void emptySeriesKeepsItsName(GameTestHelper helper) {
        Timing.record(helper, "empty", new long[0]);
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> Timing.record(helper, "empty", new long[]{ 10L }));
        helper.succeed();
    }
}
