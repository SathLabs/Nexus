package dev.satherov.nexus.internal.render.shader;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.Nexus;
import dev.satherov.zelqro.utils.StringUtils;

import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

///
/// A vanilla program that a Nexus program builds on.
///
/// The template of each stage is the text of the vanilla program plus the anchor lines for [Program#generate(ShaderType, String)].
///
@ApiStatus.Internal
@RequiredArgsConstructor
public enum Base {
    
    ///
    /// The vanilla `core/position_color` program.
    ///
    POSITION_COLOR(Set.of("DynamicTransforms", "Projection"), Set.of(), DefaultVertexFormat.POSITION_COLOR),
    
    ///
    /// The vanilla `core/position_tex_color` program.
    ///
    POSITION_TEX_COLOR(Set.of("DynamicTransforms", "Projection"), Set.of("Sampler0"), DefaultVertexFormat.POSITION_TEX_COLOR),
    
    ///
    /// The vanilla `core/entity` program.
    ///
    ENTITY(Set.of("DynamicTransforms", "Projection", "Fog", "Lighting"), Set.of("Sampler0", "Sampler2"), DefaultVertexFormat.ENTITY);
    
    ///
    /// The name of the base in the directive and in the file names of its templates.
    ///
    private final String id = StringUtils.lower(this.name());
    
    ///
    /// The uniform blocks that the vanilla program reads.
    ///
    private final @Unmodifiable Set<String> blocks;
    
    ///
    /// The samplers that the vanilla snippet of the program declares.
    ///
    private final @Unmodifiable Set<String> samplers;
    
    ///
    /// The vertex format whose elements come first in the format of every program on this base.
    ///
    @Getter
    private final VertexFormat format;
    
    ///
    /// Gets the base of the given name.
    ///
    /// @param name The name in the directive, which is the name of the constant in lower case.
    ///
    /// @return The base, or `null` if none exists.
    ///
    public static @Nullable Base byName(String name) {
        return Arrays.stream(Base.values())
                .filter(base -> base.id.equals(name))
                .findFirst()
                .orElse(null);
    }
    
    ///
    /// The uniform blocks that the vanilla program reads.
    ///
    /// @return The names of the uniform blocks.
    ///
    public @Unmodifiable Set<String> getBlocks() {
        return this.blocks;
    }
    
    ///
    /// The samplers that the vanilla snippet of the program declares.
    ///
    /// @return The names of the samplers.
    ///
    public @Unmodifiable Set<String> getSamplers() {
        return this.samplers;
    }
    
    ///
    /// Gets the identifier of the template of the given stage.
    ///
    /// The identifier will be `nexus:nexus/shaders/base/<name>.vsh` for the vertex stage and `.fsh` for the fragment stage.
    ///
    /// @param stage The shader stage.
    ///
    /// @return The identifier of the template.
    ///
    public Identifier template(ShaderType stage) {
        return stage.idConverter().idToFile(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "base/" + this.id)).withPrefix("nexus/");
    }
}
