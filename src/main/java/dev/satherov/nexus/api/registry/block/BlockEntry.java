package dev.satherov.nexus.api.registry.block;

import lombok.Getter;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.item.ItemEntry;
import dev.satherov.nexus.api.text.Translation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.BiFunction;

///
/// A holder of one block and its item that a builder registered, bound once the block registry event has run.
///
/// @param <BLOCK> The type of the block.
/// @param <ITEM>  The type of the item.
///
/// @see PlainBlockEntry
///
public final class BlockEntry<BLOCK extends Block, ITEM extends Item> extends PlainBlockEntry<BLOCK> implements ItemLike {
    
    ///
    /// The item's entry.
    ///
    @Getter
    private final ItemEntry<ITEM> item;
    
    ///
    /// Should only ever be called from [PlainBlockBuilder#item(BiFunction)].
    ///
    /// @param key         The key of the block.
    /// @param builder     The builder that registers the block.
    /// @param translation The `block.<modid>.<name>` translation.
    /// @param item        The item's entry.
    ///
    @ApiStatus.Internal
    public BlockEntry(ResourceKey<Block> key, EntryBuilder<Block, BLOCK, ?, ?, ?> builder, Translation translation, ItemEntry<ITEM> item) {
        super(key, builder, translation);
        this.item = item;
    }
    
    ///
    /// The item.
    ///
    /// @return The item.
    ///
    @Override
    public ITEM asItem() {
        return this.item.asItem();
    }
    
    ///
    /// Creates a new item stack with one item.
    ///
    /// @return The created stack.
    ///
    public ItemStack stack() {
        return this.item.stack();
    }
    
    ///
    /// Creates a new item stack with the given number of items.
    ///
    /// @param count The number of items in the stack.
    ///
    /// @return The created stack.
    ///
    public ItemStack stack(int count) {
        return this.item.stack(count);
    }
}
