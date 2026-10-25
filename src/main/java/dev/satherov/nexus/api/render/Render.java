package dev.satherov.nexus.api.render;

import lombok.Getter;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.internal.render.iris.IrisBridge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import org.jetbrains.annotations.ApiStatus;

///
/// What one mod draws and registers with the renderer.
///
/// The registrations are called from the mod's constructor on both sides and do nothing on a dedicated server.
/// The scene, the glows, and the queries throw an [IllegalStateException] there.
///
/// Can be accessed through [NexusMod#getRender()].
///
public final class Render {

    ///
    /// The handle of the mod.
    ///
    @Getter
    private final NexusMod mod;

    ///
    /// Should only ever be called from [NexusMod#getRender()].
    ///
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Render(NexusMod mod) {
        this.mod = mod;
    }

    ///
    /// If a shader pack is replacing vanilla's shaders right now.
    ///
    /// Always `false` without Iris.
    ///
    /// @return `true` if a shader pack is replacing vanilla's shaders right now.
    ///
    public boolean isShaderPackActive() {
        return FMLEnvironment.getDist().isClient() && ModList.get().isLoaded("iris") && IrisBridge.isShaderPackInUse();
    }
}
