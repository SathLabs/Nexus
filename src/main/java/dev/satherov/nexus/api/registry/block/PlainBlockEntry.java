package dev.satherov.nexus.api.registry.block;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.text.Translatable;
import dev.satherov.nexus.api.text.Translation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.ApiStatus;

///
/// A holder of one block that a builder registered, bound once the block registry event has run.
///
/// @param <BLOCK> The type of the block.
///
/// @see BlockEntry
///
public class PlainBlockEntry<BLOCK extends Block> extends RegistryEntry<Block, BLOCK> implements Translatable {
    
    ///
    /// The `block.<modid>.<name>` translation.
    ///
    private final Translation translation;
    
    ///
    /// Should only ever be called from [PlainBlockBuilder#createEntry(ResourceKey)].
    ///
    /// @param key         The key of the block.
    /// @param builder     The builder that registers the block.
    /// @param translation The `block.<modid>.<name>` translation.
    ///
    @ApiStatus.Internal
    public PlainBlockEntry(ResourceKey<Block> key, EntryBuilder<Block, BLOCK, ?, ?, ?> builder, Translation translation) {
        super(key, builder);
        this.translation = translation;
    }
    
    ///
    /// The block's default state.
    ///
    /// @return The default state.
    ///
    public BlockState getDefaultState() {
        return this.value().defaultBlockState();
    }
    
    ///
    /// @param state The block state.
    ///
    /// @return `true` if the given state is of this block.
    ///
    public boolean is(BlockState state) {
        return state.is(this);
    }
    
    ///
    /// The `block.<modid>.<name>` translation.
    ///
    /// @return The translation.
    ///
    @Override
    public Translation getTranslation() {
        return this.translation;
    }
}
