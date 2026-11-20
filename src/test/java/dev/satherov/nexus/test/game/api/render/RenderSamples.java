package dev.satherov.nexus.test.game.api.render;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.api.render.glow.Glow;
import dev.satherov.nexus.api.render.glow.Glows;
import dev.satherov.nexus.api.render.hud.HudSlot;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.api.render.post.PostEffect;
import dev.satherov.nexus.api.render.shape.Drawing;
import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.api.render.shape.Scene;
import dev.satherov.nexus.api.render.shape.Shape;
import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.test.game.api.render.pipeline.SampleQuads;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

///
/// Checks the registrations of one mod with the renderer, how its draw hooks share the pose stack, and when its state ends.
///
public class RenderSamples {
    
    private static final String ANCHORED = "render_samples_anchored";
    
    private static final String ANCHOR = "render_samples_anchor";
    
    private static final String KEY = "render_samples";
    
    private static final int RED = 0xFFFF0000;
    
    private static final int GREEN = 0xFF00FF00;
    
    private static final int BLUE = 0xFF0000FF;
    
    private static final int RED_MINIMUM = 0x40;
    
    private static final int OTHER_MAXIMUM = 0x10;
    
    private static final int HUD_OFFSET = 40;
    
    private static final int ANCHORED_HALF = 4;
    
    private static final int ANCHOR_HALF = 8;
    
    private static final int STILL_FRAMES = 10;
    
    private static final int HURT_DURATION = 10;
    
    private static final int HURT_TIME = 8;
    
    private static final float TILT_DISTANCE = 4.0F;
    
    private static final double HEIGHT = 4.0D;
    
    private static final double DISTANCE = 6.0D;
    
    private static final double MARKER_SIZE = 0.5D;
    
    private static final Vec3 MARKER = new Vec3(3.0D, 1.5D, 6.0D);
    
    private static boolean hudShown;
    
    @ClientTest
    public static void pipelineSupplierIsCalledOnce(Client client) {
        Pipeline pipeline = Objects.requireNonNull(RenderSamples.Registered.fresh, "the pipeline over a fresh render pipeline");
        Assertions.assertThat(pipeline.getPipeline().get().get()).as("the render pipeline of the pipeline").isSameAs(pipeline.getPipeline().get().get());
        
        Vec3 center = RenderSamples.enter(client).add(0.0D, 0.0D, RenderSamples.DISTANCE);
        SampleQuads.setTint(RenderSamples.BLUE);
        SampleHooks.show(pipeline, center);
        try {
            client.tick();
            RenderSamples.assertColor(client, client.capture(), center, RenderSamples.BLUE);
        } finally {
            SampleHooks.hide();
        }
    }
    
    @ClientTest
    public static void drawHookTranslationIsUndone(Client client) {
        Vec3 center = RenderSamples.enter(client).add(0.0D, 0.0D, RenderSamples.DISTANCE);
        SampleQuads.setTint(RenderSamples.GREEN);
        SampleHooks.show(Objects.requireNonNull(RenderSamples.Registered.fresh, "the pipeline over a fresh render pipeline"), center);
        SampleHooks.translate();
        try {
            client.tick();
            RenderSamples.assertColor(client, client.capture(), center, RenderSamples.GREEN);
        } finally {
            SampleHooks.hide();
        }
    }
    
    @ClientTest
    public static void drawHookPoseLeftPushedIsPopped(Client client) {
        Vec3 center = RenderSamples.enter(client).add(0.0D, 0.0D, RenderSamples.DISTANCE);
        SampleQuads.setTint(RenderSamples.GREEN);
        SampleHooks.show(Objects.requireNonNull(RenderSamples.Registered.fresh, "the pipeline over a fresh render pipeline"), center);
        SampleHooks.push();
        try {
            Assertions.assertThatNoException().as("the frame of a hook that leaves a pose pushed").isThrownBy(client::tick);
            RenderSamples.assertColor(client, client.capture(), center, RenderSamples.GREEN);
        } finally {
            SampleHooks.hide();
        }
    }
    
    @ClientTest
    public static void hudLayerAnchorsToLaterLayer(Client client) {
        RenderSamples.enter(client);
        client.hideHud(false);
        int x = client.minecraft().getWindow().getGuiScaledWidth() / 2 + RenderSamples.HUD_OFFSET;
        int y = client.minecraft().getWindow().getGuiScaledHeight() / 2;
        RenderSamples.hudShown = true;
        try {
            client.tick();
            
            Capture capture = client.capture();
            int scale = client.minecraft().getWindow().getGuiScale();
            capture.assertPixel(x * scale, y * scale, RenderSamples.BLUE);
            capture.assertPixel((x + RenderSamples.ANCHORED_HALF) * scale, y * scale, RenderSamples.RED);
        } finally {
            RenderSamples.hudShown = false;
        }
    }
    
    @ClientTest
    public static void projectionFollowsHurtTilt(Client client) {
        Vec3 point = RenderSamples.enter(client).add(RenderSamples.MARKER);
        RenderSamples.scene().draw(Shape.box(AABB.ofSize(point, RenderSamples.MARKER_SIZE, RenderSamples.MARKER_SIZE, RenderSamples.MARKER_SIZE)), Paint.fill(RenderSamples.GREEN));
        client.tick();
        Vector2f still = RenderSamples.project(point);
        
        LocalPlayer player = Objects.requireNonNull(client.minecraft().player, "the player of the client");
        player.hurtDuration = RenderSamples.HURT_DURATION;
        player.hurtTime = RenderSamples.HURT_TIME;
        try {
            client.tick();
            
            Vector2f tilted = RenderSamples.project(point);
            Assertions.assertThat(tilted.distance(still)).as("the distance the hurt tilt moved the projection").isGreaterThan(RenderSamples.TILT_DISTANCE);
            RenderSamples.assertColor(client, client.capture(), point, RenderSamples.shaded(client, RenderSamples.GREEN));
        } finally {
            player.hurtTime = 0;
        }
    }
    
    @ClientTest
    public static void unloadOfAnotherLevelKeepsState(Client client) {
        Vec3 eye = RenderSamples.enter(client);
        Drawing drawing = RenderSamples.scene().draw(Shape.box(AABB.ofSize(eye.add(0.0D, 0.0D, RenderSamples.DISTANCE), 1.0D, 1.0D, 1.0D)), Paint.fill(RenderSamples.RED));
        Glow glow = RenderSamples.glows().block(RenderSamples.KEY, BlockPos.containing(eye.add(0.0D, -1.0D, 4.0D)), RenderSamples.RED);
        client.tick();
        
        NeoForge.EVENT_BUS.post(new LevelEvent.Unload(RenderSamples.otherClientLevel()));
        Assertions.assertThat(drawing.isActive()).as("the drawing after another client level unloaded").isTrue();
        Assertions.assertThat(glow.isActive()).as("the glow after another client level unloaded").isTrue();
    }
    
    @ClientTest
    public static void leavingLevelDropsEveryGlow(Client client) {
        Vec3 eye = RenderSamples.enter(client);
        BlockPos pos = BlockPos.containing(eye.add(0.0D, -1.0D, 4.0D));
        RenderSamples.place(client, pos);
        LocalPlayer player = Objects.requireNonNull(client.minecraft().player, "the player of the client");
        RenderSamples.glows().entity(RenderSamples.KEY + "_entity", player, RenderSamples.RED);
        RenderSamples.glows().block(RenderSamples.KEY, pos, RenderSamples.RED);
        client.tick();
        Assertions.assertThat(RenderSamples.glows().hasAny()).as("the glows before leaving").isTrue();
        
        RenderSamples.enter(client);
        Assertions.assertThat(RenderSamples.glows().hasAny()).as("the glows after the rejoin").isFalse();
        RenderSamples.place(client, pos);
        Assertions.assertThat(RenderSamples.redPixels(client, client.capture(), new AABB(pos))).as("the red pixels around the block after the rejoin").isZero();
    }
    
    @ClientTest
    public static void setupInLoadSurvivesDimensionChange(Client client) {
        Vec3 eye = RenderSamples.enter(client);
        AABB box = AABB.ofSize(eye.add(0.0D, 0.0D, RenderSamples.DISTANCE), 1.0D, 1.0D, 1.0D);
        BlockPos pos = BlockPos.containing(eye.add(0.0D, -1.0D, 4.0D));
        PostEffect leftEffect = Nexus.getMod().getRender().postEffect("red");
        PostEffect loadedEffect = Nexus.getMod().getRender().postEffect("invert");
        Drawing leftDrawing = RenderSamples.scene().draw(Shape.box(box), Paint.fill(RenderSamples.RED));
        Glow leftGlow = RenderSamples.glows().block(RenderSamples.KEY, pos, RenderSamples.RED);
        AtomicReference<Drawing> loadedDrawing = new AtomicReference<>();
        AtomicReference<Glow> loadedGlow = new AtomicReference<>();
        Consumer<LevelEvent.Load> listener = event -> {
            if (event.getLevel() instanceof ClientLevel level && level.dimension() == Level.NETHER) {
                loadedDrawing.set(RenderSamples.scene().draw(Shape.box(box), Paint.fill(RenderSamples.GREEN)));
                loadedGlow.set(RenderSamples.glows().block(RenderSamples.KEY + "_nether", pos, RenderSamples.GREEN));
                loadedEffect.activate();
            }
        };
        
        leftEffect.activate();
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, listener);
        try {
            client.onServer(level -> {
                ServerPlayer player = level.players().getFirst();
                ServerLevel nether = Objects.requireNonNull(level.getServer().getLevel(Level.NETHER), "the nether of the server");
                player.teleport(new TeleportTransition(nether, player.position(), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));
            });
            client.until("the client to enter the nether", () -> {
                ClientLevel current = client.minecraft().level;
                return current != null && current.dimension() == Level.NETHER;
            });
            
            Assertions.assertThat(leftDrawing.isActive()).as("the drawing made before the dimension change").isFalse();
            Assertions.assertThat(leftGlow.isActive()).as("the glow made before the dimension change").isFalse();
            Assertions.assertThat(leftEffect.isActive()).as("the effect activated before the dimension change").isFalse();
            Assertions.assertThat(Objects.requireNonNull(loadedDrawing.get(), "the drawing made in the load of the nether").isActive()).as("the drawing made in the load of the nether").isTrue();
            Assertions.assertThat(Objects.requireNonNull(loadedGlow.get(), "the glow made in the load of the nether").isActive()).as("the glow made in the load of the nether").isTrue();
            Assertions.assertThat(RenderSamples.glows().hasAny()).as("the glows after the dimension change").isTrue();
            Assertions.assertThat(loadedEffect.isActive()).as("the effect activated in the load of the nether").isTrue();
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            leftEffect.deactivate();
            loadedEffect.deactivate();
        }
    }
    
    private static Vec3 enter(Client client) {
        client.joinWorld();
        client.hideHud(true);
        client.pose(client.serverLevel().getRespawnData().pos().getBottomCenter().add(0.0D, RenderSamples.HEIGHT, 0.0D), 0.0F, 0.0F);
        client.awaitStill(RenderSamples.STILL_FRAMES);
        return client.minecraft().gameRenderer.getMainCamera().position();
    }
    
    private static Scene scene() {
        return Nexus.getMod().getRender().getScene();
    }
    
    private static Glows glows() {
        return Nexus.getMod().getRender().getGlows();
    }
    
    private static void place(Client client, BlockPos pos) {
        client.onServer(level -> level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState()));
        client.settle();
        client.awaitStill(RenderSamples.STILL_FRAMES);
    }
    
    private static LevelAccessor otherClientLevel() {
        return (LevelAccessor) Proxy.newProxyInstance(
                LevelAccessor.class.getClassLoader(),
                new Class<?>[]{ LevelAccessor.class },
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "isClientSide" -> Boolean.TRUE;
                    case "hashCode" -> Integer.valueOf(System.identityHashCode(proxy));
                    case "equals" -> Boolean.valueOf(proxy == arguments[0]);
                    case "toString" -> "the other client level of the render samples";
                    default -> throw new UnsupportedOperationException("The other client level has no '" + method.getName() + "'");
                }
        );
    }
    
    private static Vector2f project(Vec3 point) {
        return Objects.requireNonNull(Nexus.getMod().getRender().project(point), "the point lies behind the camera");
    }
    
    private static Vector2i pixel(Client client, Vec3 point) {
        Vector2f screen = RenderSamples.project(point);
        int scale = client.minecraft().getWindow().getGuiScale();
        return new Vector2i((int) (screen.x * scale), (int) (screen.y * scale));
    }
    
    private static void assertColor(Client client, Capture capture, Vec3 point, int argb) {
        Vector2i pixel = RenderSamples.pixel(client, point);
        capture.assertPixel(pixel.x, pixel.y, argb);
    }
    
    private static int shaded(Client client, int argb) {
        return client.hasShaderPack() ? ARGB.color(ARGB.alpha(argb), ARGB.blue(argb), ARGB.green(argb), ARGB.red(argb)) : argb;
    }
    
    private static int redPixels(Client client, Capture capture, AABB box) {
        Vector2i lower = RenderSamples.pixel(client, new Vec3(box.minX, box.minY, box.minZ));
        Vector2i upper = RenderSamples.pixel(client, new Vec3(box.maxX, box.maxY, box.minZ));
        int count = 0;
        for (int y = Math.min(lower.y, upper.y); y <= Math.max(lower.y, upper.y); y++) {
            for (int x = Math.min(lower.x, upper.x); x <= Math.max(lower.x, upper.x); x++) {
                int pixel = capture.pixel(x, y);
                if (ARGB.red(pixel) >= RenderSamples.RED_MINIMUM && ARGB.green(pixel) <= RenderSamples.OTHER_MAXIMUM && ARGB.blue(pixel) <= RenderSamples.OTHER_MAXIMUM) {
                    count++;
                }
            }
        }
        
        return count;
    }
    
    private static void anchored(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        RenderSamples.square(graphics, RenderSamples.ANCHORED_HALF, RenderSamples.BLUE);
    }
    
    private static void anchor(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        RenderSamples.square(graphics, RenderSamples.ANCHOR_HALF, RenderSamples.RED);
    }
    
    private static void square(GuiGraphicsExtractor graphics, int half, int argb) {
        if (RenderSamples.hudShown) {
            int x = graphics.guiWidth() / 2 + RenderSamples.HUD_OFFSET;
            int y = graphics.guiHeight() / 2;
            graphics.fill(x - half, y - half, x + half, y + half, argb);
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        // Kept here rather than in the samples, whose client bodies a dedicated server can't link.
        private static @Nullable Pipeline fresh;
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Render render = Nexus.getMod().getRender();
            Pipeline pipeline = render.pipeline(() -> SampleHooks::fresh, Shading.OWN);
            pipeline.bind(SampleQuads.TINT_BLOCK, () -> SampleQuads::tintBlock);
            RenderSamples.Registered.fresh = pipeline;
            render.draw(() -> SampleHooks::shift);
            render.draw(() -> SampleHooks::draw);
            render.hudLayer(RenderSamples.ANCHORED, HudSlot.above(Nexus.getMod().id(RenderSamples.ANCHOR)), () -> RenderSamples::anchored);
            render.hudLayer(RenderSamples.ANCHOR, HudSlot.top(), () -> RenderSamples::anchor);
        }
    }
}
