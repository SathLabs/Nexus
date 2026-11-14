package dev.satherov.nexus.api.persistence.archive;

import java.time.Instant;
import java.util.function.IntSupplier;

///
/// A key of one entry of an archive that is ordered by its time.
///
/// @param <SELF> The type that implements this interface.
///
/// @see Retention#maxAge(IntSupplier)
///
public interface TimedKey<SELF extends TimedKey<SELF>> extends ArchiveKey<SELF> {

    ///
    /// The time of the entry of this key.
    ///
    /// @return The time of the entry.
    ///
    Instant time();

    ///
    /// Compares the time of this key against the time of the given key, oldest first.
    ///
    /// @param other The other key.
    ///
    /// @return A negative number if this key is older, `0` if both have the same time, or a positive number if this key is newer.
    ///
    @Override
    default int compareTo(SELF other) {
        return this.time().compareTo(other.time());
    }
}
