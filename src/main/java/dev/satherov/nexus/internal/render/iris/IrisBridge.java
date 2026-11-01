package dev.satherov.nexus.internal.render.iris;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.render.pipeline.Shading;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import org.jetbrains.annotations.ApiStatus;

///
/// The one class that calls the Iris API.
///
/// Should only ever be loaded if the mod `iris` is present.
///
@UtilityClass
@ApiStatus.Internal
public class IrisBridge {
    
    ///
    /// Assigns the given render pipeline the Iris program of the given shading.
    ///
    /// Does nothing for [Shading#OWN].
    ///
    /// @param pipeline The render pipeline.
    /// @param shading  The shading of the render pipeline.
    ///
    public static void assign(RenderPipeline pipeline, Shading shading) {
        IrisProgram program = switch (shading) {
            case BASIC -> IrisProgram.BASIC;
            case TEXTURED -> IrisProgram.TEXTURED;
            case LINES -> IrisProgram.LINES;
            case BLOCK -> IrisProgram.BLOCK;
            case BLOCK_TRANSLUCENT -> IrisProgram.BLOCK_TRANSLUCENT;
            case TRANSLUCENT -> IrisProgram.TRANSLUCENT;
            case ENTITIES -> IrisProgram.ENTITIES;
            case ENTITIES_TRANSLUCENT -> IrisProgram.ENTITIES_TRANSLUCENT;
            case EMISSIVE_ENTITIES -> IrisProgram.EMISSIVE_ENTITIES;
            case PARTICLES -> IrisProgram.PARTICLES;
            case PARTICLES_TRANSLUCENT -> IrisProgram.PARTICLES_TRANSLUCENT;
            case BEACON_BEAM -> IrisProgram.BEACON_BEAM;
            case OWN -> null;
        };
        
        if (program == null) {
            return;
        }
        
        IrisApi.getInstance().assignPipeline(pipeline, program);
    }
    
    ///
    /// @return `true` if a shader pack is in use.
    ///
    public static boolean isShaderPackInUse() {
        return IrisApi.getInstance().isShaderPackInUse();
    }
}
