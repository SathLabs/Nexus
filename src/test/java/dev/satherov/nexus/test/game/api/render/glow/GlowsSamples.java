package dev.satherov.nexus.test.game.api.render.glow;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.glow.Glow;
import dev.satherov.nexus.api.render.glow.Glows;
import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.joml.Vector2f;

import java.util.Objects;

///
/// The client tests that check the color of an entity glow, the glow of a block that is broken, the glow of a chest as it opens, and the expiry of a glow.
///
public class GlowsSamples {
    
    private static final int RED = 0xFFFF0000;
    
    private static final int RED_MINIMUM = 0x40;
    
    private static final int OTHER_MAXIMUM = 0x10;
    
    private static final String KEY = "glow";
    
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
        BlockPos pos = GlowsSamples.place(client, eye, Blocks.STONE);
        AABB box = new AABB(pos);
        Assertions.assertThat(GlowsSamples.redPixels(client, client.capture(), box)).as("the red pixels before the glow").isZero();
        
        Glow glow = GlowsSamples.glows().block(GlowsSamples.KEY, pos, GlowsSamples.RED);
        client.tick();
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        
        Capture capture = client.capture();
        Assertions.assertThat(GlowsSamples.redPixels(client, capture, box)).as("the red pixels around the block").isPositive();
        capture.assertGolden("block_glow_stops_when_broken");
        
        client.serverLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        client.settle();
        client.awaitStill(GlowsSamples.STILL_FRAMES);
        
        Assertions.assertThat(glow.isActive()).as("the glow of the broken block").isTrue();
        Assertions.assertThat(GlowsSamples.redPixels(client, client.capture(), box)).as("the red pixels around the broken block").isZero();
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
        if (RunOptions.fromProperties().realtime()) {
            return;
        }
        
        Vec3 eye = GlowsSamples.enter(client);
        BlockPos pos = GlowsSamples.place(client, eye, Blocks.STONE);
        AABB box = new AABB(pos);
        Glow glow = GlowsSamples.glows().block(GlowsSamples.KEY, pos, GlowsSamples.RED).expire(GlowsSamples.EXPIRY);
        
        client.ticks(GlowsSamples.EXPIRY - 1);
        Assertions.assertThat(glow.isActive()).as("the glow a tick before it expires").isTrue();
        Assertions.assertThat(GlowsSamples.redPixels(client, client.capture(), box)).as("the red pixels a tick before the expiry").isPositive();
        
        client.tick();
        Assertions.assertThat(glow.isActive()).as("the glow once it expired").isFalse();
        Assertions.assertThat(GlowsSamples.redPixels(client, client.capture(), box)).as("the red pixels after the expiry").isZero();
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
    
    private static boolean isAt(Entity entity, Vec3 position) {
        return entity.position().closerThan(position, GlowsSamples.ENTITY_TOLERANCE) && entity.getPosition(0.0F).closerThan(position, GlowsSamples.ENTITY_TOLERANCE);
    }
    
    private static int redPixels(Client client, Capture capture, AABB box) {
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
            Vector2f screen = Objects.requireNonNull(Nexus.getMod().getRender().project(point), "the corner lies behind the camera");
            left = Math.min(left, screen.x * scale);
            top = Math.min(top, screen.y * scale);
            right = Math.max(right, screen.x * scale);
            bottom = Math.max(bottom, screen.y * scale);
        }
        
        int x = Math.max(0, (int) left - GlowsSamples.MARGIN);
        int y = Math.max(0, (int) top - GlowsSamples.MARGIN);
        int width = Math.min(capture.width(), (int) right + GlowsSamples.MARGIN) - x;
        int height = Math.min(capture.height(), (int) bottom + GlowsSamples.MARGIN) - y;
        Capture region = capture.region(x, y, width, height);
        int count = 0;
        for (int row = 0; row < region.height(); row++) {
            for (int column = 0; column < region.width(); column++) {
                int pixel = region.pixel(column, row);
                if (ARGB.red(pixel) >= GlowsSamples.RED_MINIMUM && ARGB.green(pixel) <= GlowsSamples.OTHER_MAXIMUM && ARGB.blue(pixel) <= GlowsSamples.OTHER_MAXIMUM) {
                    count++;
                }
            }
        }
        
        return count;
    }
}
