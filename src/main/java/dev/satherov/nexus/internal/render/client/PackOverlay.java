package dev.satherov.nexus.internal.render.client;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.internal.render.iris.IrisBridge;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.pipeline.PipelineModifier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

///
/// The target that geometry of [Shading#OWN] pipelines draws into while a shader pack is in use.
///
/// The pack draws the level into targets of its own and then writes its final image over the main target,
/// so own-shaded geometry drawn onto the main target in the middle of the level is lost.
/// The overlay takes that geometry instead, wherever in the level it is drawn, and is blended onto the main target after the level.
///
/// While the level is drawn under a pack, [#MODIFIER] is pushed,
/// so own-shaded pipelines draw with the color target state of [#colorTargetFor(ColorTargetState)] there and with their own everywhere else.
///
/// The first draw into the overlay in a frame clears its color and copies the depth of the main target into it,
/// so the geometry is hidden by the level drawn up to that point.
/// The first draw after the translucent features and the first after the translucent blocks copy the depth again,
/// which replaces any depth the geometry before them wrote.
///
/// The overlay is blended first thing after the level, so geometry that others draw after the level lands on top of it.
///
/// Without a pack, and outside of the level, the overlay is the main target.
///
@Slf4j
@UtilityClass
@ApiStatus.Internal
public class PackOverlay {
    
    ///
    /// The output target that render types over own-shaded pipelines draw into.
    ///
    public static final OutputTarget TARGET = new OutputTarget("nexus_pack_overlay", PackOverlay::target);
    
    ///
    /// The key of the pipeline modifier that swaps an own-shaded pipeline for a copy with the color target state of [#colorTargetFor(ColorTargetState)].
    ///
    public static final ResourceKey<PipelineModifier> MODIFIER = ResourceKey.create(PipelineModifier.MODIFIERS_KEY, Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "pack_overlay"));
    
    ///
    /// Blends the premultiplied color of the overlay onto the main target, like vanilla's entity outline blit.
    ///
    private static final RenderPipeline COMPOSITE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "pipeline/pack_overlay_composite"))
            .withVertexShader("core/screenquad")
            .withFragmentShader("core/blit_screen")
            .withSampler("InSampler")
            .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA), ColorTargetState.WRITE_COLOR))
            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
            .build();
    
    ///
    /// The overlay, created on first use and freed once a frame is drawn without a pack or the level unloads.
    ///
    private static @Nullable TextureTarget overlay;
    
    ///
    /// If the level is being drawn under a shader pack, which is while [#MODIFIER] is pushed.
    /// Set when the frame graph is set up and cleared by [#disarm()].
    ///
    private static boolean armed;
    
    ///
    /// If anything was drawn into the overlay in this frame, which also means its color was cleared.
    ///
    private static boolean seeded;
    
    ///
    /// If the overlay holds the depth of the main target since it was cleared or since the last [#invalidateDepth()].
    ///
    private static boolean depthCopied;
    
    ///
    /// If it was logged that the depth formats of the overlay and the main target differ.
    ///
    private static boolean formatWarned;
    
    ///
    /// The pipeline that blends the overlay onto the main target.
    ///
    /// @return The composite pipeline.
    ///
    public static RenderPipeline getCompositePipeline() {
        return PackOverlay.COMPOSITE;
    }
    
    ///
    /// @return `true` if the level is being drawn under a shader pack.
    ///
    public static boolean isArmed() {
        return PackOverlay.armed;
    }
    
    ///
    /// Gets the output target that a render type over a pipeline of the given shading should draw into.
    ///
    /// Should only ever be called from [Pipeline#getTarget()].
    ///
    /// @param shading The shading of the pipeline.
    ///
    /// @return [#TARGET] for [Shading#OWN], and vanilla's main target otherwise.
    ///
    public static Supplier<OutputTarget> targetFor(Shading shading) {
        return shading == Shading.OWN ? () -> PackOverlay.TARGET : () -> OutputTarget.MAIN_TARGET;
    }
    
    ///
    /// Gets the color target state that an own-shaded pipeline needs to blend over the frame through the overlay like the given one blends onto the main target.
    ///
    /// A color target state that writes no color stays as it is.
    /// For any other, the color factors stay and the alpha factors become:
    /// - Zero and one if the destination color factor is one, or they stay if alpha is not written.
    /// - One and one minus the source alpha for any other blend function, with alpha written.
    ///
    /// A color target state without a blend function only has alpha written.
    ///
    /// @param state The color target state of the pipeline.
    ///
    /// @return The color target state for the overlay, equal to the given one if it already blends alike.
    ///
    public static ColorTargetState colorTargetFor(ColorTargetState state) {
        if ((state.writeMask() & ColorTargetState.WRITE_COLOR) == 0) {
            return state;
        }
        
        if (state.blendFunction().isEmpty()) {
            return new ColorTargetState(Optional.empty(), state.writeMask() | ColorTargetState.WRITE_ALPHA);
        }
        
        BlendFunction blend = state.blendFunction().get();
        if (blend.destColor() == DestFactor.ONE && !state.writeAlpha()) {
            return state;
        }
        
        if (blend.destColor() == DestFactor.ONE) {
            BlendFunction additive = new BlendFunction(blend.sourceColor(), DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE);
            return new ColorTargetState(Optional.of(additive), state.writeMask());
        }
        
        BlendFunction over = new BlendFunction(blend.sourceColor(), blend.destColor(), SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA);
        return new ColorTargetState(Optional.of(over), state.writeMask() | ColorTargetState.WRITE_ALPHA);
    }
    
    ///
    /// Checks if an own-shaded pipeline with the given color target state, drawn with that of [#colorTargetFor(ColorTargetState)],
    /// blends over the frame through the overlay like it blends onto the main target.
    ///
    /// It does for a color target state:
    /// - That writes neither color nor alpha.
    /// - With a destination color factor of one and a source color factor that doesn't read the target.
    /// - With a destination color factor of one minus the source alpha, every color channel written, and a source color factor that doesn't read the target.
    /// - Without a blend function and with every color channel written, if every fragment has an alpha of one.
    ///
    /// @param state The color target state of the pipeline.
    ///
    /// @return `true` if the pipeline blends alike.
    ///
    public static boolean blendsAlike(ColorTargetState state) {
        int color = state.writeMask() & ColorTargetState.WRITE_COLOR;
        if (color == 0) {
            return !state.writeAlpha();
        }
        
        if (state.blendFunction().isEmpty()) {
            return color == ColorTargetState.WRITE_COLOR;
        }
        
        BlendFunction blend = state.blendFunction().get();
        // The overlay starts out empty, so these factors read something else there than on the main target.
        boolean readsTarget = switch (blend.sourceColor()) {
            case DST_ALPHA, DST_COLOR, ONE_MINUS_DST_ALPHA, ONE_MINUS_DST_COLOR, SRC_ALPHA_SATURATE -> true;
            default -> false;
        };
        if (readsTarget) {
            return false;
        }
        
        return blend.destColor() == DestFactor.ONE || (blend.destColor() == DestFactor.ONE_MINUS_SRC_ALPHA && color == ColorTargetState.WRITE_COLOR);
    }
    
    ///
    /// Arms the overlay for the frame and pushes [#MODIFIER] if a shader pack is in use, and frees the overlay otherwise.
    ///
    /// Should only ever be called from [RenderDispatch] when the frame graph of a level is set up.
    ///
    public static void arm() {
        // A level drawn without the event after it, like a second view of the level, leaves the overlay armed.
        PackOverlay.disarm();
        PackOverlay.armed = ModList.get().isLoaded("iris") && IrisBridge.isShaderPackInUse();
        if (PackOverlay.armed) {
            RenderSystem.pushPipelineModifier(PackOverlay.MODIFIER);
        } else {
            PackOverlay.release();
        }
    }
    
    ///
    /// Disarms the overlay and pops [#MODIFIER] if the overlay is armed.
    ///
    /// Should only ever be called from this class and from [RenderDispatch] when a frame ends,
    /// since NeoForge throws at the end of a frame that leaves a pipeline modifier pushed.
    ///
    public static void disarm() {
        if (PackOverlay.armed) {
            RenderSystem.popPipelineModifier();
        }
        
        PackOverlay.armed = false;
        PackOverlay.seeded = false;
    }
    
    ///
    /// Makes the next draw into the overlay copy the depth of the main target again.
    ///
    /// Should only ever be called from [RenderDispatch] after the translucent features and after the translucent blocks.
    ///
    public static void invalidateDepth() {
        PackOverlay.depthCopied = false;
    }
    
    ///
    /// Gets the render target that own-shaded geometry draws into now.
    ///
    /// While armed, the first call of a frame sizes the overlay to the main target and clears its color.
    /// That call and the first call after [#invalidateDepth()] copy the depth of the main target into it.
    /// Must not be called inside an open render pass.
    ///
    /// If the depth formats of the overlay and the main target differ,
    /// which happens when NeoForge's option of a reduced depth stencil format changed since the main target was created,
    /// it is logged once and the main target is returned.
    ///
    /// @return The overlay while the level is drawn under a shader pack, and the main target otherwise.
    ///
    public static RenderTarget target() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (!PackOverlay.armed) {
            return main;
        }
        
        TextureTarget target = PackOverlay.overlay;
        if (target == null) {
            target = new TextureTarget("Nexus pack overlay", main.width, main.height, true, main.useStencil);
            PackOverlay.overlay = target;
        }
        
        if (!PackOverlay.seeded) {
            if (target.width != main.width || target.height != main.height) {
                target.resize(main.width, main.height);
            }
            
            TextureFormat format = Objects.requireNonNull(target.getDepthTexture(), "the depth texture of the pack overlay").getFormat();
            TextureFormat mainFormat = Objects.requireNonNull(main.getDepthTexture(), "the depth texture of the main target").getFormat();
            if (format != mainFormat) {
                if (!PackOverlay.formatWarned) {
                    PackOverlay.log.warn("Own-shaded geometry loses its color under the shader pack, since the depth format '{}' of the pack overlay differs from the format '{}' of the main target", format, mainFormat);
                    PackOverlay.formatWarned = true;
                }
                
                return main;
            }
            
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .clearColorTexture(Objects.requireNonNull(target.getColorTexture(), "the color texture of the pack overlay"), 0);
            PackOverlay.seeded = true;
            PackOverlay.depthCopied = false;
        }
        
        if (!PackOverlay.depthCopied) {
            target.copyDepthFrom(main);
            PackOverlay.depthCopied = true;
        }
        
        return target;
    }
    
    ///
    /// Blends the overlay onto the main target if anything was drawn into it in this frame, and then disarms it.
    ///
    /// Should only ever be called from [RenderDispatch] after the level, once the shader pack has written its final image.
    ///
    public static void composite() {
        boolean drawn = PackOverlay.armed && PackOverlay.seeded;
        PackOverlay.disarm();
        TextureTarget target = PackOverlay.overlay;
        if (!drawn || target == null) {
            return;
        }
        
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        //noinspection DataFlowIssue Both views only become null once their target is destroyed, which neither is while the level draws.
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "Nexus pack overlay", main.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(PackOverlay.COMPOSITE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, 3);
        }
    }
    
    ///
    /// Frees the overlay until the next draw into it.
    ///
    /// Should only ever be called from [#arm()] and from [RenderDispatch] when a client level unloads.
    ///
    public static void release() {
        TextureTarget target = PackOverlay.overlay;
        if (target != null) {
            target.destroyBuffers();
            PackOverlay.overlay = null;
        }
    }
}
