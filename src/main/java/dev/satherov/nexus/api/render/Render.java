package dev.satherov.nexus.api.render;

import lombok.Getter;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.render.glow.Glows;
import dev.satherov.nexus.api.render.outline.Outlines;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.api.render.post.PostEffect;
import dev.satherov.nexus.api.render.shape.Scene;
import dev.satherov.nexus.internal.render.client.RenderDispatch;
import dev.satherov.nexus.internal.render.glow.GlowOutlines;
import dev.satherov.nexus.internal.render.iris.IrisBridge;
import dev.satherov.nexus.internal.render.shape.Scenes;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

///
/// What one mod draws and registers with the renderer.
///
/// The registrations are called from the mod's constructor on both sides and do nothing on a dedicated server.
/// The scene, the glows, and the queries throw an [IllegalStateException] there.
///
/// Can be accessed through [NexusMod#getRender()].
///
public final class Render {

    ///
    /// The handle of the mod.
    ///
    @Getter
    private final NexusMod mod;

    ///
    /// The scene of the mod, or `null` until [#getScene()] is first called.
    ///
    private @Nullable Scene scene;
    
    ///
    /// The block outline rules of the mod, or `null` until [#getOutlines()] is first called.
    ///
    private @Nullable Outlines outlines;
    
    ///
    /// The glows of the mod, or `null` until [#getGlows()] is first called.
    ///
    private @Nullable Glows glows;
    
    ///
    /// The post effects of the mod by name.
    ///
    private final Map<String, PostEffect> postEffectByName = new HashMap<>();
    
    ///
    /// Should only ever be called from [NexusMod#getRender()].
    ///
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Render(NexusMod mod) {
        this.mod = mod;
    }

    ///
    /// If a shader pack is replacing vanilla's shaders right now.
    ///
    /// Always `false` without Iris.
    ///
    /// @return `true` if a shader pack is replacing vanilla's shaders right now.
    ///
    public boolean isShaderPackActive() {
        return FMLEnvironment.getDist().isClient() && ModList.get().isLoaded("iris") && IrisBridge.isShaderPackInUse();
    }
    
    ///
    /// The mod's scene of shapes in the client's level, created on first call.
    ///
    /// Its drawings are dropped whenever the level of the client unloads, on a dimension change and on logout alike.
    ///
    /// @return The scene of the mod.
    ///
    /// @throws IllegalStateException If called on a dedicated server.
    ///
    public Scene getScene() {
        if (!FMLEnvironment.getDist().isClient()) {
            throw new IllegalStateException("A dedicated server has no scene, asked for by '" + this.mod.getModId() + "'");
        }
        
        if (this.scene == null) {
            this.scene = new Scene(this.mod);
            Scenes.INSTANCE.add(this.scene);
        }
        
        return this.scene;
    }
    
    ///
    /// The mod's block outline rules, created on first call.
    ///
    /// @return The block outline rules of the mod.
    ///
    public Outlines getOutlines() {
        if (this.outlines == null) {
            this.outlines = new Outlines(this.mod);
        }
        
        return this.outlines;
    }
    
    ///
    /// The mod's glows, created on first call.
    ///
    /// They are dropped when the client leaves a level.
    ///
    /// @return The glows of the mod.
    ///
    /// @throws IllegalStateException If called on a dedicated server.
    ///
    public Glows getGlows() {
        if (!FMLEnvironment.getDist().isClient()) {
            throw new IllegalStateException("A dedicated server has no glows, asked for by '" + this.mod.getModId() + "'");
        }
        
        if (this.glows == null) {
            this.glows = new Glows(this.mod);
            GlowOutlines.INSTANCE.add(this.glows);
        }
        
        return this.glows;
    }
    
    ///
    /// The GUI-scaled screen position of a world position.
    ///
    /// @param position The world position to project.
    ///
    /// @return The position on the screen, starting from the top left corner, or `null` if the world position lies behind the camera.
    ///
    /// @throws IllegalStateException If called on a dedicated server or off the client thread.
    ///
    public @Nullable Vector2f project(Vec3 position) {
        if (!FMLEnvironment.getDist().isClient()) {
            throw new IllegalStateException("A dedicated server has no camera, asked for by '" + this.mod.getModId() + "'");
        }
        
        return RenderDispatch.project(position, this.mod.getModId());
    }
    
    ///
    /// Registers the pipeline when the client registers render pipelines and fixes what a shader pack does with it.
    ///
    /// @param pipeline The render pipeline to register.
    /// @param shading  The shading a shader pack gives the pipeline.
    ///
    /// @return The registered pipeline.
    ///
    /// @throws IllegalStateException If the client already registered its render pipelines.
    ///
    @CanIgnoreReturnValue
    public Pipeline pipeline(ClientOnly<Supplier<RenderPipeline>> pipeline, Shading shading) {
        Pipeline registered = new Pipeline(pipeline, shading);
        if (FMLEnvironment.getDist().isClient()) {
            RenderDispatch.pipeline(registered);
        }
        
        return registered;
    }
    
    ///
    /// Runs the drawer on every frame that renders a level, after the translucent features,
    /// with the camera-relative pose stack and a buffer source of our own.
    ///
    /// A render type over a pipeline registered here is ended in the slot its shading asks for, any other inside the level.
    ///
    /// Registered until the game closes.
    ///
    /// @param drawer The drawer, called with the pose stack and the buffer source.
    ///
    /// @see Shading#OWN
    ///
    public void draw(ClientOnly<BiConsumer<PoseStack, MultiBufferSource>> drawer) {
        if (FMLEnvironment.getDist().isClient()) {
            RenderDispatch.draw(drawer);
        }
    }
    
    ///
    /// The post effect `assets/<modid>/post_effect/<name>.json`, one handle per name.
    ///
    /// @param name The name of the post effect.
    ///
    /// @return The handle of the post effect.
    ///
    public PostEffect postEffect(String name) {
        return this.postEffectByName.computeIfAbsent(name, _ -> new PostEffect(this.mod.id(name)));
    }
}
