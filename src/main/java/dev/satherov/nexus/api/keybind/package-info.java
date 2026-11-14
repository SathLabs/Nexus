///
/// The keys that a mod declares.
/// - [Keybinds]: The keybinds of one mod, reached through [NexusMod#getKeybinds()].
/// - [KeybindBuilder]: The builder of a key, started through [Keybinds#key(String, String)].
/// - [Keybind]: A registered key.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.keybind;

import dev.satherov.nexus.api.mod.NexusMod;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
