package dev.satherov.nexus.internal.tick.client;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.Nexus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

///
/// The login state of the client, the client level that a packet handler builds to switch to, and all client listeners of every scheduler.
///
/// Ends the client scope of every scheduler on logout, once the client has no player anymore, or when the client logs in again without either.
///
@UtilityClass
@ApiStatus.Internal
@EventBusSubscriber(value = Dist.CLIENT, modid = Nexus.MOD_ID)
public class ClientSession {
    
    ///
    /// The client listeners of every scheduler that subscribed to the client scope.
    ///
    private static final List<ClientTicks> CLIENT_TICKS = new ArrayList<>();
    
    ///
    /// If the client is logged into a world.
    ///
    private static boolean loggedIn;
    
    ///
    /// If a packet handler that switches the client to a new level is running.
    ///
    private static boolean switching;
    
    ///
    /// If the running packet handler already built the level to switch to.
    ///
    private static boolean recorded;
    
    ///
    /// The number of client level loads that are running, one inside the other.
    ///
    private static int loads;
    
    ///
    /// The client level that the running packet handler built to switch to, or `null` if there is none.
    ///
    private static @Nullable Level loaded;
    
    ///
    /// @return `true` if the client is logged into a world and has a player in it.
    ///
    public static boolean isLoggedIn() {
        return ClientSession.loggedIn && Minecraft.getInstance().player != null;
    }
    
    ///
    /// If the given level is the one that the client is currently in, or the one that the running packet handler built to switch to.
    ///
    /// The handler switches to a level right after building it, so that level is held from its load event on.
    /// A level that a mod builds for itself outside such a handler, inside the load of another level, or after the first level of the handler, is not held.
    ///
    /// @param level The level.
    ///
    /// @return `true` if the client holds the level.
    ///
    public static boolean isHeld(Level level) {
        return ClientTicks.isCurrentLevel(level) || ClientSession.loaded == level;
    }
    
    ///
    /// If the given level is the one that the running packet handler built to switch to.
    ///
    /// @param level The level.
    ///
    /// @return `true` if the client switches to the level.
    ///
    public static boolean isSwitchingTo(LevelAccessor level) {
        return ClientSession.loaded == level;
    }
    
    ///
    /// If the running packet handler already built a level to switch to, other than the given level.
    ///
    /// @param level The level.
    ///
    /// @return `true` if the client switches from the level to another one.
    ///
    public static boolean isSwitchingFrom(LevelAccessor level) {
        return ClientSession.recorded && ClientSession.loaded != level;
    }
    
    ///
    /// Adds the given client listener.
    ///
    /// The scope will end when the client logs out.
    ///
    /// @param ticks The client listener of a scheduler.
    ///
    public static void add(ClientTicks ticks) {
        ClientSession.CLIENT_TICKS.add(ticks);
    }
    
    ///
    /// Marks the client as logged into a world.
    ///
    /// Ends the client scope of every scheduler first if the client is still marked as logged in.
    ///
    /// Runs before every other listener of the login, so all of them can already reach the client scope.
    ///
    /// @param event The login event.
    ///
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // The server started the configuration phase again and the client got back into a world before the next client tick could notice that it had left.
        if (ClientSession.loggedIn) {
            ClientSession.CLIENT_TICKS.forEach(ClientTicks::endScope);
        }
        
        ClientSession.loggedIn = true;
    }
    
    ///
    /// Marks the client as logged out.
    ///
    /// Runs before every other listener of the logout, so none of them can start a client scope.
    ///
    /// @param event The logout event.
    ///
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSession.loggedIn = false;
    }
    
    ///
    /// Ends the client scope of every scheduler.
    ///
    /// Runs after every other listener of the logout, so it also ends a scope that a listener started before the client was marked as logged out.
    ///
    /// @param event The logout event.
    ///
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSession.CLIENT_TICKS.forEach(ClientTicks::endScope);
    }
    
    ///
    /// Marks the client as logged out and ends the client scope of every scheduler if the client lost its player without logging out.
    ///
    /// The client only leaves its world without logging out when the server starts the configuration phase again.
    ///
    /// @param event The tick event.
    ///
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (ClientSession.loggedIn && Minecraft.getInstance().player == null) {
            ClientSession.loggedIn = false;
            ClientSession.CLIENT_TICKS.forEach(ClientTicks::endScope);
        }
    }
    
    ///
    /// Runs the given packet handler, which may build a client level and switch to it.
    ///
    /// Should only ever be called from the mixin of `ClientPacketListener` on `handleLogin` and `handleRespawn`.
    ///
    /// @param handler The packet handler.
    ///
    public static void switchLevel(Runnable handler) {
        // The handler first runs on the network thread, which only hands the packet to the client thread.
        if (ClientSession.switching || !ClientTicks.getEventLoop().isSameThread()) {
            handler.run();
            return;
        }
        
        ClientSession.switching = true;
        ClientSession.recorded = false;
        try {
            handler.run();
        } finally {
            ClientSession.switching = false;
            ClientSession.recorded = false;
            ClientSession.loaded = null;
        }
    }
    
    ///
    /// Posts the load of the given client level, and records the level as the one to switch to if it is the first level that a running packet handler builds and no other level is loading.
    ///
    /// Should only ever be called from the mixin of `ClientLevel` on its constructor.
    ///
    /// @param level The client level that loads.
    /// @param post  The post of its load event.
    ///
    /// @return The posted load event.
    ///
    public static Event load(ClientLevel level, Supplier<Event> post) {
        // A level that a mod builds on a thread of its own is never switched to, and the counts belong to the client thread.
        if (!ClientTicks.getEventLoop().isSameThread()) {
            return post.get();
        }
        
        if (ClientSession.switching && !ClientSession.recorded && ClientSession.loads == 0) {
            ClientSession.recorded = true;
            ClientSession.loaded = level;
        }
        
        ClientSession.loads++;
        try {
            return post.get();
        } finally {
            ClientSession.loads--;
        }
    }
    
    ///
    /// Forgets the client level to switch to if it is the one that unloaded.
    ///
    /// @param event The unload event.
    ///
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() == ClientSession.loaded) {
            ClientSession.loaded = null;
        }
    }
}
