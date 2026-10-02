package dev.satherov.nexus.api.registry.entity;

import dev.satherov.nexus.api.registry.EntryBuilder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

///
/// A builder of one entity type and its spawn egg, reached through [EntityBuilder#egg(Function)].
///
/// @param <ENTITY> The type of the entities of the type.
/// @param <EGG>    The type of the spawn egg.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class MobEntityBuilder<ENTITY extends Entity, EGG extends SpawnEggItem, PARENT> extends EntryBuilder<EntityType<?>, EntityType<ENTITY>, MobEntityEntry<ENTITY, EGG>, PARENT, MobEntityBuilder<ENTITY, EGG, PARENT>> {
    
    ///
    /// The builder of the entity type, which creates the type and its entry.
    ///
    private final EntityBuilder<ENTITY, PARENT> entity;
    
    ///
    /// Takes over the given builder of the entity type, with everything recorded on it so far.
    ///
    /// Should only ever be called from [EntityBuilder#egg(Function)].
    ///
    /// @param entity The builder of the entity type.
    /// @param parent What [#end()] returns.
    ///
    @ApiStatus.Internal
    public MobEntityBuilder(EntityBuilder<ENTITY, PARENT> entity, PARENT parent) {
        super(entity, parent);
        this.entity = entity;
    }
    
    ///
    /// Creates the type with the builder of the entity type.
    ///
    /// @return The created type.
    ///
    @Override
    protected EntityType<ENTITY> create() {
        return this.entity.create();
    }
    
    ///
    /// Creates the entry of entity type and egg with the builder of the entity type, which registers the spawn egg
    /// first.
    ///
    /// @param key The key of the entity type.
    ///
    /// @return The created entry.
    ///
    @Override
    protected MobEntityEntry<ENTITY, EGG> createEntry(ResourceKey<EntityType<?>> key) {
        //noinspection unchecked The builder of the entity type creates the entry with the spawn egg that this builder was opened for.
        return (MobEntityEntry<ENTITY, EGG>) this.entity.createEntry(key);
    }
}
