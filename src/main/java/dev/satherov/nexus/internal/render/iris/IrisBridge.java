package dev.satherov.nexus.internal.render.iris;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.api.render.pipeline.Shading;

import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.programs.ShaderKey;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import org.jetbrains.annotations.ApiStatus;

///
/// The one class that calls Iris.
///
/// Besides the API it relies on the internals [IrisPipelines#assignPipeline(RenderPipeline, ShaderKey)],
/// [ShaderKey#ENTITIES_SOLID] and [ShaderKey#ENTITIES_CUTOUT], checked against Iris 1.11.4.
/// If the Iris in use lacks them, the pipelines of [Shading#ENTITIES] stay unassigned.
///
/// Should only ever be loaded if the mod `iris` is present.
///
@Slf4j
@UtilityClass
@ApiStatus.Internal
public class IrisBridge {
    
    ///
    /// If it was logged that the internals of Iris are missing.
    ///
    private static boolean linkageWarned;
    
    ///
    /// Assigns the given render pipeline the Iris program of the given shading.
    ///
    /// Does nothing for [Shading#OWN].
    ///
    /// [Shading#ENTITIES] takes the key Iris gives vanilla's solid entity pipeline,
    /// or that of its cutout entity pipeline if the given pipeline defines `ALPHA_CUTOUT`.
    /// If the Iris in use lacks the internals this relies on, the pipeline stays unassigned, which is logged for the first one.
    ///
    /// @param pipeline The render pipeline.
    /// @param shading  The shading of the render pipeline.
    ///
    public static void assign(RenderPipeline pipeline, Shading shading) {
        if (shading == Shading.ENTITIES) {
            // The API would match the key ENTITIES_ALPHA, whose alpha test discards every opaque fragment.
            boolean cutout = pipeline.getShaderDefines().values().containsKey("ALPHA_CUTOUT");
            try {
                IrisPipelines.assignPipeline(pipeline, cutout ? ShaderKey.ENTITIES_CUTOUT : ShaderKey.ENTITIES_SOLID);
            } catch (LinkageError error) {
                if (!IrisBridge.linkageWarned) {
                    IrisBridge.log.warn("The entity pipeline '{}' and any after it stay unassigned, since the Iris in use lacks the internals checked against Iris 1.11.4", pipeline.getLocation(), error);
                    IrisBridge.linkageWarned = true;
                }
            }
            
            return;
        }
        
        IrisProgram program = switch (shading) {
            case BASIC -> IrisProgram.BASIC;
            case TEXTURED -> IrisProgram.TEXTURED;
            case LINES -> IrisProgram.LINES;
            case BLOCK -> IrisProgram.BLOCK;
            case BLOCK_TRANSLUCENT -> IrisProgram.BLOCK_TRANSLUCENT;
            case TRANSLUCENT -> IrisProgram.TRANSLUCENT;
            case ENTITIES_TRANSLUCENT -> IrisProgram.ENTITIES_TRANSLUCENT;
            case EMISSIVE_ENTITIES -> IrisProgram.EMISSIVE_ENTITIES;
            case PARTICLES -> IrisProgram.PARTICLES;
            case PARTICLES_TRANSLUCENT -> IrisProgram.PARTICLES_TRANSLUCENT;
            case BEACON_BEAM -> IrisProgram.BEACON_BEAM;
            case ENTITIES, OWN -> null;
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
