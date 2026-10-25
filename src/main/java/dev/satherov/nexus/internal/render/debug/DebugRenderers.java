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
/// The mods' debug renderers: a debug entry per renderer for the toggle, a scene per renderer, and the per-tick call while enabled.
///
@ApiStatus.Internal
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DebugRenderers {
    
    ///
    /// The renderers.
    ///
    public static final DebugRenderers INSTANCE = new DebugRenderers();
    
    ///
    /// The scene and the renderer of every debug renderer, by the identifier of its debug entry.
    ///
    private final Map<Identifier, DebugScene> sceneById = new ConcurrentHashMap<>();
    
    ///
    /// If the debug entries were registered.
    ///
    private boolean entriesRegistered;
    
    ///
    /// Adds a renderer under the id.
    ///
    /// @param mod      The handle of the mod that owns the renderer.
    /// @param id       The identifier of the renderer and its debug entry.
    /// @param renderer The renderer, called with the scene of the debug renderer.
    ///
    /// @throws IllegalStateException If the id is taken or if the debug entries were already registered.
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
    /// Registers the debug entries. Called from the registration event.
    ///
    /// @param registrar The registrar of the event, called with the identifier and the debug entry of every renderer.
    ///
    public void registerEntries(BiConsumer<Identifier, DebugScreenEntry> registrar) {
        this.sceneById.keySet().forEach(id -> registrar.accept(id, new DebugEntryNoop()));
        this.entriesRegistered = true;
    }
    
    ///
    /// Clears and refills the scene of every enabled renderer, and clears the scene of every renderer switched off since the last tick.
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
        /// The renderer of a mod.
        ///
        private final Consumer<Scene> renderer;
        
        ///
        /// If the renderer filled the scene on the last tick.
        ///
        private boolean filled;
        
        ///
        /// Clears the scene and refills it if the renderer is enabled, or clears it once after it was switched off.
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
