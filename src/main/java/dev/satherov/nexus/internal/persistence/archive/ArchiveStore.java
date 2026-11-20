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

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

///
/// A store that keeps the entries of one archive in one folder for one server.
///
/// The keys of the files are kept in memory, and the files are written on an IO thread of the store.
///
/// The keys and the files of the entries are only touched while holding the lock of this store.
/// The IO thread checks the queued writes and writes a temporary file without holding the lock, so the queued writes are kept in a concurrent map.
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
    /// The maximum number of bytes in the name of a file or folder on the common file systems.
    ///
    private static final int MAX_NAME_LENGTH = 255;

    ///
    /// The number of times a temporary file is moved into place before the write fails.
    ///
    private static final int MOVE_ATTEMPTS = 10;

    ///
    /// The number of seconds the IO thread waits for a new operation before it ends.
    ///
    private static final long IDLE_SECONDS = 1L;

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
    private final Map<List<String>, Queued> queuedBySegments = new ConcurrentHashMap<>();

    ///
    /// The number of queued operations of a key that haven't finished yet, by its segments.
    ///
    private final Map<List<String>, Integer> pendingBySegments = new HashMap<>();

    ///
    /// The IO thread, which runs the queued write-actions, retires, and deletes in order.
    ///
    /// The thread isn't a daemon, so the JVM doesn't exit before the queued operations are done.
    /// The thread ends while it has nothing to do, and a new one is started for the next operation.
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
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                ArchiveStore.IDLE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                Thread.ofPlatform()
                        .name("Nexus Archive " + root.getFileName())
                        .daemon(false)
                        .factory()
        );

        executor.allowCoreThreadTimeOut(true);
        this.executor = executor;
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
    /// Encodes the given value on the caller, queues its write under the given key, and makes the key active.
    ///
    /// @param key   The key of the entry.
    /// @param value The value to write.
    ///
    /// @throws IllegalArgumentException If the key has no segments, if one of its segments is empty or too long,
    /// or if the parser doesn't give back a key with the same segments.
    /// @throws NexusCodecException      If the value could not be encoded.
    ///
    public void write(KEY key, VALUE value) {
        List<String> segments = this.checkedSegments(key);
        Queued queued = new Queued(this.encode(value));
        synchronized (this) {
            this.queuedBySegments.put(segments, queued);
            this.queue(segments, () -> this.writeQueued(key, segments, queued));
            this.activate(key, segments);
        }
    }

    ///
    /// Writes the tag of the given queued write into the file of its key on the IO thread and then runs the retention of the group of the key.
    ///
    /// Does nothing if a later write or a deletion of the key replaced the queued write.
    ///
    /// If the file could not be written, the failure will be logged with the key and the key will be listed the way its files say.
    ///
    /// @param key      The key of the entry.
    /// @param segments The segments of the key.
    /// @param queued   The queued write.
    ///
    private void writeQueued(KEY key, List<String> segments, Queued queued) {
        if (this.queuedBySegments.get(segments) != queued) {
            return;
        }

        try {
            Path temporary = this.writeTemporary(segments, queued.tag);
            synchronized (this) {
                // The key could have been written again or deleted while the temporary file was written.
                if (this.queuedBySegments.get(segments) != queued) {
                    Files.deleteIfExists(temporary);
                    return;
                }

                this.moveIntoPlace(key, segments, temporary);
                this.queuedBySegments.remove(segments);
                this.retainWritten(key);
            }
        } catch (IOException | RuntimeException failure) {
            ArchiveStore.log.error("Could not write the entry '{}' of the archive in '{}'", key, this.folder.root(), failure);
            synchronized (this) {
                this.queuedBySegments.remove(segments, queued);
                this.reconcile(key, segments);
            }
        }
    }

    ///
    /// Waits until every queued operation of the given key is done, writes the given value on the caller,
    /// forces the folders of its file onto the disk, and then runs the retention of the group of the key.
    ///
    /// @param key   The key of the entry.
    /// @param value The value to write.
    ///
    /// @throws IllegalArgumentException If the key has no segments, if one of its segments is empty or too long,
    /// or if the parser doesn't give back a key with the same segments.
    /// @throws NexusCodecException      If the value could not be encoded.
    /// @throws UncheckedIOException     If the file could not be written.
    ///
    public void writeNow(KEY key, VALUE value) {
        List<String> segments = this.checkedSegments(key);
        CompoundTag tag = this.encode(value);
        synchronized (this) {
            this.awaitUntil(() -> !this.pendingBySegments.containsKey(segments));
            try {
                this.moveIntoPlace(key, segments, this.writeTemporary(segments, tag));
            } catch (IOException failure) {
                throw new UncheckedIOException("Could not write the entry '" + key + "' of the archive in '" + this.folder.root() + "'", failure);
            }

            this.syncFolders(segments);
            this.activate(key, segments);
            this.retainWritten(key);
        }
    }

    ///
    /// Forces the folders of the active file of the given segments onto the disk, up to the root of the archive,
    /// so that a file moved into place is still there after a power loss.
    ///
    /// A folder that can't be opened is left to the file system.
    ///
    /// @param segments The segments of the key.
    ///
    private void syncFolders(List<String> segments) {
        Path parent = this.folder.file(segments, ArchiveFolder.State.ACTIVE).getParent();
        while (parent != null && parent.startsWith(this.folder.root())) {
            try (FileChannel channel = FileChannel.open(parent, StandardOpenOption.READ)) {
                channel.force(true);
            } catch (IOException _) {
                // Windows can't open a folder as a channel.
            }

            parent = parent.getParent();
        }
    }

    ///
    /// Makes the given key active under the given segments.
    ///
    /// @param key      The key of the entry.
    /// @param segments The segments of the key.
    ///
    private void activate(KEY key, List<String> segments) {
        this.retiredBySegments.remove(segments);
        this.activeBySegments.put(segments, key);
    }

    ///
    /// Runs the retention of the group of the given key after a value of it was written.
    ///
    /// If the retention drops the given key itself, a warning will be logged with the key.
    ///
    /// @param key The key that was written.
    ///
    private void retainWritten(KEY key) {
        if (this.retain(key.group()::equals).contains(key)) {
            ArchiveStore.log.warn("The retention of the archive in '{}' dropped the entry '{}' right after it was written", this.folder.root(), key);
        }
    }

    ///
    /// Gets the segments of the given key after checking that the key can be stored.
    ///
    /// @param key The key to check.
    ///
    /// @return The segments of the key.
    ///
    /// @throws IllegalArgumentException If the key has no segments, if one of its segments is empty or too long,
    /// or if the parser doesn't give back a key with the same segments.
    ///
    private @Unmodifiable List<String> checkedSegments(KEY key) {
        List<String> segments = List.copyOf(key.segments());
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("The key '" + key + "' has no segments");
        }

        if (segments.contains("")) {
            throw new IllegalArgumentException("The key '" + key + "' has an empty segment");
        }

        // The retired file has the longest name of the files of a key, and an escaped name is ASCII, so its length is its number of bytes.
        for (Path name : this.folder.root().relativize(this.folder.file(segments, ArchiveFolder.State.RETIRED))) {
            if (name.toString().length() > ArchiveStore.MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("The key '" + key + "' has a segment that is too long for the file name '" + name + "'");
            }
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
    /// @throws NexusCodecException If the value could not be encoded.
    ///
    private CompoundTag encode(VALUE value) {
        return (CompoundTag) this.declaration.format().write(value, this.registries);
    }

    ///
    /// Writes the given tag into the temporary file of the given segments, creating all missing folders.
    ///
    /// @param segments The segments of the key.
    /// @param tag      The versioned form of the value.
    ///
    /// @return The temporary file.
    ///
    /// @throws IOException If the file could not be written.
    ///
    private Path writeTemporary(List<String> segments, CompoundTag tag) throws IOException {
        Path file = this.folder.file(segments, ArchiveFolder.State.ACTIVE);
        // No escaped segment starts with '.', so the temporary file can't be the file of a key.
        Path temporary = file.resolveSibling("." + file.getFileName());
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(tag, temporary);
        return temporary;
    }

    ///
    /// Moves the given temporary file to the active file of the given segments and then deletes their retired file.
    ///
    /// The move will be tried up to [#MOVE_ATTEMPTS] times, since on Windows another process can hold the active file open for a moment.
    /// If the retired file could not be deleted, the failure will be logged with the key and the entry will still be active after the next start.
    ///
    /// @param key       The key of the entry, used in the failure message.
    /// @param segments  The segments of the key.
    /// @param temporary The temporary file.
    ///
    /// @throws IOException If the temporary file could not be moved.
    ///
    private void moveIntoPlace(KEY key, List<String> segments, Path temporary) throws IOException {
        Path file = this.folder.file(segments, ArchiveFolder.State.ACTIVE);
        for (int attempt = 1; ; attempt++) {
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                break;
            } catch (IOException failure) {
                if (attempt == ArchiveStore.MOVE_ATTEMPTS) {
                    throw failure;
                }
            }
        }

        try {
            Files.deleteIfExists(this.folder.file(segments, ArchiveFolder.State.RETIRED));
        } catch (IOException failure) {
            ArchiveStore.log.warn("Could not delete the retired file of the entry '{}' of the archive in '{}'", key, this.folder.root(), failure);
        }
    }

    ///
    /// Decodes the value of the given key from its queued write-action or its file.
    ///
    /// @param key The key of the entry.
    ///
    /// @return The value, or empty if the key has no entry.
    ///
    /// @throws NexusCodecException If the queued write-action or the file could not be decoded.
    ///
    public synchronized Optional<VALUE> read(KEY key) {
        List<String> segments = key.segments();
        boolean retired = this.retiredBySegments.containsKey(segments);
        if (!retired && !this.activeBySegments.containsKey(segments)) {
            return Optional.empty();
        }

        Queued queued = this.queuedBySegments.get(segments);
        if (queued != null) {
            return Optional.of(this.declaration.format().read(queued.tag, this.registries));
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
    public synchronized @Unmodifiable List<KEY> list(Predicate<? super KEY> filter) {
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
    public synchronized @Unmodifiable List<KEY> listRetired(Predicate<? super KEY> filter) {
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
    /// @return The keys that were dropped.
    ///
    @CanIgnoreReturnValue
    private List<KEY> retain(Predicate<Object> groups) {
        List<KEY> dropped = Stream.concat(
                ArchiveStore.drops(this.counted(this.activeBySegments), this.declaration.active(), groups),
                ArchiveStore.drops(this.counted(this.retiredBySegments), this.declaration.retired(), groups)
        ).toList();

        dropped.forEach(this::delete);
        return dropped;
    }

    ///
    /// Gets the keys of the given map that the retention counts.
    ///
    /// A key with a queued write isn't counted, so a write that fails can't push out another entry.
    ///
    /// @param keysBySegments The keys by their segments.
    ///
    /// @return The keys without a queued write.
    ///
    private List<KEY> counted(Map<List<String>, KEY> keysBySegments) {
        return keysBySegments.entrySet().stream()
                .filter(entry -> !this.queuedBySegments.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
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
        this.queue(segments, () -> this.retireQueued(existing, segments));
    }

    ///
    /// Moves the active file of the given segments to their retired file on the IO thread and then lists the key the way its files say.
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

        this.reconcile(key, segments);
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
        this.queue(segments, () -> this.deleteQueued(key, segments));
    }

    ///
    /// Deletes the active and the retired file of the given segments on the IO thread and then lists the key the way its files say.
    ///
    /// If a file could not be deleted, the failure will be logged with the key and the other file will still be deleted.
    ///
    /// @param key      The key of the entry, used in the failure message.
    /// @param segments The segments of the key.
    ///
    private synchronized void deleteQueued(KEY key, List<String> segments) {
        for (ArchiveFolder.State state : ArchiveFolder.State.values()) {
            try {
                Files.deleteIfExists(this.folder.file(segments, state));
            } catch (IOException failure) {
                ArchiveStore.log.error("Could not delete the entry '{}' of the archive in '{}'", key, this.folder.root(), failure);
            }
        }

        this.reconcile(key, segments);
    }

    ///
    /// Queues the given operation on the IO thread and counts it under the given segments until it is done.
    ///
    /// @param segments  The segments of the key that the operation is about.
    /// @param operation The operation to run.
    ///
    private void queue(List<String> segments, Runnable operation) {
        this.pendingBySegments.merge(segments, 1, Integer::sum);
        this.executor.execute(() -> {
            try {
                operation.run();
            } finally {
                synchronized (this) {
                    this.pendingBySegments.computeIfPresent(segments, (_, count) -> count > 1 ? count - 1 : null);
                    this.notifyAll();
                }
            }
        });
    }

    ///
    /// Lists the given key the way its files say once an operation of it ran, unless a later operation of it is still queued.
    ///
    /// The key will be active if it has an active file, retired if it only has a retired file, and gone otherwise, the same as after the next start.
    ///
    /// @param key      The key of the entry.
    /// @param segments The segments of the key.
    ///
    private void reconcile(KEY key, List<String> segments) {
        // The operation that calls this is still counted until it returns.
        if (this.pendingBySegments.getOrDefault(segments, 0) > 1) {
            return;
        }

        this.activeBySegments.remove(segments);
        this.retiredBySegments.remove(segments);
        if (Files.isRegularFile(this.folder.file(segments, ArchiveFolder.State.ACTIVE))) {
            this.activeBySegments.put(segments, key);
        } else if (Files.isRegularFile(this.folder.file(segments, ArchiveFolder.State.RETIRED))) {
            this.retiredBySegments.put(segments, key);
        }
    }

    ///
    /// Waits until every queued operation is done and then ends the IO thread.
    ///
    @Override
    public synchronized void close() {
        this.awaitUntil(this.pendingBySegments::isEmpty);
        this.executor.shutdown();
    }

    ///
    /// Waits until the given condition is `true`, releasing the lock in the meantime.
    ///
    /// An interrupt doesn't stop the wait and will be set on the thread again once the condition is `true`.
    ///
    /// @param condition The condition to wait for.
    ///
    private void awaitUntil(BooleanSupplier condition) {
        boolean interrupted = false;
        while (!condition.getAsBoolean()) {
            try {
                this.wait();
            } catch (InterruptedException _) {
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    ///
    /// Moves every file below the given legacy folder that the given reader accepts to the file of its key, and then writes the marker.
    ///
    /// The files in the folder of the archive are skipped, since the legacy folder can be the same as it or above it.
    /// If a file could not be moved, it will be left where it is and the failure will be logged.
    /// The number of moved files will be logged once the walk is done.
    ///
    /// @param legacy The legacy folder, absolute and normalized.
    /// @param reader The reader of one file, called with its path relative to the legacy folder.
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

            int imported = 0;
            for (Path file : files) {
                if (reader.apply(legacy.relativize(file)).orElse(null) instanceof Archive.Imported(KEY key, boolean retired)) {
                    Path target = this.folder.file(this.checkedSegments(key), retired ? ArchiveFolder.State.RETIRED : ArchiveFolder.State.ACTIVE);
                    try {
                        Files.createDirectories(target.getParent());
                        Files.move(file, target);
                        imported++;
                    } catch (IOException failure) {
                        ArchiveStore.log.warn("Could not import the file '{}' as the entry '{}' of the archive in '{}'", file, key, this.folder.root(), failure);
                    }
                }
            }

            ArchiveStore.log.info("Imported '{}' of the '{}' files in '{}' into the archive in '{}'", imported, files.size(), legacy, this.folder.root());
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
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Queued {

        ///
        /// The versioned form of the value to write.
        ///
        private final CompoundTag tag;
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
    /// @param reader The reader of one file, called with its path relative to the folder.
    /// @param <KEY>  The type of the keys.
    ///
    public record Legacy<KEY>(String folder, Function<Path, Optional<Archive.Imported<KEY>>> reader) { }
}
