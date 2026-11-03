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
import dev.satherov.nexus.internal.render.shader.Programs;
import dev.satherov.nexus.internal.render.shape.Scenes;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import com.google.common.base.Suppliers;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
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
    /// Creates the pipeline of the program file `assets/<modid>/nexus/shaders/<name>.glsl` and registers it with the shading a shader pack gives it.
    /// The pipeline takes the blend, depth, cull, defines, and uniform blocks of the given state,
    /// and its vertex mode, or quads if the state doesn't set one.
    ///
    /// A program is one GLSL file that builds on the vanilla program `core/<name>` its directive `#nexus base <name>` picks,
    /// one of `position_color`, `position_tex_color`, and `entity`. Everything else in the file is plain GLSL:
    /// - A file-scope `in` that is a `float`, an `int`, or one of their vectors is an extra vertex input.
    /// - A file-scope `out` is a varying from the vertex stage to the fragment stage.
    /// - A uniform block or a sampler is declared in both stages, and a uniform block other than vanilla's own has to be bound with [Pipeline#bind(String, ClientOnly)].
    /// - A `#define` outside of any conditional applies to the vanilla program as well.
    /// - `void nexus_vertex()` runs as the last statement of the vertex main function.
    /// - `vec4 nexus_albedo(vec4 albedo)` wraps the color the vanilla program fetches, before the color modulator, lighting, overlay, and fog.
    /// - Everything else is copied into both stages, which define `NEXUS_VERTEX` or `NEXUS_FRAGMENT`.
    ///
    /// If the program could not be compiled at a resource reload, it will be logged once and
    /// nothing will be drawn with it through [#draw(ClientOnly)] until the next reload.
    /// Drawing a render type over it anywhere else will throw while GL validation is on.
    ///
    /// @param program The name of the program.
    /// @param state   The state of the pipeline.
    /// @param shading The shading a shader pack gives the pipeline.
    ///
    /// @return The registered pipeline.
    ///
    /// @throws IllegalArgumentException - If the jar of the mod has no such program file.
    /// - If the program file doesn't have exactly one `#nexus base` directive with a known base, or has another `#nexus` directive or a `#version` directive.
    /// - If the program file declares a `nexus_` function that is no hook, or an input of another type, with more than one variable, or with the name of an input of its base.
    /// - If another program declares an input of the same name with another type or count, or if all 32 vertex format element identifiers are taken.
    /// @throws IllegalStateException    If the client already registered its render pipelines.
    ///
    @CanIgnoreReturnValue
    public Pipeline pipeline(String program, ClientOnly<Supplier<RenderPipeline.Snippet>> state, Shading shading) {
        if (!FMLEnvironment.getDist().isClient()) {
            return new Pipeline(this::noPipeline, shading);
        }
        
        return this.programPipeline(program, state.get().get(), shading);
    }
    
    ///
    /// Creates the pipeline of the program file `assets/<modid>/nexus/shaders/<name>.glsl` and
    /// registers it with the shading a shader pack gives it.
    /// The pipeline has a depth test with depth write, no blend, culling, and quads.
    ///
    /// The program file is read as in [#pipeline(String, ClientOnly, Shading)].
    ///
    /// If the program could not be compiled at a resource reload, it will be logged once and
    /// nothing will be drawn with it through [#draw(ClientOnly)] until the next reload.
    /// Drawing a render type over it anywhere else will throw while GL validation is on.
    ///
    /// @param program The name of the program.
    /// @param shading The shading a shader pack gives the pipeline.
    ///
    /// @return The registered pipeline.
    ///
    /// @throws IllegalArgumentException - If the jar of the mod has no such program file.
    /// - If the program file doesn't have exactly one `#nexus base` directive with a known base, or has another `#nexus` directive or a `#version` directive.
    /// - If the program file declares a `nexus_` function that is no hook, or an input of another type, with more than one variable, or with the name of an input of its base.
    /// - If another program declares an input of the same name with another type or count, or if all 32 vertex format element identifiers are taken.
    /// @throws IllegalStateException    If the client already registered its render pipelines.
    ///
    @CanIgnoreReturnValue
    public Pipeline pipeline(String program, Shading shading) {
        if (!FMLEnvironment.getDist().isClient()) {
            return new Pipeline(this::noPipeline, shading);
        }
        
        return this.programPipeline(program, null, shading);
    }
    
    ///
    /// Creates the pipeline of the given program file with the given state and registers it with the given shading.
    ///
    /// Should only ever be called on the client.
    ///
    /// @param program The name of the program.
    /// @param state   The state of the pipeline, or `null` to use the default state.
    /// @param shading The shading a shader pack gives the pipeline.
    ///
    /// @return The registered pipeline.
    ///
    /// @throws IllegalArgumentException If the program file is missing or fails [Programs#create(NexusMod, String, RenderPipeline.Snippet)].
    /// @throws IllegalStateException    If the client already registered its render pipelines.
    ///
    private Pipeline programPipeline(String program, RenderPipeline.@Nullable Snippet state, Shading shading) {
        // The program file is only read once RenderDispatch#program knows that the render pipelines aren't registered yet.
        Supplier<RenderPipeline> created = Suppliers.memoize(() -> Programs.create(this.mod, program, state));
        Pipeline registered = new Pipeline(() -> created, shading);
        RenderDispatch.program(registered);
        return registered;
    }
    
    ///
    /// Throws an [IllegalStateException] since a dedicated server has no render pipelines.
    ///
    @Contract("-> fail")
    private Supplier<RenderPipeline> noPipeline() {
        throw new IllegalStateException("A dedicated server has no render pipelines, asked for by '" + this.mod.getModId() + "'");
    }
    
    ///
    /// Runs the given drawer on every frame that renders a level, after the translucent features.
    /// The pose stack is relative to the camera and the buffer source is our own.
    ///
    /// A render type over a pipeline registered with [#pipeline(ClientOnly, Shading)] or created from a program file ends in the slot its shading asks for.
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
