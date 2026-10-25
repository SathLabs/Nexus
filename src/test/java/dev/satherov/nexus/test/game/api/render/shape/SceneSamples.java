package dev.satherov.nexus.test.game.api.render.shape;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.api.render.shape.Drawing;
import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.api.render.shape.Scene;
import dev.satherov.nexus.api.render.shape.Shape;
import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// The client tests that check the depth modes, the water, the keys, the expiry, the followed entities, the repaint, and the end of the drawings of a scene, and a debug renderer's scene.
///
public class SceneSamples {
    
    private static final int RED = 0xFFFF0000;
    
    private static final int BLUE = 0xFF0000FF;
    
    private static final String KEY = "box";
    
    private static final String DEBUG = "scene_samples";
    
    private static final String LATE_DEBUG = "scene_samples_late";
    
    private static final int EXPIRY = 5;
    
    private static final int DEBUG_TICKS = 3;
    
    private static final int STILL_FRAMES = 10;
    
    private static final float WATER_PITCH = 20.0F;
    
    private static final double ENTITY_TOLERANCE = 1.0E-3D;
    
    private static final double HEIGHT = 4.0D;
    
    private static @Nullable Vec3 debugCenter;
    
    private static int debugCalls;
    
    @ClientTest
    public static void visibleBoxIsCutByWall(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        SceneSamples.wall(client, eye);
        SceneSamples.scene().draw(Shape.box(SceneSamples.slab(eye)), Paint.fill(SceneSamples.RED));
        client.tick();
        
        Capture capture = client.capture();
        Assertions.assertThat(SceneSamples.color(client, capture, eye.add(0.0D, 0.0D, 7.0D))).as("the box behind the wall").isNotEqualTo(SceneSamples.RED);
        SceneSamples.assertColor(client, capture, eye.add(2.0D, 0.0D, 7.0D), SceneSamples.RED);
        capture.assertGolden("visible_box_is_cut_by_wall");
    }
    
    @ClientTest
    public static void hiddenBoxShowsOnlyBehindWall(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        SceneSamples.wall(client, eye);
        SceneSamples.scene().draw(Shape.box(SceneSamples.slab(eye)), Paint.fill(SceneSamples.RED).withDepth(Paint.Depth.HIDDEN));
        client.tick();
        
        Capture capture = client.capture();
        SceneSamples.assertColor(client, capture, eye.add(0.0D, 0.0D, 7.0D), SceneSamples.RED);
        Assertions.assertThat(SceneSamples.color(client, capture, eye.add(2.0D, 0.0D, 7.0D))).as("the box beside the wall").isNotEqualTo(SceneSamples.RED);
        capture.assertGolden("hidden_box_shows_only_behind_wall");
    }
    
    @ClientTest
    public static void fillStandsInWater(Client client) {
        Vec3 eye = SceneSamples.enter(client, SceneSamples.WATER_PITCH);
        ServerLevel level = client.serverLevel();
        BlockPos floor = BlockPos.containing(eye).below(3);
        for (int x = -3; x <= 3; x++) {
            for (int z = 1; z <= 9; z++) {
                boolean rim = x == -3 || x == 3 || z == 1 || z == 9;
                BlockState inside = rim ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState();
                level.setBlockAndUpdate(floor.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(floor.offset(x, 1, z), inside);
            }
        }
        
        client.settle();
        AABB box = new AABB(eye.x - 0.5D, floor.getY() + 1.0D, eye.z + 4.0D, eye.x + 0.5D, eye.y - 0.62D, eye.z + 5.0D);
        SceneSamples.scene().draw(Shape.box(box), Paint.fill(SceneSamples.RED));
        client.tick();
        
        Capture capture = client.capture();
        SceneSamples.assertColor(client, capture, eye.add(0.0D, -1.12D, 4.0D), SceneSamples.RED);
        Assertions.assertThat(SceneSamples.color(client, capture, eye.add(0.0D, -2.2D, 4.0D))).as("the box below the water surface").isNotEqualTo(SceneSamples.RED);
    }
    
    @ClientTest
    public static void keyedDrawReplaces(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Vec3 first = eye.add(-1.5D, 0.0D, 6.0D);
        Vec3 second = eye.add(1.5D, 0.0D, 6.0D);
        Scene scene = SceneSamples.scene();
        Drawing replaced = scene.draw(SceneSamples.KEY, Shape.box(AABB.ofSize(first, 1.0D, 1.0D, 1.0D)), Paint.fill(SceneSamples.RED));
        Drawing drawing = scene.draw(SceneSamples.KEY, Shape.box(AABB.ofSize(second, 1.0D, 1.0D, 1.0D)), Paint.fill(SceneSamples.BLUE));
        client.tick();
        
        Capture capture = client.capture();
        Assertions.assertThat(replaced.isActive()).as("the replaced drawing").isFalse();
        Assertions.assertThat(scene.get(SceneSamples.KEY)).as("the drawing under the key").isSameAs(drawing);
        Assertions.assertThat(SceneSamples.color(client, capture, first)).as("the replaced box").isNotEqualTo(SceneSamples.RED);
        SceneSamples.assertColor(client, capture, second, SceneSamples.BLUE);
        capture.assertGolden("keyed_draw_replaces");
    }
    
    @ClientTest
    public static void expiryRemovesAfterTicksAndNotBefore(Client client) {
        if (RunOptions.fromProperties().realtime()) {
            return;
        }
        
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Vec3 center = eye.add(0.0D, 0.0D, 6.0D);
        Drawing drawing = SceneSamples.scene().draw(Shape.box(AABB.ofSize(center, 1.0D, 1.0D, 1.0D)), Paint.fill(SceneSamples.RED)).expire(SceneSamples.EXPIRY);
        
        client.ticks(SceneSamples.EXPIRY - 1);
        Assertions.assertThat(drawing.isActive()).as("the drawing a tick before it expires").isTrue();
        SceneSamples.assertColor(client, client.capture(), center, SceneSamples.RED);
        
        client.tick();
        Assertions.assertThat(drawing.isActive()).as("the drawing once it expired").isFalse();
        Assertions.assertThat(SceneSamples.color(client, client.capture(), center)).as("the expired box").isNotEqualTo(SceneSamples.RED);
    }
    
    @ClientTest
    public static void followedEntityMovesDrawing(Client client) {
        if (RunOptions.fromProperties().realtime()) {
            return;
        }
        
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Vec3 start = eye.add(0.0D, -0.5D, 5.0D);
        Vec3 moved = eye.add(3.0D, -0.5D, 5.0D);
        ServerLevel level = client.serverLevel();
        ArmorStand stand = new ArmorStand(level, start.x, start.y, start.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        level.addFreshEntity(stand);
        client.settle();
        
        ClientLevel clientLevel = Objects.requireNonNull(client.minecraft().level);
        Entity entity = Objects.requireNonNull(clientLevel.getEntity(stand.getId()), "the armor stand on the client");
        client.until("the armor stand to stand at its start", () -> SceneSamples.isAt(entity, start));
        Drawing drawing = SceneSamples.scene().draw(Shape.box(new AABB(-0.5D, 0.0D, -0.5D, 0.5D, 1.0D, 0.5D)), Paint.fill(SceneSamples.RED)).follow(entity);
        client.tick();
        SceneSamples.assertColor(client, client.capture(), start.add(0.0D, 0.5D, 0.0D), SceneSamples.RED);
        
        stand.teleportTo(moved.x, moved.y, moved.z);
        client.until("the armor stand to arrive", () -> SceneSamples.isAt(entity, moved));
        client.tick();
        
        Capture capture = client.capture();
        Assertions.assertThat(drawing.isActive()).as("the drawing of the moved armor stand").isTrue();
        Assertions.assertThat(SceneSamples.color(client, capture, start.add(0.0D, 0.5D, 0.0D))).as("the start of the armor stand").isNotEqualTo(SceneSamples.RED);
        SceneSamples.assertColor(client, capture, moved.add(0.0D, 0.5D, 0.0D), SceneSamples.RED);
    }
    
    @ClientTest
    public static void repaintChangesColor(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Vec3 center = eye.add(0.0D, 0.0D, 6.0D);
        Drawing drawing = SceneSamples.scene().draw(Shape.box(AABB.ofSize(center, 1.0D, 1.0D, 1.0D)), Paint.fill(SceneSamples.RED));
        client.tick();
        SceneSamples.assertColor(client, client.capture(), center, SceneSamples.RED);
        
        drawing.repaint(Paint.fill(SceneSamples.BLUE));
        client.tick();
        
        Capture capture = client.capture();
        Assertions.assertThat(drawing.getPaint()).as("the paint after the repaint").isEqualTo(Paint.fill(SceneSamples.BLUE));
        SceneSamples.assertColor(client, capture, center, SceneSamples.BLUE);
        capture.assertGolden("repaint_changes_color");
    }
    
    @ClientTest
    public static void leavingLevelDropsEverything(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Scene scene = SceneSamples.scene();
        Drawing keyed = scene.draw(SceneSamples.KEY, Shape.box(AABB.ofSize(eye.add(0.0D, 0.0D, 6.0D), 1.0D, 1.0D, 1.0D)), Paint.fill(SceneSamples.RED));
        Drawing unkeyed = scene.draw(Shape.box(AABB.ofSize(eye.add(2.0D, 0.0D, 6.0D), 1.0D, 1.0D, 1.0D)), Paint.fill(SceneSamples.BLUE));
        client.tick();
        client.leaveWorld();
        
        Assertions.assertThat(keyed.isActive()).as("the keyed drawing after leaving").isFalse();
        Assertions.assertThat(unkeyed.isActive()).as("the drawing under no key after leaving").isFalse();
        Assertions.assertThatIllegalStateException().as("the scene outside a level").isThrownBy(() -> scene.get(SceneSamples.KEY));
        
        Vec3 rejoined = SceneSamples.enter(client, 0.0F);
        Capture capture = client.capture();
        Assertions.assertThat(scene.get(SceneSamples.KEY)).as("the drawing under the key after the rejoin").isNull();
        Assertions.assertThat(SceneSamples.color(client, capture, rejoined.add(0.0D, 0.0D, 6.0D))).as("the keyed box after the rejoin").isNotEqualTo(SceneSamples.RED);
        Assertions.assertThat(SceneSamples.color(client, capture, rejoined.add(2.0D, 0.0D, 6.0D))).as("the box under no key after the rejoin").isNotEqualTo(SceneSamples.BLUE);
    }
    
    @ClientTest
    public static void debugRendererRefillsWhileOn(Client client) {
        if (RunOptions.fromProperties().realtime()) {
            return;
        }
        
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Vec3 first = eye.add(-1.5D, 0.0D, 6.0D);
        Vec3 second = eye.add(1.5D, 0.0D, 6.0D);
        Identifier id = Nexus.getMod().id(SceneSamples.DEBUG);
        DebugScreenEntryList entries = client.minecraft().debugEntries;
        SceneSamples.debugCenter = first;
        entries.setStatus(id, DebugScreenEntryStatus.ALWAYS_ON);
        try {
            client.tick();
            SceneSamples.assertColor(client, client.capture(), first, SceneSamples.RED);
            
            SceneSamples.debugCenter = second;
            SceneSamples.debugCalls = 0;
            client.ticks(SceneSamples.DEBUG_TICKS);
            
            Capture moved = client.capture();
            Assertions.assertThat(SceneSamples.debugCalls).as("the calls of the renderer while it is on").isEqualTo(SceneSamples.DEBUG_TICKS);
            Assertions.assertThat(SceneSamples.color(client, moved, first)).as("the box of an earlier tick").isNotEqualTo(SceneSamples.RED);
            SceneSamples.assertColor(client, moved, second, SceneSamples.RED);
            moved.assertGolden("debug_renderer_refills_while_on");
            
            entries.setStatus(id, DebugScreenEntryStatus.NEVER);
            SceneSamples.debugCalls = 0;
            client.tick();
            Assertions.assertThat(SceneSamples.debugCalls).as("the calls of the renderer once it is off").isZero();
            Assertions.assertThat(SceneSamples.color(client, client.capture(), second)).as("the box once the renderer is off").isNotEqualTo(SceneSamples.RED);
        } finally {
            entries.setStatus(id, DebugScreenEntryStatus.NEVER);
            SceneSamples.debugCenter = null;
        }
    }
    
    @ClientTest
    public static void debugRendererAfterTheEventThrows(Client client) {
        Render render = Nexus.getMod().getRender();
        Assertions.assertThatIllegalStateException()
                .as("a debug renderer registered after the debug entries were registered")
                .isThrownBy(() -> render.debugRenderer(SceneSamples.LATE_DEBUG, () -> SceneSamples::drawDebug));
    }
    
    private static Scene scene() {
        return Nexus.getMod().getRender().getScene();
    }
    
    private static Vec3 enter(Client client, float pitch) {
        client.joinWorld();
        client.hideHud(true);
        client.pose(client.serverLevel().getRespawnData().pos().getBottomCenter().add(0.0D, SceneSamples.HEIGHT, 0.0D), 0.0F, pitch);
        client.awaitStill(SceneSamples.STILL_FRAMES);
        return client.minecraft().gameRenderer.getMainCamera().position();
    }
    
    private static void wall(Client client, Vec3 eye) {
        client.serverLevel().setBlockAndUpdate(BlockPos.containing(eye.add(0.0D, 0.0D, 3.0D)), Blocks.STONE.defaultBlockState());
        client.settle();
        client.awaitStill(SceneSamples.STILL_FRAMES);
    }
    
    private static AABB slab(Vec3 eye) {
        return new AABB(eye.x - 3.0D, eye.y - 2.0D, eye.z + 7.0D, eye.x + 3.0D, eye.y + 2.0D, eye.z + 7.5D);
    }
    
    private static boolean isAt(Entity entity, Vec3 position) {
        return entity.position().closerThan(position, SceneSamples.ENTITY_TOLERANCE) && entity.getPosition(0.0F).closerThan(position, SceneSamples.ENTITY_TOLERANCE);
    }
    
    private static void assertColor(Client client, Capture capture, Vec3 point, int argb) {
        Pixel pixel = SceneSamples.pixel(client, point);
        capture.assertPixel(pixel.x(), pixel.y(), argb);
    }
    
    private static int color(Client client, Capture capture, Vec3 point) {
        Pixel pixel = SceneSamples.pixel(client, point);
        return capture.pixel(pixel.x(), pixel.y());
    }
    
    private static Pixel pixel(Client client, Vec3 point) {
        Vector2f screen = Objects.requireNonNull(Nexus.getMod().getRender().project(point), "the point lies behind the camera");
        int scale = client.minecraft().getWindow().getGuiScale();
        return new Pixel((int) (screen.x * scale), (int) (screen.y * scale));
    }
    
    private static void drawDebug(Scene scene) {
        SceneSamples.debugCalls++;
        Vec3 center = SceneSamples.debugCenter;
        if (center != null) {
            scene.draw(Shape.box(AABB.ofSize(center, 1.0D, 1.0D, 1.0D)), Paint.fill(SceneSamples.RED));
        }
    }
    
    private record Pixel(int x, int y) { }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Nexus.getMod().getRender().debugRenderer(SceneSamples.DEBUG, () -> SceneSamples::drawDebug);
        }
    }
}
