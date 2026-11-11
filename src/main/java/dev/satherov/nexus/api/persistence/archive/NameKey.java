package dev.satherov.nexus.api.persistence.archive;

import java.util.List;
import java.util.Optional;

///
/// A key of one entry of an archive that is a single name and is ordered by it.
///
/// @param name The name of the entry, which is the one segment of the key.
///
public record NameKey(String name) implements ArchiveKey<NameKey> {

    ///
    /// Parses a key from the segments of its file.
    ///
    /// Meant to be used as the parser of an archive.
    ///
    /// @param segments The segments of the file.
    ///
    /// @return The key, or empty if there isn't exactly one segment.
    ///
    public static Optional<NameKey> parse(List<String> segments) {
        return segments.size() == 1 ? Optional.of(new NameKey(segments.getFirst())) : Optional.empty();
    }

    ///
    /// The name of this key as its one segment.
    ///
    /// @return The one segment.
    ///
    @Override
    public List<String> segments() {
        return List.of(this.name);
    }

    ///
    /// Compares the name of this key against the name of the given key.
    ///
    /// @param other The other key.
    ///
    /// @return The result of comparing the two names with [String#compareTo(String)].
    ///
    @Override
    public int compareTo(NameKey other) {
        return this.name.compareTo(other.name);
    }
}
