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
/// The glows of every mod: looks up the colors of the entity glow modifier, forces the outline chain on while any glow exists,
/// submits the glowing blocks, ticks the glows through one ticker on Nexus's client scope started on login,
/// and ends them whenever the level of the client unloads.
///
@ApiStatus.Internal
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GlowOutlines {
    
    ///
    /// The glows.
    ///
    public static final GlowOutlines INSTANCE = new GlowOutlines();
    
    ///
    /// The glows of every mod, in the order they were registered.
    ///
    private final List<Glows> glows = new CopyOnWriteArrayList<>();
    
    ///
    /// The ticker of the glows on the client scope of the current session, or `null` if none was started yet.
    ///
    private @Nullable Ticker ticker;
    
    ///
    /// Registers a mod's glows.
    ///
    /// @param glows The glows of a mod.
    ///
    public void add(Glows glows) {
        this.glows.add(glows);
    }
    
    ///
    /// The color the first mod that glows the entity gave it.
    ///
    /// @param entity The entity to look up.
    ///
    /// @return The ARGB color of the outline, or `0` if no mod glows the entity.
    ///
    public int colorOf(Entity entity) {
        for (Glows glows : this.glows) {
            // Mods without a glow are skipped, or an entity drawn while the client is in no level would throw.
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
    /// @return `true` if any mod has any glow.
    ///
    public boolean hasAny() {
        return this.glows.stream().anyMatch(Glows::hasAny);
    }
    
    ///
    /// Submits every mod's glowing blocks.
    ///
    /// @param pose      The pose stack of the frame, whose origin is the camera.
    /// @param collector The collector of the frame that the models are submitted to.
    /// @param camera    The position of the camera.
    ///
    public void submitBlocks(PoseStack pose, SubmitNodeCollector collector, Vec3 camera) {
        for (Glows glows : this.glows) {
            glows.submitBlocks(pose, collector, camera);
        }
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
        
        this.ticker = Nexus.getMod().getScheduler().client().ticker("glows", _ -> {
            this.glows.forEach(Glows::tick);
            return Next.TICK;
        });
    }
    
    ///
    /// Ends every mod's glows. Called whenever the level of the client unloads, on a dimension change and on logout alike.
    ///
    public void end() {
        this.glows.forEach(Glows::end);
    }
}
