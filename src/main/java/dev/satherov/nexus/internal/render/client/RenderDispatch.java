package dev.satherov.nexus.internal.render.client;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.Render;
import dev.satherov.nexus.internal.render.shape.Scenes;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

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
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterTranslucentBlocks.class, RenderDispatch::drawScenes);
        // The client scope only exists once the client session marked the login, which it does at the normal priority.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ClientPlayerNetworkEvent.LoggingIn.class, _ -> Scenes.INSTANCE.start());
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, RenderDispatch::endScenes);
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
    /// Ends every scene once the level of the client unloads, on a dimension change and on logout alike.
    ///
    private static void endScenes(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            Scenes.INSTANCE.end();
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
