package dev.satherov.nexus.test.game.gametest.api.client;

import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

///
/// The client tests that check the frame budget, the first screen, the level, the camera, the packets, and the HUD of a client.
///
public class ClientSamples {
    
    private static final int MAX_FRAMES = 5;
    
    private static final double CAMERA_TOLERANCE = 1.0E-3D;
    
    private static final int HOTBAR_HEIGHT = 22;
    
    private static final int STILL_FRAMES = 100;
    
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
    
    @ClientTest
    public static void poseMovesCamera(Client client) {
        client.joinWorld();
        ServerLevel level = client.serverLevel();
        ServerPlayer player = level.players().getFirst();
        Vec3 position = level.getRespawnData().pos().getBottomCenter().add(3.5D, 2.25D, -1.75D);
        float yaw = 135.0F;
        float pitch = 30.0F;
        
        client.pose(position, yaw, pitch);
        
        Camera camera = client.minecraft().gameRenderer.getMainCamera();
        Vec3 eyes = position.add(0.0D, player.getEyeHeight(), 0.0D);
        Assertions.assertThat(camera.position().distanceTo(eyes)).isLessThan(ClientSamples.CAMERA_TOLERANCE);
        Assertions.assertThat(camera.yRot()).isEqualTo(yaw);
        Assertions.assertThat(camera.xRot()).isEqualTo(pitch);
        Assertions.assertThat(player.position()).isEqualTo(position);
    }
    
    @ClientTest
    public static void settleShowsServerBlock(Client client) {
        if (RunOptions.fromProperties().realtime()) {
            return;
        }
        
        client.joinWorld();
        ServerLevel level = client.serverLevel();
        BlockPos pos = level.getRespawnData().pos().east(2);
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        client.settle();
        
        ClientLevel clientLevel = Objects.requireNonNull(client.minecraft().level);
        Assertions.assertThat(clientLevel.getBlockState(pos).getBlock()).isEqualTo(Blocks.STONE);
    }
    
    @ClientTest
    public static void stillWorldFramesMatch(Client client) {
        client.joinWorld();
        ClientSamples.awaitStill(client);
        Capture first = client.capture();
        
        for (int frame = 0; frame < ClientSamples.STILL_FRAMES; frame++) {
            client.tick();
            Assertions.assertThat(ClientSamples.matches(first, client.capture())).as("frame %d", frame).isTrue();
        }
    }
    
    @ClientTest
    public static void hideHudClearsHotbar(Client client) {
        client.joinWorld();
        ClientSamples.awaitStill(client);
        Capture shown = ClientSamples.hotbar(client);
        
        client.hideHud(true);
        client.tick();
        Capture hidden = ClientSamples.hotbar(client);
        
        client.hideHud(false);
        client.tick();
        Capture shownAgain = ClientSamples.hotbar(client);
        
        Assertions.assertThat(ClientSamples.matches(shown, hidden)).isFalse();
        Assertions.assertThat(ClientSamples.matches(shown, shownAgain)).isTrue();
    }
    
    private static void awaitStill(Client client) {
        AtomicReference<@Nullable Capture> previous = new AtomicReference<>();
        client.until("the frame to stop changing", () -> {
            Capture current = client.capture();
            Capture before = previous.getAndSet(current);
            return before != null && ClientSamples.matches(before, current);
        });
    }
    
    private static Capture hotbar(Client client) {
        Capture capture = client.capture();
        int rows = ClientSamples.HOTBAR_HEIGHT * client.minecraft().getWindow().getGuiScale();
        return capture.region(0, capture.height() - rows, capture.width(), rows);
    }
    
    private static boolean matches(Capture first, Capture second) {
        for (int y = 0; y < first.height(); y++) {
            for (int x = 0; x < first.width(); x++) {
                if (first.pixel(x, y) != second.pixel(x, y)) {
                    return false;
                }
            }
        }
        
        return true;
    }
}
