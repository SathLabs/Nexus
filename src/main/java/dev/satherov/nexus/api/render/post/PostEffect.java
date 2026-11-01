package dev.satherov.nexus.api.render.post;

import lombok.Getter;

import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.internal.render.post.PostEffects;

import net.neoforged.fml.loading.FMLEnvironment;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

///
/// A screen-wide effect of one mod, the post chain in `assets/<modid>/post_effect/<name>.json`.
///
/// The active effects of all mods run after vanilla's own effect, in activation order.
/// They run under a shader pack as well.
///
/// Can be accessed through [Render#postEffect(String)].
///
public final class PostEffect {
    
    ///
    /// The identifier, `<modid>:<name>`.
    ///
    @Getter
    private final Identifier id;
    
    ///
    /// If the effect is active.
    ///
    private boolean active;
    
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param id The identifier, `<modid>:<name>`.
    ///
    @ApiStatus.Internal
    public PostEffect(Identifier id) {
        this.id = id;
    }
    
    ///
    /// Runs the effect from the next frame on until [#deactivate()] is called.
    ///
    /// Does nothing while active.
    ///
    /// If the file is missing or broken, it will be logged once per resource reload and nothing runs.
    ///
    /// @throws IllegalStateException If called on a dedicated server or off the client thread.
    ///
    public void activate() {
        this.checkClientThread();
        this.active = true;
        PostEffects.INSTANCE.activate(this);
    }
    
    ///
    /// Stops the effect from the next frame on.
    ///
    /// Does nothing while inactive.
    ///
    /// @throws IllegalStateException If called on a dedicated server or off the client thread.
    ///
    public void deactivate() {
        this.checkClientThread();
        this.active = false;
        PostEffects.INSTANCE.deactivate(this);
    }
    
    ///
    /// @return `true` if the effect is active.
    ///
    /// @throws IllegalStateException If called on a dedicated server or off the client thread.
    ///
    public boolean isActive() {
        this.checkClientThread();
        return this.active;
    }
    
    ///
    /// Checks that we are on the client thread.
    ///
    /// @throws IllegalStateException If called on a dedicated server or off the client thread.
    ///
    private void checkClientThread() {
        if (!FMLEnvironment.getDist().isClient()) {
            throw new IllegalStateException("The post effect '" + this.id + "' was used on a dedicated server");
        }
        
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("The post effect '" + this.id + "' was used on thread '" + Thread.currentThread().getName() + "'");
        }
    }
}
