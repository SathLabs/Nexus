package dev.satherov.nexus.test.unit.internal.persistence.archive;

import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.vanilla.VanillaCodecs;
import dev.satherov.nexus.api.persistence.archive.Archive;
import dev.satherov.nexus.api.persistence.archive.ArchiveKey;
import dev.satherov.nexus.api.persistence.archive.NameKey;
import dev.satherov.nexus.api.persistence.archive.Retention;
import dev.satherov.nexus.api.persistence.archive.TimedKey;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.internal.persistence.archive.ArchiveStore;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

///
/// Checks the layout, escaping, retention, retiring, IO, and legacy import of an [ArchiveStore] in a temporary world folder.
///
public class ArchiveStoreTest {

    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final Format<CompoundTag> FORMAT = Format.of(VanillaCodecs.COMPOUND_TAG).upgradeFrom(0, UnaryOperator.identity());

    private static final String BACKUPS = "data/nexus/backups";
    private static final String BLUEPRINTS = "data/nexus/blueprints";
    private static final String LEGACY = "data/epitaphs";

    private static final Pattern STAMP = Pattern.compile("(\\d+)-([a-z]+)");
    private static final Pattern LEGACY_NAME = Pattern.compile("(\\d+)-([a-z]+)\\.dat(-old)?");

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");

    @Test
    public void laysOutATimedKeyAsOneFolderPerSegment(@TempDir Path world) throws IOException {
        BackupKey key = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            store.write(key, ArchiveStoreTest.value("death"));
        }

        Path file = world.resolve(ArchiveStoreTest.BACKUPS).resolve(ArchiveStoreTest.STEVE.toString()).resolve(key.time().getEpochSecond() + "-death.dat");
        Assertions.assertThat(ArchiveStoreTest.files(world.resolve(ArchiveStoreTest.BACKUPS))).containsExactly(file);
        Assertions.assertThat(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())).isEqualTo(ArchiveStoreTest.FORMAT.write(ArchiveStoreTest.value("death"), ArchiveStoreTest.REGISTRIES));
    }

    @Test
    public void laysOutANameKeyAsOneFile(@TempDir Path world) throws IOException {
        try (ArchiveStore<NameKey, CompoundTag> store = ArchiveStoreTest.openNames(world)) {
            store.write(new NameKey("castle"), ArchiveStoreTest.value("castle"));
            store.write(new NameKey("old.castle_2-b"), ArchiveStoreTest.value("old castle"));
        }

        Path folder = world.resolve(ArchiveStoreTest.BLUEPRINTS);
        Assertions.assertThat(ArchiveStoreTest.files(folder)).containsExactlyInAnyOrder(folder.resolve("castle.dat"), folder.resolve("old.castle_2-b.dat"));
    }

    @Test
    public void refusesAnEmptyKeyOrSegment(@TempDir Path world) {
        try (ArchiveStore<NameKey, CompoundTag> names = ArchiveStoreTest.openNames(world)) {
            Assertions.assertThatThrownBy(() -> names.write(new NameKey(""), ArchiveStoreTest.value("empty"))).isInstanceOf(IllegalArgumentException.class);
        }

        try (ArchiveStore<PathKey, CompoundTag> paths = new ArchiveStore<PathKey, CompoundTag>(
                world.resolve(ArchiveStoreTest.BLUEPRINTS),
                world,
                segments -> Optional.of(new PathKey(segments)),
                ArchiveStoreTest.FORMAT,
                ArchiveStoreTest.REGISTRIES,
                Retention.forever(),
                Retention.forever(),
                null,
                null
        )) {
            Assertions.assertThatThrownBy(() -> paths.write(new PathKey(List.of()), ArchiveStoreTest.value("root"))).isInstanceOf(IllegalArgumentException.class);
            Assertions.assertThatThrownBy(() -> paths.write(new PathKey(List.of("castle", "")), ArchiveStoreTest.value("hollow"))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    public void roundTripsEscapedNamesThroughAReopenedStore(@TempDir Path world) throws IOException {
        List<String> names = List.of("Castle", "my castle", "north/south", "..", "château");
        try (ArchiveStore<NameKey, CompoundTag> store = ArchiveStoreTest.openNames(world)) {
            names.forEach(name -> store.write(new NameKey(name), ArchiveStoreTest.value(name)));
        }

        try (ArchiveStore<NameKey, CompoundTag> store = ArchiveStoreTest.openNames(world)) {
            Assertions.assertThat(store.list(_ -> true)).containsExactlyElementsOf(names.stream().sorted().map(NameKey::new).toList());
            for (String name : names) {
                Assertions.assertThat(store.read(new NameKey(name))).contains(ArchiveStoreTest.value(name));
            }
        }

        Assertions.assertThat(ArchiveStoreTest.files(world)).allMatch(file -> file.startsWith(world.resolve(ArchiveStoreTest.BLUEPRINTS)));
    }

    @Test
    public void keepsNamesDifferingOnlyInCaseApart(@TempDir Path world) throws IOException {
        List<String> names = List.of("CASTLE", "Castle", "castle");
        try (ArchiveStore<NameKey, CompoundTag> store = ArchiveStoreTest.openNames(world)) {
            names.forEach(name -> store.write(new NameKey(name), ArchiveStoreTest.value(name)));
        }

        try (ArchiveStore<NameKey, CompoundTag> store = ArchiveStoreTest.openNames(world)) {
            Assertions.assertThat(store.list(_ -> true)).containsExactlyElementsOf(names.stream().map(NameKey::new).toList());
            for (String name : names) {
                Assertions.assertThat(store.read(new NameKey(name))).contains(ArchiveStoreTest.value(name));
            }
        }

        Path folder = world.resolve(ArchiveStoreTest.BLUEPRINTS);
        Assertions.assertThat(ArchiveStoreTest.files(folder))
                .map(file -> folder.relativize(file).toString().toLowerCase(Locale.ROOT))
                .doesNotHaveDuplicates();
    }

    @Test
    public void listsInKeyOrderThroughTheFilter(@TempDir Path world) {
        BackupKey oldest = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(3));
        BackupKey middle = ArchiveStoreTest.key(ArchiveStoreTest.ALEX, Duration.ofHours(2));
        BackupKey newest = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            store.write(newest, ArchiveStoreTest.value("newest"));
            store.write(oldest, ArchiveStoreTest.value("oldest"));
            store.write(middle, ArchiveStoreTest.value("middle"));

            Assertions.assertThat(store.list(_ -> true)).containsExactly(oldest, middle, newest);
            Assertions.assertThat(store.list(key -> key.owner().equals(ArchiveStoreTest.STEVE))).containsExactly(oldest, newest);
        }
    }

    @Test
    public void leavesAFileTheParserRefuses(@TempDir Path world) throws IOException {
        Path folder = world.resolve(ArchiveStoreTest.BACKUPS);
        List<Path> refused = List.of(folder.resolve("notes.dat"), folder.resolve("readme.txt"), folder.resolve(ArchiveStoreTest.STEVE.toString()).resolve("backup.dat"));
        for (Path file : refused) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "not a backup");
        }

        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world, Retention.newest(() -> 0), Retention.newest(() -> 0))) {
            store.prune();

            Assertions.assertThat(store.list(_ -> true)).isEmpty();
            Assertions.assertThat(store.listRetired(_ -> true)).isEmpty();
        }

        Assertions.assertThat(refused).allMatch(Files::exists);
    }

    @Test
    public void dropsBeyondTheCountOfEachGroupAfterAWrite(@TempDir Path world) throws IOException {
        BackupKey oldest = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(3));
        BackupKey middle = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(2));
        BackupKey newest = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        BackupKey other = ArchiveStoreTest.key(ArchiveStoreTest.ALEX, Duration.ofHours(4));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world, Retention.newest(() -> 2), Retention.forever())) {
            store.write(oldest, ArchiveStoreTest.value("oldest"));
            store.write(other, ArchiveStoreTest.value("other"));
            store.write(middle, ArchiveStoreTest.value("middle"));
            store.write(newest, ArchiveStoreTest.value("newest"));
        }

        Assertions.assertThat(ArchiveStoreTest.files(world.resolve(ArchiveStoreTest.BACKUPS))).containsExactlyInAnyOrder(
                ArchiveStoreTest.file(world, middle, ".dat"),
                ArchiveStoreTest.file(world, newest, ".dat"),
                ArchiveStoreTest.file(world, other, ".dat")
        );
    }

    @Test
    public void dropsOldKeysWhenOpened(@TempDir Path world) throws IOException {
        BackupKey old = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofDays(10));
        BackupKey recent = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            store.write(old, ArchiveStoreTest.value("old"));
            store.write(recent, ArchiveStoreTest.value("recent"));
        }

        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world, Retention.maxAge(() -> 7), Retention.forever())) {
            Assertions.assertThat(store.list(_ -> true)).containsExactly(recent);
            Assertions.assertThat(store.read(old)).isEmpty();
        }

        Assertions.assertThat(ArchiveStoreTest.files(world.resolve(ArchiveStoreTest.BACKUPS))).containsExactly(ArchiveStoreTest.file(world, recent, ".dat"));
    }

    @Test
    public void prunesEveryGroupUnderTheChangedCount(@TempDir Path world) throws IOException {
        AtomicInteger count = new AtomicInteger(3);
        BackupKey oldest = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(3));
        BackupKey middle = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(2));
        BackupKey newest = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        BackupKey otherOld = ArchiveStoreTest.key(ArchiveStoreTest.ALEX, Duration.ofHours(5));
        BackupKey otherNew = ArchiveStoreTest.key(ArchiveStoreTest.ALEX, Duration.ofHours(4));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world, Retention.newest(count::get), Retention.forever())) {
            Stream.of(oldest, middle, newest, otherOld, otherNew).forEach(key -> store.write(key, ArchiveStoreTest.value("backup")));
            count.set(1);
            store.prune();

            Assertions.assertThat(store.list(_ -> true)).containsExactly(otherNew, newest);
        }

        Assertions.assertThat(ArchiveStoreTest.files(world.resolve(ArchiveStoreTest.BACKUPS))).containsExactlyInAnyOrder(
                ArchiveStoreTest.file(world, newest, ".dat"),
                ArchiveStoreTest.file(world, otherNew, ".dat")
        );
    }

    @Test
    public void retiresAnEntryOutOfTheList(@TempDir Path world) throws IOException {
        BackupKey retired = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(2));
        BackupKey active = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            store.write(retired, ArchiveStoreTest.value("retired"));
            store.write(active, ArchiveStoreTest.value("active"));
            store.retire(retired);

            Assertions.assertThat(store.list(_ -> true)).containsExactly(active);
            Assertions.assertThat(store.listRetired(_ -> true)).containsExactly(retired);
            Assertions.assertThat(store.isRetired(retired)).isTrue();
            Assertions.assertThat(store.isRetired(active)).isFalse();
            Assertions.assertThat(store.read(retired)).contains(ArchiveStoreTest.value("retired"));
        }

        Assertions.assertThat(ArchiveStoreTest.files(world.resolve(ArchiveStoreTest.BACKUPS))).containsExactlyInAnyOrder(
                ArchiveStoreTest.file(world, retired, ".retired"),
                ArchiveStoreTest.file(world, active, ".dat")
        );
    }

    @Test
    public void prunesRetiredKeysUnderTheRetiredRule(@TempDir Path world) {
        BackupKey oldest = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(3));
        BackupKey middle = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(2));
        BackupKey newest = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world, Retention.forever(), Retention.newest(() -> 1))) {
            Stream.of(oldest, middle, newest).forEach(key -> store.write(key, ArchiveStoreTest.value("backup")));
            store.retire(oldest);
            store.retire(middle);
            store.prune();

            Assertions.assertThat(store.listRetired(_ -> true)).containsExactly(middle);
            Assertions.assertThat(store.list(_ -> true)).containsExactly(newest);
        }
    }

    @Test
    public void keepsRetiredKeysOutOfTheActiveRule(@TempDir Path world) {
        BackupKey retired = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(2));
        BackupKey active = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world, Retention.newest(() -> 1), Retention.forever())) {
            store.write(retired, ArchiveStoreTest.value("retired"));
            store.retire(retired);
            store.write(active, ArchiveStoreTest.value("active"));
            store.prune();

            Assertions.assertThat(store.list(_ -> true)).containsExactly(active);
            Assertions.assertThat(store.listRetired(_ -> true)).containsExactly(retired);
        }
    }

    @Test
    public void reactivatesARetiredKeyOnWrite(@TempDir Path world) throws IOException {
        BackupKey key = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            store.write(key, ArchiveStoreTest.value("first"));
            store.retire(key);
            store.write(key, ArchiveStoreTest.value("second"));

            Assertions.assertThat(store.list(_ -> true)).containsExactly(key);
            Assertions.assertThat(store.listRetired(_ -> true)).isEmpty();
            Assertions.assertThat(store.isRetired(key)).isFalse();
            Assertions.assertThat(store.read(key)).contains(ArchiveStoreTest.value("second"));
        }

        Assertions.assertThat(ArchiveStoreTest.files(world.resolve(ArchiveStoreTest.BACKUPS))).containsExactly(ArchiveStoreTest.file(world, key, ".dat"));
    }

    @Test
    public void deletesActiveAndRetiredEntries(@TempDir Path world) throws IOException {
        BackupKey retired = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(2));
        BackupKey active = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            store.write(retired, ArchiveStoreTest.value("retired"));
            store.write(active, ArchiveStoreTest.value("active"));
            store.retire(retired);
            store.delete(retired);
            store.delete(active);

            Assertions.assertThat(store.list(_ -> true)).isEmpty();
            Assertions.assertThat(store.listRetired(_ -> true)).isEmpty();
            Assertions.assertThat(store.read(retired)).isEmpty();
            Assertions.assertThat(store.read(active)).isEmpty();
        }

        Assertions.assertThat(ArchiveStoreTest.files(world.resolve(ArchiveStoreTest.BACKUPS))).isEmpty();
    }

    @Test
    public void ignoresRetireAndDeleteOfAnUnknownKey(@TempDir Path world) {
        BackupKey known = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(2));
        BackupKey unknown = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            store.write(known, ArchiveStoreTest.value("known"));
            store.retire(unknown);
            store.delete(unknown);

            Assertions.assertThat(store.isRetired(unknown)).isFalse();
            Assertions.assertThat(store.read(unknown)).isEmpty();
            Assertions.assertThat(store.list(_ -> true)).containsExactly(known);
            Assertions.assertThat(store.listRetired(_ -> true)).isEmpty();
        }
    }

    @Test
    public void readsQueuedChangesOfItsKey(@TempDir Path world) {
        BackupKey key = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            IntStream.range(0, 100).forEach(index -> store.write(key, ArchiveStoreTest.value("write " + index)));
            Assertions.assertThat(store.read(key)).contains(ArchiveStoreTest.value("write 99"));
            Assertions.assertThat(store.list(_ -> true)).containsExactly(key);

            store.retire(key);
            Assertions.assertThat(store.read(key)).contains(ArchiveStoreTest.value("write 99"));

            store.delete(key);
            Assertions.assertThat(store.read(key)).isEmpty();
        }
    }

    @Test
    public void writesNowBeforeReturning(@TempDir Path world) throws IOException {
        BackupKey key = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            store.writeNow(key, ArchiveStoreTest.value("grave"));

            Path file = ArchiveStoreTest.file(world, key, ".dat");
            Assertions.assertThat(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())).isEqualTo(ArchiveStoreTest.FORMAT.write(ArchiveStoreTest.value("grave"), ArchiveStoreTest.REGISTRIES));
        }
    }

    @Test
    public void throwsWhenWritingNowIntoAnUnwritableFolder(@TempDir Path world) throws IOException {
        Path blocker = world.resolve(ArchiveStoreTest.BACKUPS).resolve(ArchiveStoreTest.STEVE.toString());
        Files.createDirectories(blocker.getParent());
        Files.writeString(blocker, "a file where the folder of the owner belongs");
        BackupKey key = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            Assertions.assertThatThrownBy(() -> store.writeNow(key, ArchiveStoreTest.value("grave"))).isInstanceOf(UncheckedIOException.class);
        }
    }

    @Test
    public void runsEveryQueuedWriteOnClose(@TempDir Path world) throws IOException {
        List<BackupKey> keys = IntStream.range(0, 50)
                .mapToObj(minutes -> ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofMinutes(minutes)))
                .toList();

        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            keys.forEach(key -> store.write(key, ArchiveStoreTest.value("backup")));
        }

        Assertions.assertThat(ArchiveStoreTest.files(world.resolve(ArchiveStoreTest.BACKUPS)))
                .containsExactlyInAnyOrderElementsOf(keys.stream().map(key -> ArchiveStoreTest.file(world, key, ".dat")).toList());
    }

    @Test
    public void listsWhatWasWrittenAfterReopening(@TempDir Path world) {
        BackupKey oldest = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(3));
        BackupKey middle = ArchiveStoreTest.key(ArchiveStoreTest.ALEX, Duration.ofHours(2));
        BackupKey newest = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            Stream.of(newest, oldest, middle).forEach(key -> store.write(key, ArchiveStoreTest.value(key.owner().toString())));
            store.retire(middle);
        }

        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            Assertions.assertThat(store.list(_ -> true)).containsExactly(oldest, newest);
            Assertions.assertThat(store.listRetired(_ -> true)).containsExactly(middle);
            Assertions.assertThat(store.read(middle)).contains(ArchiveStoreTest.value(ArchiveStoreTest.ALEX.toString()));
        }
    }

    @Test
    public void keepsAnUndecodableFileOnDisk(@TempDir Path world) throws IOException {
        BackupKey key = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(1));
        Path file = ArchiveStoreTest.file(world, key, ".dat");
        CompoundTag newer = new CompoundTag();
        newer.putInt("version", ArchiveStoreTest.FORMAT.getVersion() + 1);
        newer.put("data", ArchiveStoreTest.value("written by a newer version"));
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(newer, file);
        byte[] written = Files.readAllBytes(file);

        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.open(world)) {
            Assertions.assertThat(store.list(_ -> true)).containsExactly(key);
            Assertions.assertThatThrownBy(() -> store.read(key)).isInstanceOf(NexusCodecException.class);
        }

        Assertions.assertThat(Files.readAllBytes(file)).isEqualTo(written);
    }

    @Test
    public void importsLegacyFilesOnce(@TempDir Path world) throws IOException {
        BackupKey active = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(2));
        BackupKey retired = ArchiveStoreTest.key(ArchiveStoreTest.STEVE, Duration.ofHours(3));
        Path legacy = world.resolve(ArchiveStoreTest.LEGACY);
        Path activeFile = ArchiveStoreTest.legacyFile(legacy, active, ".dat");
        Path retiredFile = ArchiveStoreTest.legacyFile(legacy, retired, ".dat-old");
        Path foreignFile = legacy.resolve("graves.json");
        ArchiveStoreTest.writeLegacy(activeFile, "active");
        ArchiveStoreTest.writeLegacy(retiredFile, "retired");
        Files.writeString(foreignFile, "{}");
        byte[] activeBytes = Files.readAllBytes(activeFile);
        byte[] retiredBytes = Files.readAllBytes(retiredFile);

        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.openLegacy(world)) {
            Assertions.assertThat(store.list(_ -> true)).containsExactly(active);
            Assertions.assertThat(store.listRetired(_ -> true)).containsExactly(retired);
            Assertions.assertThat(store.read(active)).contains(ArchiveStoreTest.value("active"));
        }

        Assertions.assertThat(Files.readAllBytes(ArchiveStoreTest.file(world, active, ".dat"))).isEqualTo(activeBytes);
        Assertions.assertThat(Files.readAllBytes(ArchiveStoreTest.file(world, retired, ".retired"))).isEqualTo(retiredBytes);
        Assertions.assertThat(activeFile).doesNotExist();
        Assertions.assertThat(retiredFile).doesNotExist();
        Assertions.assertThat(foreignFile).exists();

        BackupKey late = ArchiveStoreTest.key(ArchiveStoreTest.ALEX, Duration.ofHours(1));
        Path lateFile = ArchiveStoreTest.legacyFile(legacy, late, ".dat");
        ArchiveStoreTest.writeLegacy(lateFile, "late");
        try (ArchiveStore<BackupKey, CompoundTag> store = ArchiveStoreTest.openLegacy(world)) {
            Assertions.assertThat(store.list(_ -> true)).containsExactly(active);
            Assertions.assertThat(store.listRetired(_ -> true)).containsExactly(retired);
        }

        Assertions.assertThat(lateFile).exists();
        Assertions.assertThat(foreignFile).exists();
    }

    private static ArchiveStore<BackupKey, CompoundTag> open(Path world) {
        return ArchiveStoreTest.open(world, Retention.forever(), Retention.forever());
    }

    private static ArchiveStore<BackupKey, CompoundTag> open(Path world, Retention<BackupKey> active, Retention<BackupKey> retired) {
        return new ArchiveStore<>(world.resolve(ArchiveStoreTest.BACKUPS), world, BackupKey::parse, ArchiveStoreTest.FORMAT, ArchiveStoreTest.REGISTRIES, active, retired, null, null);
    }

    private static ArchiveStore<BackupKey, CompoundTag> openLegacy(Path world) {
        return new ArchiveStore<>(
                world.resolve(ArchiveStoreTest.BACKUPS),
                world,
                BackupKey::parse,
                ArchiveStoreTest.FORMAT,
                ArchiveStoreTest.REGISTRIES,
                Retention.forever(),
                Retention.forever(),
                ArchiveStoreTest.LEGACY,
                ArchiveStoreTest::readLegacy
        );
    }

    private static ArchiveStore<NameKey, CompoundTag> openNames(Path world) {
        return new ArchiveStore<>(world.resolve(ArchiveStoreTest.BLUEPRINTS), world, NameKey::parse, ArchiveStoreTest.FORMAT, ArchiveStoreTest.REGISTRIES, Retention.forever(), Retention.forever(), null, null);
    }

    private static Optional<Archive.Imported<BackupKey>> readLegacy(Path file) {
        Matcher matcher = ArchiveStoreTest.LEGACY_NAME.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        UUID owner = UUID.fromString(file.getParent().getFileName().toString());
        BackupKey key = new BackupKey(owner, Instant.ofEpochSecond(Long.parseLong(matcher.group(1))), matcher.group(2));
        return Optional.of(new Archive.Imported<>(key, matcher.group(3) != null));
    }

    private static void writeLegacy(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(ArchiveStoreTest.value(text), file);
    }

    private static Path legacyFile(Path legacy, BackupKey key, String extension) {
        return legacy.resolve(key.owner().toString()).resolve(key.time().getEpochSecond() + "-" + key.kind() + extension);
    }

    private static Path file(Path world, BackupKey key, String extension) {
        return world.resolve(ArchiveStoreTest.BACKUPS).resolve(key.segments().get(0)).resolve(key.segments().get(1) + extension);
    }

    private static List<Path> files(Path folder) throws IOException {
        if (Files.notExists(folder)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".dat") || file.toString().endsWith(".retired"))
                    .toList();
        }
    }

    private static BackupKey key(UUID owner, Duration age) {
        return new BackupKey(owner, ArchiveStoreTest.NOW.minus(age), "death");
    }

    private static CompoundTag value(String text) {
        CompoundTag value = new CompoundTag();
        value.putString("text", text);
        return value;
    }

    private record BackupKey(UUID owner, Instant time, String kind) implements TimedKey<BackupKey> {

        private static Optional<BackupKey> parse(List<String> segments) {
            if (segments.size() != 2) {
                return Optional.empty();
            }

            Matcher matcher = ArchiveStoreTest.STAMP.matcher(segments.get(1));
            if (!matcher.matches()) {
                return Optional.empty();
            }

            try {
                return Optional.of(new BackupKey(UUID.fromString(segments.get(0)), Instant.ofEpochSecond(Long.parseLong(matcher.group(1))), matcher.group(2)));
            } catch (IllegalArgumentException refused) {
                return Optional.empty();
            }
        }

        @Override
        public List<String> segments() {
            return List.of(this.owner.toString(), this.time.getEpochSecond() + "-" + this.kind);
        }

        @Override
        public Object group() {
            return this.owner;
        }
    }

    private record PathKey(List<String> segments) implements ArchiveKey<PathKey> {

        @Override
        public int compareTo(PathKey other) {
            return String.join("/", this.segments).compareTo(String.join("/", other.segments));
        }
    }
}
