package dev.satherov.nexus.test.game.api.render.outline;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.outline.Outline;
import dev.satherov.nexus.api.render.outline.Outlines;
import dev.satherov.nexus.api.render.outline.TargetedBlock;
import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.api.render.shape.Shape;
import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

///
/// Checks what an outline rule does to vanilla's outline and how often it is asked.
///
public class OutlinesSamples {
    
    private static final int RED = 0xFFFF0000;
    
    private static final int STILL_FRAMES = 10;
    
    private static final double HEIGHT = 4.0D;
    
    private static final double DISTANCE = 3.0D;
    
    private static final int SPREAD = 2;
    
    private static final int MARGIN = 8;
    
    private static final AABB MARK = new AABB(0.1D, 0.1D, -0.05D, 0.4D, 0.4D, 0.0D);
    
    private static final Vec3 MARK_FRONT = new Vec3(0.25D, 0.25D, -0.05D);
    
    private static final Vec3 TOP_EDGE = new Vec3(0.25D, 1.0D, 0.0D);
    
    @ClientTest
    public static void replacingRuleHidesVanillaOutline(Client client) {
        OutlinesSamples.outlines().replace(target -> OutlinesSamples.mark(target, Blocks.EMERALD_BLOCK));
        BlockPos pos = OutlinesSamples.aim(client, Blocks.EMERALD_BLOCK);
        int[] bare = OutlinesSamples.bareEdge(client, pos);
        
        Capture capture = client.capture();
        OutlinesSamples.assertColor(client, capture, pos, OutlinesSamples.MARK_FRONT, OutlinesSamples.RED);
        Assertions.assertThat(OutlinesSamples.edge(client, capture, pos)).as("the top edge under the replacement").isEqualTo(bare);
        OutlinesSamples.face(client, capture, pos).assertGolden("replacing_rule_hides_vanilla_outline");
    }
    
    @ClientTest
    public static void addingRuleKeepsVanillaOutline(Client client) {
        OutlinesSamples.outlines().add(target -> OutlinesSamples.mark(target, Blocks.GOLD_BLOCK));
        BlockPos pos = OutlinesSamples.aim(client, Blocks.GOLD_BLOCK);
        int[] bare = OutlinesSamples.bareEdge(client, pos);
        
        Capture capture = client.capture();
        OutlinesSamples.assertColor(client, capture, pos, OutlinesSamples.MARK_FRONT, OutlinesSamples.RED);
        Assertions.assertThat(OutlinesSamples.edge(client, capture, pos)).as("the top edge beside the addition").isNotEqualTo(bare);
        OutlinesSamples.face(client, capture, pos).assertGolden("adding_rule_keeps_vanilla_outline");
    }
    
    @ClientTest
    public static void ruleIsAskedOncePerTarget(Client client) {
        List<TargetedBlock> asked = new ArrayList<>();
        OutlinesSamples.outlines().add(target -> {
            if (target.state().is(Blocks.DIAMOND_BLOCK)) {
                asked.add(target);
            }
            
            return null;
        });
        
        client.joinWorld();
        Vec3 feet = client.serverLevel().getRespawnData().pos().getBottomCenter().add(0.0D, OutlinesSamples.HEIGHT, 0.0D);
        client.pose(feet, 0.0F, 0.0F);
        BlockPos first = BlockPos.containing(client.minecraft().gameRenderer.getMainCamera().position().add(0.0D, 0.0D, OutlinesSamples.DISTANCE));
        BlockPos second = first.east();
        BlockState diamond = Blocks.DIAMOND_BLOCK.defaultBlockState();
        client.serverLevel().setBlockAndUpdate(first, diamond);
        client.serverLevel().setBlockAndUpdate(second, diamond);
        client.settle();
        client.ticks(OutlinesSamples.STILL_FRAMES);
        Assertions.assertThat(asked)
                .as("the targets asked for while looking at the first block")
                .containsExactly(new TargetedBlock(Objects.requireNonNull(client.minecraft().level), first, diamond, Direction.NORTH));
        
        client.pose(feet.add(1.0D, 0.0D, 0.0D), 0.0F, 0.0F);
        client.ticks(OutlinesSamples.STILL_FRAMES);
        client.pose(feet, 0.0F, 0.0F);
        client.ticks(OutlinesSamples.STILL_FRAMES);
        Assertions.assertThat(asked.stream().map(TargetedBlock::pos).toList()).as("the positions asked for, in order").containsExactly(first, second, first);
    }
    
    private static Outlines outlines() {
        return Nexus.getMod().getRender().getOutlines();
    }
    
    private static @Nullable Outline mark(TargetedBlock target, Block block) {
        return target.state().is(block) ? new Outline(Shape.box(OutlinesSamples.MARK), Paint.fill(OutlinesSamples.RED)) : null;
    }
    
    private static BlockPos aim(Client client, Block block) {
        client.joinWorld();
        client.pose(client.serverLevel().getRespawnData().pos().getBottomCenter().add(0.0D, OutlinesSamples.HEIGHT, 0.0D), 0.0F, 0.0F);
        BlockPos pos = BlockPos.containing(client.minecraft().gameRenderer.getMainCamera().position().add(0.0D, 0.0D, OutlinesSamples.DISTANCE));
        client.serverLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        client.settle();
        client.awaitStill(OutlinesSamples.STILL_FRAMES);
        return pos;
    }
    
    private static int[] bareEdge(Client client, BlockPos pos) {
        client.hideHud(true);
        client.awaitStill(OutlinesSamples.STILL_FRAMES);
        int[] bare = OutlinesSamples.edge(client, client.capture(), pos);
        client.hideHud(false);
        client.awaitStill(OutlinesSamples.STILL_FRAMES);
        return bare;
    }
    
    private static int[] edge(Client client, Capture capture, BlockPos pos) {
        Pixel center = OutlinesSamples.pixel(client, Vec3.atLowerCornerOf(pos).add(OutlinesSamples.TOP_EDGE));
        int size = 2 * OutlinesSamples.SPREAD + 1;
        Capture region = capture.region(center.x() - OutlinesSamples.SPREAD, center.y() - OutlinesSamples.SPREAD, size, size);
        int[] pixels = new int[size * size];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = region.pixel(i % size, i / size);
        }
        
        return pixels;
    }
    
    private static Capture face(Client client, Capture capture, BlockPos pos) {
        Pixel lower = OutlinesSamples.pixel(client, Vec3.atLowerCornerOf(pos));
        Pixel upper = OutlinesSamples.pixel(client, Vec3.atLowerCornerOf(pos).add(1.0D, 1.0D, 0.0D));
        int x = Math.min(lower.x(), upper.x()) - OutlinesSamples.MARGIN;
        int y = Math.min(lower.y(), upper.y()) - OutlinesSamples.MARGIN;
        return capture.region(x, y, Math.abs(upper.x() - lower.x()) + 2 * OutlinesSamples.MARGIN, Math.abs(upper.y() - lower.y()) + 2 * OutlinesSamples.MARGIN);
    }
    
    private static void assertColor(Client client, Capture capture, BlockPos pos, Vec3 offset, int argb) {
        Pixel pixel = OutlinesSamples.pixel(client, Vec3.atLowerCornerOf(pos).add(offset));
        capture.assertPixel(pixel.x(), pixel.y(), argb);
    }
    
    private static Pixel pixel(Client client, Vec3 point) {
        Vector2f screen = Objects.requireNonNull(Nexus.getMod().getRender().project(point), "the point lies behind the camera");
        int scale = client.minecraft().getWindow().getGuiScale();
        return new Pixel((int) (screen.x * scale), (int) (screen.y * scale));
    }
    
    private record Pixel(int x, int y) { }
}
