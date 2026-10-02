package dev.satherov.nexus.api.registry.entity;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.text.Translatable;
import dev.satherov.nexus.api.text.Translation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// A holder of one entity type that a builder registered, bound once the entity type registry event has run.
///
/// @param <ENTITY> The type of the entities of the type.
///
public class EntityEntry<ENTITY extends Entity> extends RegistryEntry<EntityType<?>, EntityType<ENTITY>> implements Translatable {
    
    ///
    /// The `entity.<modid>.<name>` translation.
    ///
    private final Translation translation;
    
    ///
    /// Binds to the given key.
    ///
    /// Should only ever be called from [EntityBuilder#createEntry(ResourceKey)].
    ///
    /// @param key         The key of the entity type.
    /// @param builder     The builder that registers the entity type.
    /// @param translation The `entity.<modid>.<name>` translation.
    ///
    @ApiStatus.Internal
    public EntityEntry(ResourceKey<EntityType<?>> key, EntryBuilder<EntityType<?>, EntityType<ENTITY>, ?, ?, ?> builder, Translation translation) {
        super(key, builder);
        this.translation = translation;
    }
    
    ///
    /// Creates a new entity of this type in the given level, or `null` if the type is disabled in the level.
    ///
    /// The entity will not be added to the level.
    ///
    /// @param level  The level of the entity.
    /// @param reason The reason the entity is created for.
    ///
    /// @return The created entity, or `null` if the type is disabled in the level.
    ///
    public @Nullable ENTITY create(Level level, EntitySpawnReason reason) {
        return this.value().create(level, reason);
    }
    
    ///
    /// Spawns a new entity of this type at the given position, or `null` if the type is disabled in the level, an
    /// event cancelled the spawn, or the entity could not be added to the level.
    ///
    /// The returned entity will be in the level, centered on the bottom of the block at the given position.
    ///
    /// @param level  The level to spawn the entity in.
    /// @param pos    The position of the block to spawn the entity in.
    /// @param reason The reason the entity is spawned for.
    ///
    /// @return The spawned entity, or `null` if the type is disabled in the level, an event cancelled the spawn, or the entity could not be added to the level.
    ///
    @CanIgnoreReturnValue
    public @Nullable ENTITY spawn(ServerLevel level, BlockPos pos, EntitySpawnReason reason) {
        ENTITY entity = this.value().spawn(level, pos, reason);
        return entity != null && entity.isAddedToLevel() ? entity : null;
    }
    
    ///
    /// @param entity The entity.
    ///
    /// @return `true` if the given entity is of this type.
    ///
    public boolean is(Entity entity) {
        return entity.getType() == this.value();
    }
    
    ///
    /// The `entity.<modid>.<name>` translation.
    ///
    /// @return The translation.
    ///
    @Override
    public Translation getTranslation() {
        return this.translation;
    }
}
