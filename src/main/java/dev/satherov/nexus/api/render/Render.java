package dev.satherov.nexus.api.render;

import lombok.Getter;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.render.glow.Glows;
import dev.satherov.nexus.api.render.hud.HudLayer;
import dev.satherov.nexus.api.render.hud.HudSlot;
import dev.satherov.nexus.api.render.outline.Outlines;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.api.render.post.PostEffect;
import dev.satherov.nexus.api.render.shape.Scene;
import dev.satherov.nexus.internal.render.client.RenderDispatch;
import dev.satherov.nexus.internal.render.debug.DebugRenderers;
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
import java.util.function.Consumer;
import java.util.function.Supplier;

///
/// What one mod draws and registers with the renderer.
///
/// The registrations are called from the mod's constructor on either side and do nothing on a dedicated server.
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
    /// The post-effects of the mod by name.
    ///
    private final Map<String, PostEffect> postEffectByName = new HashMap<>();
    
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
    /// Should only ever be called from [NexusMod#getRender()].
    ///
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Render(NexusMod mod) {
        this.mod = mod;
    }
    
    ///
    /// If a shader pack is replacing vanilla's shaders.
    ///
    /// Always `false` without iris and on a dedicated server.
    ///
    /// @return `true` if a shader pack is replacing vanilla's shaders.
    ///
    public boolean isShaderPackActive() {
        return FMLEnvironment.getDist().isClient() && ModList.get().isLoaded("iris") && IrisBridge.isShaderPackInUse();
    }
    
    ///
    /// The mod's scene of shapes in the client's level, created on the first call.
    ///
    /// Its drawings are dropped whenever the client's level unloads.
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
    /// The mod's block outline rules, created on the first call.
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
    /// The mod's glows, created on the first call.
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
    /// Projects the given world position onto the screen, in GUI-scaled pixels.
    ///
    /// @param position The world position to project.
    ///
    /// @return The screen position, starting from the top left corner, or `null` if the world position lies behind the camera.
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
    /// Registers the given render pipeline and the shading a shader pack gives it.
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
    /// Runs the given drawer on every frame that renders a level, after the translucent features.
    /// The pose stack is relative to the camera and the buffer source is our own.
    ///
    /// A render type over a pipeline registered with [#pipeline(ClientOnly, Shading)] ends in the slot its shading asks for.
    /// Any other render type ends inside the level.
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
    /// Registers the given HUD layer as `<modid>:<name>` at the given slot.
    ///
    /// @param name  The name of the layer.
    /// @param slot  The place of the layer among the others.
    /// @param layer The layer.
    ///
    /// @throws IllegalStateException If the name is already taken or if the client already registered its HUD layers.
    ///
    public void hudLayer(String name, HudSlot slot, ClientOnly<HudLayer> layer) {
        if (FMLEnvironment.getDist().isClient()) {
            RenderDispatch.hudLayer(this.mod.id(name), slot, layer);
        }
    }
    
    ///
    /// Registers the given debug renderer as `<modid>:<name>`, toggled by the player in the debug options screen.
    ///
    /// While it is on, its own scene is cleared and the renderer called once per client tick.
    /// Its scene will be empty from the first tick it is off.
    ///
    /// @param name     The name of the debug renderer.
    /// @param renderer The renderer, called with the scene of the debug renderer.
    ///
    /// @throws IllegalStateException If the name is already taken or if the client already registered its debug entries.
    ///
    public void debugRenderer(String name, ClientOnly<Consumer<Scene>> renderer) {
        if (FMLEnvironment.getDist().isClient()) {
            DebugRenderers.INSTANCE.add(this.mod, this.mod.id(name), renderer);
        }
    }
    
    ///
    /// Gets the post effect with the given name, the same handle on every call.
    ///
    /// @param name The name of the post effect.
    ///
    /// @return The handle of the post effect.
    ///
    public PostEffect postEffect(String name) {
        return this.postEffectByName.computeIfAbsent(name, _ -> new PostEffect(this.mod.id(name)));
    }
}
