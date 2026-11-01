///
/// A mod's pipelines and their shading under a shader pack.
/// - [Pipeline]: A registered pipeline with its bound uniform blocks.
/// - [Shading]: The program of the pack a pipeline is drawn with, or its own shaders.
///
/// A pipeline can also be built from a program file in `assets/<modid>/nexus/shaders/<name>.glsl` through [Render#pipeline(String, Shading)].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.render.pipeline;

import dev.satherov.nexus.api.render.Render;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
