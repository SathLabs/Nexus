package dev.satherov.nexus.mixin.render;

import dev.satherov.nexus.internal.render.client.RenderDispatch;
import dev.satherov.nexus.internal.render.post.PostEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Runs the post effects of every mod after vanilla's own post effect and stores the projection matrix of the level for [RenderDispatch].
///
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    
    ///
    /// The resource pool of the game renderer.
    ///
    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;
    
    ///
    /// Calls [PostEffects#run(RenderTarget, GraphicsResourceAllocator)] on the main render target at the first profiler pop of `render`.
    ///
    /// @param callback The callback of the injection.
    ///
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V",
                    ordinal = 0
            )
    )
    private void nexus$runPostEffects(CallbackInfo callback) {
        PostEffects.INSTANCE.run(Minecraft.getInstance().getMainRenderTarget(), this.resourcePool);
    }
    
    ///
    /// Calls [RenderDispatch#captureLevelProjection(Matrix4fc)] with the projection matrix that `renderLevel` uploads for the level.
    ///
    /// @param projection The projection matrix of the level.
    ///
    /// @return The same projection matrix.
    ///
    @ModifyArg(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"
            )
    )
    private Matrix4f nexus$captureLevelProjection(Matrix4f projection) {
        RenderDispatch.captureLevelProjection(projection);
        return projection;
    }
}
