package dev.satherov.nexus.internal.render.client;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.render.pipeline.Pipeline;
import dev.satherov.nexus.api.render.pipeline.Shading;
import dev.satherov.nexus.internal.render.iris.IrisBridge;

import net.neoforged.fml.ModList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
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
/// The first draw into the overlay in a frame clears its color and copies the depth of the main target into it,
/// so the geometry is hidden by the level drawn up to that point.
///
/// Without a pack, and outside of the level, the overlay is the main target.
///
@UtilityClass
@ApiStatus.Internal
public class PackOverlay {
    
    ///
    /// The output target that render types over own-shaded pipelines draw into.
    ///
    public static final OutputTarget TARGET = new OutputTarget("nexus_pack_overlay", PackOverlay::target);
    
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
    /// The overlay, created on first use.
    ///
    private static @Nullable TextureTarget overlay;
    
    ///
    /// If the level is being drawn under a shader pack, set when the frame graph is set up and cleared by [#composite()].
    ///
    private static boolean armed;
    
    ///
    /// If anything was drawn into the overlay in this frame, which also means it was cleared and holds the depth of the main target.
    ///
    private static boolean seeded;
    
    ///
    /// The pipeline that blends the overlay onto the main target.
    ///
    /// @return The composite pipeline.
    ///
    public static RenderPipeline getCompositePipeline() {
        return PackOverlay.COMPOSITE;
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
    /// Arms the overlay for the frame if a shader pack is in use.
    ///
    /// Should only ever be called from [RenderDispatch] when the frame graph of a level is set up.
    ///
    public static void arm() {
        PackOverlay.armed = ModList.get().isLoaded("iris") && IrisBridge.isShaderPackInUse();
        PackOverlay.seeded = false;
    }
    
    ///
    /// Gets the render target that own-shaded geometry draws into now.
    ///
    /// While armed, the first call of a frame sizes the overlay to the main target, clears its color and copies the depth of the main target into it.
    /// Must not be called inside an open render pass.
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
            target = new TextureTarget("Nexus pack overlay", main.width, main.height, true);
            PackOverlay.overlay = target;
        }
        
        if (!PackOverlay.seeded) {
            if (target.width != main.width || target.height != main.height) {
                target.resize(main.width, main.height);
            }
            
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .clearColorTexture(Objects.requireNonNull(target.getColorTexture(), "the color texture of the pack overlay"), 0);
            target.copyDepthFrom(main);
            PackOverlay.seeded = true;
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
        PackOverlay.armed = false;
        PackOverlay.seeded = false;
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
}
