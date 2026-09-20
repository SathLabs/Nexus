package dev.satherov.nexus.gametest.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

///
/// Opens the client's own frame to the pump.
///
@Mixin(Minecraft.class)
public interface MinecraftAccess {

    ///
    /// Runs one frame, ticking the client only if the frame advances the game time.
    ///
    /// @param advanceGameTime If the frame advances the game time.
    ///
    @Invoker("runTick")
    void invokeRunTick(boolean advanceGameTime);
}
