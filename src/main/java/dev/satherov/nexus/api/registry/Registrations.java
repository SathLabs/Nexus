package dev.satherov.nexus.api.registry;

import lombok.Getter;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.internal.registry.EntryRegister;
import dev.satherov.nexus.internal.registry.client.ClientDispatch;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

///
/// A table that holds every entry one mod registers, and starts the builders that register them.
///
/// Every builder should be registered from the mod's constructor, before the registry events run.
///
public final class Registrations {
    
    ///
    /// The handle of the mod.
    ///
    @Getter
    private final NexusMod mod;
    
    ///
    /// Every register of the mod by the key of its registry.
    ///
    private final Map<ResourceKey<? extends Registry<?>>, EntryRegister<?>> registersByKey = new HashMap<>();
    
    ///
    /// Every entry of the mod, in registration order.
    ///
    private final List<RegistryEntry<?, ?>> entries = new ArrayList<>();
    
    ///
    /// Creates the empty registrations of the given mod and subscribes their listeners to the event bus of the mod.
    ///
    /// Should only ever be called from [NexusMod#getRegistrations()].
    ///
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Registrations(NexusMod mod) {
        this.mod = mod;
        
        if (FMLEnvironment.getDist().isClient()) {
            ClientDispatch.listen(mod.getEventBus(), Collections.unmodifiableMap(this.registersByKey));
        }
    }
    
    ///
    /// The register of the given registry, created and subscribed to the event bus of the mod on the first call.
    ///
    /// Meant to be used for a mod's own builders.
    ///
    /// @param registry The key of the registry.
    ///
    /// @return The register of the registry.
    ///
    public <REGISTRY> DeferredRegister<REGISTRY> getRegister(ResourceKey<? extends Registry<REGISTRY>> registry) {
        //noinspection unchecked The register under a key is of the registry of that key.
        return (DeferredRegister<REGISTRY>) this.registersByKey.computeIfAbsent(registry, _ -> {
            EntryRegister<REGISTRY> register = new EntryRegister<>(registry, this.mod.getModId(), this.entries);
            register.register(this.mod.getEventBus());
            return register;
        });
    }
    
    ///
    /// Every entry registered so far, sub-objects included, in registration order.
    ///
    /// @return Every entry registered so far.
    ///
    public @Unmodifiable List<RegistryEntry<?, ?>> getAll() {
        return List.copyOf(this.entries);
    }
    
    ///
    /// Gets every entry of the given registry registered so far, in registration order.
    ///
    /// @param registry The key of the registry.
    ///
    /// @return Every entry of the registry registered so far.
    ///
    public <REGISTRY> @Unmodifiable List<RegistryEntry<REGISTRY, ? extends REGISTRY>> getAll(ResourceKey<? extends Registry<REGISTRY>> registry) {
        //noinspection unchecked An entry is of the registry that its key is for.
        return this.entries.stream()
                .filter(entry -> entry.getKey().isFor(registry))
                .<RegistryEntry<REGISTRY, ? extends REGISTRY>>map(entry -> (RegistryEntry<REGISTRY, ? extends REGISTRY>) entry)
                .toList();
    }
    
    ///
    /// Starts one object of any registry, which the given supplier creates inside the registry event.
    ///
    /// @param registry The key of the registry.
    /// @param name     The path of the identifier.
    /// @param supplier The supplier of the object, called once inside the registry event.
    ///
    /// @return The builder of the object.
    ///
    public <REGISTRY, VALUE extends REGISTRY> PlainBuilder<REGISTRY, VALUE, Registrations> register(
            ResourceKey<? extends Registry<REGISTRY>> registry,
            String name,
            Supplier<VALUE> supplier
    ) {
        return new PlainBuilder<>(this, registry, name, this, supplier);
    }
    
    ///
    /// Starts one map codec of a registry whose objects are map codecs, such as condition serializers or global loot
    /// modifier serializers.
    ///
    /// @param registry The key of the registry.
    /// @param name     The path of the identifier.
    /// @param codec    The map codec.
    ///
    /// @return The builder of the map codec.
    ///
    public <ELEMENT, VALUE extends ELEMENT> PlainBuilder<MapCodec<? extends ELEMENT>, MapCodec<VALUE>, Registrations> codec(
            ResourceKey<? extends Registry<MapCodec<? extends ELEMENT>>> registry,
            String name,
            MapCodec<VALUE> codec
    ) {
        return new PlainBuilder<>(this, registry, name, this, () -> codec);
    }
}
