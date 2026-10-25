package dev.satherov.nexus.internal.render.glow;

import dev.satherov.nexus.api.render.glow.Glows;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelSet;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// One glowing block, with its model cached while the block keeps its state and the resources aren't reloaded.
///
/// Its block entity is extracted again in every frame.
///
@ApiStatus.Internal
public final class GlowingBlock {
    
    ///
    /// The display context the models of glowing blocks are resolved in.
    ///
    private static final BlockDisplayContext CONTEXT = BlockDisplayContext.create();
    
    ///
    /// The position of the block.
    ///
    private final BlockPos pos;
    
    ///
    /// The model of the block in the cached state.
    ///
    private final BlockModelRenderState model = new BlockModelRenderState();
    
    ///
    /// The state the model was resolved for, or `null` until the first submission.
    ///
    private @Nullable BlockState state;
    
    ///
    /// The block models the model was resolved from, or `null` until the first submission.
    ///
    private @Nullable BlockModelSet models;
    
    ///
    /// Should only ever be called from [Glows#block(Object, BlockPos, int)].
    ///
    /// @param pos The position of the block.
    ///
    public GlowingBlock(BlockPos pos) {
        this.pos = pos;
    }
    
    ///
    /// Submits the outline of the block in the given color,
    /// resolving its model again first if its state changed or the resources were reloaded.
    ///
    /// If the block entity at the position has a renderer, the outline will also cover what that renderer submits in this frame.
    ///
    /// Does nothing while the position holds air.
    ///
    /// @param pose      The pose stack of the frame, whose origin is the camera.
    /// @param collector The collector of the frame that the outline is submitted to.
    /// @param camera    The position of the camera.
    /// @param argb      The ARGB color of the outline.
    ///
    public void submit(PoseStack pose, SubmitNodeCollector collector, Vec3 camera, int argb) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = Objects.requireNonNull(minecraft.level);
        BlockState current = level.getBlockState(this.pos);
        if (current.isAir()) {
            return;
        }
        
        BlockModelSet models = minecraft.getModelManager().getBlockModelSet();
        if (current != this.state || models != this.models) {
            minecraft.getBlockModelResolver().update(this.model, current, GlowingBlock.CONTEXT);
            this.state = current;
            this.models = models;
        }
        
        pose.pushPose();
        pose.translate(Vec3.atLowerCornerOf(this.pos).subtract(camera));
        // Only the model parts, because a special model would draw the item form of what the block entity renderer draws live.
        this.model.submitOnlyOutline(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, argb);
        BlockEntity blockEntity = level.getBlockEntity(this.pos);
        if (blockEntity != null) {
            CameraRenderState cameraState = minecraft.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
            BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
            float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            BlockEntityRenderState extracted = dispatcher.tryExtractRenderState(blockEntity, partialTick, null, cameraState.cullFrustum);
            if (extracted != null) {
                dispatcher.submit(extracted, pose, new GlowCollector(collector, argb), cameraState);
            }
        }
        
        pose.popPose();
    }
}
