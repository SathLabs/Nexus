package dev.satherov.nexus.api.registry.block;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.registry.item.ItemBuilder;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.internal.registry.client.ClientDispatch;

import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

///
/// A builder of one block without an item.
///
/// Calls [#item()] to open an item builder.
///
/// @param <BLOCK>  The type of the block.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class PlainBlockBuilder<BLOCK extends Block, PARENT>
        // @formatter:off
        extends EntryBuilder
        <
            Block,
            BLOCK,
            PlainBlockEntry<BLOCK>,
            PARENT,
            PlainBlockBuilder<BLOCK, PARENT>
        >
        // @formatter:on
{
    
    ///
    /// The `block.<modid>.<name>` translation.
    ///
    private final Translation translation;
    
    ///
    /// The factory of the block, called with the properties once the id is set.
    ///
    private final Function<BlockBehaviour.Properties, BLOCK> factory;
    
    ///
    /// The properties the factory receives before the id is set.
    ///
    private Supplier<BlockBehaviour.Properties> properties = BlockBehaviour.Properties::of;
    
    ///
    /// The tint sources of the block, or `null` if it has none.
    ///
    private @Nullable ClientOnly<List<BlockTintSource>> tints;
    
    ///
    /// The factory of the entry of block and item, or `null` if [#item(BiFunction)] was not called.
    ///
    private @Nullable Function<ResourceKey<Block>, BlockEntry<BLOCK, ?>> blockEntryFactory;
    
    ///
    /// Starts the block `<modid>:<name>` and declares its English name under `block.<modid>.<name>`.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English name.
    /// @param parent        What [#end()] returns.
    /// @param factory       The factory of the block, called once inside the registry event with the properties with the id set.
    ///
    public PlainBlockBuilder(Registrations registrations, String name, String english, PARENT parent, Function<BlockBehaviour.Properties, BLOCK> factory) {
        super(registrations, Registries.BLOCK, name, parent);
        this.factory = factory;
        
        this.translation = this.define("block", english);
    }
    
    ///
    /// Replaces the properties the factory receives.
    ///
    /// Replaces all previous calls, including the edits of [#properties(UnaryOperator)].
    ///
    /// @param properties The properties of the block.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public PlainBlockBuilder<BLOCK, PARENT> properties(BlockBehaviour.Properties properties) {
        this.checkUnregistered();
        this.properties = () -> properties;
        return this;
    }
    
    ///
    /// Edits the properties the factory receives.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// @param edit The edit, called with the properties inside the registry event.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public PlainBlockBuilder<BLOCK, PARENT> properties(UnaryOperator<BlockBehaviour.Properties> edit) {
        this.checkUnregistered();
        Supplier<BlockBehaviour.Properties> previous = this.properties;
        this.properties = () -> edit.apply(previous.get());
        return this;
    }
    
    ///
    /// Registers the tint sources of the block on the client, one per tint index.
    ///
    /// @param tints The tint sources, where the index corresponds to the tint index.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public PlainBlockBuilder<BLOCK, PARENT> tint(ClientOnly<List<BlockTintSource>> tints) {
        this.checkUnregistered();
        this.tints = tints;
        return this;
    }
    
    ///
    /// Starts a new item builder, with the class of the provided factory result.
    ///
    /// The identifier and translation will be the same as those of the block.
    /// The translation can be overwritten with [ItemBuilder#named(String)].
    ///
    /// This builder will throw on every later call.
    ///
    /// @param factory The factory of the item, called once inside the registry event with the block and the properties with the id set.
    ///
    /// @return The builder of the item.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    public <ITEM extends Item> ItemBuilder<ITEM, BlockBuilder<BLOCK, ITEM, PARENT>> item(BiFunction<BLOCK, Item.Properties, ITEM> factory) {
        BlockBuilder<BLOCK, ITEM, PARENT> builder = new BlockBuilder<>(this, this.end());
        DeferredHolder<Block, BLOCK> holder = DeferredHolder.create(this.getKey());
        ItemBuilder<ITEM, BlockBuilder<BLOCK, ITEM, PARENT>> item = new ItemBuilder<>(
                this.getRegistrations(),
                this.getName(),
                this.translation,
                builder,
                properties -> factory.apply(holder.value(), properties)
        );
        
        item.properties(properties -> properties.requiredFeatures(holder.value().requiredFeatures()));
        this.blockEntryFactory = key -> new BlockEntry<>(key, builder, this.translation, EntryBuilder.registerSubObject(item));
        return item;
    }
    
    ///
    /// Starts a new item builder, with [BlockItem].
    ///
    /// The identifier and translation will be the same as those of the block.
    /// The translation can be overwritten with [ItemBuilder#named(String)].
    ///
    /// This builder will throw on every later call.
    ///
    /// @return The builder of the item.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    public ItemBuilder<BlockItem, BlockBuilder<BLOCK, BlockItem, PARENT>> item() {
        return this.item(BlockItem::new);
    }
    
    ///
    /// Creates the block with the factory, from the properties with the id set.
    ///
    /// @return The created block.
    ///
    @Override
    protected BLOCK create() {
        return this.factory.apply(this.properties.get().setId(this.getKey()));
    }
    
    ///
    /// Creates the entry for the given key, registering the item first if [#item(BiFunction)] set one,
    /// and then adds the tint sources as a client hook if [#tint(ClientOnly)] was called.
    ///
    /// @param key The key of the block.
    ///
    /// @return The created entry.
    ///
    @Override
    protected PlainBlockEntry<BLOCK> createEntry(ResourceKey<Block> key) {
        Function<ResourceKey<Block>, BlockEntry<BLOCK, ?>> blockEntryFactory = this.blockEntryFactory;
        PlainBlockEntry<BLOCK> entry = blockEntryFactory == null ? new PlainBlockEntry<>(key, this, this.translation) : blockEntryFactory.apply(key);
        ClientOnly<List<BlockTintSource>> tints = this.tints;
        if (tints != null) {
            this.addClientHook(bus -> ClientDispatch.tint(bus, entry, tints));
        }
        
        return entry;
    }
}
