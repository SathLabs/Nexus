package dev.satherov.nexus.internal.persistence.archive;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.persistence.archive.Archive;
import dev.satherov.nexus.api.persistence.archive.ArchiveKey;
import dev.satherov.nexus.api.persistence.archive.Retention;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.internal.codec.CodecErrors;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

///
/// A store that keeps the entries of one archive in one folder for one server.
///
/// The keys of the files are kept in memory, and the files are written on an IO thread of the store.
///
/// The keys and the files are only touched while holding the lock of this store.
/// The IO thread encodes a value without holding the lock, so the queued writes are kept in a concurrent map.
///
/// @param <KEY>   The type of the keys.
/// @param <VALUE> The type of the values.
///
/// @see Archive
///
@Slf4j
@ApiStatus.Internal
public final class ArchiveStore<KEY extends ArchiveKey<KEY>, VALUE> implements AutoCloseable {

    ///
    /// The name of the file that marks the import as done, which no key can produce since no escaped segment starts with `.`.
    ///
    private static final String MARKER = ".imported";

    ///
    /// The folder of the archive.
    ///
    private final ArchiveFolder folder;

    ///
    /// The registry lookup provider of the server.
    ///
    private final HolderLookup.Provider registries;

    ///
    /// The declaration of the archive.
    ///
    private final Declaration<KEY, VALUE> declaration;

    ///
    /// The key of every active entry by its segments.
    ///
    private final Map<List<String>, KEY> activeBySegments = new HashMap<>();

    ///
    /// The key of every retired entry by its segments.
    ///
    private final Map<List<String>, KEY> retiredBySegments = new HashMap<>();

    ///
    /// The last queued write-action of a key by its segments until that write-action finished.
    ///
    private final Map<List<String>, Queued<VALUE>> queuedBySegments = new ConcurrentHashMap<>();

    ///
    /// The IO thread, which runs the queued write-actions, retires, and deletes in order.
    ///
    private final ExecutorService executor;

    ///
    /// Opens the archive in the given folder, imports the older layout once, and then runs the retention of every group.
    ///
    /// @param folder      The folder of the archive.
    /// @param world       The world folder, which the legacy folder is relative to.
    /// @param registries  The registry lookup provider of the server.
    /// @param declaration The declaration of the archive.
    ///
    /// @throws UncheckedIOException If the folder of the archive or the legacy folder could not be walked,
    /// or if the marker could not be written.
    ///
    public ArchiveStore(Path folder, Path world, HolderLookup.Provider registries, Declaration<KEY, VALUE> declaration) {
        this.folder = new ArchiveFolder(folder.toAbsolutePath().normalize());
        this.registries = registries;
        this.declaration = declaration;

        Path root = this.folder.root();
        this.executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("Nexus Archive " + root.getFileName()).factory());
        try {
            Legacy<KEY> legacy = declaration.legacy();
            if (legacy != null && Files.notExists(root.resolve(ArchiveStore.MARKER))) {
                this.importLegacy(world.resolve(legacy.folder()).toAbsolutePath().normalize(), legacy.reader());
            }

            if (Files.isDirectory(root)) {
                this.index();
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not open the archive in '" + root + "'", failure);
        }

        this.prune();
    }

    ///
    /// Runs the given rule over each group of the given keys that passes the given filter.
    ///
    /// @param keys   The keys to run the rule over.
    /// @param rule   The rule to run.
    /// @param groups The filter of the groups.
    ///
    /// @return The keys that the rule drops.
    ///
    private static <KEY extends ArchiveKey<KEY>> Stream<KEY> drops(Collection<KEY> keys, Retention<KEY> rule, Predicate<Object> groups) {
        return keys.stream()
                .filter(key -> groups.test(key.group()))
                .collect(Collectors.groupingBy(ArchiveKey::group))
                .values()
                .stream()
                .flatMap(group -> rule.drop(group.stream().sorted().toList()).stream());
    }

    ///
    /// Queues the write of the given value under the given key, makes the key active, and then runs the retention of its group.
    ///
    /// @param key   The key of the entry.
    /// @param value The value to write.
    ///
    /// @throws IllegalArgumentException If the key has no segments, if one of its segments is empty,
    /// or if the parser doesn't give back a key with the same segments.
    ///
    public synchronized void write(KEY key, VALUE value) {
        List<String> segments = this.checkedSegments(key);
        Queued<VALUE> queued = new Queued<>(value);
        this.queuedBySegments.put(segments, queued);
        this.executor.execute(() -> this.writeQueued(key, segments, queued));
        this.activate(key, segments);
    }

    ///
    /// Encodes the value of the given queued write and writes it into its file on the IO thread.
    ///
    /// The queued write will be removed once it is done unless a later write of the key replaced it.
    /// If the file could not be written, the failure will be logged with the key.
    ///
    /// @param key      The key of the entry, used in the failure message.
    /// @param segments The segments of the key.
    /// @param queued   The queued write.
    ///
    private void writeQueued(KEY key, List<String> segments, Queued<VALUE> queued) {
        try {
            CompoundTag tag = this.encode(queued.value);
            synchronized (this) {
                this.writeFile(segments, tag);
            }
        } catch (IOException | RuntimeException failure) {
            ArchiveStore.log.error("Could not write the entry '{}' of the archive in '{}'", key, this.folder.root(), failure);
        } finally {
            this.queuedBySegments.remove(segments, queued);
        }
    }

    ///
    /// Waits until every queued operation is done, writes the given value on the caller, and then runs the retention of the group of the key.
    ///
    /// @param key   The key of the entry.
    /// @param value The value to write.
    ///
    /// @throws IllegalArgumentException If the key has no segments, if one of its segments is empty,
    /// or if the parser doesn't give back a key with the same segments.
    /// @throws UncheckedIOException     If the file could not be written.
    ///
    public void writeNow(KEY key, VALUE value) {
        List<String> segments = this.checkedSegments(key);
        // Kinda stupid, but the best way to just wait until every other queued action is done.
        CompletableFuture.runAsync(() -> { }, this.executor).join();
        synchronized (this) {
            try {
                this.writeFile(segments, this.encode(value));
            } catch (IOException failure) {
                throw new UncheckedIOException("Could not write the entry '" + key + "' of the archive in '" + this.folder.root() + "'", failure);
            }

            this.activate(key, segments);
        }
    }

    ///
    /// Makes the given key active under the given segments and then runs the retention of its group.
    ///
    /// @param key      The key of the entry.
    /// @param segments The segments of the key.
    ///
    private void activate(KEY key, List<String> segments) {
        this.retiredBySegments.remove(segments);
        this.activeBySegments.put(segments, key);
        this.retain(key.group()::equals);
    }

    ///
    /// Gets the segments of the given key after checking that the key can be stored.
    ///
    /// @param key The key to check.
    ///
    /// @return The segments of the key.
    ///
    /// @throws IllegalArgumentException If the key has no segments, if one of its segments is empty,
    /// or if the parser doesn't give back a key with the same segments.
    ///
    private List<String> checkedSegments(KEY key) {
        List<String> segments = List.copyOf(key.segments());
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("The key '" + key + "' has no segments");
        }

        if (segments.contains("")) {
            throw new IllegalArgumentException("The key '" + key + "' has an empty segment");
        }

        if (this.parse(segments).isEmpty()) {
            throw new IllegalArgumentException("The key '" + key + "' could not be parsed back from its segments");
        }

        return segments;
    }

    ///
    /// Parses a key from the given segments with the parser of the archive.
    ///
    /// @param segments The unescaped segments.
    ///
    /// @return The key, or empty if the parser refuses the segments or gives back a key with other segments.
    ///
    private Optional<KEY> parse(List<String> segments) {
        return this.declaration.keys().parse(segments).filter(key -> key.segments().equals(segments));
    }

    ///
    /// Encodes the given value into its versioned form.
    ///
    /// @param value The value to encode.
    ///
    /// @return The versioned form of the value.
    ///
    private CompoundTag encode(VALUE value) {
        return (CompoundTag) this.declaration.format().write(value, this.registries);
    }

    ///
    /// Writes the given tag into the active file of the given segments and deletes their retired file.
    ///
    /// @param segments The segments of the key.
    /// @param tag      The versioned form of the value.
    ///
    /// @throws IOException If the file could not be written.
    ///
    private void writeFile(List<String> segments, CompoundTag tag) throws IOException {
        Path file = this.folder.file(segments, ArchiveFolder.State.ACTIVE);
        // No escaped segment starts with '.', so the temporary file can't be the file of a key.
        Path temporary = file.resolveSibling("." + file.getFileName());
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(tag, temporary);
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        Files.deleteIfExists(this.folder.file(segments, ArchiveFolder.State.RETIRED));
    }

    ///
    /// Reads the value of the given key from its queued write-action or its file.
    ///
    /// @param key The key of the entry.
    ///
    /// @return The value, or empty if the key has no entry.
    ///
    /// @throws NexusCodecException If the file could not be decoded.
    ///
    public synchronized Optional<VALUE> read(KEY key) {
        List<String> segments = key.segments();
        boolean retired = this.retiredBySegments.containsKey(segments);
        if (!retired && !this.activeBySegments.containsKey(segments)) {
            return Optional.empty();
        }

        Queued<VALUE> queued = this.queuedBySegments.get(segments);
        if (queued != null) {
            return Optional.of(queued.value);
        }

        Path retiredFile = this.folder.file(segments, ArchiveFolder.State.RETIRED);
        // A retired entry is still in its active file while the retire-action is queued.
        Path file = retired && Files.exists(retiredFile) ? retiredFile : this.folder.file(segments, ArchiveFolder.State.ACTIVE);
        return Files.exists(file) ? Optional.of(this.decode(file)) : Optional.empty();
    }

    ///
    /// Reads the given file and decodes its value.
    ///
    /// @param file The file to read.
    ///
    /// @return The value of the file.
    ///
    /// @throws NexusCodecException If the file could not be read or decoded.
    ///
    private VALUE decode(Path file) {
        CompoundTag tag;
        try {
            tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException | ReportedNbtException failure) {
            throw CodecErrors.decodeFailure(file.toString(), CodecFormat.NBT.name(), new NexusCodecException("The file could not be read, " + failure));
        }

        try {
            return this.declaration.format().read(tag, this.registries);
        } catch (NexusCodecException failure) {
            throw CodecErrors.decodeFailure(file.toString(), CodecFormat.NBT.name(), failure);
        }
    }

    ///
    /// Gets the active keys that pass the given filter, in their order.
    ///
    /// @param filter The filter of the keys.
    ///
    /// @return The active keys that pass the filter.
    ///
    public synchronized List<KEY> list(Predicate<? super KEY> filter) {
        return this.activeBySegments.values().stream()
                .filter(filter)
                .sorted()
                .toList();
    }

    ///
    /// Gets the retired keys that pass the given filter, in their order.
    ///
    /// @param filter The filter of the keys.
    ///
    /// @return The retired keys that pass the filter.
    ///
    public synchronized List<KEY> listRetired(Predicate<? super KEY> filter) {
        return this.retiredBySegments.values().stream()
                .filter(filter)
                .sorted()
                .toList();
    }

    ///
    /// Runs the retention of every group.
    ///
    public synchronized void prune() {
        this.retain(_ -> true);
    }

    ///
    /// Runs the active retention over the active keys and the retired retention over the retired keys of the groups that pass the given filter,
    /// and then deletes what they drop.
    ///
    /// @param groups The filter of the groups.
    ///
    private void retain(Predicate<Object> groups) {
        List<KEY> dropped = Stream.concat(
                ArchiveStore.drops(this.activeBySegments.values(), this.declaration.active(), groups),
                ArchiveStore.drops(this.retiredBySegments.values(), this.declaration.retired(), groups)
        ).toList();

        dropped.forEach(this::delete);
    }

    ///
    /// @param key The key of the entry.
    ///
    /// @return `true` if the key has a retired entry.
    ///
    public synchronized boolean isRetired(KEY key) {
        return this.retiredBySegments.containsKey(key.segments());
    }

    ///
    /// Moves the active entry of the given key to the retired ones and queues the move of its file.
    ///
    /// Does nothing if the key has no active entry.
    ///
    /// @param key The key of the entry.
    ///
    public synchronized void retire(KEY key) {
        List<String> segments = List.copyOf(key.segments());
        KEY existing = this.activeBySegments.remove(segments);
        if (existing == null) {
            return;
        }

        this.retiredBySegments.put(segments, existing);
        this.executor.execute(() -> this.retireQueued(existing, segments));
    }

    ///
    /// Moves the active file of the given segments to their retired file on the IO thread.
    ///
    /// If the file could not be moved, the failure will be logged with the key.
    ///
    /// @param key      The key of the entry, used in the failure message.
    /// @param segments The segments of the key.
    ///
    private synchronized void retireQueued(KEY key, List<String> segments) {
        Path file = this.folder.file(segments, ArchiveFolder.State.ACTIVE);
        try {
            if (Files.exists(file)) {
                Files.move(file, this.folder.file(segments, ArchiveFolder.State.RETIRED), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            ArchiveStore.log.error("Could not retire the entry '{}' of the archive in '{}'", key, this.folder.root(), failure);
        }
    }

    ///
    /// Removes the entry of the given key, active or retired, and queues the deletion of its files.
    ///
    /// Does nothing if the key has no entry.
    ///
    /// @param key The key of the entry.
    ///
    public synchronized void delete(KEY key) {
        List<String> segments = List.copyOf(key.segments());
        // A key is never both active and retired, so the second removal only runs if the first found nothing.
        if (this.activeBySegments.remove(segments) == null && this.retiredBySegments.remove(segments) == null) {
            return;
        }

        this.queuedBySegments.remove(segments);
        this.executor.execute(() -> this.deleteQueued(key, segments));
    }

    ///
    /// Deletes the active and the retired file of the given segments on the IO thread.
    ///
    /// If a file could not be deleted, the failure will be logged with the key.
    ///
    /// @param key      The key of the entry, used in the failure message.
    /// @param segments The segments of the key.
    ///
    private synchronized void deleteQueued(KEY key, List<String> segments) {
        try {
            Files.deleteIfExists(this.folder.file(segments, ArchiveFolder.State.ACTIVE));
            Files.deleteIfExists(this.folder.file(segments, ArchiveFolder.State.RETIRED));
        } catch (IOException failure) {
            ArchiveStore.log.error("Could not delete the entry '{}' of the archive in '{}'", key, this.folder.root(), failure);
        }
    }

    ///
    /// Runs every queued operation and then ends the IO thread.
    ///
    @Override
    public void close() {
        this.executor.close();
    }

    ///
    /// Moves every file below the given legacy folder that the given reader accepts to the file of its key, and then writes the marker.
    ///
    /// The files in the folder of the archive are skipped, since the legacy folder can be the same as it or above it.
    /// If a file could not be moved, it will be left where it is and the failure will be logged.
    ///
    /// @param legacy The legacy folder, absolute and normalized.
    /// @param reader The reader of one file, called with its path.
    ///
    /// @throws IOException If the legacy folder could not be walked or the marker could not be written.
    ///
    private void importLegacy(Path legacy, Function<Path, Optional<Archive.Imported<KEY>>> reader) throws IOException {
        if (Files.isDirectory(legacy)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(legacy)) {
                files = walk.filter(Files::isRegularFile)
                        .filter(file -> !file.startsWith(this.folder.root()))
                        .toList();
            }

            for (Path file : files) {
                if (reader.apply(file).orElse(null) instanceof Archive.Imported(KEY key, boolean retired)) {
                    Path target = this.folder.file(this.checkedSegments(key), retired ? ArchiveFolder.State.RETIRED : ArchiveFolder.State.ACTIVE);
                    try {
                        Files.createDirectories(target.getParent());
                        Files.move(file, target);
                    } catch (IOException failure) {
                        ArchiveStore.log.warn("Could not import the file '{}' as the entry '{}' of the archive in '{}'", file, key, this.folder.root(), failure);
                    }
                }
            }
        }

        Files.createDirectories(this.folder.root());
        Files.write(this.folder.root().resolve(ArchiveStore.MARKER), new byte[0]);
    }

    ///
    /// Walks the folder of the archive and keeps the key of every file that the parser accepts.
    ///
    /// If a key has an active and a retired file, it will be kept as active.
    ///
    /// @throws IOException If the folder could not be walked.
    ///
    private void index() throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(this.folder.root())) {
            files = walk.filter(Files::isRegularFile).toList();
        }

        for (Path file : files) {
            this.indexFile(file);
        }

        this.retiredBySegments.keySet().removeAll(this.activeBySegments.keySet());
    }

    ///
    /// Keeps the key of the given file if all the following is true:
    /// - The name of the file ends with `.dat` or `.retired`.
    /// - Every name of its path below the folder of the archive unescapes to a segment.
    /// - The parser accepts the segments.
    ///
    /// @param file The file below the folder of the archive.
    ///
    private void indexFile(Path file) {
        Map<List<String>, KEY> entries = file.getFileName().toString().endsWith(ArchiveFolder.State.RETIRED.getSuffix()) ? this.retiredBySegments : this.activeBySegments;
        this.folder.segments(file).ifPresent(segments -> this.parse(segments).ifPresent(key -> entries.put(segments, key)));
    }

    ///
    /// A queued write of one value.
    ///
    /// Is compared by identity, so a finished write only removes itself from the queued writes and keeps a later write of an equal value.
    ///
    /// @param <VALUE> The type of the value.
    ///
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Queued<VALUE> {

        ///
        /// The value to write.
        ///
        private final VALUE value;
    }

    ///
    /// A declaration of one archive.
    ///
    /// @param id      The identifier `<modid>:<name>` of the archive.
    /// @param keys    The parser of the keys.
    /// @param format  The format the values are saved in.
    /// @param active  The retention of the active entries.
    /// @param retired The retention of the retired entries.
    /// @param legacy  The older layout to import once per world, or `null` if there is none.
    /// @param <KEY>   The type of the keys.
    /// @param <VALUE> The type of the values.
    ///
    public record Declaration<KEY extends ArchiveKey<KEY>, VALUE>(
            Identifier id,
            ArchiveKey.Parser<KEY> keys,
            Format<VALUE> format,
            Retention<KEY> active,
            Retention<KEY> retired,
            @Nullable Legacy<KEY> legacy
    ) { }

    ///
    /// An older layout of an archive.
    ///
    /// @param folder The folder of the older layout, relative to the world folder.
    /// @param reader The reader of one file, called with its path.
    /// @param <KEY>  The type of the keys.
    ///
    public record Legacy<KEY>(String folder, Function<Path, Optional<Archive.Imported<KEY>>> reader) { }
}
