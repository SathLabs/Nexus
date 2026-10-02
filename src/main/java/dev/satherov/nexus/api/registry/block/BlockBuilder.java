package dev.satherov.nexus.api.registry.block;

import dev.satherov.nexus.api.registry.EntryBuilder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.BiFunction;

///
/// A builder of one block and its item, reached through [PlainBlockBuilder#item(BiFunction)].
///
/// @param <BLOCK>  The type of the block.
/// @param <ITEM>   The type of the item.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class BlockBuilder<BLOCK extends Block, ITEM extends Item, PARENT> extends EntryBuilder<Block, BLOCK, BlockEntry<BLOCK, ITEM>, PARENT, BlockBuilder<BLOCK, ITEM, PARENT>> {
    
    ///
    /// The builder of the block, which creates the block and its entry.
    ///
    private final PlainBlockBuilder<BLOCK, PARENT> block;
    
    ///
    /// Takes over the given builder of the block, with everything recorded on it so far.
    ///
    /// Should only ever be called from [PlainBlockBuilder#item(BiFunction)].
    ///
    /// @param block  The builder of the block.
    /// @param parent What [#end()] returns.
    ///
    @ApiStatus.Internal
    public BlockBuilder(PlainBlockBuilder<BLOCK, PARENT> block, PARENT parent) {
        super(block, parent);
        this.block = block;
    }
    
    ///
    /// Creates the block with the builder of the block.
    ///
    /// @return The created block.
    ///
    @Override
    protected BLOCK create() {
        return this.block.create();
    }
    
    ///
    /// Creates the entry of block and item with the builder of the block, which registers the item first.
    ///
    /// @param key The key of the block.
    ///
    /// @return The created entry.
    ///
    @Override
    protected BlockEntry<BLOCK, ITEM> createEntry(ResourceKey<Block> key) {
        //noinspection unchecked The builder of the block creates the entry with the item that this builder was opened for.
        return (BlockEntry<BLOCK, ITEM>) this.block.createEntry(key);
    }
}
