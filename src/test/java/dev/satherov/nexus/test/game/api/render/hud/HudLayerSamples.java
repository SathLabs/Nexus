package dev.satherov.nexus.test.game.api.render.hud;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.api.render.hud.HudSlot;
import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// The client tests that check a HUD layer above the crosshair, the order of the slots, a layer drawn at a projected block, and a layer under the hidden HUD.
///
public class HudLayerSamples {
    
    private static final String OVER_CROSSHAIR = "over_crosshair";
    
    private static final String UPPER = "upper";
    
    private static final String LOWER = "lower";
    
    private static final String MARKER = "marker";
    
    private static final int RED = 0xFFFF0000;
    
    private static final int BLUE = 0xFF0000FF;
    
    private static final int HALF = 4;
    
    private static final int UPPER_OFFSET = 20;
    
    private static final int LOWER_HALF = 8;
    
    private static final int GOLDEN_HALF = 12;
    
    private static final int HIDDEN_FRAMES = 3;
    
    private static final int STILL_FRAMES = 10;
    
    private static final double HEIGHT = 4.0D;
    
    private static Shown shown = Shown.NONE;
    
    private static int crosshairFrames;
    
    private static @Nullable Vec3 marked;
    
    @ClientTest
    public static void layerDrawsAboveCrosshair(Client client) {
        HudLayerSamples.enter(client);
        int x = HudLayerSamples.centerX(client);
        int y = HudLayerSamples.centerY(client);
        HudLayerSamples.shown = Shown.CROSSHAIR;
        try {
            client.tick();
            
            Capture capture = client.capture();
            HudLayerSamples.assertGuiPixel(client, capture, x, y, HudLayerSamples.RED);
            HudLayerSamples.assertGuiPixel(client, capture, x - HudLayerSamples.HALF, y, HudLayerSamples.RED);
            HudLayerSamples.assertGuiPixel(client, capture, x, y + HudLayerSamples.HALF - 1, HudLayerSamples.RED);
            HudLayerSamples.around(client, capture, x, y).assertGolden("layer_draws_above_crosshair");
        } finally {
            HudLayerSamples.shown = Shown.NONE;
        }
    }
    
    @ClientTest
    public static void belowSlotDrawsUnderAnchor(Client client) {
        HudLayerSamples.enter(client);
        int x = HudLayerSamples.centerX(client) - HudLayerSamples.UPPER_OFFSET;
        int y = HudLayerSamples.centerY(client);
        HudLayerSamples.shown = Shown.ORDER;
        try {
            client.tick();
            
            Capture capture = client.capture();
            HudLayerSamples.assertGuiPixel(client, capture, x, y, HudLayerSamples.BLUE);
            HudLayerSamples.assertGuiPixel(client, capture, x - HudLayerSamples.HALF - 1, y, HudLayerSamples.RED);
            HudLayerSamples.assertGuiPixel(client, capture, x + HudLayerSamples.HALF, y, HudLayerSamples.RED);
            HudLayerSamples.around(client, capture, x, y).assertGolden("below_slot_draws_under_anchor");
        } finally {
            HudLayerSamples.shown = Shown.NONE;
        }
    }
    
    @ClientTest
    public static void projectedMarkerHitsBlock(Client client) {
        Vec3 eye = HudLayerSamples.enter(client);
        BlockPos pos = BlockPos.containing(eye.add(2.0D, 1.0D, 6.0D));
        Vec3 center = Vec3.atCenterOf(pos);
        Vector2f screen = HudLayerSamples.project(center);
        int x = (int) screen.x;
        int y = (int) screen.y;
        int sky = HudLayerSamples.guiPixel(client, client.capture(), x, y);
        
        client.serverLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        client.settle();
        client.awaitStill(HudLayerSamples.STILL_FRAMES);
        Assertions.assertThat(HudLayerSamples.project(center)).as("the projection once the block is placed").isEqualTo(screen);
        Assertions.assertThat(HudLayerSamples.guiPixel(client, client.capture(), x, y)).as("the projected point once the block is placed").isNotEqualTo(sky);
        
        HudLayerSamples.marked = center;
        HudLayerSamples.shown = Shown.MARKER;
        try {
            client.tick();
            
            Capture capture = client.capture();
            HudLayerSamples.assertGuiPixel(client, capture, x, y, HudLayerSamples.RED);
            HudLayerSamples.around(client, capture, x, y).assertGolden("projected_marker_hits_block");
        } finally {
            HudLayerSamples.shown = Shown.NONE;
            HudLayerSamples.marked = null;
        }
    }
    
    @ClientTest
    public static void hiddenHudSkipsLayer(Client client) {
        HudLayerSamples.enter(client);
        int x = HudLayerSamples.centerX(client);
        int y = HudLayerSamples.centerY(client);
        HudLayerSamples.shown = Shown.CROSSHAIR;
        try {
            client.tick();
            HudLayerSamples.assertGuiPixel(client, client.capture(), x, y, HudLayerSamples.RED);
            
            client.hideHud(true);
            HudLayerSamples.crosshairFrames = 0;
            client.ticks(HudLayerSamples.HIDDEN_FRAMES);
            Assertions.assertThat(HudLayerSamples.crosshairFrames).as("the frames the layer drew while the HUD was hidden").isZero();
            Assertions.assertThat(HudLayerSamples.guiPixel(client, client.capture(), x, y)).as("the center while the HUD is hidden").isNotEqualTo(HudLayerSamples.RED);
            
            client.hideHud(false);
            client.tick();
            Assertions.assertThat(HudLayerSamples.crosshairFrames).as("the frames the layer drew once the HUD is shown").isPositive();
            HudLayerSamples.assertGuiPixel(client, client.capture(), x, y, HudLayerSamples.RED);
        } finally {
            HudLayerSamples.shown = Shown.NONE;
        }
    }
    
    private static Vec3 enter(Client client) {
        client.joinWorld();
        client.hideHud(false);
        client.pose(client.serverLevel().getRespawnData().pos().getBottomCenter().add(0.0D, HudLayerSamples.HEIGHT, 0.0D), 0.0F, 0.0F);
        client.awaitStill(HudLayerSamples.STILL_FRAMES);
        return client.minecraft().gameRenderer.getMainCamera().position();
    }
    
    private static int centerX(Client client) {
        return client.minecraft().getWindow().getGuiScaledWidth() / 2;
    }
    
    private static int centerY(Client client) {
        return client.minecraft().getWindow().getGuiScaledHeight() / 2;
    }
    
    private static Vector2f project(Vec3 point) {
        return Objects.requireNonNull(Nexus.getMod().getRender().project(point), "the point lies behind the camera");
    }
    
    private static void assertGuiPixel(Client client, Capture capture, int x, int y, int argb) {
        int scale = client.minecraft().getWindow().getGuiScale();
        capture.assertPixel(x * scale, y * scale, argb);
    }
    
    private static int guiPixel(Client client, Capture capture, int x, int y) {
        int scale = client.minecraft().getWindow().getGuiScale();
        return capture.pixel(x * scale, y * scale);
    }
    
    private static Capture around(Client client, Capture capture, int x, int y) {
        int scale = client.minecraft().getWindow().getGuiScale();
        int half = HudLayerSamples.GOLDEN_HALF;
        return capture.region((x - half) * scale, (y - half) * scale, 2 * half * scale, 2 * half * scale);
    }
    
    private static void square(GuiGraphicsExtractor graphics, int x, int y, int half, int argb) {
        graphics.fill(x - half, y - half, x + half, y + half, argb);
    }
    
    private static void overCrosshair(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (HudLayerSamples.shown == Shown.CROSSHAIR) {
            HudLayerSamples.crosshairFrames++;
            HudLayerSamples.square(graphics, graphics.guiWidth() / 2, graphics.guiHeight() / 2, HudLayerSamples.HALF, HudLayerSamples.RED);
        }
    }
    
    private static void upper(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (HudLayerSamples.shown == Shown.ORDER) {
            HudLayerSamples.square(graphics, graphics.guiWidth() / 2 - HudLayerSamples.UPPER_OFFSET, graphics.guiHeight() / 2, HudLayerSamples.HALF, HudLayerSamples.BLUE);
        }
    }
    
    private static void lower(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        if (HudLayerSamples.shown == Shown.ORDER) {
            HudLayerSamples.square(graphics, graphics.guiWidth() / 2 - HudLayerSamples.UPPER_OFFSET, graphics.guiHeight() / 2, HudLayerSamples.LOWER_HALF, HudLayerSamples.RED);
        }
    }
    
    private static void marker(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Vec3 point = HudLayerSamples.marked;
        if (HudLayerSamples.shown != Shown.MARKER || point == null) {
            return;
        }
        
        Vector2f screen = Nexus.getMod().getRender().project(point);
        if (screen != null) {
            HudLayerSamples.square(graphics, (int) screen.x, (int) screen.y, 1, HudLayerSamples.RED);
        }
    }
    
    private enum Shown {
        NONE,
        CROSSHAIR,
        ORDER,
        MARKER
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Render render = Nexus.getMod().getRender();
            render.hudLayer(HudLayerSamples.OVER_CROSSHAIR, HudSlot.above(VanillaGuiLayers.CROSSHAIR), () -> HudLayerSamples::overCrosshair);
            render.hudLayer(HudLayerSamples.UPPER, HudSlot.top(), () -> HudLayerSamples::upper);
            render.hudLayer(HudLayerSamples.LOWER, HudSlot.below(Nexus.getMod().id(HudLayerSamples.UPPER)), () -> HudLayerSamples::lower);
            render.hudLayer(HudLayerSamples.MARKER, HudSlot.top(), () -> HudLayerSamples::marker);
        }
    }
}
