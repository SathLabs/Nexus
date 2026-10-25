///
/// Rules that replace or add to the outline of the block under the crosshair.
/// - [Outlines]: The rules of one mod, accessed through [Render#getOutlines()].
/// - [OutlineRule]: The rule a mod adds, asked when the targeted block changes.
/// - [Outline]: The shape and paint a rule answers with.
/// - [TargetedBlock]: The block a rule is asked about.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.render.outline;

import dev.satherov.nexus.api.render.Render;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
