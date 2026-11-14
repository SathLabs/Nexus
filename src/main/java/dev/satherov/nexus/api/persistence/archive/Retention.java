package dev.satherov.nexus.api.persistence.archive;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;

///
/// A rule that picks the keys of one group that an archive deletes.
///
/// @param <KEY> The type of the keys.
///
/// @see ArchiveKey#group()
///
@FunctionalInterface
public interface Retention<KEY> {

    ///
    /// Creates a new rule that drops nothing.
    ///
    /// @return The new rule.
    ///
    static <KEY> Retention<KEY> forever() {
        return _ -> List.of();
    }

    ///
    /// Creates a new rule that keeps the given number of greatest keys of each group and drops the rest.
    ///
    /// The count will be read on every run.
    ///
    /// @param count The supplier of the number of keys to keep.
    ///
    /// @return The new rule.
    ///
    static <KEY> Retention<KEY> newest(IntSupplier count) {
        return keys -> keys.subList(0, Math.clamp(keys.size() - (long) count.getAsInt(), 0, keys.size()));
    }

    ///
    /// Creates a new rule that drops the keys whose time is more than the given number of days ago.
    ///
    /// The number of days will be read on every run.
    ///
    /// @param days The supplier of the number of days to keep a key.
    ///
    /// @return The new rule.
    ///
    static <KEY extends TimedKey<KEY>> Retention<KEY> maxAge(IntSupplier days) {
        return keys -> {
            Instant limit = Instant.now().minus(Duration.ofDays(days.getAsInt()));
            return keys.stream()
                    .filter(key -> key.time().isBefore(limit))
                    .toList();
        };
    }

    ///
    /// Picks the keys to drop from the keys of one group.
    ///
    /// @param keys The keys of one group, in their order.
    ///
    /// @return The keys to drop.
    ///
    Collection<KEY> drop(List<KEY> keys);

    ///
    /// Creates a new rule that drops every key that this rule or the given rule drops.
    ///
    /// @param other The other rule.
    ///
    /// @return The new rule.
    ///
    default Retention<KEY> and(Retention<KEY> other) {
        return keys -> Stream.concat(this.drop(keys).stream(), other.drop(keys).stream())
                .distinct()
                .toList();
    }

    ///
    /// Creates a new rule that runs this rule over the keys that pass the given filter.
    ///
    /// The new rule will keep every other key.
    ///
    /// @param filter The filter of the keys this rule runs over.
    ///
    /// @return The new rule.
    ///
    default Retention<KEY> only(Predicate<? super KEY> filter) {
        return keys -> this.drop(keys.stream()
                .filter(filter)
                .toList()
        );
    }
}
