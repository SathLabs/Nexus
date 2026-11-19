package dev.satherov.nexus.test.game.api.render.shape;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.api.render.shape.Drawing;
import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.api.render.shape.Quad;
import dev.satherov.nexus.api.render.shape.Scene;
import dev.satherov.nexus.api.render.shape.Shape;
import dev.satherov.nexus.api.render.shape.Sprite;
import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.platform.NativeImage;

import org.assertj.core.api.Assertions;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

///
/// Checks how a drawing of the scene or a debug renderer shows up and how long it stays.
///
public class SceneSamples {
    
    private static final int RED = 0xFFFF0000;
    
    private static final int BLUE = 0xFF0000FF;
    
    private static final int TRANSLUCENT_BLUE = 0x800000FF;
    
    private static final int GREEN = 0xFF00FF00;
    
    private static final int WHITE = 0xFFFFFFFF;
    
    private static final int BLACK = 0xFF000000;
    
    private static final int MISSING_PINK = 0xFFF800F8;
    
    private static final String KEY = "box";
    
    private static final String DEBUG = "scene_samples";
    
    private static final String LATE_DEBUG = "scene_samples_late";
    
    private static final String TEXTURE = "scene_samples_texture";
    
    private static final String ATLAS = "textures/atlas/scene_samples.png";
    
    private static final String LABEL = "label";
    
    private static final int SWATCH = 16;
    
    private static final int EXPIRY = 5;
    
    private static final int DEBUG_TICKS = 3;
    
    private static final int STILL_FRAMES = 10;
    
    private static final float WATER_PITCH = 20.0F;
    
    private static final double ENTITY_TOLERANCE = 1.0E-3D;
    
    private static final double HEIGHT = 4.0D;
    
    private static final float RING_WIDTH = 6.0F;
    
    private static final Vec3 CIRCLE = new Vec3(-1.6D, 0.0D, 7.0D);
    
    private static final Vec3 SPHERE = new Vec3(1.6D, 0.0D, 7.0D);
    
    private static final double NEAR_DISTANCE = 3.0D;
    
    private static final double NEAR_RADIUS = 2.0D;
    
    private static final double FAR = 16_777_216.0D;
    
    private static final double LABEL_DISTANCE = 2.0D;
    
    private static final double PAST_FOG = 16.0D;
    
    private static final int CHANNEL_TOLERANCE = 8;
    
    private static final double FOLLOW_DISTANCE = 3.0D;
    
    private static final double FOLLOW_SIZE = 0.4D;
    
    private static final double FROZEN_PUSH = 0.5D;
    
    private static final double FROZEN_TRAVEL = 1.0D;
    
    private static @Nullable Vec3 debugCenter;
    
    private static int debugCalls;
    
    @ClientTest
    public static void visibleBoxIsCutByWall(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        SceneSamples.wall(client, eye);
        SceneSamples.scene().draw(Shape.box(SceneSamples.slab(eye)), Paint.fill(SceneSamples.RED));
        client.tick();
        
        Capture capture = client.capture();
        Assertions.assertThat(SceneSamples.color(client, capture, eye.add(0.0D, 0.0D, 7.0D))).as("the box behind the wall").isNotEqualTo(SceneSamples.shaded(client, SceneSamples.RED));
        SceneSamples.assertColor(client, capture, eye.add(2.0D, 0.0D, 7.0D), SceneSamples.shaded(client, SceneSamples.RED));
        capture.assertGolden("visible_box_is_cut_by_wall");
    }
    
    @ClientTest
    public static void hiddenBoxShowsOnlyBehindWall(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        SceneSamples.wall(client, eye);
        SceneSamples.scene().draw(Shape.box(SceneSamples.slab(eye)), Paint.fill(SceneSamples.RED).withDepth(Paint.Depth.HIDDEN));
        client.tick();
        
        Capture capture = client.capture();
        SceneSamples.assertColor(client, capture, eye.add(0.0D, 0.0D, 7.0D), SceneSamples.shaded(client, SceneSamples.RED));
        Assertions.assertThat(SceneSamples.color(client, capture, eye.add(2.0D, 0.0D, 7.0D))).as("the box beside the wall").isNotEqualTo(SceneSamples.shaded(client, SceneSamples.RED));
        capture.assertGolden("hidden_box_shows_only_behind_wall");
    }
    
    @ClientTest
    public static void hiddenCopyOfOpaqueBoxShowsOnlyBehindWall(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        SceneSamples.wall(client, eye);
        Vec3 behind = eye.add(0.0D, 0.0D, 7.0D);
        int stone = SceneSamples.color(client, client.capture(), behind);
        LocalPlayer player = Objects.requireNonNull(client.minecraft().player);
        Scene scene = SceneSamples.scene();
        scene.draw(Shape.box(SceneSamples.slab(eye)), Paint.fill(SceneSamples.RED));
        scene.draw(Shape.box(SceneSamples.slab(eye).move(player.position().reverse())), Paint.fill(SceneSamples.TRANSLUCENT_BLUE).withDepth(Paint.Depth.HIDDEN)).follow(player);
        client.tick();
    
        Capture capture = client.capture();
        SceneSamples.assertColor(client, capture, eye.add(2.0D, 0.0D, 7.0D), SceneSamples.shaded(client, SceneSamples.RED));
        // The view behind the wall passes through the near and the far face of the copy, so the stone is tinted twice.
        int blue = SceneSamples.shaded(client, SceneSamples.TRANSLUCENT_BLUE);
        SceneSamples.assertNear(client, capture, behind, ARGB.alphaBlend(ARGB.alphaBlend(stone, blue), blue));
    }
    
    @ClientTest
    public static void opaqueFillHidesTheFillDrawnBehindIt(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Vec3 front = eye.add(0.0D, 0.0D, 5.0D);
        Scene scene = SceneSamples.scene();
        scene.draw(Shape.box(AABB.ofSize(front, 1.0D, 1.0D, 1.0D)), Paint.fill(SceneSamples.RED));
        scene.draw(Shape.box(AABB.ofSize(eye.add(0.0D, 0.0D, 8.0D), 4.0D, 4.0D, 1.0D)), Paint.fill(SceneSamples.BLUE));
        client.tick();
        
        SceneSamples.assertColor(client, client.capture(), front, SceneSamples.shaded(client, SceneSamples.RED));
    }
    
    @ClientTest
    public static void fillStandsInWater(Client client) {
        SceneSamples.assertFillStandsInWater(client);
    }
    
    @ClientTest
    public static void fillStandsInWaterUnderImprovedTransparency(Client client) {
        OptionInstance<Boolean> transparency = client.minecraft().options.improvedTransparency();
        boolean before = transparency.get();
        transparency.set(true);
        try {
            SceneSamples.assertFillStandsInWater(client);
        } finally {
            transparency.set(before);
        }
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
        Assertions.assertThat(SceneSamples.color(client, capture, first)).as("the replaced box").isNotEqualTo(SceneSamples.shaded(client, SceneSamples.RED));
        SceneSamples.assertColor(client, capture, second, SceneSamples.shaded(client, SceneSamples.BLUE));
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
        SceneSamples.assertColor(client, client.capture(), center, SceneSamples.shaded(client, SceneSamples.RED));
        
        client.tick();
        Assertions.assertThat(drawing.isActive()).as("the drawing once it expired").isFalse();
        Assertions.assertThat(SceneSamples.color(client, client.capture(), center)).as("the expired box").isNotEqualTo(SceneSamples.shaded(client, SceneSamples.RED));
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
        SceneSamples.assertColor(client, client.capture(), start.add(0.0D, 0.5D, 0.0D), SceneSamples.shaded(client, SceneSamples.RED));
        
        stand.teleportTo(moved.x, moved.y, moved.z);
        client.until("the armor stand to arrive", () -> SceneSamples.isAt(entity, moved));
        client.tick();
        
        Capture capture = client.capture();
        Assertions.assertThat(drawing.isActive()).as("the drawing of the moved armor stand").isTrue();
        Assertions.assertThat(SceneSamples.color(client, capture, start.add(0.0D, 0.5D, 0.0D))).as("the start of the armor stand").isNotEqualTo(SceneSamples.shaded(client, SceneSamples.RED));
        SceneSamples.assertColor(client, capture, moved.add(0.0D, 0.5D, 0.0D), SceneSamples.shaded(client, SceneSamples.RED));
    }
    
    @ClientTest
    public static void followedPlayerKeepsDrawingInPlaceWhileTicksAreFrozen(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        SceneSamples.freezeTicks(client, true);
        try {
            ClientLevel clientLevel = Objects.requireNonNull(client.minecraft().level);
            client.until("the ticks of the client to freeze", () -> !clientLevel.tickRateManager().runsNormally());
            LocalPlayer player = Objects.requireNonNull(client.minecraft().player);
            Vec3 offset = new Vec3(0.0D, player.getEyeHeight(), SceneSamples.FOLLOW_DISTANCE);
            SceneSamples.scene().draw(Shape.box(AABB.ofSize(offset, SceneSamples.FOLLOW_SIZE, SceneSamples.FOLLOW_SIZE, SceneSamples.FOLLOW_SIZE)), Paint.fill(SceneSamples.RED)).follow(player);
            client.until("the player to move in the last frozen tick", () -> {
                boolean moved = player.getX() - eye.x > SceneSamples.FROZEN_TRAVEL && player.getX() - player.xo > SceneSamples.FOLLOW_SIZE;
                player.setDeltaMovement(new Vec3(SceneSamples.FROZEN_PUSH, 0.0D, 0.0D));
                return moved;
            });
            
            Vec3 camera = client.minecraft().gameRenderer.getMainCamera().position();
            SceneSamples.assertColor(client, client.capture(), camera.add(0.0D, 0.0D, SceneSamples.FOLLOW_DISTANCE), SceneSamples.shaded(client, SceneSamples.RED));
        } finally {
            SceneSamples.freezeTicks(client, false);
        }
    }
    
    @ClientTest
    public static void repaintChangesColor(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Vec3 center = eye.add(0.0D, 0.0D, 6.0D);
        Drawing drawing = SceneSamples.scene().draw(Shape.box(AABB.ofSize(center, 1.0D, 1.0D, 1.0D)), Paint.fill(SceneSamples.RED));
        client.tick();
        SceneSamples.assertColor(client, client.capture(), center, SceneSamples.shaded(client, SceneSamples.RED));
        
        drawing.repaint(Paint.fill(SceneSamples.BLUE));
        client.tick();
        
        Capture capture = client.capture();
        Assertions.assertThat(drawing.getPaint()).as("the paint after the repaint").isEqualTo(Paint.fill(SceneSamples.BLUE));
        SceneSamples.assertColor(client, capture, center, SceneSamples.shaded(client, SceneSamples.BLUE));
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
        Assertions.assertThat(SceneSamples.color(client, capture, rejoined.add(0.0D, 0.0D, 6.0D))).as("the keyed box after the rejoin").isNotEqualTo(SceneSamples.shaded(client, SceneSamples.RED));
        Assertions.assertThat(SceneSamples.color(client, capture, rejoined.add(2.0D, 0.0D, 6.0D))).as("the box under no key after the rejoin").isNotEqualTo(SceneSamples.shaded(client, SceneSamples.BLUE));
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
            SceneSamples.assertColor(client, client.capture(), first, SceneSamples.shaded(client, SceneSamples.RED));
            
            SceneSamples.debugCenter = second;
            SceneSamples.debugCalls = 0;
            client.ticks(SceneSamples.DEBUG_TICKS);
            
            Capture moved = client.capture();
            Assertions.assertThat(SceneSamples.debugCalls).as("the calls of the renderer while it is on").isEqualTo(SceneSamples.DEBUG_TICKS);
            Assertions.assertThat(SceneSamples.color(client, moved, first)).as("the box of an earlier tick").isNotEqualTo(SceneSamples.shaded(client, SceneSamples.RED));
            SceneSamples.assertColor(client, moved, second, SceneSamples.shaded(client, SceneSamples.RED));
            moved.assertGolden("debug_renderer_refills_while_on");
            
            entries.setStatus(id, DebugScreenEntryStatus.NEVER);
            SceneSamples.debugCalls = 0;
            client.tick();
            Assertions.assertThat(SceneSamples.debugCalls).as("the calls of the renderer once it is off").isZero();
            Assertions.assertThat(SceneSamples.color(client, client.capture(), second)).as("the box once the renderer is off").isNotEqualTo(SceneSamples.shaded(client, SceneSamples.RED));
        } finally {
            entries.setStatus(id, DebugScreenEntryStatus.NEVER);
            SceneSamples.debugCenter = null;
        }
    }
    
    @ClientTest
    public static void visibleCircleAndSphereAreCutByWall(Client client) {
        Vec3 eye = SceneSamples.drawCircleAndSphere(client, Paint.Depth.VISIBLE);
        Capture capture = client.capture();
        SceneSamples.assertColor(client, capture, eye.add(SceneSamples.CIRCLE).add(-0.6D, 0.0D, 0.0D), SceneSamples.RED);
        SceneSamples.assertColor(client, capture, eye.add(SceneSamples.CIRCLE).add(-1.0D, 0.0D, 0.0D), SceneSamples.BLUE);
        Assertions.assertThat(SceneSamples.color(client, capture, eye.add(SceneSamples.CIRCLE).add(0.6D, 0.0D, 0.0D))).as("the disc behind the wall").isNotEqualTo(SceneSamples.RED);
        Assertions.assertThat(SceneSamples.isShadedRed(SceneSamples.color(client, capture, eye.add(SceneSamples.SPHERE).add(0.5D, 0.4D, 0.0D)))).as("the sphere beside the wall").isTrue();
        Assertions.assertThat(SceneSamples.isShadedRed(SceneSamples.color(client, capture, eye.add(SceneSamples.SPHERE).add(-0.5D, 0.4D, 0.0D)))).as("the sphere behind the wall").isFalse();
        capture.assertGolden("visible_circle_and_sphere_are_cut_by_wall");
    }
    
    @ClientTest
    public static void hiddenCircleAndSphereShowOnlyBehindWall(Client client) {
        Vec3 eye = SceneSamples.drawCircleAndSphere(client, Paint.Depth.HIDDEN);
        Capture capture = client.capture();
        SceneSamples.assertColor(client, capture, eye.add(SceneSamples.CIRCLE).add(0.6D, 0.0D, 0.0D), SceneSamples.RED);
        SceneSamples.assertColor(client, capture, eye.add(SceneSamples.CIRCLE).add(1.0D, 0.0D, 0.0D), SceneSamples.BLUE);
        Assertions.assertThat(SceneSamples.color(client, capture, eye.add(SceneSamples.CIRCLE).add(-0.6D, 0.0D, 0.0D))).as("the disc beside the wall").isNotEqualTo(SceneSamples.RED);
        Assertions.assertThat(SceneSamples.isShadedRed(SceneSamples.color(client, capture, eye.add(SceneSamples.SPHERE).add(-0.5D, 0.4D, 0.0D)))).as("the sphere behind the wall").isTrue();
        Assertions.assertThat(SceneSamples.isShadedRed(SceneSamples.color(client, capture, eye.add(SceneSamples.SPHERE).add(0.5D, 0.4D, 0.0D)))).as("the sphere beside the wall").isFalse();
        capture.assertGolden("hidden_circle_and_sphere_show_only_behind_wall");
    }
    
    @ClientTest
    public static void alwaysCircleAndSphereShowEverywhere(Client client) {
        Vec3 eye = SceneSamples.drawCircleAndSphere(client, Paint.Depth.ALWAYS);
        Capture capture = client.capture();
        SceneSamples.assertColor(client, capture, eye.add(SceneSamples.CIRCLE).add(-0.6D, 0.0D, 0.0D), SceneSamples.RED);
        SceneSamples.assertColor(client, capture, eye.add(SceneSamples.CIRCLE).add(0.6D, 0.0D, 0.0D), SceneSamples.RED);
        SceneSamples.assertColor(client, capture, eye.add(SceneSamples.CIRCLE).add(-1.0D, 0.0D, 0.0D), SceneSamples.BLUE);
        SceneSamples.assertColor(client, capture, eye.add(SceneSamples.CIRCLE).add(1.0D, 0.0D, 0.0D), SceneSamples.BLUE);
        Assertions.assertThat(SceneSamples.isShadedRed(SceneSamples.color(client, capture, eye.add(SceneSamples.SPHERE).add(0.5D, 0.4D, 0.0D)))).as("the sphere beside the wall").isTrue();
        Assertions.assertThat(SceneSamples.isShadedRed(SceneSamples.color(client, capture, eye.add(SceneSamples.SPHERE).add(-0.5D, 0.4D, 0.0D)))).as("the sphere behind the wall").isTrue();
        capture.assertGolden("always_circle_and_sphere_show_everywhere");
    }
    
    @ClientTest
    public static void nearSphereFillsItsSilhouette(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        SceneSamples.scene().draw(Shape.sphere(eye.add(0.0D, 0.0D, SceneSamples.NEAR_DISTANCE), SceneSamples.NEAR_RADIUS), Paint.fill(SceneSamples.RED));
        client.tick();
        
        Capture capture = client.capture();
        double silhouette = Math.asin(SceneSamples.NEAR_RADIUS / SceneSamples.NEAR_DISTANCE);
        double centerPlane = Math.atan(SceneSamples.NEAR_RADIUS / SceneSamples.NEAR_DISTANCE);
        double inside = (silhouette + centerPlane) / 2.0D;
        double outside = silhouette + Math.toRadians(4.0D);
        for (double side : new double[]{ -1.0D, 1.0D }) {
            Vec3 grown = eye.add(side * Math.sin(inside), 0.0D, Math.cos(inside));
            Vec3 past = eye.add(side * Math.sin(outside), 0.0D, Math.cos(outside));
            Assertions.assertThat(SceneSamples.isShadedRed(SceneSamples.color(client, capture, grown))).as("the sphere past the extent of its center plane").isTrue();
            Assertions.assertThat(SceneSamples.isShadedRed(SceneSamples.color(client, capture, past))).as("the sky past the silhouette").isFalse();
        }
        
        capture.assertGolden("near_sphere_fills_its_silhouette");
    }
    
    @ClientTest
    public static void edgeOnRingKeepsItsBand(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Vec3 center = eye.add(0.0D, 0.0D, 5.0D);
        SceneSamples.scene().draw(Shape.circle(center, Vec3.Y_AXIS, 1.5D), Paint.stroke(SceneSamples.BLUE).withLineWidth(SceneSamples.RING_WIDTH));
        client.tick();
        
        Capture capture = client.capture();
        Pixel front = SceneSamples.pixel(client, center.add(0.0D, 0.0D, -1.5D));
        capture.assertPixel(front.x(), front.y(), SceneSamples.BLUE);
        capture.assertPixel(front.x(), front.y() - 2, SceneSamples.BLUE);
        capture.assertPixel(front.x(), front.y() + 2, SceneSamples.BLUE);
        SceneSamples.assertColor(client, capture, center.add(1.5D, 0.0D, 0.0D), SceneSamples.BLUE);
        SceneSamples.assertColor(client, capture, center.add(-1.5D, 0.0D, 0.0D), SceneSamples.BLUE);
        Assertions.assertThat(SceneSamples.color(client, capture, center.add(2.0D, 0.0D, 0.0D))).as("the line past the end of the ring").isNotEqualTo(SceneSamples.BLUE);
        Assertions.assertThat(capture.pixel(front.x(), front.y() - 8)).as("the sky above the band").isNotEqualTo(SceneSamples.BLUE);
        capture.assertGolden("edge_on_ring_keeps_its_band");
    }
    
    @ClientTest
    public static void visibleStrokeIsCutByWall(Client client) {
        Vec3 eye = SceneSamples.drawStrokedBox(client, Paint.Depth.VISIBLE);
        Capture capture = client.capture();
        Assertions.assertThat(SceneSamples.isNear(SceneSamples.color(client, capture, eye.add(0.0D, 0.0D, 7.0D)), SceneSamples.shaded(client, SceneSamples.RED))).as("the edge behind the wall").isFalse();
        SceneSamples.assertNear(client, capture, eye.add(2.0D, 0.0D, 7.0D), SceneSamples.shaded(client, SceneSamples.RED));
        capture.assertGolden("visible_stroke_is_cut_by_wall");
    }
    
    @ClientTest
    public static void hiddenStrokeShowsOnlyBehindWall(Client client) {
        Vec3 eye = SceneSamples.drawStrokedBox(client, Paint.Depth.HIDDEN);
        Capture capture = client.capture();
        SceneSamples.assertNear(client, capture, eye.add(0.0D, 0.0D, 7.0D), SceneSamples.shaded(client, SceneSamples.RED));
        Assertions.assertThat(SceneSamples.isNear(SceneSamples.color(client, capture, eye.add(2.0D, 0.0D, 7.0D)), SceneSamples.shaded(client, SceneSamples.RED))).as("the edge beside the wall").isFalse();
        capture.assertGolden("hidden_stroke_shows_only_behind_wall");
    }
    
    @ClientTest
    public static void alwaysStrokeShowsEverywhere(Client client) {
        Vec3 eye = SceneSamples.drawStrokedBox(client, Paint.Depth.ALWAYS);
        Capture capture = client.capture();
        SceneSamples.assertNear(client, capture, eye.add(0.0D, 0.0D, 7.0D), SceneSamples.shaded(client, SceneSamples.RED));
        SceneSamples.assertNear(client, capture, eye.add(2.0D, 0.0D, 7.0D), SceneSamples.shaded(client, SceneSamples.RED));
        capture.assertGolden("always_stroke_shows_everywhere");
    }
    
    @ClientTest
    public static void strokeKeepsItsColorAfterTheCameraMovesPastTheFog(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        double distance = client.minecraft().options.getEffectiveRenderDistance() * SectionPos.SECTION_SIZE + SceneSamples.PAST_FOG;
        Vec3 near = eye.add(0.0D, 0.0D, distance);
        AABB box = new AABB(near.x - 3.0D, near.y, near.z + 7.0D, near.x + 3.0D, near.y + 1.0D, near.z + 7.5D);
        SceneSamples.scene().draw(Shape.box(box), Paint.stroke(SceneSamples.RED).withLineWidth(SceneSamples.RING_WIDTH));
        client.tick();
        
        client.pose(client.serverLevel().getRespawnData().pos().getBottomCenter().add(0.0D, SceneSamples.HEIGHT, distance), 0.0F, 0.0F);
        client.tick();
        SceneSamples.assertNear(client, client.capture(), near.add(2.0D, 0.0D, 7.0D), SceneSamples.shaded(client, SceneSamples.RED));
    }
    
    @ClientTest
    public static void boxStaysInPlaceAfterTravellingFar(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Vec3 far = eye.add(SceneSamples.FAR, 0.0D, 0.0D);
        AABB box = new AABB(far.x + 0.25D, far.y - 0.25D, far.z + 2.0D, far.x + 0.75D, far.y + 0.25D, far.z + 2.5D);
        SceneSamples.scene().draw(Shape.box(box), Paint.fill(SceneSamples.RED));
        client.tick();
        
        client.pose(client.serverLevel().getRespawnData().pos().getBottomCenter().add(SceneSamples.FAR, SceneSamples.HEIGHT, 0.0D), 0.0F, 0.0F);
        client.tick();
        SceneSamples.assertColor(client, client.capture(), far.add(0.5D, 0.0D, 2.25D), SceneSamples.shaded(client, SceneSamples.RED));
    }
    
    @ClientTest
    public static void releasedSpriteTextureShowsMissingTexture(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Identifier id = Nexus.getMod().id(SceneSamples.TEXTURE);
        TextureManager textures = client.minecraft().getTextureManager();
        textures.register(id, new DynamicTexture(() -> SceneSamples.TEXTURE, SceneSamples.image(SceneSamples.GREEN)));
        try {
            Quad quad = SceneSamples.facing(eye);
            SceneSamples.scene().draw(Shape.face(quad, new Sprite(id, id)), Paint.fill(SceneSamples.WHITE));
            client.tick();
            SceneSamples.assertColor(client, client.capture(), SceneSamples.on(quad, 0.5D, 0.5D), SceneSamples.GREEN);
            
            textures.release(id);
            client.tick();
            
            Capture capture = client.capture();
            List<Integer> quarters = List.of(
                    SceneSamples.color(client, capture, SceneSamples.on(quad, 0.25D, 0.25D)),
                    SceneSamples.color(client, capture, SceneSamples.on(quad, 0.75D, 0.25D))
            );
            Assertions.assertThat(quarters).as("the missing texture on the face").containsExactlyInAnyOrder(SceneSamples.MISSING_PINK, SceneSamples.BLACK);
        } finally {
            textures.release(id);
        }
    }
    
    @ClientTest
    public static void spriteFaceFollowsTheStitchOfItsAtlas(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Identifier id = Nexus.getMod().id(SceneSamples.ATLAS);
        Swatch red = new Swatch(Nexus.getMod().id("red"), SceneSamples.RED);
        Swatch green = new Swatch(Nexus.getMod().id("green"), SceneSamples.GREEN);
        TextureAtlas atlas = new TextureAtlas(id);
        SceneSamples.stitch(atlas, List.of(red, green));
        TextureManager textures = client.minecraft().getTextureManager();
        textures.register(id, atlas);
        try {
            Quad quad = SceneSamples.facing(eye);
            Vec3 center = SceneSamples.on(quad, 0.5D, 0.5D);
            SceneSamples.scene().draw(Shape.face(quad, new Sprite(id, green.id())), Paint.fill(SceneSamples.WHITE));
            client.tick();
            SceneSamples.assertColor(client, client.capture(), center, SceneSamples.GREEN);
            
            SceneSamples.stitch(atlas, List.of(green, red));
            client.tick();
            SceneSamples.assertColor(client, client.capture(), center, SceneSamples.GREEN);
        } finally {
            textures.release(id);
        }
    }
    
    @ClientTest
    public static void labelShowsItsTextOnItsBackdrop(Client client) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        Vec3 at = eye.add(0.0D, 0.0D, SceneSamples.LABEL_DISTANCE);
        Component text = Component.literal(SceneSamples.LABEL);
        SceneSamples.scene().draw(Shape.label(at, text), new Paint(SceneSamples.WHITE, Paint.DEFAULT_LINE_WIDTH, SceneSamples.BLACK, Paint.Depth.VISIBLE));
        client.tick();
        
        Capture capture = client.capture();
        double half = (client.minecraft().font.width(text) / 2.0D - 1.0D) * EntityRenderer.NAMETAG_SCALE;
        Pixel left = SceneSamples.pixel(client, at.add(-half, 0.0D, 0.0D));
        Pixel right = SceneSamples.pixel(client, at.add(half, 0.0D, 0.0D));
        List<Integer> row = IntStream.rangeClosed(Math.min(left.x(), right.x()), Math.max(left.x(), right.x()))
                .mapToObj(x -> capture.pixel(x, left.y()))
                .toList();
        Assertions.assertThat(row).as("the text of the label").anyMatch(SceneSamples::isLight);
        Assertions.assertThat(row).as("the backdrop between the letters").anyMatch(SceneSamples::isDark);
        capture.assertGolden("label_shows_its_text_on_its_backdrop");
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
    
    private static void assertFillStandsInWater(Client client) {
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
        SceneSamples.assertColor(client, capture, eye.add(0.0D, -1.12D, 4.0D), SceneSamples.shaded(client, SceneSamples.RED));
        Assertions.assertThat(SceneSamples.color(client, capture, eye.add(0.0D, -2.2D, 4.0D))).as("the box below the water surface").isNotEqualTo(SceneSamples.shaded(client, SceneSamples.RED));
    }
    
    private static void freezeTicks(Client client, boolean frozen) {
        ServerTickRateManager ticks = client.serverLevel().getServer().tickRateManager();
        CompletableFuture<Void> done = client.serverLevel().getServer().submit(() -> ticks.setFrozen(frozen));
        client.until("the server to set the tick rate", done::isDone);
        done.join();
    }
    
    private static void wall(Client client, Vec3 eye) {
        client.serverLevel().setBlockAndUpdate(BlockPos.containing(eye.add(0.0D, 0.0D, 3.0D)), Blocks.STONE.defaultBlockState());
        client.settle();
        client.awaitStill(SceneSamples.STILL_FRAMES);
    }
    
    private static Vec3 drawCircleAndSphere(Client client, Paint.Depth depth) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        SceneSamples.wall(client, eye);
        Paint paint = new Paint(SceneSamples.BLUE, SceneSamples.RING_WIDTH, SceneSamples.RED, depth);
        Scene scene = SceneSamples.scene();
        scene.draw(Shape.circle(eye.add(SceneSamples.CIRCLE), new Vec3(0.0D, 0.0D, 1.0D), 1.0D), paint);
        scene.draw(Shape.sphere(eye.add(SceneSamples.SPHERE), 1.0D), paint);
        client.tick();
        return eye;
    }
    
    private static Vec3 drawStrokedBox(Client client, Paint.Depth depth) {
        Vec3 eye = SceneSamples.enter(client, 0.0F);
        SceneSamples.wall(client, eye);
        AABB box = new AABB(eye.x - 3.0D, eye.y, eye.z + 7.0D, eye.x + 3.0D, eye.y + 1.0D, eye.z + 7.5D);
        SceneSamples.scene().draw(Shape.box(box), Paint.stroke(SceneSamples.RED).withLineWidth(SceneSamples.RING_WIDTH).withDepth(depth));
        client.tick();
        return eye;
    }
    
    private static Quad facing(Vec3 eye) {
        return new Quad(eye.add(-1.0D, -1.0D, 3.0D), new Vec3(2.0D, 0.0D, 0.0D), new Vec3(0.0D, 2.0D, 0.0D));
    }
    
    private static Vec3 on(Quad quad, double u, double v) {
        return quad.origin().add(quad.edgeU().scale(u)).add(quad.edgeV().scale(v));
    }
    
    private static NativeImage image(int argb) {
        NativeImage image = new NativeImage(SceneSamples.SWATCH, SceneSamples.SWATCH, false);
        image.fillRect(0, 0, SceneSamples.SWATCH, SceneSamples.SWATCH, argb);
        return image;
    }
    
    private static void stitch(TextureAtlas atlas, List<Swatch> swatches) {
        int width = SceneSamples.SWATCH * (swatches.size() + 1);
        TextureAtlasSprite missing = SceneSamples.sprite(atlas, MissingTextureAtlasSprite.create(), width, 0);
        Map<Identifier, TextureAtlasSprite> regions = new HashMap<>();
        regions.put(MissingTextureAtlasSprite.getLocation(), missing);
        for (int i = 0; i < swatches.size(); i++) {
            Swatch swatch = swatches.get(i);
            SpriteContents contents = new SpriteContents(swatch.id(), new FrameSize(SceneSamples.SWATCH, SceneSamples.SWATCH), SceneSamples.image(swatch.argb()));
            regions.put(swatch.id(), SceneSamples.sprite(atlas, contents, width, (i + 1) * SceneSamples.SWATCH));
        }
        
        atlas.upload(new SpriteLoader.Preparations(width, SceneSamples.SWATCH, 0, missing, regions, CompletableFuture.allOf()));
    }
    
    private static TextureAtlasSprite sprite(TextureAtlas atlas, SpriteContents contents, int width, int x) {
        return new TextureAtlasSprite(atlas.location(), contents, width, SceneSamples.SWATCH, x, 0, 0) { };
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
    
    private static void assertNear(Client client, Capture capture, Vec3 point, int argb) {
        Pixel pixel = SceneSamples.pixel(client, point);
        int actual = capture.pixel(pixel.x(), pixel.y());
        for (int shift = 0; shift < Integer.SIZE; shift += Byte.SIZE) {
            Assertions.assertThat(actual >>> shift & 0xFF)
                    .as("the channel at bit '%d' of the color '%08X' at '%d, %d'", shift, actual, pixel.x(), pixel.y())
                    .isCloseTo(argb >>> shift & 0xFF, Assertions.within(SceneSamples.CHANNEL_TOLERANCE));
        }
    }
    
    private static boolean isNear(int actual, int argb) {
        for (int shift = 0; shift < Integer.SIZE; shift += Byte.SIZE) {
            if (Math.abs((actual >>> shift & 0xFF) - (argb >>> shift & 0xFF)) > SceneSamples.CHANNEL_TOLERANCE) {
                return false;
            }
        }
        
        return true;
    }
    
    private static int color(Client client, Capture capture, Vec3 point) {
        Pixel pixel = SceneSamples.pixel(client, point);
        return capture.pixel(pixel.x(), pixel.y());
    }
    
    private static int shaded(Client client, int argb) {
        return client.hasShaderPack() ? ARGB.color(ARGB.alpha(argb), ARGB.blue(argb), ARGB.green(argb), ARGB.red(argb)) : argb;
    }
    
    private static boolean isShadedRed(int argb) {
        return (argb & 0x0000FFFF) == 0 && (argb >> 16 & 0xFF) > 0x40;
    }
    
    private static boolean isLight(int argb) {
        return ARGB.red(argb) > 0xC0 && ARGB.green(argb) > 0xC0 && ARGB.blue(argb) > 0xC0;
    }
    
    private static boolean isDark(int argb) {
        return ARGB.red(argb) < 0x40 && ARGB.green(argb) < 0x40 && ARGB.blue(argb) < 0x40;
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
    
    private record Swatch(Identifier id, int argb) { }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Nexus.getMod().getRender().debugRenderer(SceneSamples.DEBUG, () -> SceneSamples::drawDebug);
        }
    }
}
