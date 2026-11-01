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
/// The active post effects of every mod.
///
/// @see GameRendererMixin
///
@Slf4j
@ApiStatus.Internal
public enum PostEffects {
    INSTANCE;
    
    ///
    /// The active effects, in activation order.
    ///
    private final SequencedSet<PostEffect> active = new LinkedHashSet<>();
    
    ///
    /// Loads the post-chain of the given identifier.
    ///
    /// @param id The identifier of the post-chain.
    ///
    /// @return The post-chain, or `null` if it could not be loaded.
    ///
    private static @Nullable PostChain chain(Identifier id) {
        // We don't go through ShaderManager#getPostChain here, because it would pass a failure on to the resource pack recovery,
        // which clears the selected packs or leaves the world.
        ShaderManager.CompilationCache cache = Minecraft.getInstance().getShaderManager().compilationCache;
        try {
            return cache.getOrLoadPostChain(id, LevelTargetBundle.MAIN_TARGETS);
        } catch (ShaderManager.CompilationException exception) {
            PostEffects.log.error("Could not load the post effect '{}'", id, exception);
            // Cache it as missing like vanilla does, so the failure is only logged once per resource reload.
            cache.postChains.put(id, Optional.empty());
            return null;
        }
    }
    
    ///
    /// Adds the given effect if it isn't active yet.
    ///
    /// Should only ever be called from [PostEffect#activate()].
    ///
    /// @param effect The effect.
    ///
    public void activate(PostEffect effect) {
        this.active.add(effect);
    }
    
    ///
    /// Removes the given effect if it is active.
    ///
    /// Should only ever be called from [PostEffect#deactivate()].
    ///
    /// @param effect The effect.
    ///
    public void deactivate(PostEffect effect) {
        this.active.remove(effect);
    }
    
    ///
    /// Runs the post-chain of every active effect over the given render target.
    ///
    /// @param target The main render target.
    /// @param pool   The graphics resource allocator of the game renderer.
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
