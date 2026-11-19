package dev.satherov.nexus.internal.render.client;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.internal.render.iris.IrisBridge;
import dev.satherov.nexus.internal.render.shader.VanillaSampler;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

///
/// The pipelines and render types of lines, untextured quads, sprite quads, and shapes, one of each per depth state.
///
/// The shapes are the discs, rings, and spheres of the shape program.
///
/// A fully opaque color in [Paint.Depth#VISIBLE] writes the depth, except in a ring.
/// Every render type draws into the item entity target of vanilla, which is the main target while improved transparency is off,
/// except that the shape render types draw into the [PackOverlay] while a shader pack is in use.
///
@UtilityClass
@ApiStatus.Internal
public class ShapePipelines {
    
    ///
    /// The alpha of a fully opaque color.
    ///
    private static final int OPAQUE_ALPHA = 0xFF;
    
    ///
    /// The output target of the shape render types, which is the [PackOverlay] while a shader pack is in use and the item entity target of vanilla otherwise.
    ///
    private static final OutputTarget SHAPE_TARGET = new OutputTarget(
            "nexus_shape",
            () -> PackOverlay.isArmed() ? PackOverlay.target() : OutputTarget.ITEM_ENTITY_TARGET.getRenderTarget()
    );
    
    ///
    /// The snippet of vanilla's lines with a vertex shader that adds the model offset to every position, before the view and the fog distances.
    ///
    private static final RenderPipeline.Snippet LINES_SNIPPET = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withVertexShader(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "core/lines"))
            .buildSnippet();
    
    ///
    /// The line pipelines by depth state.
    ///
    private static final Map<DepthState, RenderPipeline> LINE_PIPELINES = Util.makeEnumMap(
            DepthState.class,
            state -> ShapePipelines.pipeline(ShapePipelines.LINES_SNIPPET, "lines", state)
    );
    
    ///
    /// The untextured quad pipelines by depth state.
    ///
    private static final Map<DepthState, RenderPipeline> FILL_PIPELINES = Util.makeEnumMap(
            DepthState.class,
            state -> ShapePipelines.pipeline(RenderPipelines.DEBUG_FILLED_SNIPPET, "fill", state)
    );
    
    ///
    /// The sprite quad pipelines by depth state.
    ///
    private static final Map<DepthState, RenderPipeline> SPRITE_PIPELINES = Util.makeEnumMap(
            DepthState.class,
            state -> ShapePipelines.pipeline(RenderPipelines.GUI_TEXTURED_SNIPPET, "sprite", state)
    );
    
    ///
    /// The pipelines of the shape program by depth state, which never go through the registration event.
    ///
    private static final Map<DepthState, RenderPipeline> SHAPE_PIPELINES = Util.makeEnumMap(DepthState.class, ShapePipelines::program);
    
    ///
    /// The render setup of every render type created so far.
    ///
    private static final Map<RenderType, RenderSetup> SETUPS = new LinkedHashMap<>();
    
    ///
    /// The depth state of every render type created so far.
    ///
    private static final Map<RenderType, DepthState> STATES = new HashMap<>();
    
    ///
    /// The order the render types should be drawn in, by their depth state and with the line render types last within each.
    ///
    private static final Comparator<RenderType> DRAW_ORDER = Comparator.comparing((RenderType type) -> ShapePipelines.STATES.get(type))
            .thenComparing(type -> type.mode() == VertexFormat.Mode.LINES);
    
    ///
    /// The line render types by depth state.
    ///
    private static final Map<DepthState, RenderType> LINE_TYPES = Util.makeEnumMap(
            DepthState.class,
            state -> ShapePipelines.type("nexus_lines", state, ShapePipelines.setup(ShapePipelines.LINE_PIPELINES.get(state)).createRenderSetup())
    );
    
    ///
    /// The untextured quad render types by depth state.
    ///
    private static final Map<DepthState, RenderType> FILL_TYPES = Util.makeEnumMap(
            DepthState.class,
            state -> ShapePipelines.type("nexus_fill", state, ShapePipelines.setup(ShapePipelines.FILL_PIPELINES.get(state)).sortOnUpload().createRenderSetup())
    );
    
    ///
    /// The shape render types by depth state, which draw into [#SHAPE_TARGET] since their pipelines are own-shaded.
    ///
    private static final Map<DepthState, RenderType> SHAPE_TYPES = Util.makeEnumMap(
            DepthState.class,
            state -> ShapePipelines.type(
                    "nexus_shape",
                    state,
                    ShapePipelines.setup(ShapePipelines.SHAPE_PIPELINES.get(state))
                            .sortOnUpload()
                            .setOutputTarget(ShapePipelines.SHAPE_TARGET)
                            .createRenderSetup()
            )
    );
    
    ///
    /// The sprite quad render types by atlas and depth state, created on first use.
    ///
    private static final BiFunction<Identifier, DepthState, RenderType> SPRITE_TYPES = Util.memoize(
            (atlas, state) -> ShapePipelines.type(
                    "nexus_sprite",
                    state,
                    ShapePipelines.setup(ShapePipelines.SPRITE_PIPELINES.get(state)).sortOnUpload().withTexture(VanillaSampler.TEXTURE.getGlslName(), atlas).createRenderSetup()
            )
    );
    
    ///
    /// Creates a new pipeline from the given snippet without culling and with the given depth state.
    ///
    /// @param snippet The snippet to build on.
    /// @param kind    The kind of shape, part of the identifier of the pipeline.
    /// @param state   The depth state.
    ///
    /// @return The pipeline.
    ///
    private static RenderPipeline pipeline(RenderPipeline.Snippet snippet, String kind, DepthState state) {
        return RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "pipeline/" + kind + "_" + state.name().toLowerCase(Locale.ROOT)))
                .withCull(false)
                .withDepthStencilState(state.depthStencil)
                .build();
    }
    
    ///
    /// Creates the pipeline of the shape program of Nexus with translucent blending, no culling, and the given depth state.
    ///
    /// The pipeline takes vanilla's Globals block from its state and is stored through [RenderDispatch#program(NexusMod, String, RenderPipeline.Snippet, Shading)] with [Shading#OWN].
    ///
    /// @param state The depth state.
    ///
    /// @return The pipeline.
    ///
    private static RenderPipeline program(DepthState state) {
        RenderPipeline.Snippet snippet = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withDepthStencilState(state.depthStencil)
                .buildSnippet();
        return RenderDispatch.program(Nexus.getMod(), "shape", snippet, Shading.OWN).getPipeline().get().get();
    }
    
    ///
    /// Starts a new render setup over the given pipeline with the view offset layering of vanilla, drawn into the item entity target.
    ///
    /// @param pipeline The pipeline of the render setup.
    ///
    /// @return The render setup builder.
    ///
    private static RenderSetup.RenderSetupBuilder setup(RenderPipeline pipeline) {
        return RenderSetup.builder(pipeline).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET);
    }
    
    ///
    /// Creates a new render type over the given render setup and keeps the render setup in [#SETUPS] and the depth state in [#STATES].
    ///
    /// @param name  The name of the render type.
    /// @param state The depth state of the pipeline of the render setup.
    /// @param setup The render setup.
    ///
    /// @return The render type.
    ///
    private static RenderType type(String name, DepthState state, RenderSetup setup) {
        RenderType type = RenderType.create(name, setup);
        ShapePipelines.SETUPS.put(type, setup);
        ShapePipelines.STATES.put(type, state);
        return type;
    }
    
    ///
    /// Registers the given pipelines with the given registrar and assigns them the given shading if iris is present.
    ///
    /// @param registrar The registrar of the registration event.
    /// @param pipelines The pipelines by depth state.
    /// @param shading   The shading of the pipelines.
    ///
    private static void register(Consumer<RenderPipeline> registrar, Map<DepthState, RenderPipeline> pipelines, Shading shading) {
        boolean iris = ModList.get().isLoaded("iris");
        for (RenderPipeline pipeline : pipelines.values()) {
            registrar.accept(pipeline);
            if (iris) {
                IrisBridge.assign(pipeline, shading);
            }
        }
    }
    
    ///
    /// Gets the line render type of the given depth mode in the given color.
    ///
    /// @param depth The depth mode.
    /// @param argb  The ARGB color of the lines.
    ///
    /// @return The render type.
    ///
    public static RenderType lines(Paint.Depth depth, int argb) {
        return ShapePipelines.LINE_TYPES.get(DepthState.of(depth, argb));
    }
    
    ///
    /// Gets the untextured quad render type of the given depth mode in the given color.
    ///
    /// @param depth The depth mode.
    /// @param argb  The ARGB color of the quads.
    ///
    /// @return The render type.
    ///
    public static RenderType fill(Paint.Depth depth, int argb) {
        return ShapePipelines.FILL_TYPES.get(DepthState.of(depth, argb));
    }
    
    ///
    /// Gets the shape render type of the given depth mode in the given color.
    ///
    /// @param depth The depth mode.
    /// @param argb  The ARGB color of the shapes.
    ///
    /// @return The render type.
    ///
    public static RenderType shape(Paint.Depth depth, int argb) {
        return ShapePipelines.SHAPE_TYPES.get(DepthState.of(depth, argb));
    }
    
    ///
    /// Gets the shape render type of a ring in the given depth mode, which never writes the depth.
    ///
    /// @param depth The depth mode.
    ///
    /// @return The render type.
    ///
    public static RenderType ring(Paint.Depth depth) {
        return ShapePipelines.SHAPE_TYPES.get(DepthState.of(depth));
    }
    
    ///
    /// Gets the sprite quad render type of the given atlas in the given depth mode and tint.
    ///
    /// @param atlas The identifier of the atlas texture.
    /// @param depth The depth mode.
    /// @param argb  The ARGB color the sprites are tinted with.
    ///
    /// @return The render type.
    ///
    public static RenderType sprite(Identifier atlas, Paint.Depth depth, int argb) {
        return ShapePipelines.SPRITE_TYPES.apply(atlas, DepthState.of(depth, argb));
    }
    
    ///
    /// Every render type of this class created so far, in the order they should be drawn.
    ///
    /// The render types come in the order of their depth: [Paint.Depth#HIDDEN], then [Paint.Depth#VISIBLE] in a fully opaque color,
    /// then [Paint.Depth#VISIBLE] in a translucent color, and [Paint.Depth#ALWAYS] last.
    /// Within each depth, the line render types come last.
    ///
    /// @return The render types.
    ///
    public static @Unmodifiable List<RenderType> getAllRenderTypes() {
        return ShapePipelines.SETUPS.keySet().stream().sorted(ShapePipelines.DRAW_ORDER).toList();
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
        return Objects.requireNonNull(ShapePipelines.SETUPS.get(type), () -> "Render type '" + type + "' is not a shape render type").getTextures();
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
    
    ///
    /// The depth test and depth write that a pipeline is built with, in the order their render types should be drawn.
    ///
    @RequiredArgsConstructor
    private enum DepthState {
        ///
        /// The state of [Paint.Depth#HIDDEN], which passes only behind the depth and does not write it.
        ///
        HIDDEN(new DepthStencilState(CompareOp.GREATER_THAN, false)),
        
        ///
        /// The state of [Paint.Depth#VISIBLE] in a fully opaque color, which tests the depth and writes it.
        ///
        OPAQUE(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true)),
        
        ///
        /// The state of [Paint.Depth#VISIBLE] in a translucent color, which tests the depth without writing it.
        ///
        VISIBLE(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false)),
        
        ///
        /// The state of [Paint.Depth#ALWAYS], which skips the depth test and does not write the depth.
        ///
        ALWAYS(new DepthStencilState(CompareOp.ALWAYS_PASS, false));
        
        ///
        /// The depth test and depth write of the state.
        ///
        private final DepthStencilState depthStencil;
        
        ///
        /// Gets the state of the given depth mode in the given color.
        ///
        /// @param depth The depth mode.
        /// @param argb  The ARGB color drawn with the state.
        ///
        /// @return The state.
        ///
        private static DepthState of(Paint.Depth depth, int argb) {
            return depth == Paint.Depth.VISIBLE && ARGB.alpha(argb) == ShapePipelines.OPAQUE_ALPHA ? DepthState.OPAQUE : DepthState.of(depth);
        }
        
        ///
        /// Gets the state of the given depth mode that does not write the depth.
        ///
        /// @param depth The depth mode.
        ///
        /// @return The state.
        ///
        private static DepthState of(Paint.Depth depth) {
            return switch (depth) {
                case VISIBLE -> DepthState.VISIBLE;
                case HIDDEN -> DepthState.HIDDEN;
                case ALWAYS -> DepthState.ALWAYS;
            };
        }
    }
}
