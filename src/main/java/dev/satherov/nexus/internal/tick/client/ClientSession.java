package dev.satherov.nexus.internal.tick.client;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.Nexus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

///
/// The login state of the client and all client listeners of every scheduler.
///
/// Ends the client scope of every scheduler on logout.
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
    /// @return `true` if the client is logged into a world.
    ///
    public static boolean isLoggedIn() {
        return ClientSession.loggedIn;
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
    /// @param event The login event.
    ///
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
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
}
