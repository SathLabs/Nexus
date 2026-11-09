package dev.satherov.nexus.mixin.component;

import dev.satherov.nexus.internal.component.PathReaders;

import net.neoforged.neoforge.registries.GameData;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Collects the path readers once neoforge froze the registries.
///
@Mixin(GameData.class)
public abstract class GameDataMixin {
    
    ///
    /// Collects the path readers after the registries are frozen.
    ///
    /// @param callback The callback of the injection.
    ///
    @Inject(
            method = "freezeData",
            at = @At("RETURN")
    )
    private static void nexus$collectPathReaders(CallbackInfo callback) {
        PathReaders.collect();
    }
}
