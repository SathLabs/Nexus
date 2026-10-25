package dev.satherov.nexus.internal.tick.client;

import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.TickScope;

import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.client.Minecraft;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// The client listeners of one mod's scheduler.
///
/// Passes the client scope on the client tick unless the game is paused.
///
/// Should only ever be loaded on the client.
///
@ApiStatus.Internal
public final class ClientTicks {
    
    ///
    /// The scheduler the client scope belongs to.
    ///
    private final Scheduler scheduler;
    
    ///
    /// The scope of the current session, or `null` if there is none.
    ///
    private @Nullable TickScope scope;
    
    ///
    /// Subscribes the listener for the given scheduler.
    ///
    /// The client scope will end when the client logs out.
    ///
    /// @param scheduler The scheduler the client scope belongs to.
    ///
    public ClientTicks(Scheduler scheduler) {
        this.scheduler = scheduler;
        
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, this::onClientTick);
        ClientSession.add(this);
    }
    
    ///
    /// The event loop of the client thread.
    ///
    /// @return The minecraft client.
    ///
    public static BlockableEventLoop<?> getEventLoop() {
        return Minecraft.getInstance();
    }
    
    ///
    /// If the given level is the one that the client is currently in.
    ///
    /// @param level The level.
    ///
    /// @return `true` if the client is currently in the level.
    ///
    public static boolean isCurrentLevel(Level level) {
        return Minecraft.getInstance().level == level;
    }
    
    ///
    /// The scope of the current session, created on the first method call if it doesn't exist.
    ///
    /// @return The scope of the current session.
    ///
    /// @throws IllegalStateException If the client is not currently in any world.
    ///
    public TickScope getScope() {
        if (!ClientSession.isLoggedIn()) {
            throw new IllegalStateException("The client is not in a world");
        }
        
        if (this.scope == null) {
            this.scope = new TickScope(this.scheduler, LogicalSide.CLIENT);
        }
        
        return this.scope;
    }
    
    ///
    /// Ends the scope and forgets it if there was one.
    ///
    /// Should only ever be called from [ClientSession#onLoggedOut(ClientPlayerNetworkEvent.LoggingOut)].
    ///
    public void endScope() {
        if (this.scope != null) {
            this.scope.end();
            this.scope = null;
        }
    }
    
    ///
    /// Passes the client scope if there is one and the game is not paused.
    ///
    private void onClientTick(ClientTickEvent.Post event) {
        if (this.scope != null && !Minecraft.getInstance().isPaused()) {
            this.scope.pass(() -> true);
        }
    }
}
