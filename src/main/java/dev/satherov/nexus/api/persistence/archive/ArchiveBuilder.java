package dev.satherov.nexus.api.persistence.archive;

import dev.satherov.nexus.api.persistence.Persistence;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.internal.persistence.archive.ArchiveStore;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

///
/// A builder that declares one archive on the persistence of a mod.
///
/// Can be started through [Persistence#archive(String, ArchiveKey.Parser, Format)].
///
/// @param <KEY>   The type of the keys.
/// @param <VALUE> The type of the values.
///
public final class ArchiveBuilder<KEY extends ArchiveKey<KEY>, VALUE> {

    ///
    /// The persistence the archive is declared on.
    ///
    private final Persistence persistence;

    ///
    /// The name of the archive.
    ///
    private final String name;

    ///
    /// The parser of the keys.
    ///
    private final ArchiveKey.Parser<KEY> keys;

    ///
    /// The format the values are saved in.
    ///
    private final Format<VALUE> format;

    ///
    /// The retention of the active entries.
    ///
    private Retention<KEY> active = Retention.forever();

    ///
    /// The retention of the retired entries.
    ///
    private Retention<KEY> retired = Retention.forever();

    ///
    /// The folder of the older layout relative to the world folder, or `null` if there is none.
    ///
    private @Nullable String legacyFolder;

    ///
    /// The reader of the files of the older layout, or `null` if there is none.
    ///
    private @Nullable Function<Path, Optional<Archive.Imported<KEY>>> legacyReader;

    ///
    /// Should only ever be called from [Persistence#archive(String, ArchiveKey.Parser, Format)].
    ///
    /// @param persistence The persistence the archive is declared on.
    /// @param name        The name of the archive.
    /// @param keys        The parser of the keys.
    /// @param format      The format the values are saved in.
    ///
    @ApiStatus.Internal
    public ArchiveBuilder(Persistence persistence, String name, ArchiveKey.Parser<KEY> keys, Format<VALUE> format) {
        this.persistence = persistence;
        this.name = name;
        this.keys = keys;
        this.format = format;
    }

    ///
    /// Sets the retention of the active entries and keeps every retired entry.
    ///
    /// The retention runs once when a server starts, over the group of a key after every write, and over every group when the archive is pruned.
    ///
    /// @param active The retention of the active entries.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public ArchiveBuilder<KEY, VALUE> retention(Retention<KEY> active) {
        return this.retention(active, Retention.forever());
    }

    ///
    /// Sets the retention of the active and the retired entries.
    ///
    /// The retention runs once when a server starts, over the group of a key after every write, and over every group when the archive is pruned.
    ///
    /// @param active  The retention of the active entries.
    /// @param retired The retention of the retired entries.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public ArchiveBuilder<KEY, VALUE> retention(Retention<KEY> active, Retention<KEY> retired) {
        this.active = active;
        this.retired = retired;
        return this;
    }

    ///
    /// Imports the files of an older layout into the archive once per world.
    ///
    /// When a server starts, the given reader will be called with every file below the given folder that isn't in the folder of the archive.
    /// If the reader returns an entry, the file will be moved unchanged to the file of its key.
    /// If the reader returns empty, the file will be left where it is.
    ///
    /// A marker file will then be written into the folder of the archive, and the import won't run again while it exists.
    /// A moved file that only holds a bare tag will be read as version `0` of the format.
    ///
    /// If a file could not be moved, it will stay where it is and the failure will be logged.
    /// If the reader returns a key that can't be written, an [IllegalArgumentException] will be thrown once the server starts.
    ///
    /// @param folder The folder of the older layout, relative to the world folder.
    /// @param reader The reader of one file, called with its path.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public ArchiveBuilder<KEY, VALUE> legacy(String folder, Function<Path, Optional<Archive.Imported<KEY>>> reader) {
        this.legacyFolder = folder;
        this.legacyReader = reader;
        return this;
    }

    ///
    /// Declares and creates the archive.
    ///
    /// @return The new archive.
    ///
    /// @throws IllegalStateException If a server already started, or if the mod already declared a saved value or an archive with the same name.
    ///
    public Archive<KEY, VALUE> register() {
        Identifier id = this.persistence.declare(this.name);
        return new Archive<>(id, server -> new ArchiveStore<>(
                server.getWorldPath(LevelResource.DATA).resolve(id.getNamespace()).resolve(id.getPath()),
                server.getWorldPath(LevelResource.ROOT),
                this.keys,
                this.format,
                server.registryAccess(),
                this.active,
                this.retired,
                this.legacyFolder,
                this.legacyReader
        ));
    }
}
