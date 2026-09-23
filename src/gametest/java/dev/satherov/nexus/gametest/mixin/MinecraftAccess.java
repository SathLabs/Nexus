package dev.satherov.nexus.gametest.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

///
/// Allows manually controlling the flow of ticks.
///
@Mixin(Minecraft.class)
public interface MinecraftAccess {
    
    ///
    /// Advances one frame, ticking the client only if the frame actually advances the game time.
    ///
    /// @param advanceGameTime If the frame advances the game time.
    ///
    @Invoker("runTick")
    void invokeRunTick(boolean advanceGameTime);
}
