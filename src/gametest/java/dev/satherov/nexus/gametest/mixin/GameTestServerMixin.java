package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.level.VoidLevel;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Boots the gametest server into the void level and lets a realtime run tick at the normal rate.
///
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin extends MinecraftServer {
    
    ///
    /// Never called.
    ///
    /// The mixin only extends [MinecraftServer] so that [#waitAtNormalRate(CallbackInfo)] can call its tick wait.
    ///
    private GameTestServerMixin() {
        //noinspection DataFlowIssue This is never called.
        super(null, null, null, null, null, null, null, null, null, false);
    }
    
    ///
    /// Replaces the flat preset that vanilla creates the level with by the void preset.
    ///
    /// @return The resource key of the void preset.
    ///
    @Redirect(
            method = "lambda$create$1",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/levelgen/presets/WorldPresets;FLAT:Lnet/minecraft/resources/ResourceKey;",
                    opcode = Opcodes.GETSTATIC
            )
    )
    private static ResourceKey<WorldPreset> useVoidPreset() {
        return VoidLevel.PRESET;
    }
    
    ///
    /// Waits the way [MinecraftServer] does if [RunOptions#REALTIME] is set, so the run ticks at the normal rate.
    ///
    /// @param callback The callback of the injection.
    ///
    @Inject(
            method = "waitUntilNextTick",
            at = @At("HEAD"),
            cancellable = true
    )
    private void waitAtNormalRate(CallbackInfo callback) {
        if (Boolean.getBoolean(RunOptions.REALTIME)) {
            super.waitUntilNextTick();
            callback.cancel();
        }
    }
}
