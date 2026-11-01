package dev.satherov.nexus.internal.render.debug;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.render.shape.Scene;
import dev.satherov.nexus.internal.render.shape.Scenes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryNoop;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

///
/// The debug renderers of every mod, each with its scene and the debug entry that switches it on.
///
@ApiStatus.Internal
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DebugRenderers {
    
    ///
    /// The one instance.
    ///
    public static final DebugRenderers INSTANCE = new DebugRenderers();
    
    ///
    /// The scene and renderer of every debug renderer, by the identifier of its debug entry.
    ///
    private final Map<Identifier, DebugScene> sceneById = new ConcurrentHashMap<>();
    
    ///
    /// If the debug entries were registered already.
    ///
    private boolean entriesRegistered;
    
    ///
    /// Adds a renderer under the given identifier.
    ///
    /// @param mod      The mod that owns the renderer.
    /// @param id       The identifier of the renderer and its debug entry.
    /// @param renderer The renderer, called with its scene.
    ///
    /// @throws IllegalStateException If the identifier is taken or if the debug entries were registered already.
    ///
    public void add(NexusMod mod, Identifier id, ClientOnly<Consumer<Scene>> renderer) {
        if (this.entriesRegistered) {
            throw new IllegalStateException("Could not register the debug renderer '" + id + "', the debug entries were already registered");
        }
        
        Scene scene = new Scene(mod);
        if (this.sceneById.putIfAbsent(id, new DebugScene(scene, renderer.get())) != null) {
            throw new IllegalStateException("The debug renderer '" + id + "' is registered twice");
        }
        
        Scenes.INSTANCE.add(scene);
    }
    
    ///
    /// Registers a debug entry per renderer with the given registrar.
    ///
    /// @param registrar The registrar, called with the identifier and the debug entry of every renderer.
    ///
    public void registerEntries(BiConsumer<Identifier, DebugScreenEntry> registrar) {
        this.sceneById.keySet().forEach(id -> registrar.accept(id, new DebugEntryNoop()));
        this.entriesRegistered = true;
    }
    
    ///
    /// Refills the scene of every enabled renderer and clears the scene of every renderer that was switched off.
    ///
    /// Does nothing while the client is in no level.
    ///
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        
        this.sceneById.forEach((id, debug) -> debug.tick(minecraft.debugEntries.isCurrentlyEnabled(id)));
    }
    
    ///
    /// The scene of one debug renderer and the renderer that fills it.
    ///
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class DebugScene {
        
        ///
        /// The scene of the renderer.
        ///
        private final Scene scene;
        
        ///
        /// The renderer that fills the scene.
        ///
        private final Consumer<Scene> renderer;
        
        ///
        /// If the scene was filled on the last tick.
        ///
        private boolean filled;
        
        ///
        /// Refills the scene if the renderer is enabled, or clears it once after the renderer was switched off.
        ///
        /// @param enabled If the debug entry of the renderer is enabled.
        ///
        private void tick(boolean enabled) {
            if (enabled || this.filled) {
                this.scene.clear();
            }
            
            if (enabled) {
                this.renderer.accept(this.scene);
            }
            
            this.filled = enabled;
        }
    }
}
