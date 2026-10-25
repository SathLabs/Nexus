package dev.satherov.nexus.internal.render.client;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.api.render.shape.Paint;
import dev.satherov.nexus.internal.render.iris.IrisBridge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

///
/// The library's own pipelines and render types for lines, untextured quads, and sprite quads, each in the three depth modes,
/// over vanilla's shaders.
///
/// If Iris is present, they will be assigned the pack's line, basic, and textured programs.
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
    /// The line render types by depth mode.
    ///
    private static final Map<Paint.Depth, RenderType> LINE_TYPES = Util.makeEnumMap(
            Paint.Depth.class,
            depth -> RenderType.create("nexus_lines", ShapePipelines.setup(ShapePipelines.LINE_PIPELINES.get(depth)).createRenderSetup())
    );

    ///
    /// The untextured quad render types by depth mode.
    ///
    private static final Map<Paint.Depth, RenderType> FILL_TYPES = Util.makeEnumMap(
            Paint.Depth.class,
            depth -> RenderType.create("nexus_fill", ShapePipelines.setup(ShapePipelines.FILL_PIPELINES.get(depth)).sortOnUpload().createRenderSetup())
    );

    ///
    /// The sprite quad render types by atlas and depth mode, created on first use.
    ///
    private static final BiFunction<Identifier, Paint.Depth, RenderType> SPRITE_TYPES = Util.memoize(
            (atlas, depth) -> RenderType.create(
                    "nexus_sprite",
                    ShapePipelines.setup(ShapePipelines.SPRITE_PIPELINES.get(depth)).sortOnUpload().withTexture("Sampler0", atlas).createRenderSetup()
            )
    );

    ///
    /// Creates a new pipeline from the given snippet, with the depth test of the given depth mode and without culling.
    ///
    private static RenderPipeline pipeline(RenderPipeline.Snippet snippet, String kind, Paint.Depth depth) {
        CompareOp test = switch (depth) {
            case VISIBLE -> CompareOp.LESS_THAN_OR_EQUAL;
            case HIDDEN -> CompareOp.GREATER_THAN;
            case ALWAYS -> CompareOp.ALWAYS_PASS;
        };

        return RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "pipeline/" + kind + "_" + depth.name().toLowerCase(Locale.ROOT)))
                .withCull(false)
                .withDepthStencilState(new DepthStencilState(test, false))
                .build();
    }

    ///
    /// Starts a new render setup over the given pipeline, shifted toward the camera like vanilla's lines.
    ///
    private static RenderSetup.RenderSetupBuilder setup(RenderPipeline pipeline) {
        return RenderSetup.builder(pipeline).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
    }

    ///
    /// Registers the given pipelines with the given registrar and assigns each the pack program of the given shading if Iris is present.
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
    /// The render type for lines in the given depth mode.
    ///
    /// @param depth The depth mode.
    ///
    /// @return The render type.
    ///
    public static RenderType lines(Paint.Depth depth) {
        return ShapePipelines.LINE_TYPES.get(depth);
    }

    ///
    /// The render type for untextured quads and triangles in the given depth mode.
    ///
    /// A triangle is written as a quad with its last vertex repeated.
    ///
    /// @param depth The depth mode.
    ///
    /// @return The render type.
    ///
    public static RenderType fill(Paint.Depth depth) {
        return ShapePipelines.FILL_TYPES.get(depth);
    }

    ///
    /// The render type for sprite quads of the given atlas in the given depth mode.
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
    /// Registers every pipeline with the given registrar.
    ///
    /// If Iris is present, each pipeline will also be assigned its pack program.
    ///
    /// Should only ever be called from the registration event that [RenderDispatch#listen(IEventBus)] subscribes to.
    ///
    /// @param registrar The registrar of the registration event.
    ///
    public static void register(Consumer<RenderPipeline> registrar) {
        ShapePipelines.register(registrar, ShapePipelines.LINE_PIPELINES, Shading.LINES);
        ShapePipelines.register(registrar, ShapePipelines.FILL_PIPELINES, Shading.BASIC);
        ShapePipelines.register(registrar, ShapePipelines.SPRITE_PIPELINES, Shading.TEXTURED);
    }
}
