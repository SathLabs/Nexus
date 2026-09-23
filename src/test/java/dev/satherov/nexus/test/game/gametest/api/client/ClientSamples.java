package dev.satherov.nexus.test.game.gametest.api.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;

import org.assertj.core.api.Assertions;

import java.util.concurrent.atomic.AtomicInteger;

///
/// The client tests that check the frame budget, the first screen, and the level of a client.
///
public class ClientSamples {
    
    private static final int MAX_FRAMES = 5;
    
    @ClientTest
    public static void titleScreenAtStart(Client client) {
        Assertions.assertThat(client.screen()).isInstanceOf(TitleScreen.class);
    }
    
    @ClientTest
    public static void ticksAdvanceServer(Client client) {
        if (RunOptions.fromProperties().realtime()) {
            return;
        }
        
        int count = 10;
        client.joinWorld();
        MinecraftServer server = client.serverLevel().getServer();
        int start = server.getTickCount();
        client.ticks(count);
        Assertions.assertThat(server.getTickCount() - start).isEqualTo(count);
        client.leaveWorld();
    }
    
    @ClientTest(maxFrames = ClientSamples.MAX_FRAMES)
    public static void untilOutOfFrames(Client client) {
        String what = "a condition that never holds";
        AtomicInteger checks = new AtomicInteger();
        
        Assertions.assertThatExceptionOfType(AssertionError.class)
                .isThrownBy(() -> client.until(what, () -> {
                    checks.incrementAndGet();
                    return false;
                }))
                .withMessageContaining(what);
        Assertions.assertThat(checks).hasValueGreaterThanOrEqualTo(ClientSamples.MAX_FRAMES);
    }
    
    @ClientTest
    public static void serverLevelOutsideWorld(Client client) {
        Assertions.assertThatIllegalStateException().isThrownBy(client::serverLevel);
    }
}
