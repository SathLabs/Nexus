package dev.satherov.nexus.api.persistence;

import lombok.Getter;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.persistence.archive.ArchiveBuilder;
import dev.satherov.nexus.api.persistence.archive.ArchiveKey;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.api.persistence.saved.SavedValueBuilder;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

///
/// Starts the builders of the saved values and the archives of one mod.
///
/// Every saved value and every archive should be declared inside the mod's constructor.
///
/// Can be accessed through [NexusMod#getPersistence()].
///
@EventBusSubscriber(modid = Nexus.MOD_ID)
public final class Persistence {
    
    ///
    /// If a server is about to start or already started.
    ///
    private static boolean started;
    
    ///
    /// The handle of the mod.
    ///
    @Getter
    private final NexusMod mod;
    
    ///
    /// Every name that the mod already declared.
    ///
    private final Set<String> names = new HashSet<>();
    
    ///
    /// Should only ever be called from [NexusMod#getPersistence()].
    ///
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Persistence(NexusMod mod) {
        this.mod = mod;
    }
    
    ///
    /// Records that a server is about to start.
    ///
    @SubscribeEvent
    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Persistence.started = true;
    }
    
    ///
    /// Starts a new builder of a value that is saved once per server.
    ///
    /// The value will be saved in `data/<modid>/<name>.dat` of the data storage of the server.
    /// The format can use the registry access of the server.
    ///
    /// @param name    The name of the value.
    /// @param format  The format the value is saved in.
    /// @param initial The supplier of the initial value, called if the server doesn't have a saved one.
    ///
    /// @return The builder of the value.
    ///
    public <VALUE> SavedValueBuilder<MinecraftServer, VALUE> serverValue(String name, Format<VALUE> format, Supplier<VALUE> initial) {
        return SavedValueBuilder.server(this, name, format, initial);
    }
    
    ///
    /// Starts a new builder of a value that is saved once per level.
    ///
    /// The value will be saved in `data/<modid>/<name>.dat` of the data storage of each level.
    /// The format can use the registry access of the server.
    ///
    /// @param name    The name of the value.
    /// @param format  The format the value is saved in.
    /// @param initial The supplier of the initial value, called if a level doesn't have a saved one.
    ///
    /// @return The builder of the value.
    ///
    public <VALUE> SavedValueBuilder<ServerLevel, VALUE> levelValue(String name, Format<VALUE> format, Supplier<VALUE> initial) {
        return SavedValueBuilder.level(this, name, format, initial);
    }

    ///
    /// Starts a new builder of an archive that saves one file per key with each server.
    ///
    /// The files will be saved in `data/<modid>/<name>/` of the world folder.
    /// The format can use the registry access of the server.
    ///
    /// @param name   The name of the archive.
    /// @param keys   The parser of the keys, called with the segments of every file of the archive.
    /// @param format The format the values are saved in.
    ///
    /// @return The builder of the archive.
    ///
    public <KEY extends ArchiveKey<KEY>, VALUE> ArchiveBuilder<KEY, VALUE> archive(String name, ArchiveKey.Parser<KEY> keys, Format<VALUE> format) {
        return new ArchiveBuilder<>(this, name, keys, format);
    }

    ///
    /// Declares the given name for the mod and creates its identifier.
    ///
    /// Should only ever be called from [SavedValueBuilder#register()] or [ArchiveBuilder#register()].
    ///
    /// @param name The name to declare.
    ///
    /// @return The identifier `<modid>:<name>`.
    ///
    /// @throws IllegalStateException If a server already started, or if the mod already declared the name.
    ///
    @ApiStatus.Internal
    public Identifier declare(String name) {
        Identifier id = this.mod.id(name);
        if (Persistence.started) {
            throw new IllegalStateException("Could not declare '" + id + "' because a server already started");
        }
        
        if (!this.names.add(name)) {
            throw new IllegalStateException("'" + id + "' is already declared");
        }
        
        return id;
    }
}
