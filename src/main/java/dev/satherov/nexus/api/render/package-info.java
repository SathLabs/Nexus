///
/// What mods draw and register with the renderer.
/// - [Render]: The drawing and render registrations of one mod, accessed through [NexusMod#getRender()].
///
/// For the shapes and their paint see [dev.satherov.nexus.api.render.shape].
///
/// For the HUD layers see [dev.satherov.nexus.api.render.hud].
///
/// For the shading of pipelines under a shader pack see [dev.satherov.nexus.api.render.pipeline].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.render;

import dev.satherov.nexus.api.mod.NexusMod;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
