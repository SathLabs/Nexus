package dev.satherov.nexus.gametest.mixin;

import net.minecraft.gametest.framework.GameTestInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

///
/// Allows reading the tick a server test is currently in.
///
/// @see GameTestSequenceMixin
///
@Mixin(GameTestInfo.class)
public interface GameTestInfoAccess {

    ///
    /// Invokes the package-private `getTick` of the test.
    ///
    /// @return The tick the test is currently in.
    ///
    @Invoker("getTick")
    int invokeGetTick();
}
