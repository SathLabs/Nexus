package dev.satherov.nexus.test.game.api.render.post;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.post.PostEffect;
import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.api.render.shape.Shape;
import dev.satherov.nexus.gametest.api.client.Capture;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;

///
/// The client tests that check the activation, the order, the deactivation, and the missing files of post effects.
///
public class PostEffectSamples {
    
    private static final int RED = 0xFFFF0000;
    
    private static final int CYAN = 0xFF00FFFF;
    
    private static final int BLACK = 0xFF000000;
    
    private static final int STILL_FRAMES = 10;
    
    private static final double HEIGHT = 4.0D;
    
    @ClientTest
    public static void activatedEffectChangesPixel(Client client) {
        PostEffectSamples.enter(client);
        PostEffect invert = PostEffectSamples.effect("invert");
        try {
            invert.activate();
            client.tick();
            
            Capture capture = client.capture();
            Assertions.assertThat(invert.isActive()).as("the activated effect").isTrue();
            PostEffectSamples.assertCenter(capture, PostEffectSamples.CYAN);
            capture.assertGolden("inverted");
        } finally {
            invert.deactivate();
        }
    }
    
    @ClientTest
    public static void effectsRunInActivationOrder(Client client) {
        PostEffectSamples.enter(client);
        PostEffect red = PostEffectSamples.effect("red");
        PostEffect invert = PostEffectSamples.effect("invert");
        try {
            red.activate();
            invert.activate();
            red.activate();
            client.tick();
            
            Capture redFirst = client.capture();
            PostEffectSamples.assertCenter(redFirst, PostEffectSamples.CYAN);
            redFirst.assertGolden("red_then_inverted");
            
            red.deactivate();
            red.activate();
            client.tick();
            
            Capture invertFirst = client.capture();
            PostEffectSamples.assertCenter(invertFirst, PostEffectSamples.BLACK);
            invertFirst.assertGolden("inverted_then_red");
        } finally {
            red.deactivate();
            invert.deactivate();
        }
    }
    
    @ClientTest
    public static void deactivationRestores(Client client) {
        PostEffectSamples.enter(client);
        PostEffect invert = PostEffectSamples.effect("invert");
        try {
            invert.activate();
            client.tick();
            PostEffectSamples.assertCenter(client.capture(), PostEffectSamples.CYAN);
            
            invert.deactivate();
            client.tick();
            
            Capture capture = client.capture();
            Assertions.assertThat(invert.isActive()).as("the deactivated effect").isFalse();
            PostEffectSamples.assertCenter(capture, PostEffectSamples.RED);
            capture.assertGolden("restored");
        } finally {
            invert.deactivate();
        }
    }
    
    @ClientTest
    public static void missingFileRunsNothing(Client client) {
        PostEffectSamples.enter(client);
        PostEffect missing = PostEffectSamples.effect("missing");
        try {
            missing.activate();
            client.ticks(2);
            
            Assertions.assertThat(client.minecraft().level).as("the level of the client").isNotNull();
            Assertions.assertThat(missing.isActive()).as("the effect without a file").isTrue();
            PostEffectSamples.assertCenter(client.capture(), PostEffectSamples.RED);
        } finally {
            missing.deactivate();
        }
    }
    
    private static PostEffect effect(String name) {
        return Nexus.getMod().getRender().postEffect(name);
    }
    
    private static void enter(Client client) {
        client.joinWorld();
        client.hideHud(true);
        client.pose(client.serverLevel().getRespawnData().pos().getBottomCenter().add(0.0D, PostEffectSamples.HEIGHT, 0.0D), 0.0F, 0.0F);
        client.awaitStill(PostEffectSamples.STILL_FRAMES);
        
        Vec3 eye = client.minecraft().gameRenderer.getMainCamera().position();
        Nexus.getMod().getRender().getScene().draw(Shape.box(AABB.ofSize(eye.add(0.0D, 0.0D, 6.0D), 2.0D, 2.0D, 1.0D)), Paint.fill(PostEffectSamples.RED));
        client.tick();
        PostEffectSamples.assertCenter(client.capture(), PostEffectSamples.RED);
    }
    
    private static void assertCenter(Capture capture, int argb) {
        capture.assertPixel(capture.width() / 2, capture.height() / 2, argb);
    }
}
