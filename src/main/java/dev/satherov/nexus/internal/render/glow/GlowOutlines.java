package dev.satherov.nexus.internal.render.glow;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.glow.Glows;
import dev.satherov.nexus.api.tick.Ticker;
import dev.satherov.nexus.api.tick.ticker.Next;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

///
/// The glows of every mod, ticked through one ticker on the client scope of Nexus.
///
@ApiStatus.Internal
public enum GlowOutlines {
    INSTANCE;
    
    ///
    /// The glows of every mod.
    ///
    private final List<Glows> glows = new CopyOnWriteArrayList<>();
    
    ///
    /// The ticker of the glows, or `null` if none was started yet.
    ///
    private @Nullable Ticker ticker;
    
    ///
    /// Adds the glows of a mod.
    ///
    /// @param glows The glows of the mod.
    ///
    public void add(Glows glows) {
        this.glows.add(glows);
    }
    
    ///
    /// Gets the glow color of the given entity from the first mod that glows it.
    ///
    /// @param entity The entity.
    ///
    /// @return The ARGB color of the outline, or `0` if no mod glows the entity.
    ///
    public int colorOf(Entity entity) {
        for (Glows glows : this.glows) {
            // Mods without a glow have to be skipped, or an entity drawn while the client is in no level would throw.
            if (!glows.hasAny()) {
                continue;
            }
            
            int color = glows.colorOf(entity);
            if (color != 0) {
                return color;
            }
        }
        
        return 0;
    }
    
    ///
    /// @return `true` if any mod has a glow.
    ///
    public boolean hasAny() {
        return this.glows.stream().anyMatch(Glows::hasAny);
    }
    
    ///
    /// Submits the glowing blocks of every mod.
    ///
    /// @param pose      The pose stack of the frame, whose origin is the camera.
    /// @param collector The collector of the frame.
    /// @param camera    The position of the camera.
    ///
    public void submitBlocks(PoseStack pose, SubmitNodeCollector collector, Vec3 camera) {
        for (Glows glows : this.glows) {
            glows.submitBlocks(pose, collector, camera);
        }
    }
    
    ///
    /// Starts the ticker of the glows, unless one is still active.
    ///
    public void start() {
        if (this.ticker != null && this.ticker.isActive()) {
            return;
        }
        
        this.ticker = Nexus.getMod().getScheduler().client().ticker("glows", _ -> {
            this.glows.forEach(Glows::tick);
            return Next.TICK;
        });
    }
    
    ///
    /// Ends the glows of every mod.
    ///
    public void end() {
        this.glows.forEach(Glows::end);
    }
}
