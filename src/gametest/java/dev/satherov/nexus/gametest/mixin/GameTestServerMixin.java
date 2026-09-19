package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.level.VoidLevel;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Boots the gametest server into the void level and lets a realtime run tick at the normal rate.
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin extends MinecraftServer {

    /// Never called: the mixin extends [MinecraftServer] only so [#waitAtNormalRate(CallbackInfo)] can call its tick wait.
    private GameTestServerMixin() {
        //noinspection DataFlowIssue The constructor is dropped when the mixin is applied
        super(null, null, null, null, null, null, null, null, null, false);
    }

    /// Vanilla looks the flat preset up inside a lambda of `create`; the regex selector matches them whatever they are numbered.
    @Redirect(
            method = "/^lambda\\$create\\$/",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/levelgen/presets/WorldPresets;FLAT:Lnet/minecraft/resources/ResourceKey;"),
            allow = 1
    )
    private static ResourceKey<WorldPreset> useVoidPreset() {
        return VoidLevel.PRESET;
    }

    /// Under [RunOptions#REALTIME], waits the way [MinecraftServer] does, so the run ticks at the normal rate.
    @Inject(method = "waitUntilNextTick", at = @At("HEAD"), cancellable = true)
    private void waitAtNormalRate(CallbackInfo callback) {
        if (Boolean.getBoolean(RunOptions.REALTIME)) {
            super.waitUntilNextTick();
            callback.cancel();
        }
    }
}
