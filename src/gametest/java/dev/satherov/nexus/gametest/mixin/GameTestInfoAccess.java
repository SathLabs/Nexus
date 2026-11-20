package dev.satherov.nexus.gametest.mixin;

import dev.satherov.nexus.gametest.internal.server.TickWindow;

import net.minecraft.gametest.framework.GameTestInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

///
/// Allows reading the tick a server test is currently in and the callbacks it has scheduled.
///
/// @see GameTestSequenceMixin
/// @see TickWindow
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

    ///
    /// The callbacks scheduled on the test, each with the tick it runs on.
    ///
    /// Should never be modified.
    ///
    /// @return The scheduled callbacks of the test.
    ///
    @Accessor("runAtTickTimeMap")
    Object2LongMap<Runnable> getRunAtTickTimeMap();
}
