package dev.satherov.nexus.api.registry.block.entity;

import dev.satherov.nexus.api.registry.RegistryEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// An entry of one block entity type that a [BlockEntityBuilder] registered.
///
/// @param <ENTITY> The type of the block entities of the type.
///
public final class BlockEntityEntry<ENTITY extends BlockEntity> extends RegistryEntry<BlockEntityType<?>, BlockEntityType<ENTITY>> {
    
    ///
    /// Binds to the given key.
    ///
    /// Should only ever be called from [BlockEntityBuilder#createEntry(ResourceKey)].
    ///
    /// @param key     The key of the block entity type.
    /// @param builder The builder that registers the block entity type.
    ///
    @ApiStatus.Internal
    public BlockEntityEntry(ResourceKey<BlockEntityType<?>> key, BlockEntityBuilder<ENTITY, ?> builder) {
        super(key, builder);
    }
    
    ///
    /// The block entity of this type at the given position, or `null` if there is none or it is of another type.
    ///
    /// @param level The level of the block entity.
    /// @param pos   The position of the block entity.
    ///
    /// @return The block entity of this type, or `null` if there is none or it is of another type.
    ///
    public @Nullable ENTITY at(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos, this.value()).orElse(null);
    }
    
    ///
    /// @param entity The block entity.
    ///
    /// @return `true` if the given block entity is of this type.
    ///
    public boolean is(BlockEntity entity) {
        return entity.getType() == this.value();
    }
}
