package dev.satherov.nexus.test.game.api.render.pipeline;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// Checks that a mod's own render type over an own-shaded pipeline, drawn by the mod in the middle of the level,
/// keeps its color under a shader pack once it draws into the target of the pipeline, and is hidden by the level in front of it.
///
public class PipelineTargetSamples {
    
    private static final int RED = 0xFFFF0000;
    
    private static final int SWAPPED_RED = 0xFF0000FF;
    
    private static final int STILL_FRAMES = 10;
    
    private static final double HEIGHT = 4.0D;
    
    private static final double DISTANCE = 6.0D;
    
    private static final Vec3 BLOCK = new Vec3(-1.0D, 0.0D, 3.0D);
    
    private static final Vec3 VISIBLE = new Vec3(1.0D, 0.0D, PipelineTargetSamples.DISTANCE);
    
    private static final Vec3 HIDDEN = new Vec3(-1.5D, 0.0D, PipelineTargetSamples.DISTANCE);
    
    private static @Nullable Pipeline red;
    
    @ClientTest
    public static void ownTypeOnPipelineTargetKeepsItsColor(Client client) {
        Vec3 eye = PipelineTargetSamples.enter(client);
        TargetQuads.showOnPipelineTarget(PipelineTargetSamples.pipeline(), eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
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
        TargetQuads.showOnPipelineTarget(PipelineTargetSamples.pipeline(), eye.add(0.0D, 0.0D, PipelineTargetSamples.DISTANCE));
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
    
    private static Pipeline pipeline() {
        return Objects.requireNonNull(PipelineTargetSamples.red, "the red pipeline");
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
            PipelineTargetSamples.red = Nexus.getMod().getRender().pipeline(() -> TargetQuads::red, Shading.OWN);
            if (FMLEnvironment.getDist().isClient()) {
                NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class, TargetQuads::draw);
            }
        }
    }
}
