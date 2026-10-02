package dev.satherov.nexus.internal.registry;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.RegistryEntry;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

///
/// The register of one registry of one mod, whose holders are the entries that the builders create.
///
/// @param <REGISTRY> The type of the objects of the registry.
///
@ApiStatus.Internal
@EventBusSubscriber(modid = Nexus.MOD_ID)
public final class EntryRegister<REGISTRY> extends DeferredRegister<REGISTRY> {
    
    ///
    /// Every registry whose registry event already ran.
    ///
    private static final Set<ResourceKey<? extends Registry<?>>> FIRED_REGISTRIES = new HashSet<>();
    
    ///
    /// Every entry of the mod in registration order, shared by every register of the mod.
    ///
    private final List<RegistryEntry<?, ?>> entries;
    
    ///
    /// The client hooks of the entries of this register, in order, oldest first.
    ///
    private final List<Consumer<IEventBus>> clientHooks = new ArrayList<>();
    
    ///
    /// The entry that the next [#createHolder(ResourceKey, Identifier)] returns, or `null` if it should create a plain holder.
    ///
    private @Nullable RegistryEntry<REGISTRY, ?> pending;
    
    ///
    /// Creates the empty register of the given registry.
    ///
    /// @param registry The key of the registry.
    /// @param modId    The mod id.
    /// @param entries  Every entry of the mod, which this register adds its entries to.
    ///
    public EntryRegister(ResourceKey<? extends Registry<REGISTRY>> registry, String modId, List<RegistryEntry<?, ?>> entries) {
        super(registry, modId);
        this.entries = entries;
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
    /// Adds the given entry, whose object the given factory creates inside the registry event.
    ///
    /// Should only ever be called from [EntryBuilder#register()].
    ///
    /// @param entry   The still unbound entry.
    /// @param factory The factory of the object.
    ///
    /// @throws IllegalStateException If the registry event of the registry already ran.
    ///
    public <VALUE extends REGISTRY> void add(RegistryEntry<REGISTRY, VALUE> entry, Supplier<VALUE> factory) {
        if (EntryRegister.FIRED_REGISTRIES.contains(this.getRegistryKey())) {
            throw new IllegalStateException("Could not register '" + entry.getId() + "' because the registry event of '" + this.getRegistryName() + "' already ran");
        }
        
        this.pending = entry;
        this.register(entry.getName(), factory);
        this.entries.add(entry);
    }
    
    ///
    /// Adds the given client hook, called with the mod bus once the registry event of this register has run.
    ///
    /// @param hook The client hook, called with the mod bus.
    ///
    public void addClientHook(Consumer<IEventBus> hook) {
        this.clientHooks.add(hook);
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
    /// Takes the entry that [#add(RegistryEntry, Supplier)] is adding, or creates a plain holder outside of it.
    ///
    /// @param registryKey The key of the registry.
    /// @param key         The identifier of the object.
    ///
    /// @return The holder of the object.
    ///
    @Override
    protected <I extends REGISTRY> DeferredHolder<REGISTRY, I> createHolder(ResourceKey<? extends Registry<REGISTRY>> registryKey, Identifier key) {
        RegistryEntry<REGISTRY, ?> entry = this.pending;
        if (entry == null) {
            return super.createHolder(registryKey, key);
        }
        
        this.pending = null;
        //noinspection unchecked The entry holds the type that its builder creates.
        return (DeferredHolder<REGISTRY, I>) entry;
    }
}
