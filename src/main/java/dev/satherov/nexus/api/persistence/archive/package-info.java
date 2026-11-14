///
/// Archives of entries that a mod saves with each server in one file per key.
///
/// An [Archive] is declared in the mod constructor through an [ArchiveBuilder].
/// A [Retention] picks the keys of one group that the archive deletes.
/// - [ArchiveKey]: The key of one entry, implemented by the mod or taken from [NameKey] or [TimedKey].
/// - [Retention]: The rule that picks the entries to delete.
/// - [Archive]: One declared archive, which writes, reads, lists, retires, and deletes its entries.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.persistence.archive;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
