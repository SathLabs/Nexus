package dev.satherov.nexus.test.unit.internal.render.client;

import dev.satherov.nexus.internal.render.client.PackOverlay;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

///
/// Checks the color target states that own-shaded pipelines draw into the pack overlay with, and which of them blend through it like onto the main target.
///
public class PackOverlayTest {
    
    private static ColorTargetState state(BlendFunction blend, int mask) {
        return new ColorTargetState(Optional.of(blend), mask);
    }
    
    private static ColorTargetState unblended(int mask) {
        return new ColorTargetState(Optional.empty(), mask);
    }
    
    @Test
    public void leavesAStateThatWritesNoColor() {
        ColorTargetState depthOnly = PackOverlayTest.unblended(ColorTargetState.WRITE_NONE);
        ColorTargetState blendedDepthOnly = PackOverlayTest.state(BlendFunction.TRANSLUCENT, ColorTargetState.WRITE_NONE);
        Assertions.assertThat(PackOverlay.colorTargetFor(depthOnly)).isEqualTo(depthOnly);
        Assertions.assertThat(PackOverlay.colorTargetFor(blendedDepthOnly)).isEqualTo(blendedDepthOnly);
    }
    
    @Test
    public void keepsTheOverlayAlphaUnderAnAdditiveBlend() {
        Assertions.assertThat(PackOverlay.colorTargetFor(PackOverlayTest.state(BlendFunction.ADDITIVE, ColorTargetState.WRITE_ALL)))
                .isEqualTo(PackOverlayTest.state(new BlendFunction(SourceFactor.ONE, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE), ColorTargetState.WRITE_ALL));
        Assertions.assertThat(PackOverlay.colorTargetFor(PackOverlayTest.state(BlendFunction.LIGHTNING, ColorTargetState.WRITE_ALL)))
                .isEqualTo(PackOverlayTest.state(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE), ColorTargetState.WRITE_ALL));
    }
    
    @Test
    public void leavesAnAdditiveBlendThatWritesNoAlpha() {
        ColorTargetState additive = PackOverlayTest.state(BlendFunction.ADDITIVE, ColorTargetState.WRITE_COLOR);
        Assertions.assertThat(PackOverlay.colorTargetFor(additive)).isEqualTo(additive);
    }
    
    @Test
    public void writesTheCoverageOfATranslucentBlend() {
        ColorTargetState translucent = PackOverlayTest.state(BlendFunction.TRANSLUCENT, ColorTargetState.WRITE_ALL);
        ColorTargetState colorOnly = PackOverlayTest.state(new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA), ColorTargetState.WRITE_COLOR);
        Assertions.assertThat(PackOverlay.colorTargetFor(translucent)).isEqualTo(translucent);
        Assertions.assertThat(PackOverlay.colorTargetFor(colorOnly)).isEqualTo(translucent);
    }
    
    @Test
    public void writesAlphaWithoutABlend() {
        Assertions.assertThat(PackOverlay.colorTargetFor(PackOverlayTest.unblended(ColorTargetState.WRITE_COLOR)))
                .isEqualTo(PackOverlayTest.unblended(ColorTargetState.WRITE_ALL));
    }
    
    @Test
    public void blendsAlikeWhereTheCompositeRestoresTheFrame() {
        Assertions.assertThat(PackOverlay.blendsAlike(PackOverlayTest.state(BlendFunction.ADDITIVE, ColorTargetState.WRITE_ALL))).isTrue();
        Assertions.assertThat(PackOverlay.blendsAlike(PackOverlayTest.state(BlendFunction.LIGHTNING, ColorTargetState.WRITE_RED))).isTrue();
        Assertions.assertThat(PackOverlay.blendsAlike(PackOverlayTest.state(BlendFunction.TRANSLUCENT, ColorTargetState.WRITE_COLOR))).isTrue();
        Assertions.assertThat(PackOverlay.blendsAlike(PackOverlayTest.unblended(ColorTargetState.WRITE_COLOR))).isTrue();
        Assertions.assertThat(PackOverlay.blendsAlike(PackOverlayTest.unblended(ColorTargetState.WRITE_NONE))).isTrue();
    }
    
    @Test
    public void blendsDifferentlyWhereTheCompositeCannotRestoreTheFrame() {
        // These write coverage where they leave the color of the overlay empty, so the composite darkens the frame there.
        Assertions.assertThat(PackOverlay.blendsAlike(PackOverlayTest.state(BlendFunction.TRANSLUCENT, ColorTargetState.WRITE_RED))).isFalse();
        Assertions.assertThat(PackOverlay.blendsAlike(PackOverlayTest.unblended(ColorTargetState.WRITE_RED))).isFalse();
        Assertions.assertThat(PackOverlay.blendsAlike(PackOverlayTest.unblended(ColorTargetState.WRITE_ALPHA))).isFalse();
        // These read the color of the target, which the overlay doesn't hold.
        Assertions.assertThat(PackOverlay.blendsAlike(PackOverlayTest.state(new BlendFunction(SourceFactor.DST_COLOR, DestFactor.ZERO), ColorTargetState.WRITE_ALL))).isFalse();
        Assertions.assertThat(PackOverlay.blendsAlike(PackOverlayTest.state(new BlendFunction(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ONE), ColorTargetState.WRITE_ALL))).isFalse();
    }
}
