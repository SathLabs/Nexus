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
import net.neoforged.fml.loading.FMLEnvironment;

import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.systems.RenderSystem;

import org.assertj.core.api.Assertions;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// Checks that a mod's pipeline draws through the draw hook with its bound uniform blocks, and that a program file builds one.
///
public class PipelineSamples {
    
    private static final int GREEN = 0xFF00FF00;
    
    private static final int BLUE = 0xFF0000FF;
    
    private static final int YELLOW = 0xFFFFFF00;
    
    private static final int WHITE = 0xFFFFFFFF;
    
    private static final int STILL_FRAMES = 10;
    
    private static final int BROKEN_FRAMES = 3;
    
    private static final int FOG_TOLERANCE = 8;
    
    private static final double HEIGHT = 4.0D;
    
    private static final double DISTANCE = 6.0D;
    
    private static @Nullable Pipeline tinted;
    
    private static @Nullable Pipeline color;
    
    private static @Nullable Pipeline textured;
    
    private static @Nullable Pipeline entity;
    
    private static @Nullable Pipeline shaded;
    
    private static @Nullable Pipeline broken;
    
    private static @Nullable Pipeline unbound;
    
    private static @Nullable Pipeline fog;
    
    private static @Nullable Throwable missing;
    
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
        Assertions.assertThatIllegalStateException()
                .as("a pipeline over a missing program file created after the render pipelines were registered")
                .isThrownBy(() -> render.pipeline("pipeline_samples_missing", Shading.OWN));
        Assertions.assertThatIllegalStateException()
                .as("a pipeline with a state over a missing program file created after the render pipelines were registered")
                .isThrownBy(() -> render.pipeline("pipeline_samples_missing", () -> SampleQuads::texturedState, Shading.OWN));
    }
    
    @ClientTest
    public static void colorProgramRunsEveryHook(Client client) {
        PipelineSamples.assertProgramShowsTint(client, PipelineSamples.color, PipelineSamples.GREEN, 0);
    }
    
    @ClientTest
    public static void texturedProgramRunsEveryHook(Client client) {
        PipelineSamples.assertProgramShowsTint(client, PipelineSamples.textured, PipelineSamples.BLUE, 0);
    }
    
    @ClientTest
    public static void entityProgramRunsEveryHook(Client client) {
        PipelineSamples.assertProgramShowsTint(client, PipelineSamples.entity, PipelineSamples.YELLOW, PipelineSamples.FOG_TOLERANCE);
    }
    
    @ClientTest
    public static void shadedProgramIsDrawnByThePack(Client client) {
        PipelineSamples.assertProgramDraws(
                client,
                PipelineSamples.shaded,
                client.hasShaderPack() ? PipelineSamples.WHITE : PipelineSamples.BLUE,
                "shaded_program_is_drawn_by_the_pack"
        );
    }
    
    @ClientTest
    public static void ownProgramKeepsItsHooksUnderThePack(Client client) {
        PipelineSamples.assertProgramDraws(client, PipelineSamples.color, PipelineSamples.BLUE, "own_program_keeps_its_hooks_under_the_pack");
    }
    
    @ClientTest
    public static void brokenProgramIsSkippedAndLoggedOnce(Client client) {
        Vec3 center = PipelineSamples.enter(client);
        Vector2i pixel = PipelineSamples.pixelOf(client, center);
        int background = client.capture().pixel(pixel.x, pixel.y);
        SampleQuads.showProgram(Objects.requireNonNull(PipelineSamples.broken, "the broken program pipeline"), center);
        try {
            client.ticks(PipelineSamples.BROKEN_FRAMES);
            
            Assertions.assertThat(SampleQuads.failure()).as("the failure of drawing the broken program").isNull();
            client.capture().assertPixel(pixel.x, pixel.y, background);
        } finally {
            SampleQuads.hide();
        }
        
        Assertions.assertThat(SampleQuads.brokenLogs()).as("the logged failures of the broken program").hasSize(1);
    }
    
    @ClientTest
    public static void missingProgramFileThrows(Client client) {
        Assertions.assertThat(PipelineSamples.missing)
                .as("the failure of a pipeline over a missing program file")
                .isInstanceOf(IllegalArgumentException.class);
    }
    
    @ClientTest
    public static void vanillaBlockOfProgramNeedsNoBinding(Client client) {
        PipelineSamples.assertProgramShowsTint(client, PipelineSamples.fog, PipelineSamples.WHITE, 0);
    }
    
    @ClientTest
    public static void hookThrowingMidVertexLeavesNextFrameClean(Client client) {
        Vec3 center = PipelineSamples.enter(client);
        SampleQuads.setTint(PipelineSamples.GREEN);
        SampleQuads.showProgram(Objects.requireNonNull(PipelineSamples.color, "the color program pipeline"), center);
        try {
            SampleQuads.throwMidVertex();
            Assertions.assertThatIllegalStateException()
                    .as("the frame of a hook that throws in the middle of a vertex")
                    .isThrownBy(client::tick)
                    .withMessage(SampleQuads.MID_VERTEX_FAILURE);
            
            // Vanilla leaves the model-view matrix of the aborted frame on the stack, which would move everything drawn after it.
            RenderSystem.getModelViewStack().clear();
            client.tick();
            Assertions.assertThat(SampleQuads.failure()).as("the failure of the frame after").isNull();
            PipelineSamples.assertColor(client, client.capture(), center, PipelineSamples.GREEN);
        } finally {
            SampleQuads.hide();
        }
    }
    
    @ClientTest
    public static void unboundProgramBlockThrowsAtFirstDraw(Client client) {
        Vec3 center = PipelineSamples.enter(client);
        SampleQuads.showProgram(Objects.requireNonNull(PipelineSamples.unbound, "the unbound program pipeline"), center);
        try {
            client.tick();
            Assertions.assertThat(SampleQuads.failure())
                    .as("the failure of the first draw with an unbound block")
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            SampleQuads.hide();
        }
    }
    
    private static Vec3 enter(Client client) {
        client.joinWorld();
        client.hideHud(true);
        client.pose(client.serverLevel().getRespawnData().pos().getBottomCenter().add(0.0D, PipelineSamples.HEIGHT, 0.0D), 0.0F, 0.0F);
        client.awaitStill(PipelineSamples.STILL_FRAMES);
        return client.minecraft().gameRenderer.getMainCamera().position().add(0.0D, 0.0D, PipelineSamples.DISTANCE);
    }
    
    private static void assertProgramShowsTint(Client client, @Nullable Pipeline program, int argb, int tolerance) {
        Vec3 center = PipelineSamples.enter(client);
        SampleQuads.setTint(argb);
        SampleQuads.showProgram(Objects.requireNonNull(program, "the program pipeline"), center);
        try {
            client.tick();
            
            Assertions.assertThat(SampleQuads.failure()).as("the failure of drawing the program").isNull();
            Vector2i pixel = PipelineSamples.pixelOf(client, center);
            int actual = client.capture().pixel(pixel.x, pixel.y);
            for (int shift = 0; shift < Integer.SIZE; shift += Byte.SIZE) {
                Assertions.assertThat(actual >>> shift & 0xFF)
                        .as("the channel at bit '%d' of the color '%08X' at '%d, %d'", shift, actual, pixel.x, pixel.y)
                        .isCloseTo(argb >>> shift & 0xFF, Assertions.within(tolerance));
            }
        } finally {
            SampleQuads.hide();
        }
    }
    
    private static void assertProgramDraws(Client client, @Nullable Pipeline program, int expected, String golden) {
        Vec3 center = PipelineSamples.enter(client);
        SampleQuads.setTint(PipelineSamples.BLUE);
        SampleQuads.showProgram(Objects.requireNonNull(program, "the program pipeline"), center);
        try {
            client.tick();
            
            Assertions.assertThat(SampleQuads.failure()).as("the failure of drawing the program").isNull();
            Capture capture = client.capture();
            PipelineSamples.assertColor(client, capture, center, expected);
            capture.assertGolden(golden);
        } finally {
            SampleQuads.hide();
        }
    }
    
    private static void assertColor(Client client, Capture capture, Vec3 point, int argb) {
        Vector2i pixel = PipelineSamples.pixelOf(client, point);
        capture.assertPixel(pixel.x, pixel.y, argb);
    }
    
    private static Vector2i pixelOf(Client client, Vec3 point) {
        Vector2f screen = Objects.requireNonNull(Nexus.getMod().getRender().project(point), "the point lies behind the camera");
        int scale = client.minecraft().getWindow().getGuiScale();
        return new Vector2i((int) (screen.x * scale), (int) (screen.y * scale));
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            if (FMLEnvironment.getDist().isClient()) {
                SampleQuads.captureBrokenLogs();
            }
            
            Render render = Nexus.getMod().getRender();
            render.pipeline(() -> SampleQuads::solid, Shading.OWN);
            Pipeline pipeline = render.pipeline(() -> SampleQuads::tinted, Shading.OWN);
            pipeline.bind(SampleQuads.TINT_BLOCK, () -> SampleQuads::tintBlock);
            PipelineSamples.tinted = pipeline;
            
            Pipeline color = render.pipeline("pipeline_samples_color", Shading.OWN);
            color.bind(SampleQuads.TINT_BLOCK, () -> SampleQuads::tintBlock);
            PipelineSamples.color = color;
            Pipeline textured = render.pipeline("pipeline_samples_textured", () -> SampleQuads::texturedState, Shading.OWN);
            textured.bind(SampleQuads.TINT_BLOCK, () -> SampleQuads::tintBlock);
            PipelineSamples.textured = textured;
            Pipeline entity = render.pipeline("pipeline_samples_entity", () -> SampleQuads::entityState, Shading.ENTITIES);
            entity.bind(SampleQuads.TINT_BLOCK, () -> SampleQuads::tintBlock);
            PipelineSamples.entity = entity;
            Pipeline shaded = render.pipeline("pipeline_samples_shaded", Shading.BASIC);
            shaded.bind(SampleQuads.TINT_BLOCK, () -> SampleQuads::tintBlock);
            PipelineSamples.shaded = shaded;
            
            PipelineSamples.broken = render.pipeline("pipeline_samples_broken", Shading.OWN);
            PipelineSamples.unbound = render.pipeline("pipeline_samples_unbound", Shading.OWN);
            PipelineSamples.fog = render.pipeline("pipeline_samples_fog", Shading.OWN);
            PipelineSamples.missing = Assertions.catchThrowable(() -> render.pipeline("pipeline_samples_missing", Shading.OWN));
            render.draw(() -> SampleQuads::draw);
        }
    }
}
