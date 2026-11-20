package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.level.VoidLevel;
import dev.satherov.nexus.gametest.internal.measurement.Measurements;
import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.stream.Stream;

///
/// Boots the gametest server into the void level, runs the baseline with every selection, and lets a realtime run tick at the normal rate.
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
    /// Adds the baseline to the tests that a selection matches, if the run registered one.
    ///
    /// The selection rarely matches the baseline itself, so without it a selected run would record no baseline.
    ///
    /// @param registries The registries that hold the tests.
    /// @param selection  The selection that the run was started with.
    /// @param original   The wrapped lookup of the tests that the selection matches.
    ///
    /// @return The tests that the selection matches, and the baseline.
    ///
    @WrapOperation(
            method = "evaluateTestsToRun",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/gametest/framework/GameTestServer;getTestsForSelection(Lnet/minecraft/core/RegistryAccess;Ljava/lang/String;)Ljava/util/stream/Stream;"
            )
    )
    private Stream<Holder.Reference<GameTestInstance>> addBaselineToSelection(RegistryAccess registries, String selection, Operation<Stream<Holder.Reference<GameTestInstance>>> original) {
        Stream<Holder.Reference<GameTestInstance>> baseline = registries.lookupOrThrow(Registries.TEST_INSTANCE).get(Measurements.BASELINE).stream();
        return Stream.concat(original.call(registries, selection), baseline).distinct();
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
