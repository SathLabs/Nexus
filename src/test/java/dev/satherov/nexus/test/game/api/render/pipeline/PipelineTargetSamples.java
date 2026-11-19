package dev.satherov.nexus.test.game.api.render.pipeline;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.config.NeoForgeClientConfig;
import net.neoforged.neoforge.client.event.ConfigureMainRenderTargetEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.client.OptionInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.pipeline.RenderTarget;

import org.assertj.core.api.Assertions;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// Checks that a mod's own render type over an own-shaded pipeline keeps its color and its blend under a shader pack once it draws into the target of the pipeline,
/// wherever in the level it is drawn, that it is hidden by the level in front of it and covered by what is drawn after the level,
/// and that it keeps its own blend without a pack.
///
public class PipelineTargetSamples {
    
    private static final int RED = 0xFFFF0000;
    
    private static final int SWAPPED_RED = 0xFF0000FF;
    
    private static final int GREEN = 0xFF00FF00;
    
    private static final int GREY = 0xFF303030;
    
    private static final int HALF_ALPHA_GREY = 0x80606060;
    
    private static final int STILL_FRAMES = 10;
    
    private static final int TOLERANCE = 1;
    
    private static final int COLOR_BITS = 24;
    
    private static final double HEIGHT = 4.0D;
    
    private static final double DISTANCE = 6.0D;
    
    private static final double COVER_DISTANCE = 5.0D;
    
    private static final Vec3 BLOCK = new Vec3(-1.0D, 0.0D, 3.0D);
    
    private static final Vec3 GLASS = new Vec3(0.5D, 0.0D, 3.0D);
    
    private static final Vec3 VISIBLE = new Vec3(1.0D, 0.0D, PipelineTargetSamples.DISTANCE);
    
    private static final Vec3 HIDDEN = new Vec3(-1.5D, 0.0D, PipelineTargetSamples.DISTANCE);
    
    private static final String STENCIL_PROPERTY = "nexus.test.stencil";
    
    private static @Nullable Pipeline red;
    
    private static @Nullable Pipeline additive;
    
    private static @Nullable Pipeline lightning;
    
    @ClientTest
    public static void ownTypeOnPipelineTargetKeepsItsColor(Client client) {
        Vec3 eye = PipelineTargetSamples.enter(client);
        TargetQuads.showOnPipelineTarget(PipelineTargetSamples.pipeline(), PipelineTargetSamples.RED, eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
        try {
            client.tick();
            
            Assertions.assertThat(TargetQuads.failure()).as("the failure of drawing onto the target of the pipeline").isNull();
            int visible = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.VISIBLE));
            Assertions.assertThat(visible).as("the quad beside the block, '%08X'", visible).isEqualTo(PipelineTargetSamples.RED);
        } finally {
            TargetQuads.hide();
        }
    }
    
    @ClientTest
    public static void ownTypeOnPipelineTargetIsHiddenByTheLevel(Client client) {
        Vec3 eye = PipelineTargetSamples.enter(client);
        int before = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.HIDDEN));
        TargetQuads.showOnPipelineTarget(PipelineTargetSamples.pipeline(), PipelineTargetSamples.RED, eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
        try {
            client.tick();
            
            Assertions.assertThat(TargetQuads.failure()).as("the failure of drawing onto the target of the pipeline").isNull();
            int hidden = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.HIDDEN));
            Assertions.assertThat(hidden).as("the quad behind the block, '%08X', against '%08X' without it", hidden, before).isEqualTo(before);
        } finally {
            TargetQuads.hide();
        }
    }
    
    @ClientTest
    public static void ownTypeOnMainTargetLosesItsColorUnderThePack(Client client) {
        Vec3 eye = PipelineTargetSamples.enter(client);
        int before = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.VISIBLE));
        TargetQuads.showOnMainTarget(eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
        try {
            client.tick();
            
            Assertions.assertThat(TargetQuads.failure()).as("the failure of drawing onto the main target").isNull();
            int visible = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.VISIBLE));
            if (client.hasShaderPack()) {
                Assertions.assertThat(visible)
                        .as("the quad on the main target under the pack, '%08X', against '%08X' without it", visible, before)
                        .isNotIn(PipelineTargetSamples.RED, PipelineTargetSamples.SWAPPED_RED);
            } else {
                Assertions.assertThat(visible).as("the quad on the main target, '%08X'", visible).isEqualTo(PipelineTargetSamples.RED);
            }
        } finally {
            TargetQuads.hide();
        }
    }
    
    @ClientTest
    public static void additiveOwnTypeOnPipelineTargetAddsToTheFrame(Client client) {
        PipelineTargetSamples.assertAddsToTheFrame(client, PipelineTargetSamples.additive, PipelineTargetSamples.GREY, PipelineTargetSamples.GREY);
    }
    
    @ClientTest
    public static void lightningOwnTypeOnPipelineTargetAddsToTheFrame(Client client) {
        PipelineTargetSamples.assertAddsToTheFrame(client, PipelineTargetSamples.lightning, PipelineTargetSamples.HALF_ALPHA_GREY, PipelineTargetSamples.GREY);
    }
    
    @ClientTest
    public static void ownTypeBehindGlassIsUnchangedByAnEarlierDraw(Client client) {
        Vec3 eye = PipelineTargetSamples.enter(client);
        client.serverLevel().setBlockAndUpdate(BlockPos.containing(eye.add(PipelineTargetSamples.GLASS)), Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        client.settle();
        client.awaitStill(PipelineTargetSamples.STILL_FRAMES);
        TargetQuads.showOnPipelineTarget(PipelineTargetSamples.pipeline(), PipelineTargetSamples.RED, eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
        try {
            client.tick();
            int alone = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.VISIBLE));
            
            // The earlier quad lies behind the camera, so all it does is draw into the target of the pipeline before the glass is drawn.
            TargetQuads.showEarlyOnPipelineTarget(PipelineTargetSamples.pipeline(), eye.add(0.0D, 0.0D, -PipelineTargetSamples.DISTANCE));
            client.tick();
            
            Assertions.assertThat(TargetQuads.failure()).as("the failure of drawing onto the target of the pipeline").isNull();
            int behind = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.VISIBLE));
            Assertions.assertThat(behind).as("the quad behind the glass after an earlier draw, '%08X', against '%08X' without it", behind, alone).isEqualTo(alone);
        } finally {
            TargetQuads.hide();
        }
    }
    
    @ClientTest
    public static void ownTypeFromTheFeaturePassIsHiddenByTheLevel(Client client) {
        Vec3 eye = PipelineTargetSamples.enter(client);
        int before = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.HIDDEN));
        TargetQuads.submitOnPipelineTarget(PipelineTargetSamples.pipeline(), eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
        try {
            client.tick();
            
            Capture capture = client.capture();
            int visible = PipelineTargetSamples.pixel(client, capture, eye.add(PipelineTargetSamples.VISIBLE));
            Assertions.assertThat(visible).as("the quad beside the block, '%08X'", visible).isEqualTo(PipelineTargetSamples.RED);
            int hidden = PipelineTargetSamples.pixel(client, capture, eye.add(PipelineTargetSamples.HIDDEN));
            Assertions.assertThat(hidden).as("the quad behind the block, '%08X', against '%08X' without it", hidden, before).isEqualTo(before);
        } finally {
            TargetQuads.hide();
        }
    }
    
    @ClientTest
    public static void ownItemEntityTypeKeepsItsColorThroughDrawHook(Client client) {
        Vec3 eye = PipelineTargetSamples.enter(client);
        TargetQuads.showThroughDrawHook(PipelineTargetSamples.pipeline(), PipelineTargetSamples.RED, eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
        try {
            client.tick();
            
            Assertions.assertThat(TargetQuads.failure()).as("the failure of drawing through the draw hook").isNull();
            int visible = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.VISIBLE));
            Assertions.assertThat(visible).as("the quad beside the block, '%08X'", visible).isEqualTo(PipelineTargetSamples.RED);
        } finally {
            TargetQuads.hide();
        }
    }
    
    @ClientTest
    public static void additiveItemEntityTypeKeepsItsBlendWithImprovedTransparency(Client client) {
        OptionInstance<Boolean> transparency = client.minecraft().options.improvedTransparency();
        boolean saved = transparency.get();
        // Without a pack this makes the item entity target a layer of its own, and the transparency pass drops every pixel of a layer at an alpha of zero.
        transparency.set(!client.hasShaderPack());
        try {
            Vec3 eye = PipelineTargetSamples.enter(client);
            int before = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.VISIBLE));
            TargetQuads.showThroughDrawHook(Objects.requireNonNull(PipelineTargetSamples.additive, "the additive pipeline"), PipelineTargetSamples.GREY, eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
            client.tick();
            
            Assertions.assertThat(TargetQuads.failure()).as("the failure of drawing through the draw hook").isNull();
            int visible = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.VISIBLE));
            if (client.hasShaderPack()) {
                PipelineTargetSamples.assertCloseTo(visible, PipelineTargetSamples.added(before, PipelineTargetSamples.GREY), "the additive quad from the draw hook");
            } else {
                // The layer starts out empty, so the quad adds its color onto nothing, and the transparency pass lays that color over the frame.
                PipelineTargetSamples.assertCloseTo(visible, PipelineTargetSamples.GREY, "the additive quad on the item entity layer");
            }
        } finally {
            TargetQuads.hide();
            transparency.set(saved);
        }
    }
    
    @ClientTest
    public static void geometryDrawnAfterTheLevelCoversTheOwnType(Client client) {
        Vec3 eye = PipelineTargetSamples.enter(client);
        TargetQuads.showOnPipelineTarget(PipelineTargetSamples.pipeline(), PipelineTargetSamples.RED, eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
        TargetQuads.showAfterLevel(PipelineTargetSamples.GREEN, eye.add(0.0D, 0.0D, PipelineTargetSamples.COVER_DISTANCE));
        try {
            client.tick();
            
            Assertions.assertThat(TargetQuads.failure()).as("the failure of drawing onto the target of the pipeline and after the level").isNull();
            int visible = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.VISIBLE));
            Assertions.assertThat(visible).as("the quad drawn after the level in front of the quad, '%08X'", visible).isEqualTo(PipelineTargetSamples.GREEN);
        } finally {
            TargetQuads.hide();
        }
    }
    
    @ClientTest
    public static void overlayIsFreedOnceTheLevelUnloads(Client client) {
        Vec3 eye = PipelineTargetSamples.enter(client);
        TargetQuads.showOnPipelineTarget(PipelineTargetSamples.pipeline(), PipelineTargetSamples.RED, eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
        RenderTarget drawn;
        try {
            client.tick();
            
            Assertions.assertThat(TargetQuads.failure()).as("the failure of drawing onto the target of the pipeline").isNull();
            drawn = Objects.requireNonNull(TargetQuads.drawnInto(), "the render target the quad was drawn into");
        } finally {
            TargetQuads.hide();
        }
        
        client.leaveWorld();
        if (client.hasShaderPack()) {
            Assertions.assertThat(drawn.getColorTexture()).as("the color texture of the target of the pipeline once the level unloaded").isNull();
        } else {
            Assertions.assertThat(drawn).as("the render target of the pipeline without a pack").isSameAs(client.minecraft().getMainRenderTarget());
        }
    }
    
    @ClientTest
    public static void ownTypeFallsBackToTheMainTargetOnAnotherDepthFormat(Client client) {
        ModConfigSpec.BooleanValue reduced = NeoForgeClientConfig.INSTANCE.reducedDepthStencilFormat;
        boolean saved = reduced.getAsBoolean();
        // The main target keeps the depth format it was created with, while the overlay is freed as the sample enters a new level and takes the flipped one.
        reduced.set(!saved);
        try {
            Vec3 eye = PipelineTargetSamples.enter(client);
            TargetQuads.showOnPipelineTarget(PipelineTargetSamples.pipeline(), PipelineTargetSamples.RED, eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
            try {
                client.tick();
                
                Assertions.assertThat(TargetQuads.failure()).as("the failure of drawing onto the target of the pipeline with another depth format").isNull();
                RenderTarget main = client.minecraft().getMainRenderTarget();
                // Only a main target with a stencil takes its depth format from the option.
                if (client.hasShaderPack() && main.useStencil) {
                    Assertions.assertThat(TargetQuads.drawnInto()).as("the render target the quad was drawn into with another depth format").isSameAs(main);
                }
            } finally {
                TargetQuads.hide();
            }
        } finally {
            reduced.set(saved);
            // The overlay still has the flipped format, so the level has to unload once more to free it.
            client.leaveWorld();
        }
    }
    
    @ClientTest
    public static void mainTargetStencilFollowsTheRunProperty(Client client) {
        Assertions.assertThat(client.minecraft().getMainRenderTarget().useStencil)
                .as("the stencil of the main target with the property '%s' at '%s'", PipelineTargetSamples.STENCIL_PROPERTY, System.getProperty(PipelineTargetSamples.STENCIL_PROPERTY))
                .isEqualTo(Boolean.getBoolean(PipelineTargetSamples.STENCIL_PROPERTY));
    }
    
    private static Pipeline pipeline() {
        return Objects.requireNonNull(PipelineTargetSamples.red, "the red pipeline");
    }
    
    private static void assertAddsToTheFrame(Client client, @Nullable Pipeline pipeline, int argb, int added) {
        Vec3 eye = PipelineTargetSamples.enter(client);
        int before = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.VISIBLE));
        TargetQuads.showOnPipelineTarget(Objects.requireNonNull(pipeline, "the additive pipeline"), argb, eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
        try {
            client.tick();
            
            Assertions.assertThat(TargetQuads.failure()).as("the failure of drawing onto the target of the pipeline").isNull();
            int visible = PipelineTargetSamples.pixel(client, client.capture(), eye.add(PipelineTargetSamples.VISIBLE));
            PipelineTargetSamples.assertCloseTo(visible, PipelineTargetSamples.added(before, added), "the additive quad");
        } finally {
            TargetQuads.hide();
        }
    }
    
    private static int added(int before, int added) {
        int sum = 0;
        for (int shift = 0; shift < PipelineTargetSamples.COLOR_BITS; shift += Byte.SIZE) {
            sum |= Math.min(0xFF, (before >>> shift & 0xFF) + (added >>> shift & 0xFF)) << shift;
        }
        
        return sum;
    }
    
    private static void assertCloseTo(int visible, int expected, String quad) {
        for (int shift = 0; shift < PipelineTargetSamples.COLOR_BITS; shift += Byte.SIZE) {
            Assertions.assertThat(visible >>> shift & 0xFF)
                    .as("the channel at bit '%d' of %s, '%08X', against '%08X'", shift, quad, visible, expected)
                    .isCloseTo(expected >>> shift & 0xFF, Assertions.within(PipelineTargetSamples.TOLERANCE));
        }
    }
    
    private static Vec3 enter(Client client) {
        client.joinWorld();
        client.hideHud(true);
        client.pose(client.serverLevel().getRespawnData().pos().getBottomCenter().add(0.0D, PipelineTargetSamples.HEIGHT, 0.0D), 0.0F, 0.0F);
        client.awaitStill(PipelineTargetSamples.STILL_FRAMES);
        
        Vec3 eye = client.minecraft().gameRenderer.getMainCamera().position();
        client.serverLevel().setBlockAndUpdate(BlockPos.containing(eye.add(PipelineTargetSamples.BLOCK)), Blocks.STONE.defaultBlockState());
        client.settle();
        client.awaitStill(PipelineTargetSamples.STILL_FRAMES);
        return eye;
    }
    
    private static int pixel(Client client, Capture capture, Vec3 point) {
        Vector2f screen = Objects.requireNonNull(Nexus.getMod().getRender().project(point), "the point lies behind the camera");
        int scale = client.minecraft().getWindow().getGuiScale();
        return capture.pixel((int) (screen.x * scale), (int) (screen.y * scale));
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Render render = Nexus.getMod().getRender();
            PipelineTargetSamples.red = render.pipeline(() -> TargetQuads::red, Shading.OWN);
            PipelineTargetSamples.additive = render.pipeline("pipeline_target_samples_additive", () -> TargetQuads::additiveState, Shading.OWN);
            PipelineTargetSamples.lightning = render.pipeline("pipeline_target_samples_lightning", () -> TargetQuads::lightningState, Shading.OWN);
            render.pipeline(() -> TargetQuads::cover, Shading.OWN);
            render.draw(() -> TargetQuads::drawHook);
            if (FMLEnvironment.getDist().isClient()) {
                NeoForge.EVENT_BUS.addListener(SubmitCustomGeometryEvent.class, TargetQuads::submit);
                NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentFeatures.class, TargetQuads::drawEarly);
                NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class, TargetQuads::draw);
                // At a high priority this draws before a composite at normal priority, whichever of the two listened first.
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RenderLevelStageEvent.AfterLevel.class, TargetQuads::drawAfterLevel);
                // The run that sets this property checks the samples against a main target with a stencil, which any mod may ask for.
                if (Boolean.getBoolean(PipelineTargetSamples.STENCIL_PROPERTY)) {
                    Nexus.getMod().getEventBus().addListener(ConfigureMainRenderTargetEvent.class, ConfigureMainRenderTargetEvent::enableStencil);
                }
            }
        }
    }
}
