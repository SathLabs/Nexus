package dev.satherov.nexus.api.registry.entity;

import lombok.Getter;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.item.ItemEntry;
import dev.satherov.nexus.api.text.Translation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;

import org.jetbrains.annotations.ApiStatus;

///
/// A holder of one entity type and its spawn egg that a builder registered, bound once the entity type registry event
/// has run.
///
/// @param <ENTITY> The type of the entities of the type.
/// @param <EGG>    The type of the spawn egg.
///
public final class MobEntityEntry<ENTITY extends Entity, EGG extends SpawnEggItem> extends EntityEntry<ENTITY> {
    
    ///
    /// The spawn egg's entry.
    ///
    @Getter
    private final ItemEntry<EGG> egg;
    
    ///
    /// Binds to the given key.
    ///
    /// Should only ever be called from [EntityBuilder#createEntry(ResourceKey)].
    ///
    /// @param key         The key of the entity type.
    /// @param builder     The builder that registers the entity type.
    /// @param translation The `entity.<modid>.<name>` translation.
    /// @param egg         The spawn egg's entry.
    ///
    @ApiStatus.Internal
    public MobEntityEntry(ResourceKey<EntityType<?>> key, EntryBuilder<EntityType<?>, EntityType<ENTITY>, ?, ?, ?> builder, Translation translation, ItemEntry<EGG> egg) {
        super(key, builder, translation);
        this.egg = egg;
    }
}
