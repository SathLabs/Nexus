package dev.satherov.nexus.api.registry.component;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

///
/// A builder of one data component type, saved and synced with a Nexus codec.
///
/// @param <VALUE>  The type of the values of the component.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class DataComponentBuilder<VALUE, PARENT>
        // @formatter:off
        extends EntryBuilder
        <
            DataComponentType<?>,
            DataComponentType<VALUE>,
            DataComponentEntry<VALUE>,
            PARENT,
            DataComponentBuilder<VALUE, PARENT>
        >
        // @formatter:on
{
    
    ///
    /// The vanilla builder of the type.
    ///
    private final DataComponentType.Builder<VALUE> builder;
    
    ///
    /// Starts the data component type `<modid>:<name>` saved and synced with the given codec.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param parent        What [#end()] returns.
    /// @param codec         The codec of the values of the component.
    ///
    public DataComponentBuilder(Registrations registrations, String name, PARENT parent, NexusCodec<VALUE, ?> codec) {
        super(registrations, Registries.DATA_COMPONENT_TYPE, name, parent);
        
        this.builder = DataComponentType.<VALUE>builder()
                .persistent(codec.asDfu())
                .networkSynchronized(codec.asStream());
    }
    
    ///
    /// Caches the encoded form of each value.
    ///
    /// Meant to be used for values that are large and rarely change.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public DataComponentBuilder<VALUE, PARENT> cacheEncoding() {
        this.checkUnregistered();
        this.builder.cacheEncoding();
        return this;
    }
    
    ///
    /// Skips the swap animation that normally plays when a component changes its value.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public DataComponentBuilder<VALUE, PARENT> ignoreSwapAnimation() {
        this.checkUnregistered();
        this.builder.ignoreSwapAnimation();
        return this;
    }
    
    ///
    /// Creates the type with the codec and the options of this builder.
    ///
    /// @return The created type.
    ///
    @Override
    protected DataComponentType<VALUE> create() {
        return this.builder.build();
    }
    
    ///
    /// Creates the entry for the given key.
    ///
    /// @param key The key of the data component type.
    ///
    /// @return The created entry.
    ///
    @Override
    protected DataComponentEntry<VALUE> createEntry(ResourceKey<DataComponentType<?>> key) {
        return new DataComponentEntry<>(key, this);
    }
}
