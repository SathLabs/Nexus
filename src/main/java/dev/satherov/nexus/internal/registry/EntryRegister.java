package dev.satherov.nexus.internal.registry;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.internal.registry.client.ClientDispatch;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

///
/// The register of one registry of one mod, which registers the objects of the entries that the builders create.
///
/// @param <REGISTRY> The type of the objects of the registry.
///
@ApiStatus.Internal
@EventBusSubscriber(modid = Nexus.MOD_ID)
public final class EntryRegister<REGISTRY> {
    
    ///
    /// Every registry whose registry event already ran.
    ///
    private static final Set<ResourceKey<? extends Registry<?>>> FIRED_REGISTRIES = new HashSet<>();
    
    ///
    /// The key of the registry.
    ///
    private final ResourceKey<? extends Registry<REGISTRY>> registryKey;
    
    ///
    /// Every entry of the mod in registration order, shared by every register of the mod.
    ///
    private final List<RegistryEntry<?, ?>> entries;
    
    ///
    /// The identifiers of the entries of this register.
    ///
    private final Set<Identifier> ids = new HashSet<>();
    
    ///
    /// The entries of this register with the factories of their objects, in registration order.
    ///
    private final List<Map.Entry<RegistryEntry<REGISTRY, ?>, Supplier<REGISTRY>>> factories = new ArrayList<>();
    
    ///
    /// The client hooks of the entries of this register.
    ///
    private final List<Consumer<IEventBus>> clientHooks = new ArrayList<>();
    
    ///
    /// Creates the empty register of the given registry and subscribes it to the given event bus.
    ///
    /// @param registryKey The key of the registry.
    /// @param bus         The event bus of the mod.
    /// @param entries     Every entry of the mod, which this register adds its entries to.
    ///
    public EntryRegister(ResourceKey<? extends Registry<REGISTRY>> registryKey, IEventBus bus, List<RegistryEntry<?, ?>> entries) {
        this.registryKey = registryKey;
        this.entries = entries;
        
        bus.addListener(RegisterEvent.class, this::onRegisterObjects);
    }
    
    ///
    /// Records the registry of the given event as one whose registry event already ran.
    ///
    /// Runs on the event bus of Nexus, which gets every registry event before the mods that depend on it.
    ///
    /// @param event The registry event.
    ///
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRegister(RegisterEvent event) {
        EntryRegister.FIRED_REGISTRIES.add(event.getRegistryKey());
    }
    
    ///
    /// Throws an [IllegalStateException] if the registry event of the registry already ran or the given identifier is
    /// already registered.
    ///
    /// @param id The identifier of the object to register.
    ///
    /// @throws IllegalStateException If the registry event of the registry already ran, or if the identifier is already registered.
    ///
    public void checkAddable(Identifier id) {
        if (this.hasFired()) {
            throw new IllegalStateException("Could not register '" + id + "' because the registry event of '" + this.registryKey.identifier() + "' already ran");
        }
        
        if (this.ids.contains(id)) {
            throw new IllegalStateException("'" + id + "' is already registered");
        }
    }
    
    ///
    /// Adds the given entry, whose object the given factory creates inside the registry event.
    ///
    /// Should only ever be called from [Registrations#add(RegistryEntry, Supplier)].
    ///
    /// @param entry   The still unbound entry.
    /// @param factory The factory of the object.
    ///
    /// @throws IllegalStateException If the registry event of the registry already ran, or if the identifier of the entry is already registered.
    ///
    public <VALUE extends REGISTRY> void add(RegistryEntry<REGISTRY, VALUE> entry, Supplier<VALUE> factory) {
        Identifier id = entry.getId();
        this.checkAddable(id);
        this.ids.add(id);
        this.factories.add(Map.entry(entry, factory::get));
        this.entries.add(entry);
    }
    
    ///
    /// Adds the given client hook, called with the mod bus once the registry event of this register has run.
    ///
    /// @param hook The client hook, called with the mod bus.
    ///
    /// @throws IllegalStateException If the registry event of the registry already ran.
    ///
    public void addClientHook(Consumer<IEventBus> hook) {
        if (this.hasFired()) {
            throw new IllegalStateException("Could not add a client hook because the registry event of '" + this.registryKey.identifier() + "' already ran");
        }
        
        this.clientHooks.add(hook);
    }
    
    ///
    /// If the registry event of the registry already ran.
    ///
    private boolean hasFired() {
        return EntryRegister.FIRED_REGISTRIES.contains(this.registryKey);
    }
    
    ///
    /// The client hooks of the entries of this register, in order, oldest first.
    ///
    /// @return The client hooks.
    ///
    public @Unmodifiable List<Consumer<IEventBus>> getClientHooks() {
        return List.copyOf(this.clientHooks);
    }
    
    ///
    /// Registers the objects of the entries of this register in registration order if the given event is the one of its registry.
    ///
    /// Runs at the normal priority, so before [ClientDispatch] runs the client hooks at the lowest.
    ///
    private void onRegisterObjects(RegisterEvent event) {
        this.factories.forEach(pair -> event.register(this.registryKey, pair.getKey().getId(), pair.getValue()));
    }
}
