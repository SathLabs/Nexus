package dev.satherov.nexus.mixin.render;

import dev.satherov.nexus.internal.render.post.PostEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Runs the mods' post effects right after vanilla's own post effect and before the GUI.
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
    /// Calls [PostEffects#run(RenderTarget, GraphicsResourceAllocator)] at the profiler pop that closes the world section of `render`,
    /// right after the post effect block.
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
}
