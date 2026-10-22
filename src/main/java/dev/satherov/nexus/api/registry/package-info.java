///
/// Builders that register the objects of a mod and the entries that hold them.
///
/// The registrations of a mod are reached through [NexusMod#getRegistrations()].
/// The parent of a builder is the [Registrations] of its mod, or the builder of the object it belongs to.
/// - [Registrations]: The table every builder of a mod is started from.
/// - [EntryBuilder]: The superclass of every builder.
/// - [RegistryEntry]: The superclass of every entry.
///
/// The builders and the entries of one registry are in a sub-package of their own, such as [dev.satherov.nexus.api.registry.item].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.registry;

import dev.satherov.nexus.api.mod.NexusMod;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
