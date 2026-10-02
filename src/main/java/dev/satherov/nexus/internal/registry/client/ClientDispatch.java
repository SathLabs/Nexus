package dev.satherov.nexus.internal.registry.client;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.registry.block.PlainBlockEntry;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.registry.EntryRegister;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

///
/// The dispatch of the client hooks that the builders of one mod stored.
///
/// Should only ever be loaded on the client.
///
@UtilityClass
@ApiStatus.Internal
public class ClientDispatch {
    
    ///
    /// Listens on the given event bus for [RegisterEvent], after every other listener, and runs the `client(...)` hooks
    /// of the register of its registry.
    ///
    /// @param bus       The event bus of the mod.
    /// @param registers Every register of the mod by the key of its registry.
    ///
    public static void listen(IEventBus bus, Map<ResourceKey<? extends Registry<?>>, EntryRegister<?>> registers) {
        bus.addListener(EventPriority.LOWEST, RegisterEvent.class, event -> ClientDispatch.runClientHooks(bus, registers.get(event.getRegistryKey())));
    }
    
    ///
    /// Runs the `client(...)` hooks of the given register, if there is one, with the given event bus.
    ///
    private static void runClientHooks(IEventBus bus, @Nullable EntryRegister<?> register) {
        if (register == null) {
            return;
        }
        
        register.getClientHooks().forEach(hook -> hook.accept(bus));
    }
    
    ///
    /// Registers the given tint sources for the block of the given entry once the block tint sources are registered.
    ///
    /// @param bus   The event bus of the mod.
    /// @param block The entry of the block.
    /// @param tints The tint sources, where the index corresponds to the tint index.
    ///
    public static void tint(IEventBus bus, PlainBlockEntry<?> block, ClientOnly<List<BlockTintSource>> tints) {
        bus.addListener(RegisterColorHandlersEvent.BlockTintSources.class, event -> event.register(tints.get(), block.get()));
    }
}
