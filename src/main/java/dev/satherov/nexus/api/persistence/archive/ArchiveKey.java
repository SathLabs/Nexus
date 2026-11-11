package dev.satherov.nexus.api.persistence.archive;

import java.util.List;
import java.util.Optional;

///
/// A key of one entry of an archive.
///
/// Should be implemented on a record, so that two keys with the same segments are equal.
///
/// @param <SELF> The type that implements this interface.
///
public interface ArchiveKey<SELF extends ArchiveKey<SELF>> extends Comparable<SELF> {

    ///
    /// The path segments of the file of this key, below the folder of the archive.
    ///
    /// Every segment will be a folder except the last one, which will be the name of the file with `.dat` appended,
    /// or `.retired` once the entry is retired.
    ///
    /// Lowercase letters, digits, `_`, `-`, and inner `.` will be written as they are, and every other character will be escaped.
    /// The escaping keeps any non-empty segment inside the folder of the archive and apart from every other segment, also on a file system that ignores case.
    ///
    /// The parser of the archive should give back a key with the same segments.
    ///
    /// @return The segments, outermost first.
    ///
    List<String> segments();

    ///
    /// The group of this key.
    ///
    /// [Retention] runs over the keys of each group on its own, and two keys are in the same group if their groups are equal.
    /// Every key will be in the same group unless this is overridden.
    ///
    /// @return The group of this key.
    ///
    default Object group() {
        return List.of();
    }

    ///
    /// A parser of the keys of an archive.
    ///
    /// @param <KEY> The type of the keys.
    ///
    @FunctionalInterface
    interface Parser<KEY> {

        ///
        /// Parses a key from the segments of its file.
        ///
        /// If this returns empty, the file will be left where it is and won't be listed.
        ///
        /// @param segments The unescaped segments of the file, outermost first.
        ///
        /// @return The key, or empty if the segments don't belong to a key of the archive.
        ///
        Optional<KEY> parse(List<String> segments);
    }
}
