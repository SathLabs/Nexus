package dev.satherov.nexus.internal.render.post;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.api.render.post.PostEffect;
import dev.satherov.nexus.mixin.render.GameRendererMixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.SequencedSet;

///
/// The active post effects of every mod in activation order, run after vanilla's own effect by the game renderer mixin.
///
/// @see GameRendererMixin
///
@Slf4j
@ApiStatus.Internal
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PostEffects {
    
    ///
    /// The effects.
    ///
    public static final PostEffects INSTANCE = new PostEffects();
    
    ///
    /// The active effects, in activation order.
    ///
    private final SequencedSet<PostEffect> active = new LinkedHashSet<>();
    
    ///
    /// The post chain of the given identifier, or `null` if it could not be loaded.
    ///
    private static @Nullable PostChain chain(Identifier id) {
        // ShaderManager#getPostChain would hand a failure to the resource pack recovery,
        // which clears the selected packs or leaves the world.
        ShaderManager.CompilationCache cache = Minecraft.getInstance().getShaderManager().compilationCache;
        try {
            return cache.getOrLoadPostChain(id, LevelTargetBundle.MAIN_TARGETS);
        } catch (ShaderManager.CompilationException exception) {
            PostEffects.log.error("Could not load the post effect '{}'", id, exception);
            // Cached as missing like vanilla does, so the failure is logged once per resource reload.
            cache.postChains.put(id, Optional.empty());
            return null;
        }
    }
    
    ///
    /// Appends the effect, if absent.
    ///
    /// Should only ever be called from [PostEffect#activate()].
    ///
    /// @param effect The effect to append.
    ///
    public void activate(PostEffect effect) {
        this.active.add(effect);
    }
    
    ///
    /// Removes the effect, if present.
    ///
    /// Should only ever be called from [PostEffect#deactivate()].
    ///
    /// @param effect The effect to remove.
    ///
    public void deactivate(PostEffect effect) {
        this.active.remove(effect);
    }
    
    ///
    /// Processes every active chain on the target with the renderer's resource pool.
    ///
    /// @param target The main render target.
    /// @param pool   The resource pool of the game renderer.
    ///
    public void run(RenderTarget target, GraphicsResourceAllocator pool) {
        if (this.active.isEmpty()) {
            return;
        }
        
        FrameGraphBuilder frame = new FrameGraphBuilder();
        PostChain.TargetBundle targets = PostChain.TargetBundle.of(PostChain.MAIN_TARGET_ID, frame.importExternal("main", target));
        for (PostEffect effect : this.active) {
            PostChain chain = PostEffects.chain(effect.getId());
            if (chain != null) {
                chain.addToFrame(frame, target.width, target.height, targets);
            }
        }
        
        frame.execute(pool);
    }
}
