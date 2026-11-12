package dev.satherov.nexus.api.persistence.archive;

import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.internal.persistence.archive.ArchiveStore;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

///
/// A collection of entries that a mod saves with each server in one file per key.
///
/// The files will be saved in `data/<modid>/<name>/` of the world folder, and each holds the versioned form of its value as compressed NBT.
/// When the server starts, the folder will be walked once and the key of every file that the parser accepts will be kept in memory for listing.
///
/// An entry is either active or retired.
/// A retired entry can still be read but is only listed by [#listRetired(MinecraftServer, Predicate)].
///
/// Can be created through [ArchiveBuilder#register()].
///
/// @param <KEY>   The type of the keys.
/// @param <VALUE> The type of the values.
///
public final class Archive<KEY extends ArchiveKey<KEY>, VALUE> {

    ///
    /// The declaration of the archive.
    ///
    private final ArchiveStore.Declaration<KEY, VALUE> declaration;

    ///
    /// The store of every running server.
    ///
    private final Map<MinecraftServer, ArchiveStore<KEY, VALUE>> stores = new ConcurrentHashMap<>();

    ///
    /// Should only ever be called from [ArchiveBuilder#register()].
    ///
    /// @param declaration The declaration of the archive.
    ///
    Archive(ArchiveStore.Declaration<KEY, VALUE> declaration) {
        this.declaration = declaration;
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, this::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, this::onServerStopped);
    }

    ///
    /// Queues the given value to be written under the given key and then runs the retention of the group of the key.
    ///
    /// The queued writes of an archive run in order on an IO thread of their own, which finishes them once the server has stopped.
    /// If the file could not be written, the failure will be logged with the key.
    ///
    /// If the key already has an entry, it will be replaced and become active.
    ///
    /// @param server The server that holds the entry.
    /// @param key    The key of the entry.
    /// @param value  The value to write.
    ///
    /// @throws IllegalArgumentException If the key has no segments, if one of its segments is empty,
    /// or if the parser of the archive doesn't give back a key with the same segments.
    /// @throws IllegalStateException    If the archive isn't open on the server.
    ///
    public void write(MinecraftServer server, KEY key, VALUE value) {
        this.getStore(server).write(key, value);
    }

    ///
    /// Waits until the queued writes of the given key are done, writes the given value under it on the caller,
    /// and then runs the retention of the group of the key.
    ///
    /// If the key already has an entry, it will be replaced and become active.
    ///
    /// @param server The server that holds the entry.
    /// @param key    The key of the entry.
    /// @param value  The value to write.
    ///
    /// @throws IllegalArgumentException If the key has no segments, if one of its segments is empty,
    /// or if the parser of the archive doesn't give back a key with the same segments.
    /// @throws IllegalStateException    If the archive isn't open on the server.
    /// @throws NexusCodecException      If the value could not be encoded.
    /// @throws UncheckedIOException     If the file could not be written.
    ///
    public void writeNow(MinecraftServer server, KEY key, VALUE value) {
        this.getStore(server).writeNow(key, value);
    }

    ///
    /// Reads the value of the entry of the given key, active or retired.
    ///
    /// If a write of the key is still queued, its value will be returned.
    /// If the file of the entry could not be decoded, it will be left as it is.
    ///
    /// @param server The server that holds the entry.
    /// @param key    The key of the entry.
    ///
    /// @return The value, or empty if the key has no entry.
    ///
    /// @throws IllegalStateException If the archive isn't open on the server.
    /// @throws NexusCodecException   If the file of the entry could not be decoded.
    ///
    public Optional<VALUE> read(MinecraftServer server, KEY key) {
        return this.getStore(server).read(key);
    }

    ///
    /// Gets the keys of every active entry, in the order of the keys.
    ///
    /// @param server The server that holds the entries.
    ///
    /// @return The keys of the active entries.
    ///
    /// @throws IllegalStateException If the archive isn't open on the server.
    ///
    public List<KEY> list(MinecraftServer server) {
        return this.getStore(server).list(_ -> true);
    }

    ///
    /// Gets the keys of the active entries that pass the given filter, in the order of the keys.
    ///
    /// @param server The server that holds the entries.
    /// @param filter The filter of the keys.
    ///
    /// @return The keys of the active entries that pass the filter.
    ///
    /// @throws IllegalStateException If the archive isn't open on the server.
    ///
    public List<KEY> list(MinecraftServer server, Predicate<? super KEY> filter) {
        return this.getStore(server).list(filter);
    }

    ///
    /// Gets the keys of the retired entries that pass the given filter, in the order of the keys.
    ///
    /// @param server The server that holds the entries.
    /// @param filter The filter of the keys.
    ///
    /// @return The keys of the retired entries that pass the filter.
    ///
    /// @throws IllegalStateException If the archive isn't open on the server.
    ///
    public List<KEY> listRetired(MinecraftServer server, Predicate<? super KEY> filter) {
        return this.getStore(server).listRetired(filter);
    }

    ///
    /// Runs the retention of every group and deletes the entries that it drops.
    ///
    /// The retention also runs once when the server starts and over the group of a key after every write.
    ///
    /// @param server The server that holds the entries.
    ///
    /// @throws IllegalStateException If the archive isn't open on the server.
    ///
    public void prune(MinecraftServer server) {
        this.getStore(server).prune();
    }

    ///
    /// @param server The server that holds the entry.
    /// @param key    The key of the entry.
    ///
    /// @return `true` if the key has a retired entry.
    ///
    /// @throws IllegalStateException If the archive isn't open on the server.
    ///
    public boolean isRetired(MinecraftServer server, KEY key) {
        return this.getStore(server).isRetired(key);
    }

    ///
    /// Retires the active entry of the given key.
    ///
    /// The entry can still be read but is only listed by [#listRetired(MinecraftServer, Predicate)], and the retired retention runs over it.
    /// A later write of the key will make the entry active again.
    ///
    /// Does nothing if the key has no active entry.
    ///
    /// @param server The server that holds the entry.
    /// @param key    The key of the entry.
    ///
    /// @throws IllegalStateException If the archive isn't open on the server.
    ///
    public void retire(MinecraftServer server, KEY key) {
        this.getStore(server).retire(key);
    }

    ///
    /// Deletes the entry of the given key, active or retired.
    ///
    /// Does nothing if the key has no entry.
    ///
    /// @param server The server that holds the entry.
    /// @param key    The key of the entry.
    ///
    /// @throws IllegalStateException If the archive isn't open on the server.
    ///
    public void delete(MinecraftServer server, KEY key) {
        this.getStore(server).delete(key);
    }

    ///
    /// The store of the given server.
    ///
    /// @param server The server that holds the store.
    ///
    /// @return The store of the server.
    ///
    /// @throws IllegalStateException If the archive isn't open on the server.
    ///
    private ArchiveStore<KEY, VALUE> getStore(MinecraftServer server) {
        ArchiveStore<KEY, VALUE> store = this.stores.get(server);
        if (store == null) {
            throw new IllegalStateException("The archive '" + this.declaration.id() + "' is not open on the server");
        }

        return store;
    }

    ///
    /// Opens the store of the server that is about to start.
    ///
    /// @param event The event of the server that is about to start.
    ///
    private void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        Identifier id = this.declaration.id();
        Path folder = server.getWorldPath(LevelResource.DATA).resolve(id.getNamespace()).resolve(id.getPath());
        this.stores.put(server, new ArchiveStore<>(folder, server.getWorldPath(LevelResource.ROOT), server.registryAccess(), this.declaration));
    }

    ///
    /// Closes the store of the server that stopped and waits until its queued writes are done.
    ///
    /// @param event The event of the server that stopped.
    ///
    private void onServerStopped(ServerStoppedEvent event) {
        ArchiveStore<KEY, VALUE> store = this.stores.remove(event.getServer());
        if (store != null) {
            store.close();
        }
    }

    ///
    /// An entry that the reader of an older layout found in one file.
    ///
    /// @param key     The key the file will be moved to.
    /// @param retired If the entry should be retired.
    /// @param <KEY>   The type of the key.
    ///
    public record Imported<KEY>(KEY key, boolean retired) { }
}
