package dev.satherov.nexus.api.render.pipeline;

import lombok.Getter;

import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.render.Render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

///
/// A pipeline registered through [Render#pipeline(ClientOnly, Shading)].
///
public final class Pipeline {
    
    ///
    /// The pipeline as registered.
    ///
    private final ClientOnly<Supplier<RenderPipeline>> pipeline;
    
    ///
    /// The shading a shader pack gives it.
    ///
    @Getter
    private final Shading shading;
    
    ///
    /// The buffer slices of the bound uniform blocks by block name.
    ///
    private final Map<String, ClientOnly<Supplier<GpuBufferSlice>>> bindingByBlock = new HashMap<>();
    
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param pipeline The pipeline as registered.
    /// @param shading  The shading a shader pack gives it.
    ///
    @ApiStatus.Internal
    public Pipeline(ClientOnly<Supplier<RenderPipeline>> pipeline, Shading shading) {
        this.pipeline = pipeline;
        this.shading = shading;
    }
    
    ///
    /// Supplies the named uniform block to every draw of the pipeline through [Render#draw(ClientOnly)], read once per draw.
    ///
    /// Replaces an earlier binding of the name.
    ///
    /// If the pipeline does not declare a uniform of that name, the first such draw will throw an [IllegalStateException].
    ///
    /// @param block The name of the uniform block.
    /// @param value The buffer slice that holds the block, called once per draw.
    ///
    public void bind(String block, ClientOnly<Supplier<GpuBufferSlice>> value) {
        this.bindingByBlock.put(block, value);
    }
    
    ///
    /// The pipeline as registered.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @return The pipeline as registered.
    ///
    @ApiStatus.Internal
    public ClientOnly<Supplier<RenderPipeline>> getPipeline() {
        return this.pipeline;
    }
    
    ///
    /// The bindings by block name.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @return The buffer slices of the bound uniform blocks by block name.
    ///
    @ApiStatus.Internal
    public @UnmodifiableView Map<String, ClientOnly<Supplier<GpuBufferSlice>>> getBindings() {
        return Collections.unmodifiableMap(this.bindingByBlock);
    }
}
