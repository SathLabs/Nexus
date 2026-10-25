package dev.satherov.nexus.internal.render.shape;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.shape.Scene;
import dev.satherov.nexus.api.tick.Ticker;
import dev.satherov.nexus.api.tick.ticker.Next;

import net.minecraft.client.renderer.MultiBufferSource;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

///
/// Every scene of every mod: ticks them through one ticker on Nexus's client scope started on login, draws them per frame and ends them
/// whenever the level of the client unloads.
///
@ApiStatus.Internal
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Scenes {
    
    ///
    /// The scenes.
    ///
    public static final Scenes INSTANCE = new Scenes();
    
    ///
    /// Every scene created so far, in the order they were created.
    ///
    private final List<Scene> scenes = new CopyOnWriteArrayList<>();
    
    ///
    /// The ticker of the scenes on the client scope of the current session, or `null` if none was started yet.
    ///
    private @Nullable Ticker ticker;
    
    ///
    /// Registers a scene.
    ///
    /// @param scene The scene of a mod.
    ///
    public void add(Scene scene) {
        this.scenes.add(scene);
    }
    
    ///
    /// Starts the ticker on the client scope. Called on login.
    ///
    /// Does nothing while the ticker of an earlier login still runs.
    ///
    public void start() {
        if (this.ticker != null && this.ticker.isActive()) {
            return;
        }
        
        this.ticker = Nexus.getMod().getScheduler().client().ticker("scenes", _ -> {
            this.scenes.forEach(Scene::tick);
            return Next.TICK;
        });
    }
    
    ///
    /// Draws every scene.
    ///
    /// @param pose        The pose stack of the frame, whose origin is the camera.
    /// @param partialTick The partial tick of the frame.
    /// @param buffers     The buffer source the labels are drawn into.
    ///
    public void draw(PoseStack pose, float partialTick, MultiBufferSource.BufferSource buffers) {
        for (Scene scene : this.scenes) {
            scene.draw(pose, partialTick, buffers);
        }
    }
    
    ///
    /// Ends every scene. Called whenever the level of the client unloads, on a dimension change and on logout alike.
    ///
    public void end() {
        this.scenes.forEach(Scene::end);
    }
}
