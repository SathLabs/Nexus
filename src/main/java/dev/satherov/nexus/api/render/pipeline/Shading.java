package dev.satherov.nexus.api.render.pipeline;

///
/// The shading of a pipeline's geometry while a shader pack is active.
///
/// Every constant except [#OWN] is a program of the pack.
/// The program replaces the pipeline's shaders and binds the pipeline's vertices by attribute position.
///
/// If no pack is active, every constant will draw alike.
///
public enum Shading {
    
    ///
    /// The pack's untextured program, for position and color.
    ///
    BASIC,
    
    ///
    /// The pack's textured program, for position, texture, and color.
    ///
    TEXTURED,
    
    ///
    /// The pack's line program, for vanilla's line format.
    ///
    LINES,
    
    ///
    /// The pack's solid block program, for the block format.
    ///
    BLOCK,
    
    ///
    /// The pack's translucent block program, for the block format.
    ///
    BLOCK_TRANSLUCENT,
    
    ///
    /// The pack's translucent terrain program, for the block format.
    ///
    TRANSLUCENT,
    
    ///
    /// The pack's entity program, for the entity format.
    ///
    /// The program reads the overlay and the lightmap whatever the pipeline's own shaders declare.
    /// The render type has to bind them as `Sampler1` and `Sampler2`, or the first draw under a pack will throw an `IllegalStateException`.
    ///
    ENTITIES,
    
    ///
    /// The pack's translucent entity program, for the entity format.
    ///
    /// The program reads the overlay and the lightmap whatever the pipeline's own shaders declare.
    /// The render type has to bind them as `Sampler1` and `Sampler2`, or the first draw under a pack will throw an `IllegalStateException`.
    ///
    ENTITIES_TRANSLUCENT,
    
    ///
    /// The pack's emissive entity program, for the entity format.
    ///
    /// The program reads the overlay and the lightmap whatever the pipeline's own shaders declare.
    /// The render type has to bind them as `Sampler1` and `Sampler2`, or the first draw under a pack will throw an `IllegalStateException`.
    ///
    EMISSIVE_ENTITIES,
    
    ///
    /// The pack's particle program, for the particle format.
    ///
    PARTICLES,
    
    ///
    /// The pack's translucent particle program, for the particle format.
    ///
    PARTICLES_TRANSLUCENT,
    
    ///
    /// The pack's beacon beam program, for the block format.
    ///
    BEACON_BEAM,
    
    ///
    /// The pipeline's own shaders, under a pack too.
    ///
    /// If a pack is active, the geometry is drawn into a target of Nexus that is depth tested against the level drawn so far
    /// and blended over the finished frame after the level.
    /// The lighting and fog of the pack will not apply to it.
    /// Geometry from `Render#draw` ends in that target by itself.
    /// A render type a mod draws elsewhere has to draw into `Pipeline#getTarget()`, or it will lose its color under a pack.
    ///
    /// Under a pack, the pipeline draws with the alpha factors and the alpha write that the target of Nexus needs,
    /// and the geometry blends over the frame as it does without a pack if the pipeline has one of these blends:
    /// - A destination color factor of one.
    /// - A destination color factor of one minus the source alpha, with every color channel written.
    /// - No blend function, with every color channel written and every fragment at an alpha of one.
    ///
    /// The source color factor of the first two mustn't read the target, as the destination factors and the saturated source alpha do.
    /// A pipeline with any other blend is logged when the pipelines are registered.
    /// Without a pack, the pipeline draws with its own blend.
    /// Under a pack, it also draws with the alpha factors and the alpha write of the target of Nexus into the entity outline target.
    ///
    OWN
}
