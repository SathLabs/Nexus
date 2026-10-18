package dev.satherov.nexus.api.registry;

import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.registry.datagen.ProviderKind;

import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

///
/// A holder of one object that a builder registered, bound once the registry event of its registry has run.
///
/// @param <REGISTRY> The type of the objects of the registry.
/// @param <VALUE>    The type of the object.
///
public class RegistryEntry<REGISTRY, VALUE extends REGISTRY> extends DeferredHolder<REGISTRY, VALUE> {
    
    ///
    /// The handle of the mod that registered the object.
    ///
    private final NexusMod mod;
    
    ///
    /// The tags the builder recorded, in call order.
    ///
    private final @Unmodifiable Set<TagKey<REGISTRY>> tags;
    
    ///
    /// The facts the builder recorded for datagen, by kind.
    ///
    private final @Unmodifiable Map<ProviderKind<?>, Object> factsByKind;
    
    ///
    /// Should only ever be called from [EntryBuilder#createEntry(ResourceKey)].
    ///
    /// Copies what it needs out of the given builder and keeps no reference to it.
    ///
    /// @param key     The key of the object.
    /// @param builder The builder that registers the object.
    ///
    protected RegistryEntry(ResourceKey<REGISTRY> key, EntryBuilder<REGISTRY, VALUE, ?, ?, ?> builder) {
        super(key);
        this.mod = builder.getRegistrations().getMod();
        this.tags = builder.getTags();
        this.factsByKind = builder.getAllDatagen();
    }
    
    ///
    /// The handle of the mod that registered the object.
    ///
    /// @return The handle of the mod.
    ///
    @ApiStatus.Internal
    public NexusMod getMod() {
        return this.mod;
    }
    
    ///
    /// The path of the identifier.
    ///
    /// @return The path of the identifier.
    ///
    public String getName() {
        return this.getId().getPath();
    }
    
    ///
    /// The tags the builder recorded, in call order.
    ///
    /// @return The recorded tags.
    ///
    public @Unmodifiable Set<TagKey<REGISTRY>> getTags() {
        return this.tags;
    }
    
    ///
    /// The fact the builder recorded under the given kind, or `null` if it didn't record one.
    ///
    /// @param kind The kind of the fact.
    ///
    /// @return The recorded fact, or `null` if the builder didn't record one.
    ///
    @ApiStatus.Internal
    public <FACT> @Nullable FACT getDatagen(ProviderKind<FACT> kind) {
        //noinspection unchecked A fact is only ever recorded under a kind of its own type.
        return (FACT) this.factsByKind.get(kind);
    }
}
