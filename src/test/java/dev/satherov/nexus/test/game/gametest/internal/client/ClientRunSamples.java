package dev.satherov.nexus.test.game.gametest.internal.client;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.measurement.Measured;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.TutorialToast;

import com.mojang.blaze3d.pipeline.RenderTarget;

import org.assertj.core.api.Assertions;

///
/// The client tests a run measures, profiles, or lets fail without failing the run, and the client state it resets between tests.
///
public class ClientRunSamples {
    
    private static final int WIDTH = 854;
    
    private static final int HEIGHT = 480;
    
    private static final int GUI_SCALE = 2;
    
    private static final int CHANGED_WIDTH = 640;
    
    private static final int CHANGED_HEIGHT = 360;
    
    private static final int CHANGED_GUI_SCALE = 1;
    
    private static final int TUTORIAL_TICKS = 150;
    
    @ClientTest
    @Measured(40)
    public static void window(Client client) { }
    
    @ClientTest
    @Measured(value = 3, profile = true)
    public static void profiledWindow(Client client) { }
    
    @ClientTest(required = false)
    public static void optionalFailure(Client client) {
        throw new AssertionError("This test always fails");
    }
    
    @ClientTest(maxFrames = 1, required = false)
    public static void outOfFrames(Client client) {
        client.ticks(2);
    }
    
    @ClientTest
    public static void changesTheWindowTheGuiScaleAndTheDebugOverlay(Client client) {
        Minecraft minecraft = client.minecraft();
        minecraft.getWindow().setWindowed(ClientRunSamples.CHANGED_WIDTH, ClientRunSamples.CHANGED_HEIGHT);
        client.until("the window to resize", () -> minecraft.getMainRenderTarget().width == ClientRunSamples.CHANGED_WIDTH);
        minecraft.options.guiScale().set(ClientRunSamples.CHANGED_GUI_SCALE);
        minecraft.debugEntries.setOverlayVisible(true);
        Assertions.assertThat(minecraft.getWindow().getGuiScale()).isEqualTo(ClientRunSamples.CHANGED_GUI_SCALE);
    }
    
    @ClientTest
    public static void keepsTheWindowOfTheRun(Client client) {
        Minecraft minecraft = client.minecraft();
        RenderTarget target = minecraft.getMainRenderTarget();
        Assertions.assertThat(target.width).isEqualTo(ClientRunSamples.WIDTH);
        Assertions.assertThat(target.height).isEqualTo(ClientRunSamples.HEIGHT);
        Assertions.assertThat(minecraft.getWindow().getGuiScale()).isEqualTo(ClientRunSamples.GUI_SCALE);
        Assertions.assertThat(minecraft.debugEntries.isOverlayVisible()).isFalse();
    }
    
    @ClientTest
    public static void showsNoTutorialToastInAWorld(Client client) {
        client.joinWorld();
        client.ticks(ClientRunSamples.TUTORIAL_TICKS);
        Assertions.assertThat(client.minecraft().getToastManager().getToast(TutorialToast.class, Toast.NO_TOKEN)).isNull();
        client.leaveWorld();
    }
}
