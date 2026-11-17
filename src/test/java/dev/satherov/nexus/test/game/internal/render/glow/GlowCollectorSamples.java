package dev.satherov.nexus.test.game.internal.render.glow;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.internal.render.glow.GlowCollector;

import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Blocks;

import com.mojang.blaze3d.vertex.PoseStack;

import org.assertj.core.api.Assertions;

///
/// Checks what the collector of a glowing block passes on from a block entity renderer.
///
public class GlowCollectorSamples {
    
    private static final int RED = 0xFFFF0000;
    
    @ClientTest
    public static void multiLayerBlockModelIsOutlined(Client client) {
        BlockModelRenderState model = new BlockModelRenderState();
        client.minecraft().getBlockModelResolver().update(model, Blocks.STONE.defaultBlockState(), BlockDisplayContext.create());
        SubmitNodeStorage storage = new SubmitNodeStorage();
        model.submitMultiLayer(new PoseStack(), new GlowCollector(storage, GlowCollectorSamples.RED), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        
        Assertions.assertThat(storage.order(0).getBlockModelSubmits()).as("the block models passed on").singleElement().satisfies(submit -> {
            Assertions.assertThat(submit.renderType().isOutline()).as("the render type of the passed model is an outline").isTrue();
            Assertions.assertThat(submit.outlineColor()).as("the outline color of the passed model").isEqualTo(GlowCollectorSamples.RED);
            Assertions.assertThat(submit.modelParts()).as("the parts of the passed model").isNotEmpty();
        });
    }
}
