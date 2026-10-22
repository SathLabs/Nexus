package dev.satherov.nexus.api.tick;

import lombok.Getter;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.internal.tick.Ticks;

import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// The tick scopes and budgets of one mod.
///
/// Every method runs on the thread of the side it names.
///
/// Can be reached through [NexusMod#getScheduler()].
///
public final class Scheduler {
    
    ///
    /// The handle of the mod.
    ///
    @Getter
    private final NexusMod mod;
    
    ///
    /// The game bus listeners of the scheduler, which hold its scopes.
    ///
    private final Ticks ticks;
    
    ///
    /// Should only ever be called from [NexusMod#getScheduler()].
    ///
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Scheduler(NexusMod mod) {
        this.mod = mod;
        this.ticks = new Ticks(this);
    }
    
    ///
    /// The server that currently runs, or `null` if there is none or its tick loop ended.
    ///
    private static @Nullable MinecraftServer runningServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && !server.isStopped() ? server : null;
    }
    
    ///
    /// The scope of the given level, on the level's side.
    ///
    /// The scope stays the same object while the level is loaded.
    ///
    /// @param level The level.
    ///
    /// @return The scope of the level.
    ///
    /// @throws IllegalStateException If the level is not one that the running server currently holds.
    ///
    public TickScope in(Level level) {
        MinecraftServer server = Scheduler.runningServer();
        if (server == null || server.getLevel(level.dimension()) != level) {
            throw new IllegalStateException("Level '" + level.dimension().identifier() + "' is not held by the running server");
        }
        
        return this.ticks.getLevelScope(level, () -> new TickScope(this, level));
    }
    
    ///
    /// The scope of the running server, created on first call.
    ///
    /// @return The scope of the running server.
    ///
    /// @throws IllegalStateException If no server is running.
    ///
    public TickScope server() {
        if (Scheduler.runningServer() == null) throw new IllegalStateException("No server is running");
        return this.ticks.getServerScope(() -> new TickScope(this, LogicalSide.SERVER));
    }
}
