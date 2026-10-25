package dev.satherov.nexus.internal.render.glow;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.List;

///
/// A submit node collector that passes on only the models it receives, as outlines in one color.
///
/// Every other submission is dropped, and so is every model whose render type has no outline.
///
/// Each outline is drawn white into the outline target and then over that in the color by the outline buffer.
///
@ApiStatus.Internal
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class GlowCollector implements SubmitNodeCollector {
    
    ///
    /// The collector of the frame, which the orders are taken from.
    ///
    private final SubmitNodeCollector collector;
    
    ///
    /// The collector of the frame or of one of its orders, which the outlines are submitted to.
    ///
    private final OrderedSubmitNodeCollector target;
    
    ///
    /// The ARGB color of the outlines.
    ///
    private final int argb;
    
    ///
    /// Should only ever be called from [GlowingBlock#submit(PoseStack, SubmitNodeCollector, Vec3, int)].
    ///
    /// @param collector The collector of the frame that the outlines are submitted to.
    /// @param argb      The ARGB color of the outlines.
    ///
    public GlowCollector(SubmitNodeCollector collector, int argb) {
        this(collector, collector, argb);
    }
    
    ///
    /// The outline render type of the given render type, or `null` if it has none.
    ///
    private static @Nullable RenderType outlineOf(RenderType renderType) {
        return renderType.isOutline() ? renderType : renderType.outline().orElse(null);
    }
    
    ///
    /// Passes the outlines on to the given order of the frame's collector.
    ///
    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return new GlowCollector(this.collector, this.collector.order(order), this.argb);
    }
    
    ///
    /// Does nothing.
    ///
    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) { }
    
    ///
    /// Does nothing.
    ///
    @Override
    public void submitNameTag(
            PoseStack poseStack,
            @Nullable Vec3 nameTagAttachment,
            int offset,
            Component name,
            boolean seeThrough,
            int lightCoords,
            double distanceToCameraSq,
            CameraRenderState camera
    ) { }
    
    ///
    /// Does nothing.
    ///
    @Override
    public void submitText(
            PoseStack poseStack,
            float x,
            float y,
            FormattedCharSequence string,
            boolean dropShadow,
            Font.DisplayMode displayMode,
            int lightCoords,
            int color,
            int backgroundColor,
            int outlineColor
    ) { }
    
    ///
    /// Does nothing.
    ///
    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) { }
    
    ///
    /// Does nothing.
    ///
    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) { }
    
    ///
    /// Submits the model as an outline in the color.
    ///
    @Override
    public <STATE> void submitModel(
            Model<? super STATE> model,
            STATE state,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            int tintedColor,
            @Nullable TextureAtlasSprite sprite,
            int outlineColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay
    ) {
        RenderType outline = GlowCollector.outlineOf(renderType);
        if (outline != null) {
            this.target.submitModel(model, state, poseStack, outline, lightCoords, overlayCoords, tintedColor, sprite, this.argb, null);
        }
    }
    
    ///
    /// Submits the model part as an outline in the color.
    ///
    @Override
    public void submitModelPart(
            ModelPart modelPart,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            @Nullable TextureAtlasSprite sprite,
            boolean sheeted,
            boolean hasFoil,
            int tintedColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
            int outlineColor
    ) {
        RenderType outline = GlowCollector.outlineOf(renderType);
        if (outline != null) {
            this.target.submitModelPart(modelPart, poseStack, outline, lightCoords, overlayCoords, sprite, sheeted, false, tintedColor, null, this.argb);
        }
    }
    
    ///
    /// Does nothing.
    ///
    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) { }
    
    ///
    /// Submits the parts of the block model as an outline in the color.
    ///
    @Override
    public void submitBlockModel(
            PoseStack poseStack,
            RenderType renderType,
            List<BlockStateModelPart> parts,
            int[] tintLayers,
            int lightCoords,
            int overlayCoords,
            int outlineColor
    ) {
        RenderType outline = GlowCollector.outlineOf(renderType);
        if (outline != null) {
            this.target.submitBlockModel(poseStack, outline, parts, tintLayers, lightCoords, overlayCoords, this.argb);
        }
    }
    
    ///
    /// Does nothing.
    ///
    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) { }
    
    ///
    /// Does nothing.
    ///
    @Override
    public void submitItem(
            PoseStack poseStack,
            ItemDisplayContext displayContext,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            int[] tintLayers,
            List<BakedQuad> quads,
            ItemStackRenderState.FoilType foilType
    ) { }
    
    ///
    /// Does nothing.
    ///
    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) { }
    
    ///
    /// Does nothing.
    ///
    @Override
    public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) { }
}
