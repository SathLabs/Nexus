package dev.satherov.nexus.test.game.gametest.internal.discovery;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.measurement.Measured;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.minecraft.gametest.framework.GameTestHelper;

import org.jspecify.annotations.Nullable;

///
/// Test methods for the discovery tests, where every one that fails in its run is optional.
///
public class DiscoverySamples {
    
    public static @Nullable Object received;
    
    @ServerTest
    public static void meetsConstraints(GameTestHelper helper) {
        helper.succeed();
    }
    
    @ServerTest(required = false)
    public static void optional(GameTestHelper helper) {
        helper.succeed();
    }
    
    @ServerTest
    @Measured(5)
    public static void measured(GameTestHelper helper) { }
    
    @ServerTest
    @Measured(10)
    public static void longWindow(GameTestHelper helper) { }
    
    @ClientTest
    public static void client(Client client) { }
    
    @ServerTest(required = false)
    public static void throwsItsOwnException(GameTestHelper helper) {
        DiscoverySamples.received = helper;
        throw new UnsupportedOperationException("Thrown by the sample on purpose");
    }
    
    @ServerTest(required = false)
    private static void notPublic(GameTestHelper helper) { }
    
    @ServerTest(required = false)
    public void notStatic(GameTestHelper helper) { }
    
    @ServerTest(required = false)
    public static int notVoid(GameTestHelper helper) {
        return 0;
    }
    
    @ServerTest(required = false)
    public static void twoParameters(GameTestHelper helper, GameTestHelper other) { }
    
    @ServerTest(required = false)
    public static void takesAString(String text) { }
    
    @ClientTest(required = false)
    public static void clientWithHelper(GameTestHelper helper) { }
    
    @Measured(0)
    @ServerTest(required = false)
    public static void emptyWindow(GameTestHelper helper) { }
    
    @ServerTest(maxTicks = 0, required = false)
    public static void noTicks(GameTestHelper helper) { }
    
    @Measured(20)
    @ServerTest(maxTicks = 21, required = false)
    public static void windowOverrunsTicks(GameTestHelper helper) { }
    
    @ServerTest(setupTicks = -1, required = false)
    public static void negativeSetup(GameTestHelper helper) { }
    
    @ServerTest(structure = "My Structure", required = false)
    public static void wrongStructure(GameTestHelper helper) { }
    
    @ClientTest(maxFrames = 0, required = false)
    public static void noFrames(Client client) { }
    
    @ServerTest(required = false)
    public static void sameId(GameTestHelper helper) { }
    
    @ServerTest(required = false)
    public static void same_id(GameTestHelper helper) { }
    
    private static class HiddenClass {
        
        @ServerTest(required = false)
        public static void hiddenMethod(GameTestHelper helper) { }
    }
}
