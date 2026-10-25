package dev.satherov.nexus.internal.render.client;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.api.render.outline.TargetedBlock;
import dev.satherov.nexus.internal.render.glow.GlowOutlines;
import dev.satherov.nexus.internal.render.outline.OutlineRules;
import dev.satherov.nexus.internal.render.shape.Scenes;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.FrameGraphSetupEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.google.common.reflect.TypeToken;
import com.mojang.blaze3d.platform.Window;

import org.jetbrains.annotations.ApiStatus;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

///
/// The only class that uses client render events.
/// - Registers the pipelines of the library and the mods, the HUD layers, the debug renderers, and the entity glow modifier.
/// - Runs the scenes, the draw hooks, the glows, and the outline rules at their points of the frame.
/// - Starts them on login and ends them whenever the client's level unloads.
/// - Projects world positions onto the screen for [Render].
///
/// Should only ever be loaded on the client.
///
@UtilityClass
@ApiStatus.Internal
public class RenderDispatch {

    ///
    /// Subscribes on the mod bus of Nexus and the game bus.
    ///
    /// Should only ever be called once from [Nexus], behind the dist check.
    /// The registrations of every [Render] are only queued.
    ///
    /// @param nexusBus The mod bus of Nexus.
    ///
    public static void listen(IEventBus nexusBus) {
        nexusBus.addListener(RegisterRenderPipelinesEvent.class, event -> ShapePipelines.register(event::registerPipeline));
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
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class, RenderDispatch::drawScenes);
        // The client scope only exists once the client session marked the login, which it does at the normal priority.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ClientPlayerNetworkEvent.LoggingIn.class, RenderDispatch::start);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, RenderDispatch::end);
    }
    
    ///
    /// Sets the outline color of the entity's render state to the color of its glow, if any mod glows the entity.
    ///
    private static void glow(Entity entity, EntityRenderState state) {
        int color = GlowOutlines.INSTANCE.colorOf(entity);
        if (color != 0) {
            state.outlineColor = color;
        }
    }
    
    ///
    /// Turns on the outline post chain of the frame while any glow exists.
    ///
    private static void enableGlows(FrameGraphSetupEvent event) {
        if (GlowOutlines.INSTANCE.hasAny()) {
            event.enableOutlineProcessing();
        }
    }
    
    ///
    /// Updates the outline rules for the block under the crosshair and adds them as a custom renderer if any rule answered.
    ///
    private static void extractOutline(ExtractBlockOutlineRenderStateEvent event) {
        TargetedBlock target = new TargetedBlock(event.getLevel(), event.getBlockPos().immutable(), event.getBlockState(), event.getHitResult().getDirection());
        if (OutlineRules.INSTANCE.update(target)) {
            event.addCustomRenderer(OutlineRules.INSTANCE);
        }
    }
    
    ///
    /// Draws every scene once the translucent blocks are drawn and then draws the labels of the scenes.
    ///
    private static void drawScenes(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Scenes.INSTANCE.draw(event.getPoseStack(), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false), buffers);
        buffers.endBatch();
    }
    
    ///
    /// Starts the tickers of the scenes and the glows on login.
    ///
    private static void start(ClientPlayerNetworkEvent.LoggingIn event) {
        Scenes.INSTANCE.start();
        GlowOutlines.INSTANCE.start();
    }
    
    ///
    /// Ends every scene, every glow, and the kept outlines once the level of the client unloads, on a dimension change and on logout alike.
    ///
    private static void end(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            Scenes.INSTANCE.end();
            GlowOutlines.INSTANCE.end();
            OutlineRules.INSTANCE.end();
        }
    }
    
    ///
    /// The GUI-scaled screen position of the given world position.
    ///
    /// Should only ever be called from [Render#project(Vec3)].
    ///
    /// @param position The world position to project.
    /// @param modId    The id of the mod that asks, used in the failure message.
    ///
    /// @return The position on the screen, starting from the top left corner, or `null` if the world position lies behind the camera.
    ///
    /// @throws IllegalStateException If called off the client thread.
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
}
