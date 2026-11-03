package dev.satherov.nexus.internal.render.shader;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.jetbrains.annotations.ApiStatus;

///
/// A sampler that vanilla's core programs read, bound by its name on a render type.
///
@ApiStatus.Internal
@RequiredArgsConstructor
public enum VanillaSampler {
    
    ///
    /// The texture of the draw.
    ///
    TEXTURE("Sampler0"),
    
    ///
    /// The overlay texture, which holds the hurt flash and the breaking animation.
    ///
    OVERLAY("Sampler1"),
    
    ///
    /// The lightmap.
    ///
    LIGHTMAP("Sampler2");
    
    ///
    /// The name of the sampler in GLSL.
    ///
    /// A pipeline declares the sampler and a render type binds it under this name.
    ///
    @Getter
    private final String glslName;
}
