package dev.satherov.nexus.internal.render.client;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.api.render.hud.HudLayer;
import dev.satherov.nexus.api.render.hud.HudSlot;
import dev.satherov.nexus.api.render.outline.TargetedBlock;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.internal.render.debug.DebugRenderers;
import dev.satherov.nexus.internal.render.glow.GlowOutlines;
import dev.satherov.nexus.internal.render.iris.IrisBridge;
import dev.satherov.nexus.internal.render.outline.OutlineRules;
import dev.satherov.nexus.internal.render.post.PostEffects;
import dev.satherov.nexus.internal.render.shader.Programs;
import dev.satherov.nexus.internal.render.shape.Scenes;
import dev.satherov.nexus.internal.tick.client.ClientSession;
import dev.satherov.nexus.mixin.render.GameRendererMixin;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.FrameGraphSetupEvent;
import net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.pipeline.RegisterPipelineModifiersEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

import com.google.common.base.Suppliers;
import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

///
/// The client event listeners of the render feature.
/// - Registers the pipelines of the library and the mods, the modifier of the pack overlay, the HUD layers, the debug renderers, and the glow modifier of the entities.
/// - Runs the scenes, the draw hooks, the glows, and the outline rules at their stage of the frame.
/// - Ticks the debug renderers after each client tick.
/// - Starts the scenes and the glows on login and ends them, the outline rules, and the post effects when the client leaves its level.
/// - Makes the scenes rebuild their GPU buffers once a texture atlas is stitched again.
/// - Projects world positions onto the screen for [Render], with the projection matrix the level was last drawn with.
/// - Registers the reload listeners of the programs and the outline rules and stores the pipelines created from program files.
///
/// Should only ever be loaded on the client.
///
@Slf4j
@UtilityClass
@ApiStatus.Internal
public class RenderDispatch {
    
    ///
    /// The mod pipelines queued for the registration event.
    ///
    private static final List<Pipeline> WAITING_PIPELINES = new CopyOnWriteArrayList<>();
    
    ///
    /// The mod pipelines by their render pipeline, filled by the registration event and [#program(NexusMod, String, RenderPipeline.Snippet, Shading)].
    ///
    private static final Map<RenderPipeline, Pipeline> PIPELINES = new ConcurrentHashMap<>();
    
    ///
    /// The draw hooks of the mods.
    ///
    private static final List<DrawHooks.Hook> HOOKS = new CopyOnWriteArrayList<>();
    
    ///
    /// The runner of the draw hooks.
    ///
    private static final DrawHooks DRAW_HOOKS = new DrawHooks(RenderDispatch.HOOKS, RenderDispatch.PIPELINES);
    
    ///
    /// The HUD layers each mod queued for the registration event, by their identifier.
    ///
    private static final Map<NexusMod, Map<Identifier, WaitingHudLayer>> WAITING_HUD_LAYERS = new ConcurrentHashMap<>();
    
    ///
    /// The projection matrix the level was last drawn with, or the identity matrix before the first frame that draws a level.
    ///
    private static final Matrix4f LEVEL_PROJECTION = new Matrix4f();
    
    ///
    /// The model-view matrix the level was last drawn with, or the identity matrix before the first frame that draws a level.
    ///
    /// A shader pack may move the view bobbing and the hurt tilt from the projection matrix into this one.
    ///
    private static final Matrix4f LEVEL_MODEL_VIEW = new Matrix4f();
    
    ///
    /// If the registration event of the render pipelines ran.
    ///
    private static boolean pipelinesRegistered;
    
    ///
    /// If the registration event of the HUD layers ran.
    ///
    private static boolean hudLayersRegistered;
    
    ///
    /// Subscribes to the mod bus of Nexus and the game bus.
    ///
    /// Should only ever be called once from [Nexus].
    ///
    /// @param nexusBus The mod bus of Nexus.
    ///
    public static void listen(IEventBus nexusBus) {
        // The shape pipelines are created here because RenderDispatch#program refuses them once the registration event ran.
        ShapePipelines.init();
        nexusBus.addListener(RegisterRenderPipelinesEvent.class, RenderDispatch::registerPipelines);
        nexusBus.addListener(RegisterPipelineModifiersEvent.class, event -> event.register(PackOverlay.MODIFIER, RenderDispatch::overlaid));
        nexusBus.addListener(AddClientReloadListenersEvent.class, Programs::register);
        nexusBus.addListener(AddClientReloadListenersEvent.class, OutlineRules::register);
        nexusBus.addListener(TextureAtlasStitchedEvent.class, _ -> Scenes.INSTANCE.invalidate());
        // Every mod registers its HUD layers from its own bus, so the bus of Nexus only marks the event as run.
        nexusBus.addListener(RegisterGuiLayersEvent.class, _ -> RenderDispatch.hudLayersRegistered = true);
        nexusBus.addListener(RegisterDebugEntriesEvent.class, event -> DebugRenderers.INSTANCE.registerEntries(event::register));
        nexusBus.addListener(
                RegisterRenderStateModifiersEvent.class,
                event -> event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() { }, RenderDispatch::glow)
        );
        NeoForge.EVENT_BUS.addListener(FrameGraphSetupEvent.class, RenderDispatch::enableGlows);
        NeoForge.EVENT_BUS.addListener(FrameGraphSetupEvent.class, _ -> PackOverlay.arm());
        NeoForge.EVENT_BUS.addListener(
                SubmitCustomGeometryEvent.class,
                event -> GlowOutlines.INSTANCE.submitBlocks(event.getPoseStack(), event.getSubmitNodeCollector(), event.getLevelRenderState().cameraRenderState.pos)
        );
        NeoForge.EVENT_BUS.addListener(ExtractBlockOutlineRenderStateEvent.class, RenderDispatch::extractOutline);
        // By these stages more of the level was drawn, so the overlay has to copy the depth again before anyone draws into it.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderLevelStageEvent.AfterTranslucentFeatures.class, _ -> PackOverlay.invalidateDepth());
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderLevelStageEvent.AfterTranslucentBlocks.class, _ -> PackOverlay.invalidateDepth());
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentFeatures.class, event -> RenderDispatch.DRAW_HOOKS.drawInLevel(event.getPoseStack()));
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterOpaqueBlocks.class, event -> RenderDispatch.LEVEL_MODEL_VIEW.set(event.getModelViewMatrix()));
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class, RenderDispatch::drawScenes);
        // The composite isn't depth tested, so it has to run before anyone else draws after the level, or it would cover what they drew.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderLevelStageEvent.AfterLevel.class, _ -> PackOverlay.composite());
        // A level drawn without the event after it leaves the overlay armed, and NeoForge throws at the end of a frame that leaves its modifier pushed.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, RenderFrameEvent.Post.class, _ -> PackOverlay.disarm());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, _ -> DebugRenderers.INSTANCE.tick());
        // The client session only opens the client scope at normal priority, so we have to start after it.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ClientPlayerNetworkEvent.LoggingIn.class, RenderDispatch::start);
        // A new level loads before the old one unloads, so we have to end the state of the old one before any mod sets up its own in the load.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LevelEvent.Load.class, RenderDispatch::leave);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, RenderDispatch::end);
    }
    
    ///
    /// Registers the pipelines of the library and then the mod pipelines.
    ///
    /// If iris is present, every pipeline will also be assigned its shading.
    ///
    /// The blends of the own-shaded pipelines are logged as [#logOwnBlends()] describes.
    ///
    /// @param event The registration event.
    ///
    private static void registerPipelines(RegisterRenderPipelinesEvent event) {
        ShapePipelines.register(event::registerPipeline);
        event.registerPipeline(PackOverlay.getCompositePipeline());
        for (Pipeline pipeline : RenderDispatch.WAITING_PIPELINES) {
            RenderPipeline renderPipeline = pipeline.getPipeline().get().get();
            event.registerPipeline(renderPipeline);
            RenderDispatch.PIPELINES.put(renderPipeline, pipeline);
        }
        
        RenderDispatch.logOwnBlends();
        
        // This also assigns the pipelines built from program files, since the parallel mod constructors that build them shouldn't call into Iris.
        if (ModList.get().isLoaded("iris")) {
            RenderDispatch.PIPELINES.forEach((renderPipeline, pipeline) -> IrisBridge.assign(renderPipeline, pipeline.getShading()));
        }
        
        RenderDispatch.WAITING_PIPELINES.clear();
        RenderDispatch.pipelinesRegistered = true;
    }
    
    ///
    /// Logs each own-shaded pipeline that blends over the frame differently under a pack, since [PackOverlay#blendsAlike(ColorTargetState)] doesn't hold for it.
    ///
    /// Then logs once which own-shaded pipelines [PackOverlay#MODIFIER] swaps for a copy under a pack,
    /// since the copy is drawn into the entity outline target as well.
    ///
    private static void logOwnBlends() {
        List<Identifier> swapped = new ArrayList<>();
        for (Map.Entry<RenderPipeline, Pipeline> entry : RenderDispatch.PIPELINES.entrySet()) {
            if (entry.getValue().getShading() != Shading.OWN) {
                continue;
            }
            
            ColorTargetState state = entry.getKey().getColorTargetState();
            if (!PackOverlay.blendsAlike(state)) {
                RenderDispatch.log.warn("The own-shaded pipeline '{}' will blend over the frame differently under a shader pack, since the pack overlay can't reproduce its color target state '{}'", entry.getKey().getLocation(), state);
            }
            
            if (!PackOverlay.colorTargetFor(state).equals(state)) {
                swapped.add(entry.getKey().getLocation());
            }
        }
        
        if (!swapped.isEmpty()) {
            RenderDispatch.log.info("Under a shader pack, the own-shaded pipelines {} draw with the alpha factors and the alpha write of the pack overlay, into the entity outline target too", swapped);
        }
    }
    
    ///
    /// Swaps a registered own-shaded pipeline for a copy with the color target state of [PackOverlay#colorTargetFor(ColorTargetState)].
    ///
    /// Should only ever be called through [PackOverlay#MODIFIER].
    ///
    /// @param pipeline The pipeline a render pass is set to.
    /// @param name     The location of the copy.
    ///
    /// @return The copy, or the given pipeline if it isn't a registered own-shaded one or the color target state wouldn't change.
    ///
    private static RenderPipeline overlaid(RenderPipeline pipeline, Identifier name) {
        Pipeline registered = RenderDispatch.PIPELINES.get(pipeline);
        ColorTargetState state = pipeline.getColorTargetState();
        ColorTargetState overlaid = PackOverlay.colorTargetFor(state);
        if (registered == null || registered.getShading() != Shading.OWN || overlaid.equals(state)) {
            return pipeline;
        }
        
        // The copy keeps the shaders and the defines, so for a program file it compiles from the shader modules that Programs compiled.
        return pipeline.toBuilder().withLocation(name).withColorTargetState(overlaid).build();
    }
    
    ///
    /// Registers the given queued HUD layers of one mod at their slots.
    ///
    /// A layer anchored to another queued layer will be registered after it.
    ///
    /// @param waiting The queued HUD layers of the mod by their identifier.
    /// @param event   The registration event.
    ///
    /// @throws IllegalStateException If the queued layers are anchored to each other in a cycle or a layer could not be registered at its slot.
    ///
    private static void registerHudLayers(Map<Identifier, WaitingHudLayer> waiting, RegisterGuiLayersEvent event) {
        Map<Identifier, WaitingHudLayer> remaining = new LinkedHashMap<>(waiting);
        while (!remaining.isEmpty()) {
            int before = remaining.size();
            Iterator<Map.Entry<Identifier, WaitingHudLayer>> entries = remaining.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry<Identifier, WaitingHudLayer> entry = entries.next();
                Identifier anchor = entry.getValue().slot().anchor();
                if (anchor == null || !remaining.containsKey(anchor)) {
                    RenderDispatch.registerHudLayer(event, entry.getKey(), entry.getValue());
                    entries.remove();
                }
            }
            
            if (remaining.size() == before) {
                throw new IllegalStateException("Could not register the HUD layers '" + remaining.keySet() + "', they are anchored to each other");
            }
        }
    }
    
    ///
    /// Registers the given queued HUD layer at its slot.
    ///
    /// The layer draws nothing while the HUD is hidden.
    ///
    /// @param event   The registration event.
    /// @param id      The identifier of the layer.
    /// @param waiting The queued layer.
    ///
    /// @throws IllegalStateException If the layer could not be registered at its slot.
    ///
    private static void registerHudLayer(RegisterGuiLayersEvent event, Identifier id, WaitingHudLayer waiting) {
        HudLayer hud = waiting.layer();
        GuiLayer layer = (graphics, delta) -> {
            if (!Minecraft.getInstance().options.hideGui) {
                hud.extract(graphics, delta);
            }
        };
        
        Identifier anchor = waiting.slot().anchor();
        try {
            switch (waiting.slot().relation()) {
                case ABOVE -> {
                    if (anchor == null) {
                        event.registerAboveAll(id, layer);
                    } else {
                        event.registerAbove(anchor, id, layer);
                    }
                }
                case BELOW -> {
                    if (anchor == null) {
                        event.registerBelowAll(id, layer);
                    } else {
                        event.registerBelow(anchor, id, layer);
                    }
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Could not register the HUD layer '" + id + "' at its slot '" + waiting.slot() + "'", exception);
        }
    }
    
    ///
    /// Sets the outline color of the render state to the glow color of the entity, if any mod glows it.
    ///
    /// @param entity The entity.
    /// @param state  The render state of the entity.
    ///
    private static void glow(Entity entity, EntityRenderState state) {
        int color = GlowOutlines.INSTANCE.colorOf(entity);
        if (color != 0) {
            state.outlineColor = color;
        }
    }
    
    ///
    /// Turns on the outline processing of the frame if any glow exists.
    ///
    /// @param event The frame graph setup event.
    ///
    private static void enableGlows(FrameGraphSetupEvent event) {
        if (GlowOutlines.INSTANCE.hasAny()) {
            event.enableOutlineProcessing();
        }
    }
    
    ///
    /// Updates the outline rules with the targeted block and adds them as a custom renderer if any rule applies to it.
    ///
    /// @param event The extraction event of the block outline.
    ///
    private static void extractOutline(ExtractBlockOutlineRenderStateEvent event) {
        TargetedBlock target = new TargetedBlock(event.getLevel(), event.getBlockPos().immutable(), event.getBlockState(), event.getHitResult().getDirection());
        if (OutlineRules.INSTANCE.update(target)) {
            event.addCustomRenderer(OutlineRules.INSTANCE);
        }
    }
    
    ///
    /// Draws every scene into the buffer source of the client and then ends its batch.
    ///
    /// @param event The render stage event after the translucent blocks.
    ///
    private static void drawScenes(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Scenes.INSTANCE.draw(event.getPoseStack(), minecraft.getDeltaTracker(), buffers);
        buffers.endBatch();
    }
    
    ///
    /// Starts the scenes and the glows on login.
    ///
    /// @param event The login event.
    ///
    private static void start(ClientPlayerNetworkEvent.LoggingIn event) {
        Scenes.INSTANCE.start();
        GlowOutlines.INSTANCE.start();
    }
    
    ///
    /// Ends the scenes, the glows, the outline rules, and the post effects and frees the pack overlay if the client leaves its level for the level that loads.
    ///
    /// @param event The load event of the level.
    ///
    private static void leave(LevelEvent.Load event) {
        if (ClientSession.isSwitchingTo(event.getLevel()) && Minecraft.getInstance().level != null) {
            RenderDispatch.endAll();
        }
    }
    
    ///
    /// Ends the scenes, the glows, the outline rules, and the post effects and frees the pack overlay when the level of the client unloads.
    ///
    /// Does nothing if the client switches from the level to another one, whose load already ended them.
    ///
    /// @param event The unload event of the level.
    ///
    private static void end(LevelEvent.Unload event) {
        LevelAccessor level = event.getLevel();
        if (level == Minecraft.getInstance().level && !ClientSession.isSwitchingFrom(level)) {
            RenderDispatch.endAll();
        }
    }
    
    ///
    /// Ends the scenes, the glows, the outline rules, and the post effects and frees the pack overlay.
    ///
    private static void endAll() {
        Scenes.INSTANCE.end();
        GlowOutlines.INSTANCE.end();
        OutlineRules.INSTANCE.end();
        PostEffects.INSTANCE.end();
        PackOverlay.release();
    }
    
    ///
    /// Queues a pipeline of the given mod for the registration event.
    ///
    /// The given supplier will be called once, when the render pipeline is first needed.
    ///
    /// Should only ever be called from [Render#pipeline(ClientOnly, Shading)].
    ///
    /// @param mod      The mod that registers the pipeline.
    /// @param pipeline The supplier of the render pipeline.
    /// @param shading  The shading a shader pack gives the pipeline.
    ///
    /// @return The queued pipeline.
    ///
    /// @throws IllegalStateException If the registration event already ran.
    ///
    public static Pipeline pipeline(NexusMod mod, ClientOnly<Supplier<RenderPipeline>> pipeline, Shading shading) {
        Supplier<RenderPipeline> created = Suppliers.memoize(() -> RenderDispatch.create(mod, pipeline));
        if (RenderDispatch.pipelinesRegistered) {
            throw new IllegalStateException("Could not register the pipeline '" + created.get().getLocation() + "' of '" + mod.getModId() + "', the render pipelines were already registered");
        }
        
        Pipeline queued = new Pipeline(() -> created, shading);
        RenderDispatch.WAITING_PIPELINES.add(queued);
        return queued;
    }
    
    ///
    /// Creates the render pipeline of a mod pipeline with the given supplier.
    ///
    /// @param mod      The mod that registers the pipeline, used in the failure message.
    /// @param pipeline The supplier of the render pipeline.
    ///
    /// @return The render pipeline.
    ///
    /// @throws IllegalStateException If the supplier throws.
    ///
    private static RenderPipeline create(NexusMod mod, ClientOnly<Supplier<RenderPipeline>> pipeline) {
        try {
            return pipeline.get().get();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not create a render pipeline of '" + mod.getModId() + "'", exception);
        }
    }
    
    ///
    /// Creates the pipeline of the given program file with the given state and stores it by its render pipeline.
    ///
    /// The pipeline is not registered with the registration event, which only assigns it the Iris program of its shading.
    ///
    /// Should only ever be called from [Render#pipeline(String, ClientOnly, Shading)], [Render#pipeline(String, Shading)], and [ShapePipelines].
    ///
    /// @param mod     The mod that owns the program file.
    /// @param program The name of the program.
    /// @param state   The state of the pipeline, or `null` to use the default state.
    /// @param shading The shading a shader pack gives the pipeline.
    ///
    /// @return The created pipeline.
    ///
    /// @throws IllegalArgumentException If the program file is missing or fails [Programs#create(NexusMod, String, RenderPipeline.Snippet)].
    /// @throws IllegalStateException    If the registration event already ran.
    ///
    public static Pipeline program(NexusMod mod, String program, RenderPipeline.@Nullable Snippet state, Shading shading) {
        if (RenderDispatch.pipelinesRegistered) {
            throw new IllegalStateException("Could not create the pipeline of the program '" + program + "' of '" + mod.getModId() + "', the render pipelines were already registered");
        }
        
        RenderPipeline renderPipeline = Programs.create(mod, program, state);
        Pipeline created = new Pipeline(() -> () -> renderPipeline, shading);
        RenderDispatch.PIPELINES.put(renderPipeline, created);
        return created;
    }
    
    ///
    /// Queues a HUD layer of the given mod for the registration event on the bus of the mod.
    ///
    /// Should only ever be called from [Render#hudLayer(String, HudSlot, ClientOnly)].
    ///
    /// @param mod   The mod that registers the layer.
    /// @param name  The name of the layer.
    /// @param slot  The slot of the layer.
    /// @param layer The layer to queue.
    ///
    /// @throws IllegalStateException If the registration event already ran or the identifier is already taken.
    ///
    public static void hudLayer(NexusMod mod, String name, HudSlot slot, ClientOnly<HudLayer> layer) {
        Identifier id = mod.id(name);
        if (RenderDispatch.hudLayersRegistered) {
            throw new IllegalStateException("Could not register the HUD layer '" + id + "', the HUD layers were already registered");
        }
        
        Map<Identifier, WaitingHudLayer> waiting = RenderDispatch.WAITING_HUD_LAYERS.computeIfAbsent(mod, RenderDispatch::listenForHudLayers);
        if (waiting.putIfAbsent(id, new WaitingHudLayer(slot, layer.get())) != null) {
            throw new IllegalStateException("The HUD layer '" + id + "' is registered twice");
        }
    }
    
    ///
    /// Creates the queue of HUD layers of the given mod and registers it from the bus of the mod once the registration event runs.
    ///
    /// @param mod The mod that registers the layers.
    ///
    /// @return The empty queue of HUD layers.
    ///
    private static Map<Identifier, WaitingHudLayer> listenForHudLayers(NexusMod mod) {
        Map<Identifier, WaitingHudLayer> waiting = new LinkedHashMap<>();
        // The lowest priority lets every layer that the mod registers itself be in place before we anchor to it.
        mod.getEventBus().addListener(EventPriority.LOWEST, RegisterGuiLayersEvent.class, event -> RenderDispatch.registerHudLayers(waiting, event));
        return waiting;
    }
    
    ///
    /// Queues the given draw hook of the given mod.
    ///
    /// Should only ever be called from [Render#draw(ClientOnly)].
    ///
    /// @param mod    The mod that registers the draw hook.
    /// @param drawer The draw hook to queue.
    ///
    public static void draw(NexusMod mod, ClientOnly<BiConsumer<PoseStack, MultiBufferSource>> drawer) {
        RenderDispatch.HOOKS.add(new DrawHooks.Hook(mod, drawer.get()));
    }
    
    ///
    /// Stores the given projection matrix that the level is drawn with in this frame.
    ///
    /// @param projection The projection matrix of the level.
    ///
    /// @see GameRendererMixin
    ///
    public static void captureLevelProjection(Matrix4fc projection) {
        RenderDispatch.LEVEL_PROJECTION.set(projection);
    }
    
    ///
    /// Projects the given world position onto the screen, in GUI-scaled pixels, with the projection and model-view matrices the level was last drawn with.
    ///
    /// Should only ever be called from [Render#project(Vec3)].
    ///
    /// @param position The world position to project.
    /// @param modId    The id of the calling mod, used in the failure message.
    ///
    /// @return The screen position, starting from the top left corner, or `null` if the world position lies behind the camera.
    ///
    /// @throws IllegalStateException If not called on the client thread.
    ///
    public static @Nullable Vector2f project(Vec3 position, String modId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("Render#project of '" + modId + "' was called on thread '" + Thread.currentThread().getName() + "'");
        }
        
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vector3f offset = position.subtract(camera.position()).toVector3f();
        if (offset.dot(camera.forwardVector()) <= 0.0F) {
            return null;
        }
        
        Vector3f screen = new Matrix4f(RenderDispatch.LEVEL_PROJECTION).mul(RenderDispatch.LEVEL_MODEL_VIEW).transformProject(offset);
        Window window = minecraft.getWindow();
        return new Vector2f((screen.x + 1.0F) / 2.0F * window.getGuiScaledWidth(), (1.0F - screen.y) / 2.0F * window.getGuiScaledHeight());
    }
    
    ///
    /// A mod HUD layer queued for the registration event.
    ///
    /// @param slot  The slot of the layer.
    /// @param layer The layer.
    ///
    private record WaitingHudLayer(HudSlot slot, HudLayer layer) { }
}
