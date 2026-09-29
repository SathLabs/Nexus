package dev.satherov.nexus.api.mod;

import lombok.Getter;

import dev.satherov.nexus.api.text.Translations;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

///
/// A mod as Nexus sees it, holding the mod's id, event bus, and container.
///
/// Should be created inside the mod's constructor and then kept as a static field inside the mod's main class.
///
public final class NexusMod {
    
    ///
    /// The ids of every mod that already has a handle.
    ///
    private static final Set<String> TAKEN = new HashSet<>();
    
    ///
    /// The mod id.
    ///
    @Getter
    private final String modId;
    
    ///
    /// The mod's own event bus.
    ///
    @Getter
    private final IEventBus eventBus;
    
    ///
    /// The mod container.
    ///
    @Getter
    private final ModContainer container;
    
    ///
    /// The mod's translation table, created on first call.
    ///
    @Getter(lazy = true)
    private final Translations translations = new Translations(this.modId);
    
    ///
    /// Creates the handle of the mod the given container belongs to.
    ///
    private NexusMod(ModContainer container) {
        this.modId = container.getModId();
        this.eventBus = Objects.requireNonNull(container.getEventBus(), "Mod '" + container.getModId() + "' has no event bus");
        this.container = container;
    }
    
    ///
    /// Creates the handle of the mod the given container belongs to.
    ///
    /// @param container The mod container.
    /// @return The handle.
    /// @throws IllegalStateException If that mod already has a handle.
    ///
    public static NexusMod of(ModContainer container) {
        if (!NexusMod.TAKEN.add(container.getModId())) {
            throw new IllegalStateException("Mod '" + container.getModId() + "' already has a handle");
        }
        
        return new NexusMod(container);
    }
    
    ///
    /// Creates a new identifier under this mod's namespace and with the given path.
    ///
    /// @param path The path of the identifier.
    ///
    /// @return The created identifier.
    ///
    public Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(this.modId, path);
    }
    
    ///
    /// Creates a new resource key for the given registry and
    /// an identifier created with this mod's namespace and the given path.
    ///
    /// @param path The path of the identifier.
    ///
    /// @return The created resource key.
    ///
    public <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registryKey, String path) {
        return ResourceKey.create(registryKey, this.id(path));
    }
}
