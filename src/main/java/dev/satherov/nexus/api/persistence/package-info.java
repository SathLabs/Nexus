///
/// The data that a mod saves with the world.
/// - [Persistence]: The persistence of one mod, reached through [NexusMod#getPersistence()].
/// - [PlayerData]: The saved data of every player, online or offline, reached through [Persistence#getPlayerData()].
///
/// The saved values are in [dev.satherov.nexus.api.persistence.saved].
/// The archives are in [dev.satherov.nexus.api.persistence.archive].
/// The formats are in [dev.satherov.nexus.api.persistence.format].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.persistence;

import dev.satherov.nexus.api.mod.NexusMod;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
