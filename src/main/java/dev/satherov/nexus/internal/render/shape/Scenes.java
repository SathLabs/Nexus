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
/// The scenes of every mod, ticked by one ticker on the client scope.
///
@ApiStatus.Internal
public enum Scenes {
    INSTANCE;
    
    ///
    /// Every scene added so far.
    ///
    private final List<Scene> scenes = new CopyOnWriteArrayList<>();
    
    ///
    /// The ticker of the scenes, or `null` if none was started yet.
    ///
    private @Nullable Ticker ticker;
    
    ///
    /// Adds the given scene.
    ///
    /// @param scene The scene to add.
    ///
    public void add(Scene scene) {
        this.scenes.add(scene);
    }
    
    ///
    /// Starts the ticker on the client scope.
    ///
    /// Does nothing if the ticker is still active.
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
    /// Ends every scene.
    ///
    public void end() {
        this.scenes.forEach(Scene::end);
    }
}
