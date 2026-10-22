package dev.satherov.nexus.internal.tick;

import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.TickScope;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.minecraft.world.level.Level;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

///
/// The game bus listeners of one scheduler, which hold its scopes.
///
/// Passes the level scopes on the level tick and the server scope on the server tick.
/// Drops the scope of a level when the level unloads and the server scope when the server stops.
///
/// Registered by the scheduler's constructor.
///
@ApiStatus.Internal
public final class Ticks {
    
    ///
    /// The level scopes by their level.
    ///
    private final Map<Level, TickScope> scopesByLevel = new HashMap<>();
    
    ///
    /// The server scope, or `null` if there is none.
    ///
    private @Nullable TickScope server;
    
    ///
    /// Subscribes the listeners for the given scheduler.
    ///
    /// @param scheduler The scheduler.
    ///
    public Ticks(Scheduler scheduler) {
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, this::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, this::onServerTick);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, this::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, this::onServerStopped);
    }
    
    ///
    /// Gets the scope of the given level, created with the given factory if the level has none.
    ///
    /// @param level   The level.
    /// @param factory The factory of the scope, called if the level has none.
    ///
    /// @return The scope of the level.
    ///
    public TickScope getLevelScope(Level level, Supplier<TickScope> factory) {
        return this.scopesByLevel.computeIfAbsent(level, _ -> factory.get());
    }
    
    ///
    /// Gets the server scope, created with the given factory if there is none.
    ///
    /// @param factory The factory of the scope, called if there is none.
    ///
    /// @return The server scope.
    ///
    public TickScope getServerScope(Supplier<TickScope> factory) {
        if (this.server == null) {
            this.server = factory.get();
        }
        
        return this.server;
    }
    
    ///
    /// Passes the scope of the level if it has one and its tick rate manager runs normally.
    ///
    private void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        TickScope scope = this.scopesByLevel.get(level);
        if (scope != null && level.tickRateManager().runsNormally()) {
            scope.pass(event::hasTime);
        }
    }
    
    ///
    /// Passes the server scope if there is one.
    ///
    private void onServerTick(ServerTickEvent.Post event) {
        if (this.server != null) {
            this.server.pass(event::hasTime);
        }
    }
    
    ///
    /// Ends and forgets the scope of the level if it has one.
    ///
    private void onLevelUnload(LevelEvent.Unload event) {
        TickScope scope = this.scopesByLevel.remove(event.getLevel());
        if (scope != null) {
            scope.end();
        }
    }
    
    ///
    /// Ends and forgets the server scope if there is one.
    ///
    private void onServerStopped(ServerStoppedEvent event) {
        if (this.server != null) {
            this.server.end();
            this.server = null;
        }
    }
}
