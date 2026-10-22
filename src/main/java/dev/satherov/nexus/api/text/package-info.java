///
/// Translation keys declared in code, and the components created from them.
///
/// A type with a [Translation] implements [Translatable] and creates its components through it.
/// - [Translations]: The table of a mod, reached through [NexusMod#getTranslations()].
/// - [Translatable]: The value with a translated key that components are created from.
///
/// The tooltip builder is in [dev.satherov.nexus.api.text.tooltip].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.text;

import dev.satherov.nexus.api.mod.NexusMod;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
