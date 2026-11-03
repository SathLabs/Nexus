package dev.satherov.nexus.internal.render.shader;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

///
/// A uniform block that vanilla's shader includes declare and that vanilla binds on every draw.
///
@ApiStatus.Internal
@RequiredArgsConstructor
public enum VanillaBlock {
    
    ///
    /// The projection matrix, declared in `minecraft:projection.glsl`.
    ///
    PROJECTION("Projection"),
    
    ///
    /// The model view matrix, the color modulator, the model offset and the texture matrix, declared in `minecraft:dynamictransforms.glsl`.
    ///
    DYNAMIC_TRANSFORMS("DynamicTransforms"),
    
    ///
    /// The fog color and distances, declared in `minecraft:fog.glsl`.
    ///
    FOG("Fog"),
    
    ///
    /// The camera position, the screen size, the game time and the other frame-wide values, declared in `minecraft:globals.glsl`.
    ///
    GLOBALS("Globals"),
    
    ///
    /// The two light directions, declared in `minecraft:light.glsl`.
    ///
    LIGHTING("Lighting");
    
    ///
    /// The blocks by their GLSL name.
    ///
    private static final Map<String, VanillaBlock> BY_GLSL_NAME = Arrays.stream(VanillaBlock.values())
            .collect(Collectors.toUnmodifiableMap(VanillaBlock::getGlslName, Function.identity()));
    
    ///
    /// The name of the block in GLSL.
    ///
    /// A pipeline declares the block and a render pass binds it under this name.
    ///
    @Getter
    private final String glslName;
    
    ///
    /// Gets the block of the given GLSL name.
    ///
    /// @param glslName The name of the block in GLSL.
    ///
    /// @return The block, or `null` if vanilla has no block of that name.
    ///
    public static @Nullable VanillaBlock byName(String glslName) {
        return VanillaBlock.BY_GLSL_NAME.get(glslName);
    }
}
