package dev.satherov.nexus.internal.render.client;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.internal.render.iris.IrisBridge;
import dev.satherov.nexus.internal.render.shader.Programs;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

///
/// The pipelines and render types of lines, untextured quads, sprite quads, and shapes, one of each per depth mode.
///
/// The shapes are the discs, rings, and spheres of the shape program.
///
@UtilityClass
@ApiStatus.Internal
public class ShapePipelines {
    
    ///
    /// The line pipelines by depth mode.
    ///
    private static final Map<Paint.Depth, RenderPipeline> LINE_PIPELINES = Util.makeEnumMap(
            Paint.Depth.class,
            depth -> ShapePipelines.pipeline(RenderPipelines.LINES_SNIPPET, "lines", depth)
    );
    
    ///
    /// The untextured quad pipelines by depth mode.
    ///
    private static final Map<Paint.Depth, RenderPipeline> FILL_PIPELINES = Util.makeEnumMap(
            Paint.Depth.class,
            depth -> ShapePipelines.pipeline(RenderPipelines.DEBUG_FILLED_SNIPPET, "fill", depth)
    );
    
    ///
    /// The sprite quad pipelines by depth mode.
    ///
    private static final Map<Paint.Depth, RenderPipeline> SPRITE_PIPELINES = Util.makeEnumMap(
            Paint.Depth.class,
            depth -> ShapePipelines.pipeline(RenderPipelines.GUI_TEXTURED_SNIPPET, "sprite", depth)
    );
    
    ///
    /// The pipelines of the shape program by depth mode, which never go through the registration event.
    ///
    private static final Map<Paint.Depth, RenderPipeline> SHAPE_PIPELINES = Util.makeEnumMap(Paint.Depth.class, ShapePipelines::program);
    
    ///
    /// The render setup of every render type created so far.
    ///
    private static final Map<RenderType, RenderSetup> SETUPS = new LinkedHashMap<>();
    
    ///
    /// The line render types by depth mode.
    ///
    private static final Map<Paint.Depth, RenderType> LINE_TYPES = Util.makeEnumMap(
            Paint.Depth.class,
            depth -> ShapePipelines.type("nexus_lines", ShapePipelines.setup(ShapePipelines.LINE_PIPELINES.get(depth)).createRenderSetup())
    );
    
    ///
    /// The untextured quad render types by depth mode.
    ///
    private static final Map<Paint.Depth, RenderType> FILL_TYPES = Util.makeEnumMap(
            Paint.Depth.class,
            depth -> ShapePipelines.type("nexus_fill", ShapePipelines.setup(ShapePipelines.FILL_PIPELINES.get(depth)).sortOnUpload().createRenderSetup())
    );
    
    ///
    /// The shape render types by depth mode.
    ///
    private static final Map<Paint.Depth, RenderType> SHAPE_TYPES = Util.makeEnumMap(
            Paint.Depth.class,
            depth -> ShapePipelines.type("nexus_shape", ShapePipelines.setup(ShapePipelines.SHAPE_PIPELINES.get(depth)).sortOnUpload().createRenderSetup())
    );
    
    ///
    /// The sprite quad render types by atlas and depth mode, created on first use.
    ///
    private static final BiFunction<Identifier, Paint.Depth, RenderType> SPRITE_TYPES = Util.memoize(
            (atlas, depth) -> ShapePipelines.type(
                    "nexus_sprite",
                    ShapePipelines.setup(ShapePipelines.SPRITE_PIPELINES.get(depth)).sortOnUpload().withTexture("Sampler0", atlas).createRenderSetup()
            )
    );
    
    ///
    /// Creates a new pipeline from the given snippet without culling and with the depth test of the given depth mode.
    ///
    /// @param snippet The vanilla snippet to build on.
    /// @param kind    The kind of shape, part of the identifier of the pipeline.
    /// @param depth   The depth mode.
    ///
    /// @return The pipeline.
    ///
    private static RenderPipeline pipeline(RenderPipeline.Snippet snippet, String kind, Paint.Depth depth) {
        return RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "pipeline/" + kind + "_" + depth.name().toLowerCase(Locale.ROOT)))
                .withCull(false)
                .withDepthStencilState(ShapePipelines.depthTest(depth))
                .build();
    }
    
    ///
    /// Creates the pipeline of the shape program of Nexus with translucent blending, no culling, and the depth test of the given depth mode.
    ///
    /// The pipeline takes vanilla's Globals block from its state and is stored through [RenderDispatch#program(Pipeline)] with [Shading#OWN].
    ///
    /// @param depth The depth mode.
    ///
    /// @return The pipeline.
    ///
    private static RenderPipeline program(Paint.Depth depth) {
        RenderPipeline.Snippet state = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withDepthStencilState(ShapePipelines.depthTest(depth))
                .buildSnippet();
        RenderPipeline pipeline = Programs.create(Nexus.getMod(), "shape", state);
        RenderDispatch.program(new Pipeline(() -> () -> pipeline, Shading.OWN));
        return pipeline;
    }
    
    ///
    /// Creates the depth test of the given depth mode, without depth write.
    ///
    /// @param depth The depth mode.
    ///
    /// @return The depth test.
    ///
    private static DepthStencilState depthTest(Paint.Depth depth) {
        CompareOp test = switch (depth) {
            case VISIBLE -> CompareOp.LESS_THAN_OR_EQUAL;
            case HIDDEN -> CompareOp.GREATER_THAN;
            case ALWAYS -> CompareOp.ALWAYS_PASS;
        };
        
        return new DepthStencilState(test, false);
    }
    
    ///
    /// Starts a new render setup over the given pipeline with the view offset layering of vanilla.
    ///
    /// @param pipeline The pipeline of the render setup.
    ///
    /// @return The render setup builder.
    ///
    private static RenderSetup.RenderSetupBuilder setup(RenderPipeline pipeline) {
        return RenderSetup.builder(pipeline).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
    }
    
    ///
    /// Creates a new render type over the given render setup and keeps the render setup in [#SETUPS].
    ///
    /// @param name  The name of the render type.
    /// @param setup The render setup.
    ///
    /// @return The render type.
    ///
    private static RenderType type(String name, RenderSetup setup) {
        RenderType type = RenderType.create(name, setup);
        ShapePipelines.SETUPS.put(type, setup);
        return type;
    }
    
    ///
    /// Registers the given pipelines with the given registrar and assigns them the given shading if iris is present.
    ///
    /// @param registrar The registrar of the registration event.
    /// @param pipelines The pipelines by depth mode.
    /// @param shading   The shading of the pipelines.
    ///
    private static void register(Consumer<RenderPipeline> registrar, Map<Paint.Depth, RenderPipeline> pipelines, Shading shading) {
        boolean iris = ModList.get().isLoaded("iris");
        for (RenderPipeline pipeline : pipelines.values()) {
            registrar.accept(pipeline);
            if (iris) {
                IrisBridge.assign(pipeline, shading);
            }
        }
    }
    
    ///
    /// Gets the line render type of the given depth mode.
    ///
    /// @param depth The depth mode.
    ///
    /// @return The render type.
    ///
    public static RenderType lines(Paint.Depth depth) {
        return ShapePipelines.LINE_TYPES.get(depth);
    }
    
    ///
    /// Gets the untextured quad render type of the given depth mode.
    ///
    /// @param depth The depth mode.
    ///
    /// @return The render type.
    ///
    public static RenderType fill(Paint.Depth depth) {
        return ShapePipelines.FILL_TYPES.get(depth);
    }
    
    ///
    /// Gets the shape render type of the given depth mode.
    ///
    /// @param depth The depth mode.
    ///
    /// @return The render type.
    ///
    public static RenderType shape(Paint.Depth depth) {
        return ShapePipelines.SHAPE_TYPES.get(depth);
    }
    
    ///
    /// Gets the sprite quad render type of the given atlas in the given depth mode.
    ///
    /// @param atlas The identifier of the atlas texture.
    /// @param depth The depth mode.
    ///
    /// @return The render type.
    ///
    public static RenderType sprite(Identifier atlas, Paint.Depth depth) {
        return ShapePipelines.SPRITE_TYPES.apply(atlas, depth);
    }
    
    ///
    /// Every render type of this class created so far.
    ///
    /// @return The render types.
    ///
    public static @UnmodifiableView Set<RenderType> getAllRenderTypes() {
        return Collections.unmodifiableSet(ShapePipelines.SETUPS.keySet());
    }
    
    ///
    /// Gets the textures of the given render type by sampler name.
    ///
    /// @param type The render type, one of this class.
    ///
    /// @return The textures and their samplers by sampler name.
    ///
    /// @throws NullPointerException If the render type is not one of this class.
    ///
    public static Map<String, RenderSetup.TextureAndSampler> getTextures(RenderType type) {
        return Objects.requireNonNull(ShapePipelines.SETUPS.get(type), "Render type '" + type + "' is not a shape render type").getTextures();
    }
    
    ///
    /// Loads this class, which creates the shape pipelines.
    ///
    /// Should only ever be called from [RenderDispatch#listen(IEventBus)], before the pipeline registration event.
    ///
    public static void init() { }
    
    ///
    /// Registers every pipeline but the shape pipelines with the given registrar.
    ///
    /// If iris is present, each pipeline will also be assigned its shading.
    ///
    /// Should only ever be called from [RenderDispatch] on the pipeline registration event.
    ///
    /// @param registrar The registrar of the registration event.
    ///
    public static void register(Consumer<RenderPipeline> registrar) {
        ShapePipelines.register(registrar, ShapePipelines.LINE_PIPELINES, Shading.LINES);
        ShapePipelines.register(registrar, ShapePipelines.FILL_PIPELINES, Shading.BASIC);
        ShapePipelines.register(registrar, ShapePipelines.SPRITE_PIPELINES, Shading.TEXTURED);
    }
}
