package dev.satherov.nexus.internal.tick;

import dev.satherov.nexus.api.tick.TickScope;
import dev.satherov.nexus.internal.tick.client.ClientSession;
import dev.satherov.nexus.internal.tick.client.ClientTicks;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

///
/// The event bus listeners of one mod's scheduler.
///
/// Manages the level scopes for tickers per level and the server scope for the server ticker.
///
/// Scopes are dropped when the level unloads or the server stops, and forgotten once another server ticks if that stop never reached them.
///
/// @see ClientTicks
///
@ApiStatus.Internal
public final class Ticks {
    
    ///
    /// All levels unloaded so far, across all schedulers.
    ///
    /// Held weakly so garbage collection can remove entries from here once they're no longer referenced.
    ///
    /// Forcefully dropped during [#onLevelLoad(LevelEvent.Load)] when a level with the same resource key gets loaded,
    /// since we cannot trust that a mod won't do a stupid and keep that reference somewhere forever causing leaks.
    ///
    private static final Set<Level> UNLOADED_LEVELS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    
    ///
    /// The level scopes of both sides by their level.
    /// Safe from both threads of an integrated server.
    ///
    private final Map<Level, TickScope> scopesByLevel = new ConcurrentHashMap<>();
    
    ///
    /// The server scope, or `null` if there is none.
    ///
    private @Nullable TickScope server;
    
    ///
    /// The server that the server scope runs on, or `null` if there is no server scope.
    ///
    private @Nullable MinecraftServer serverOfScope;
    
    ///
    /// Subscribes the listeners.
    ///
    public Ticks() {
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, this::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, this::onServerTick);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, this::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, this::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, this::onServerStopped);
    }
    
    ///
    /// The server that currently runs, or `null` if there is none or its tick loop already ended.
    ///
    /// @return The running server, or `null` if there is none.
    ///
    public static @Nullable MinecraftServer getRunningServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && !server.isStopped() ? server : null;
    }
    
    ///
    /// If the given level is one that the running server or the client currently holds and that has not unloaded yet.
    ///
    /// @param level The level.
    ///
    /// @return `true` if the level is held and has not unloaded yet.
    ///
    public static boolean isHeld(Level level) {
        if (Ticks.UNLOADED_LEVELS.contains(level)) {
            return false;
        }
        
        if (level.isClientSide()) {
            return FMLEnvironment.getDist().isClient() && ClientSession.isHeld(level);
        }
        
        MinecraftServer server = Ticks.getRunningServer();
        return server != null && server.getLevel(level.dimension()) == level;
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
    /// Gets the server scope of the given server, created with the given factory if there is none.
    ///
    /// Forgets the scopes of every other server first.
    ///
    /// @param server  The running server.
    /// @param factory The factory of the scope, called if the server has none.
    ///
    /// @return The server scope.
    ///
    public TickScope getServerScope(MinecraftServer server, Supplier<TickScope> factory) {
        this.forgetStale(server);
        if (this.server == null) {
            this.server = factory.get();
            this.serverOfScope = server;
        }
        
        return this.server;
    }
    
    ///
    /// Passes the scope of the level if it has one and its tick rate manager runs normally,
    /// or only forgets the inactive work of the scope while the level is frozen.
    ///
    private void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        TickScope scope = this.scopesByLevel.get(level);
        if (scope == null) {
            return;
        }
        
        if (level.tickRateManager().runsNormally()) {
            scope.pass(event::hasTime);
        } else {
            scope.prune();
        }
    }
    
    ///
    /// Forgets the scopes of every other server, then passes the server scope if there is one.
    ///
    private void onServerTick(ServerTickEvent.Post event) {
        this.forgetStale(event.getServer());
        if (this.server != null) {
            this.server.pass(event::hasTime);
        }
    }
    
    ///
    /// Forgets every unloaded level with the same resource key as the level that was loaded.
    ///
    private void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        
        Ticks.UNLOADED_LEVELS.removeIf(unloaded -> unloaded.dimension().equals(level.dimension()));
    }
    
    ///
    /// Forgets the level that was unloaded.
    ///
    private void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        
        this.forget(level);
    }
    
    ///
    /// Forgets the scopes of every other server, then ends and forgets the server scope if there is one, and then every scope of a server level that is still left.
    ///
    private void onServerStopped(ServerStoppedEvent event) {
        this.forgetStale(event.getServer());
        if (this.server != null) {
            this.server.end();
            this.server = null;
            this.serverOfScope = null;
        }
        
        // If a listener of the shutdown threw before the levels unloaded, their scopes and bindings would stay around forever.
        for (Level level : this.scopesByLevel.keySet()) {
            if (!level.isClientSide()) {
                this.forget(level);
            }
        }
    }
    
    ///
    /// Forgets the server scope and every server level scope that belong to another server than the given one, without ending them.
    ///
    private void forgetStale(MinecraftServer running) {
        // Their server stopped without our stop listener running, so their thread is gone and they cannot be ended anymore.
        if (this.server != null && this.serverOfScope != running) {
            this.server = null;
            this.serverOfScope = null;
        }
        
        for (Level level : this.scopesByLevel.keySet()) {
            if (level instanceof ServerLevel serverLevel && serverLevel.getServer() != running) {
                Ticks.UNLOADED_LEVELS.add(level);
                this.scopesByLevel.remove(level);
                Bindings.drop(level);
            }
        }
    }
    
    ///
    /// Marks the level as unloaded, ends and removes its scope if it has one, and then drops its bindings.
    ///
    private void forget(Level level) {
        Ticks.UNLOADED_LEVELS.add(level);
        TickScope scope = this.scopesByLevel.remove(level);
        if (scope != null) {
            scope.end();
        }
        
        Bindings.drop(level);
    }
}
