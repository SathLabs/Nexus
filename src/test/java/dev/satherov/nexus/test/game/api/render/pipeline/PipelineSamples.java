package dev.satherov.nexus.test.game.api.render.pipeline;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// The client tests that check a mod's own pipeline drawn through the draw hook, its bound uniform blocks, and its registration after the event.
///
public class PipelineSamples {
    
    private static final int GREEN = 0xFF00FF00;
    
    private static final int BLUE = 0xFF0000FF;
    
    private static final int YELLOW = 0xFFFFFF00;
    
    private static final int WHITE = 0xFFFFFFFF;
    
    private static final int STILL_FRAMES = 10;
    
    private static final double HEIGHT = 4.0D;
    
    private static final double DISTANCE = 6.0D;
    
    private static @Nullable Pipeline tinted;
    
    @ClientTest
    public static void ownShaderDrawsThroughDrawHook(Client client) {
        Vec3 center = PipelineSamples.enter(client);
        SampleQuads.showSolid(center);
        try {
            client.tick();
            
            Capture capture = client.capture();
            PipelineSamples.assertColor(client, capture, center, PipelineSamples.GREEN);
            capture.assertGolden("own_shader_draws_through_draw_hook");
        } finally {
            SampleQuads.hide();
        }
    }
    
    @ClientTest
    public static void boundUniformReachesShader(Client client) {
        Vec3 center = PipelineSamples.enter(client);
        SampleQuads.setTint(PipelineSamples.BLUE);
        SampleQuads.showTinted(center);
        try {
            client.tick();
            
            Capture capture = client.capture();
            PipelineSamples.assertColor(client, capture, center, PipelineSamples.BLUE);
            capture.assertGolden("bound_uniform_reaches_shader");
            
            SampleQuads.setTint(PipelineSamples.YELLOW);
            client.tick();
            PipelineSamples.assertColor(client, client.capture(), center, PipelineSamples.YELLOW);
        } finally {
            SampleQuads.hide();
        }
    }
    
    @ClientTest
    public static void laterBindingReplacesEarlier(Client client) {
        Vec3 center = PipelineSamples.enter(client);
        Pipeline pipeline = Objects.requireNonNull(PipelineSamples.tinted, "the tinted pipeline");
        pipeline.bind(SampleQuads.TINT_BLOCK, () -> SampleQuads::tintBlock);
        pipeline.bind(SampleQuads.TINT_BLOCK, () -> SampleQuads::whiteBlock);
        SampleQuads.setTint(PipelineSamples.BLUE);
        SampleQuads.showTinted(center);
        try {
            client.tick();
            PipelineSamples.assertColor(client, client.capture(), center, PipelineSamples.WHITE);
        } finally {
            SampleQuads.hide();
            pipeline.bind(SampleQuads.TINT_BLOCK, () -> SampleQuads::tintBlock);
        }
    }
    
    @ClientTest
    public static void registrationAfterTheEventThrows(Client client) {
        Render render = Nexus.getMod().getRender();
        Assertions.assertThatIllegalStateException()
                .as("a pipeline registered after the render pipelines were registered")
                .isThrownBy(() -> render.pipeline(() -> SampleQuads::solid, Shading.OWN));
    }
    
    private static Vec3 enter(Client client) {
        client.joinWorld();
        client.hideHud(true);
        client.pose(client.serverLevel().getRespawnData().pos().getBottomCenter().add(0.0D, PipelineSamples.HEIGHT, 0.0D), 0.0F, 0.0F);
        client.awaitStill(PipelineSamples.STILL_FRAMES);
        return client.minecraft().gameRenderer.getMainCamera().position().add(0.0D, 0.0D, PipelineSamples.DISTANCE);
    }
    
    private static void assertColor(Client client, Capture capture, Vec3 point, int argb) {
        Vector2f screen = Objects.requireNonNull(Nexus.getMod().getRender().project(point), "the point lies behind the camera");
        int scale = client.minecraft().getWindow().getGuiScale();
        capture.assertPixel((int) (screen.x * scale), (int) (screen.y * scale), argb);
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Render render = Nexus.getMod().getRender();
            render.pipeline(() -> SampleQuads::solid, Shading.OWN);
            Pipeline pipeline = render.pipeline(() -> SampleQuads::tinted, Shading.OWN);
            pipeline.bind(SampleQuads.TINT_BLOCK, () -> SampleQuads::tintBlock);
            PipelineSamples.tinted = pipeline;
            render.draw(() -> SampleQuads::draw);
        }
    }
}
