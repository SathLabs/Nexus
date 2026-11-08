package dev.satherov.nexus.internal.component;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.Nexus;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

///
/// A number that changes whenever the registries that a filter reads through change.
///
/// The registries change with a new server, a new connection of the client, or an update of their tags.
///
@UtilityClass
@ApiStatus.Internal
@EventBusSubscriber(modid = Nexus.MOD_ID)
public class LookupGeneration {
    
    ///
    /// The owner of the registries while no server is running and no world is loaded on the client.
    ///
    private static final Object NO_OWNER = new Object();
    
    ///
    /// The number of the current generation.
    ///
    private static final AtomicInteger GENERATION = new AtomicInteger();
    
    ///
    /// The owner of the registries that the current generation started with.
    ///
    private static volatile Object owner = LookupGeneration.NO_OWNER;
    
    ///
    /// Gets the number of the current generation, starting a new one if the owner of the registries changed.
    ///
    /// Two owners only match if they are the same object.
    ///
    /// @return The number of the current generation.
    ///
    public static int current() {
        Object found = LookupGeneration.findOwner();
        if (found != LookupGeneration.owner) {
            // Count first, so a thread that sees the new owner also sees the new generation.
            LookupGeneration.GENERATION.incrementAndGet();
            LookupGeneration.owner = found;
        }
        
        return LookupGeneration.GENERATION.get();
    }
    
    ///
    /// Starts a new generation after the tags of the registries were updated.
    ///
    /// @param event The event of the tag update.
    ///
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        LookupGeneration.GENERATION.incrementAndGet();
    }
    
    ///
    /// Finds the owner of the registries that filters read through right now.
    ///
    /// The owner is the running server, or the client's lookup of the dimension types if no server is running,
    /// or [#NO_OWNER] if neither exists.
    ///
    /// @return The owner of the registries.
    ///
    private static Object findOwner() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server;
        }
        
        // The client gets a new registry of dimension types with every connection, while the built-in registries stay the same object.
        return Objects.requireNonNullElse(CommonHooks.resolveLookup(Registries.DIMENSION_TYPE), LookupGeneration.NO_OWNER);
    }
}
