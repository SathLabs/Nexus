package dev.satherov.nexus.api.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import com.mojang.serialization.MapCodec;

import java.util.function.Supplier;

///
/// A builder of one object with no sub-objects, for [Registrations#register(ResourceKey, String, Supplier)] and
/// [Registrations#codec(ResourceKey, String, MapCodec)].
///
/// @param <REGISTRY> The type of the objects of the registry.
/// @param <VALUE>    The type of the object.
/// @param <PARENT>   The type of what [#end()] returns.
///
public final class PlainBuilder<REGISTRY, VALUE extends REGISTRY, PARENT> extends EntryBuilder<REGISTRY, VALUE, RegistryEntry<REGISTRY, VALUE>, PARENT, PlainBuilder<REGISTRY, VALUE, PARENT>> {
    
    ///
    /// The supplier of the object, called inside the registry event.
    ///
    private final Supplier<VALUE> supplier;
    
    ///
    /// Starts the object that the given supplier creates inside the registry event.
    ///
    /// @param registrations The registrations to register through.
    /// @param registry      The key of the registry.
    /// @param name          The path of the identifier.
    /// @param parent        What [#end()] returns.
    /// @param supplier      The supplier of the object, called once inside the registry event.
    ///
    public PlainBuilder(Registrations registrations, ResourceKey<? extends Registry<REGISTRY>> registry, String name, PARENT parent, Supplier<VALUE> supplier) {
        super(registrations, registry, name, parent);
        this.supplier = supplier;
    }
    
    ///
    /// Creates the object with the supplier.
    ///
    /// @return The created object.
    ///
    @Override
    protected VALUE create() {
        return this.supplier.get();
    }
    
    ///
    /// Creates the plain entry for the given key.
    ///
    /// @param key The key of the object.
    ///
    /// @return The created entry.
    ///
    @Override
    protected RegistryEntry<REGISTRY, VALUE> createEntry(ResourceKey<REGISTRY> key) {
        return new RegistryEntry<>(key, this) { };
    }
}
