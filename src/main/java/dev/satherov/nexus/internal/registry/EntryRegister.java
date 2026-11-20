package dev.satherov.nexus.internal.registry;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.internal.registry.client.ClientDispatch;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    /// Every registry whose registry events have already fired.
    ///
    private static final Set<ResourceKey<? extends Registry<?>>> FIRED_REGISTRIES = new HashSet<>();
    
    ///
    /// The resource key of the registry.
    ///
    private final ResourceKey<? extends Registry<REGISTRY>> registryKey;
    
    ///
    /// A reference to the list of all entries of the mod in registration order.
    ///
    /// This also contains entries which do not belong to this registry!
    ///
    /// @see Registrations#entries
    ///
    private final List<RegistryEntry<?, ?>> entries;
    
    ///
    /// A view of every registry whose registry event already ran on the event bus of the mod.
    ///
    /// @see Registrations#firedRegistries
    ///
    private final Set<ResourceKey<? extends Registry<?>>> firedRegistries;
    
    ///
    /// The identifiers of the entries of this register.
    ///
    private final Set<Identifier> ids = new HashSet<>();
    
    ///
    /// The registrations of the entries of this register, called with the registry event in registration order.
    ///
    /// Emptied once the registry event has run.
    ///
    private final List<Consumer<RegisterEvent>> pending = new ArrayList<>();
    
    ///
    /// The client hooks of the entries of this register.
    ///
    /// Emptied once the hooks have run.
    ///
    private final List<Consumer<IEventBus>> clientHooks = new ArrayList<>();
    
    ///
    /// Creates the empty register of the given registry and subscribes it to the given event bus.
    ///
    /// @param registryKey     The key of the registry.
    /// @param bus             The event bus of the mod.
    /// @param entries         A reference to the list of all entries of the mod, which this register adds its entries to.
    /// @param firedRegistries A view of every registry whose registry event already ran on the event bus of the mod.
    ///
    public EntryRegister(
            ResourceKey<? extends Registry<REGISTRY>> registryKey,
            IEventBus bus,
            List<RegistryEntry<?, ?>> entries,
            Set<ResourceKey<? extends Registry<?>>> firedRegistries
    ) {
        this.registryKey = registryKey;
        this.entries = entries;
        this.firedRegistries = firedRegistries;
        
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
    /// @throws IllegalArgumentException If the registry is a datapack registry.
    /// @throws IllegalStateException    If the registry event of the registry already ran, or if the identifier is already registered.
    ///
    public void checkAddable(Identifier id) {
        if (DataPackRegistriesHooks.getDataPackRegistriesWithDimensions().anyMatch(data -> data.key().equals(this.registryKey))) {
            throw new IllegalArgumentException("Could not register '" + id + "' because '" + this.registryKey.identifier() + "' is a datapack registry");
        }
        
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
    /// Should only ever be called from [Registrations#add(RegistryEntry, Supplier, Consumer)].
    ///
    /// @param entry    The still unbound entry.
    /// @param factory  The factory of the object.
    /// @param callback The callback, called with the object right after it is registered.
    ///
    /// @throws IllegalArgumentException If the registry is a datapack registry.
    /// @throws IllegalStateException    If the registry event of the registry already ran, or if the identifier of the entry is already registered.
    ///
    public <VALUE extends REGISTRY> void add(RegistryEntry<REGISTRY, VALUE> entry, Supplier<VALUE> factory, Consumer<VALUE> callback) {
        Identifier id = entry.getId();
        this.checkAddable(id);
        this.ids.add(id);
        this.pending.add(event -> {
            VALUE value = factory.get();
            event.register(this.registryKey, id, () -> value);
            callback.accept(value);
        });
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
        return this.firedRegistries.contains(this.registryKey) || EntryRegister.FIRED_REGISTRIES.contains(this.registryKey);
    }
    
    ///
    /// Runs the client hooks of the entries of this register with the given event bus, in order, oldest first, and then drops them.
    ///
    /// @param bus The event bus of the mod.
    ///
    public void runClientHooks(IEventBus bus) {
        this.clientHooks.forEach(hook -> hook.accept(bus));
        this.clientHooks.clear();
    }
    
    ///
    /// Registers the objects of the entries of this register in registration order if the given event is the one of its registry,
    /// and then drops the registrations.
    ///
    /// Runs at the normal priority, so before [ClientDispatch] runs the client hooks at the lowest.
    ///
    private void onRegisterObjects(RegisterEvent event) {
        if (!event.getRegistryKey().equals(this.registryKey)) {
            return;
        }
        
        this.pending.forEach(registration -> registration.accept(event));
        this.pending.clear();
    }
}
