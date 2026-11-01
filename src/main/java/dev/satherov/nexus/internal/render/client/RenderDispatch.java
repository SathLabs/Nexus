package dev.satherov.nexus.internal.render.client;

import lombok.experimental.UtilityClass;

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
import dev.satherov.nexus.internal.render.shader.Programs;
import dev.satherov.nexus.internal.render.shape.Scenes;

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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
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
import net.minecraft.world.phys.Vec3;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

///
/// The client event listeners of the render feature.
/// - Registers the pipelines of the library and the mods, the HUD layers, the debug renderers, and the glow modifier of the entities.
/// - Runs the scenes, the draw hooks, the glows, and the outline rules at their stage of the frame.
/// - Ticks the debug renderers after each client tick.
/// - Starts the scenes and the glows on login and ends them when the level of the client unloads.
/// - Projects world positions onto the screen for [Render].
/// - Registers the reload listener of the programs and stores the pipelines built from program files.
///
/// Should only ever be loaded on the client.
///
@UtilityClass
@ApiStatus.Internal
public class RenderDispatch {
    
    ///
    /// The mod pipelines queued for the registration event.
    ///
    private static final List<Pipeline> WAITING_PIPELINES = new CopyOnWriteArrayList<>();
    
    ///
    /// The mod pipelines by their render pipeline, filled by the registration event and [#program(Pipeline)].
    ///
    private static final Map<RenderPipeline, Pipeline> PIPELINES = new ConcurrentHashMap<>();
    
    ///
    /// The draw hooks of the mods.
    ///
    private static final List<BiConsumer<PoseStack, MultiBufferSource>> HOOKS = new CopyOnWriteArrayList<>();
    
    ///
    /// The runner of the draw hooks.
    ///
    private static final DrawHooks DRAW_HOOKS = new DrawHooks(RenderDispatch.HOOKS, RenderDispatch.PIPELINES);
    
    ///
    /// The mod HUD layers queued for the registration event, by their identifier.
    ///
    private static final Map<Identifier, WaitingHudLayer> WAITING_HUD_LAYERS = Collections.synchronizedMap(new LinkedHashMap<>());
    
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
        nexusBus.addListener(RegisterRenderPipelinesEvent.class, RenderDispatch::registerPipelines);
        nexusBus.addListener(AddClientReloadListenersEvent.class, Programs::register);
        nexusBus.addListener(RegisterGuiLayersEvent.class, RenderDispatch::registerHudLayers);
        nexusBus.addListener(RegisterDebugEntriesEvent.class, event -> DebugRenderers.INSTANCE.registerEntries(event::register));
        nexusBus.addListener(
                RegisterRenderStateModifiersEvent.class,
                event -> event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() { }, RenderDispatch::glow)
        );
        NeoForge.EVENT_BUS.addListener(FrameGraphSetupEvent.class, RenderDispatch::enableGlows);
        NeoForge.EVENT_BUS.addListener(
                SubmitCustomGeometryEvent.class,
                event -> GlowOutlines.INSTANCE.submitBlocks(event.getPoseStack(), event.getSubmitNodeCollector(), event.getLevelRenderState().cameraRenderState.pos)
        );
        NeoForge.EVENT_BUS.addListener(ExtractBlockOutlineRenderStateEvent.class, RenderDispatch::extractOutline);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentFeatures.class, event -> RenderDispatch.DRAW_HOOKS.drawInLevel(event.getPoseStack()));
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class, RenderDispatch::drawScenes);
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterLevel.class, event -> RenderDispatch.DRAW_HOOKS.drawAfterLevel(event.getModelViewMatrix()));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, _ -> DebugRenderers.INSTANCE.tick());
        // The client session only opens the client scope at normal priority, so we have to start after it.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ClientPlayerNetworkEvent.LoggingIn.class, RenderDispatch::start);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, RenderDispatch::end);
    }
    
    ///
    /// Registers the pipelines of the library and then the mod pipelines.
    ///
    /// If iris is present, every pipeline will also be assigned its shading.
    ///
    /// @param event The registration event.
    ///
    private static void registerPipelines(RegisterRenderPipelinesEvent event) {
        ShapePipelines.register(event::registerPipeline);
        for (Pipeline pipeline : RenderDispatch.WAITING_PIPELINES) {
            RenderPipeline renderPipeline = pipeline.getPipeline().get().get();
            event.registerPipeline(renderPipeline);
            RenderDispatch.PIPELINES.put(renderPipeline, pipeline);
        }
        
        // This also assigns the pipelines built from program files, since the parallel mod constructors that build them shouldn't call into Iris.
        if (ModList.get().isLoaded("iris")) {
            RenderDispatch.PIPELINES.forEach((renderPipeline, pipeline) -> IrisBridge.assign(renderPipeline, pipeline.getShading()));
        }
        
        RenderDispatch.WAITING_PIPELINES.clear();
        RenderDispatch.pipelinesRegistered = true;
    }
    
    ///
    /// Registers the queued HUD layers at their slots.
    ///
    /// A layer draws nothing while the HUD is hidden.
    ///
    /// @param event The registration event.
    ///
    private static void registerHudLayers(RegisterGuiLayersEvent event) {
        RenderDispatch.WAITING_HUD_LAYERS.forEach((id, waiting) -> {
            HudLayer hud = waiting.layer();
            GuiLayer layer = (graphics, delta) -> {
                if (!Minecraft.getInstance().options.hideGui) {
                    hud.extract(graphics, delta);
                }
            };
            
            Identifier anchor = waiting.slot().anchor();
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
        });
        
        RenderDispatch.WAITING_HUD_LAYERS.clear();
        RenderDispatch.hudLayersRegistered = true;
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
        Scenes.INSTANCE.draw(event.getPoseStack(), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false), buffers);
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
    /// Ends the scenes, the glows, and the outline rules when a client level unloads.
    ///
    /// @param event The unload event of the level.
    ///
    private static void end(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            Scenes.INSTANCE.end();
            GlowOutlines.INSTANCE.end();
            OutlineRules.INSTANCE.end();
        }
    }
    
    ///
    /// Queues a mod pipeline for the registration event.
    ///
    /// Should only ever be called from [Render#pipeline(ClientOnly, Shading)].
    ///
    /// @param pipeline The pipeline to queue.
    ///
    /// @throws IllegalStateException If the registration event already ran.
    ///
    public static void pipeline(Pipeline pipeline) {
        if (RenderDispatch.pipelinesRegistered) {
            Identifier location = pipeline.getPipeline().get().get().getLocation();
            throw new IllegalStateException("Could not register the pipeline '" + location + "', the render pipelines were already registered");
        }
        
        RenderDispatch.WAITING_PIPELINES.add(pipeline);
    }
    
    ///
    /// Builds the render pipeline of the given pipeline from its program file and stores the pipeline for its bindings.
    ///
    /// The pipeline never goes through the registration event, which only assigns it the Iris program of its shading.
    /// If the registration event already ran, the program file will not be read.
    ///
    /// Should only ever be called from [Render#pipeline(String, ClientOnly, Shading)] and [Render#pipeline(String, Shading)].
    ///
    /// @param pipeline The pipeline to store.
    ///
    /// @throws IllegalArgumentException If the program file is missing or [Programs#create(NexusMod, String, RenderPipeline.Snippet)] rejects it.
    /// @throws IllegalStateException    If the registration event already ran.
    ///
    public static void program(Pipeline pipeline) {
        if (RenderDispatch.pipelinesRegistered) {
            throw new IllegalStateException("Could not create a pipeline from a program file, the render pipelines were already registered");
        }
        
        RenderDispatch.PIPELINES.put(pipeline.getPipeline().get().get(), pipeline);
    }
    
    ///
    /// Queues a mod HUD layer for the registration event.
    ///
    /// Should only ever be called from [Render#hudLayer(String, HudSlot, ClientOnly)].
    ///
    /// @param id    The identifier of the layer.
    /// @param slot  The slot of the layer.
    /// @param layer The layer to queue.
    ///
    /// @throws IllegalStateException If the registration event already ran or the identifier is already taken.
    ///
    public static void hudLayer(Identifier id, HudSlot slot, ClientOnly<HudLayer> layer) {
        if (RenderDispatch.hudLayersRegistered) {
            throw new IllegalStateException("Could not register the HUD layer '" + id + "', the HUD layers were already registered");
        }
        
        if (RenderDispatch.WAITING_HUD_LAYERS.putIfAbsent(id, new WaitingHudLayer(slot, layer.get())) != null) {
            throw new IllegalStateException("The HUD layer '" + id + "' is registered twice");
        }
    }
    
    ///
    /// Queues a draw hook.
    ///
    /// Should only ever be called from [Render#draw(ClientOnly)].
    ///
    /// @param drawer The draw hook to queue.
    ///
    public static void draw(ClientOnly<BiConsumer<PoseStack, MultiBufferSource>> drawer) {
        RenderDispatch.HOOKS.add(drawer.get());
    }
    
    ///
    /// Projects the given world position onto the screen, in GUI-scaled pixels.
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
        if (position.subtract(camera.position()).toVector3f().dot(camera.forwardVector()) <= 0.0F) {
            return null;
        }
        
        Vec3 screen = minecraft.gameRenderer.projectPointToScreen(position);
        Window window = minecraft.getWindow();
        return new Vector2f((float) (screen.x + 1.0D) / 2.0F * window.getGuiScaledWidth(), (float) (1.0D - screen.y) / 2.0F * window.getGuiScaledHeight());
    }
    
    ///
    /// A mod HUD layer queued for the registration event.
    ///
    /// @param slot  The slot of the layer.
    /// @param layer The layer.
    ///
    private record WaitingHudLayer(HudSlot slot, HudLayer layer) { }
}
