package dev.satherov.nexus.test.game.api.render.glow;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.glow.Glow;
import dev.satherov.nexus.api.render.glow.Glows;
import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

///
/// Checks where and for how long a glow draws around an entity or a block.
///
public class GlowsSamples {
    
    private static final int RED = 0xFFFF0000;
    
    private static final int GREEN = 0xFF00FF00;
    
    private static final int BLUE = 0xFF0000FF;
    
    private static final int RED_MINIMUM = 0x40;
    
    private static final int OTHER_MAXIMUM = 0x10;
    
    private static final String KEY = "glow";
    
    private static final String OTHER_KEY = "other";
    
    private static final String EXPIRING_KEY = "expiring";
    
    private static final int MASS = 200;
    
    private static final double MINIMUM_OFFSET = 0.15D;
    
    private static final int EXPIRY = 5;
    
    private static final int CHEST_OPENERS_EVENT = 1;
    
    private static final int STILL_FRAMES = 10;
    
    private static final int MARGIN = 8;
    
    private static final float FACING_CAMERA = 180.0F;
    
    private static final double HEIGHT = 4.0D;
    
    private static final double ENTITY_TOLERANCE = 1.0E-3D;
    
    @ClientTest
    public static void entityGlowsInColor(Client client) {
        Vec3 eye = GlowsSamples.enter(client);
        Vec3 feet = eye.add(0.0D, -1.5D, 4.0D);
        ServerLevel level = client.serverLevel();
        Pig pig = new Pig(EntityType.PIG, level);
        pig.snapTo(feet, GlowsSamples.FACING_CAMERA, 0.0F);
        pig.setYHeadRot(GlowsSamples.FACING_CAMERA);
        pig.setYBodyRot(GlowsSamples.FACING_CAMERA);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        level.addFreshEntity(pig);
        client.settle();
        
        ClientLevel clientLevel = Objects.requireNonNull(client.minecraft().level);
        Entity entity = Objects.requireNonNull(clientLevel.getEntity(pig.getId()), "the pig on the client");
        client.until("the pig to stand at its spawn", () -> GlowsSamples.isAt(entity, feet));
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        Assertions.assertThat(GlowsSamples.redPixels(client, client.capture(), entity.getBoundingBox())).as("the red pixels before the glow").isZero();
        
        Glow glow = GlowsSamples.glows().entity(GlowsSamples.KEY, entity, GlowsSamples.RED);
        client.tick();
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        
        Capture capture = client.capture();
        Assertions.assertThat(glow.isActive()).as("the glow of the pig").isTrue();
        Assertions.assertThat(GlowsSamples.redPixels(client, capture, entity.getBoundingBox())).as("the red pixels around the pig").isPositive();
        capture.assertGolden("entity_glows_in_color");
    }
    
    @ClientTest
    public static void blockGlowStopsWhenBroken(Client client) {
        Vec3 eye = GlowsSamples.enter(client);
        Capture empty = client.capture();
        BlockPos pos = GlowsSamples.place(client, eye, Blocks.STONE);
        AABB box = new AABB(pos);
        Capture unlit = client.capture();
        Assertions.assertThat(GlowsSamples.redPixels(client, unlit, box)).as("the red pixels before the glow").isZero();
        
        Glow glow = GlowsSamples.glows().block(GlowsSamples.KEY, pos, GlowsSamples.RED);
        client.tick();
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        
        Capture capture = client.capture();
        if (client.hasShaderPack()) {
            Assertions.assertThat(GlowsSamples.around(client, capture, box)).as("the pixels around the block").isNotEqualTo(GlowsSamples.around(client, unlit, box));
        } else {
            Assertions.assertThat(GlowsSamples.redPixels(client, capture, box)).as("the red pixels around the block").isPositive();
        }
        
        capture.assertGolden("block_glow_stops_when_broken");
        
        client.serverLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        client.settle();
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        
        Assertions.assertThat(glow.isActive()).as("the glow of the broken block").isTrue();
        if (client.hasShaderPack()) {
            Assertions.assertThat(GlowsSamples.around(client, client.capture(), box)).as("the pixels around the broken block").isEqualTo(GlowsSamples.around(client, empty, box));
        } else {
            Assertions.assertThat(GlowsSamples.redPixels(client, client.capture(), box)).as("the red pixels around the broken block").isZero();
        }
    }
    
    ///
    /// Iris has no program for vanilla's outline pipelines, so a block glow loses its color under a shader pack.
    ///
    @ClientTest(required = false)
    public static void blockGlowKeepsItsColorUnderThePack(Client client) {
        Vec3 eye = GlowsSamples.enter(client);
        BlockPos pos = GlowsSamples.place(client, eye, Blocks.STONE);
        GlowsSamples.glows().block(GlowsSamples.KEY, pos, GlowsSamples.RED);
        client.tick();
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        
        Assertions.assertThat(GlowsSamples.redPixels(client, client.capture(), new AABB(pos))).as("the red pixels around the block").isPositive();
    }
    
    @ClientTest
    public static void chestGlows(Client client) {
        Vec3 eye = GlowsSamples.enter(client);
        BlockPos pos = GlowsSamples.place(client, eye, Blocks.CHEST);
        AABB box = new AABB(pos);
        AABB lid = new AABB(pos.getX() + 0.2D, pos.getY() + 1.25D, pos.getZ() + 0.6D, pos.getX() + 0.8D, pos.getY() + 1.45D, pos.getZ() + 0.95D);
        Assertions.assertThat(GlowsSamples.redPixels(client, client.capture(), box)).as("the red pixels before the glow").isZero();
        
        GlowsSamples.glows().block(GlowsSamples.KEY, pos, GlowsSamples.RED);
        client.tick();
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        
        Capture closed = client.capture();
        Assertions.assertThat(GlowsSamples.redPixels(client, closed, box)).as("the red pixels around the closed chest").isPositive();
        Assertions.assertThat(GlowsSamples.redPixels(client, closed, lid)).as("the red pixels where the open lid stands").isZero();
        
        ChestBlockEntity chest = (ChestBlockEntity) Objects.requireNonNull(Objects.requireNonNull(client.minecraft().level).getBlockEntity(pos), "the chest on the client");
        client.serverLevel().blockEvent(pos, Blocks.CHEST, GlowsSamples.CHEST_OPENERS_EVENT, 1);
        client.settle();
        client.until("the chest lid to open", () -> chest.getOpenNess(0.0F) >= 1.0F);
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        
        Capture open = client.capture();
        Assertions.assertThat(GlowsSamples.redPixels(client, open, lid)).as("the red pixels around the open lid").isPositive();
        open.assertGolden("chest_glows");
    }
    
    @ClientTest
    public static void expiryRemovesAfterTicksAndNotBefore(Client client) {
        Vec3 eye = GlowsSamples.enter(client);
        BlockPos pos = GlowsSamples.place(client, eye, Blocks.STONE);
        AABB box = new AABB(pos);
        Capture unlit = client.capture();
        Glow glow = GlowsSamples.glows().block(GlowsSamples.KEY, pos, GlowsSamples.RED).expire(GlowsSamples.EXPIRY);
        List<Boolean> activity = new ArrayList<>();
        Consumer<ClientTickEvent.Post> recorder = _ -> {
            if (!client.minecraft().isPaused()) {
                activity.add(glow.isActive());
            }
        };
        
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ClientTickEvent.Post.class, recorder);
        try {
            client.until("the glow to age by one tick less than its expiry", () -> activity.size() >= GlowsSamples.EXPIRY - 1);
            // A realtime run renders frames apart from the ticks, so the last frame before the expiry can't be caught there.
            if (!RunOptions.fromProperties().realtime()) {
                if (client.hasShaderPack()) {
                    Assertions.assertThat(GlowsSamples.around(client, client.capture(), box)).as("the pixels a tick before the expiry").isNotEqualTo(GlowsSamples.around(client, unlit, box));
                } else {
                    Assertions.assertThat(GlowsSamples.redPixels(client, client.capture(), box)).as("the red pixels a tick before the expiry").isPositive();
                }
            }
            
            client.until("the glow to age by its expiry", () -> activity.size() >= GlowsSamples.EXPIRY);
        } finally {
            NeoForge.EVENT_BUS.unregister(recorder);
        }
        
        List<Boolean> expected = new ArrayList<>(Collections.nCopies(GlowsSamples.EXPIRY - 1, true));
        expected.add(false);
        Assertions.assertThat(activity.subList(0, GlowsSamples.EXPIRY)).as("the activity of the glow after each client tick").isEqualTo(expected);
        if (client.hasShaderPack()) {
            Assertions.assertThat(GlowsSamples.around(client, client.capture(), box)).as("the pixels after the expiry").isEqualTo(GlowsSamples.around(client, unlit, box));
        } else {
            Assertions.assertThat(GlowsSamples.redPixels(client, client.capture(), box)).as("the red pixels after the expiry").isZero();
        }
    }
    
    @ClientTest
    public static void blockGlowFollowsTheRandomOffset(Client client) {
        Vec3 eye = GlowsSamples.enter(client);
        ClientLevel level = Objects.requireNonNull(client.minecraft().level);
        BlockState dandelion = Blocks.DANDELION.defaultBlockState();
        BlockPos base = BlockPos.containing(eye.add(0.0D, -1.0D, 4.0D));
        BlockPos pos = BlockPos.betweenClosedStream(base.offset(-2, 0, 0), base.offset(2, 0, 2))
                .filter(candidate -> Math.abs(dandelion.getOffset(candidate).x) >= GlowsSamples.MINIMUM_OFFSET)
                .findFirst()
                .map(BlockPos::immutable)
                .orElseThrow(() -> new AssertionError("no position near the camera moves a dandelion far enough to the side"));
        
        client.serverLevel().setBlockAndUpdate(pos.below(), Blocks.GRASS_BLOCK.defaultBlockState());
        client.serverLevel().setBlockAndUpdate(pos, dandelion);
        client.settle();
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        Capture unlit = client.capture();
        
        GlowsSamples.glows().block(GlowsSamples.KEY, pos, GlowsSamples.RED);
        client.tick();
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        
        AABB flower = dandelion.getShape(level, pos).bounds().move(pos);
        AABB cell = flower.move(dandelion.getOffset(pos).reverse());
        double outline = GlowsSamples.changedCenterX(client, unlit, client.capture(), flower.minmax(cell));
        Assertions.assertThat(Math.abs(outline - GlowsSamples.screenX(client, flower.getCenter())))
                .as("the distance of the outline from the drawn flower, in pixels")
                .isLessThan(Math.abs(outline - GlowsSamples.screenX(client, cell.getCenter())));
    }
    
    @ClientTest
    public static void entityGlowEndsOnceTheEntityIsRemoved(Client client) {
        GlowsSamples.enter(client);
        Pig pig = GlowsSamples.pig(client);
        Glows glows = GlowsSamples.glows();
        Glow glow = glows.entity(GlowsSamples.KEY, pig, GlowsSamples.RED);
        Assertions.assertThat(glow.isActive()).as("the glow of the pig").isTrue();
        
        pig.discard();
        Assertions.assertThat(glow.isActive()).as("the glow right after the pig was removed").isFalse();
        client.until("the glows to drop the removed pig", () -> glows.colorOf(pig) == 0);
    }
    
    @ClientTest
    public static void earlierKeyTakesOverOnceTheLaterIsRemoved(Client client) {
        GlowsSamples.enter(client);
        Pig pig = GlowsSamples.pig(client);
        Glows glows = GlowsSamples.glows();
        glows.entity(GlowsSamples.KEY, pig, GlowsSamples.RED);
        glows.entity(GlowsSamples.OTHER_KEY, pig, GlowsSamples.BLUE);
        Assertions.assertThat(glows.colorOf(pig)).as("the color of the pig under two keys").isEqualTo(GlowsSamples.BLUE);
        
        glows.remove(GlowsSamples.OTHER_KEY);
        Assertions.assertThat(glows.colorOf(pig)).as("the color of the pig once the later key is removed").isEqualTo(GlowsSamples.RED);
    }
    
    @ClientTest
    public static void removeAndClearEndGlows(Client client) {
        GlowsSamples.enter(client);
        Pig pig = GlowsSamples.pig(client);
        Glows glows = GlowsSamples.glows();
        Glow removed = glows.entity(GlowsSamples.KEY, pig, GlowsSamples.RED);
        glows.remove(GlowsSamples.KEY);
        Assertions.assertThat(removed.isActive()).as("the glow removed by its key").isFalse();
        Assertions.assertThat(glows.colorOf(pig)).as("the color of the pig once its glow is removed").isZero();
        
        Glow first = glows.entity(GlowsSamples.KEY, pig, GlowsSamples.RED);
        Glow second = glows.block(GlowsSamples.OTHER_KEY, BlockPos.ZERO, GlowsSamples.BLUE);
        glows.clear();
        Assertions.assertThat(first.isActive()).as("the entity glow after the clear").isFalse();
        Assertions.assertThat(second.isActive()).as("the block glow after the clear").isFalse();
        Assertions.assertThat(glows.colorOf(pig)).as("the color of the pig after the clear").isZero();
    }
    
    @ClientTest
    public static void leavingTheWorldEndsGlows(Client client) {
        Vec3 eye = GlowsSamples.enter(client);
        BlockPos pos = GlowsSamples.place(client, eye, Blocks.STONE);
        Glow glow = GlowsSamples.glows().block(GlowsSamples.KEY, pos, GlowsSamples.RED);
        client.tick();
        Assertions.assertThat(glow.isActive()).as("the glow before leaving the world").isTrue();
        
        GlowsSamples.enter(client);
        Assertions.assertThat(glow.isActive()).as("the glow after leaving the world").isFalse();
    }
    
    @ClientTest
    public static void entityGlowSkipsAnotherEntityWithTheSameId(Client client) {
        GlowsSamples.enter(client);
        Pig glowing = GlowsSamples.pig(client);
        Pig other = GlowsSamples.pig(client);
        other.setId(glowing.getId());
        GlowsSamples.glows().entity(GlowsSamples.KEY, glowing, GlowsSamples.RED);
        
        Assertions.assertThat(GlowsSamples.glows().colorOf(glowing)).as("the color of the glowing pig").isEqualTo(GlowsSamples.RED);
        Assertions.assertThat(GlowsSamples.glows().colorOf(other)).as("the color of another pig with the same id").isZero();
    }
    
    @ClientTest
    public static void latestRemainingGlowWinsAfterAMassExpiry(Client client) {
        GlowsSamples.enter(client);
        Glows glows = GlowsSamples.glows();
        List<Pig> pigs = new ArrayList<>();
        List<Glow> expiring = new ArrayList<>();
        for (int i = 0; i < GlowsSamples.MASS; i++) {
            Pig pig = GlowsSamples.pig(client);
            pigs.add(pig);
            glows.entity(GlowsSamples.KEY + i, pig, GlowsSamples.BLUE);
            glows.entity(GlowsSamples.OTHER_KEY + i, pig, GlowsSamples.GREEN);
            expiring.add(glows.entity(GlowsSamples.EXPIRING_KEY + i, pig, GlowsSamples.RED).expire(1));
        }
        
        client.until("every expiring glow to expire", () -> expiring.stream().noneMatch(Glow::isActive));
        Assertions.assertThat(pigs.stream().map(glows::colorOf).toList())
                .as("the colors of the pigs once their latest glow expired")
                .containsOnly(GlowsSamples.GREEN);
    }
    
    private static Glows glows() {
        return Nexus.getMod().getRender().getGlows();
    }
    
    private static Vec3 enter(Client client) {
        client.joinWorld();
        client.hideHud(true);
        client.pose(client.serverLevel().getRespawnData().pos().getBottomCenter().add(0.0D, GlowsSamples.HEIGHT, 0.0D), 0.0F, 0.0F);
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        return client.minecraft().gameRenderer.getMainCamera().position();
    }
    
    private static BlockPos place(Client client, Vec3 eye, Block block) {
        BlockPos pos = BlockPos.containing(eye.add(0.0D, -1.0D, 4.0D));
        client.serverLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        client.settle();
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        return pos;
    }
    
    private static Pig pig(Client client) {
        return new Pig(EntityType.PIG, Objects.requireNonNull(client.minecraft().level));
    }
    
    private static boolean isAt(Entity entity, Vec3 position) {
        return entity.position().closerThan(position, GlowsSamples.ENTITY_TOLERANCE) && entity.getPosition(0.0F).closerThan(position, GlowsSamples.ENTITY_TOLERANCE);
    }
    
    private static int redPixels(Client client, Capture capture, AABB box) {
        int count = 0;
        for (int pixel : GlowsSamples.around(client, capture, box)) {
            if (ARGB.red(pixel) >= GlowsSamples.RED_MINIMUM && ARGB.green(pixel) <= GlowsSamples.OTHER_MAXIMUM && ARGB.blue(pixel) <= GlowsSamples.OTHER_MAXIMUM) {
                count++;
            }
        }
        
        return count;
    }
    
    private static int[] around(Client client, Capture capture, AABB box) {
        Region region = GlowsSamples.region(client, capture, box);
        Capture cut = capture.region(region.x(), region.y(), region.width(), region.height());
        int[] pixels = new int[region.width() * region.height()];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = cut.pixel(i % region.width(), i / region.width());
        }
        
        return pixels;
    }
    
    private static double changedCenterX(Client client, Capture before, Capture after, AABB box) {
        Region region = GlowsSamples.region(client, after, box);
        long sum = 0L;
        int count = 0;
        for (int y = region.y(); y < region.y() + region.height(); y++) {
            for (int x = region.x(); x < region.x() + region.width(); x++) {
                if (before.pixel(x, y) != after.pixel(x, y)) {
                    sum += x;
                    count++;
                }
            }
        }
        
        Assertions.assertThat(count).as("the pixels the glow changed").isPositive();
        return (double) sum / count;
    }
    
    private static Region region(Client client, Capture capture, AABB box) {
        int scale = client.minecraft().getWindow().getGuiScale();
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 point = new Vec3(
                    (corner & 1) == 0 ? box.minX : box.maxX,
                    (corner & 2) == 0 ? box.minY : box.maxY,
                    (corner & 4) == 0 ? box.minZ : box.maxZ
            );
            Vector2f screen = GlowsSamples.project(point);
            left = Math.min(left, screen.x * scale);
            top = Math.min(top, screen.y * scale);
            right = Math.max(right, screen.x * scale);
            bottom = Math.max(bottom, screen.y * scale);
        }
        
        int x = Math.max(0, (int) left - GlowsSamples.MARGIN);
        int y = Math.max(0, (int) top - GlowsSamples.MARGIN);
        return new Region(x, y, Math.min(capture.width(), (int) right + GlowsSamples.MARGIN) - x, Math.min(capture.height(), (int) bottom + GlowsSamples.MARGIN) - y);
    }
    
    private static double screenX(Client client, Vec3 point) {
        return GlowsSamples.project(point).x * client.minecraft().getWindow().getGuiScale();
    }
    
    private static Vector2f project(Vec3 point) {
        return Objects.requireNonNull(Nexus.getMod().getRender().project(point), "the point lies behind the camera");
    }
    
    private record Region(int x, int y, int width, int height) { }
}
