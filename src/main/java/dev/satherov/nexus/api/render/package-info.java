///
/// What mods draw and register with the renderer.
/// - [Render]: The drawings and registrations of one mod.
///
/// The shapes and their paint are in [dev.satherov.nexus.api.render.shape].
/// The block outline rules are in [dev.satherov.nexus.api.render.outline].
/// The glowing outlines of entities and blocks are in [dev.satherov.nexus.api.render.glow].
/// The HUD layers are in [dev.satherov.nexus.api.render.hud].
/// The shading of pipelines under a shader pack is in [dev.satherov.nexus.api.render.pipeline].
/// The screen-wide post effects are in [dev.satherov.nexus.api.render.post].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.render;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
