package dev.satherov.nexus.api.registry.item;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.tab.TabEntry;
import dev.satherov.nexus.api.text.Translation;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import com.google.common.base.Suppliers;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

///
/// A builder of one item, either an item of its own or the item of another object, such as a block's.
///
/// @param <ITEM>   The type of the item.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class ItemBuilder<ITEM extends Item, PARENT>
        // @formatter:off
        extends EntryBuilder
        <
            Item,
            ITEM,
            ItemEntry<ITEM>,
            PARENT,
            ItemBuilder<ITEM, PARENT>
        >
        // @formatter:on
{
    
    ///
    /// The factory of the item, called with the properties once the id is set.
    ///
    private final Function<Item.Properties, ITEM> factory;
    
    ///
    /// The edits of the properties, in call order.
    ///
    private final List<UnaryOperator<Item.Properties>> edits = new ArrayList<>();
    
    ///
    /// The translation of the item, declared on the first call.
    ///
    private Supplier<Translation> translation;
    
    ///
    /// The properties the factory receives, before the id, the description, and the edits are applied.
    ///
    private Supplier<Item.Properties> properties = Item.Properties::new;
    
    ///
    /// The tabs the item shows in, in call order, or `null` if it shows in the mod's first tab.
    ///
    private @Nullable Set<ResourceKey<CreativeModeTab>> tabs;
    
    ///
    /// Starts the item under `item.<modid>.<name>`.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// The factory receives the properties with the id set.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English name.
    /// @param parent        What [#end()] returns.
    /// @param factory       The factory of the item, called once inside the registry event.
    ///
    public ItemBuilder(Registrations registrations, String name, String english, PARENT parent, Function<Item.Properties, ITEM> factory) {
        super(registrations, Registries.ITEM, name, parent);
        this.factory = factory;
        this.translation = this.declare(english);
    }
    
    ///
    /// Starts an item as a sub-object, such as the block item of a block or the bucket of a fluid,
    /// from which the translation is inherited unless [#named(String)] overrides it.
    ///
    /// The factory receives the properties with the id set.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param derived       The translation of the object the item belongs to.
    /// @param parent        What [#end()] returns.
    /// @param factory       The factory of the item, called once inside the registry event.
    ///
    public ItemBuilder(Registrations registrations, String name, Translation derived, PARENT parent, Function<Item.Properties, ITEM> factory) {
        super(registrations, Registries.ITEM, name, parent);
        this.factory = factory;
        this.translation = () -> derived;
    }
    
    ///
    /// Replaces the properties the factory receives.
    ///
    /// Replaces previous calls, including the edits of [#properties(UnaryOperator)].
    ///
    /// The id and the description of the translation will be set on the given properties before any edits run.
    ///
    /// @param properties The properties.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public ItemBuilder<ITEM, PARENT> properties(Item.Properties properties) {
        this.checkUnregistered();
        this.properties = () -> properties;
        this.edits.clear();
        return this;
    }
    
    ///
    /// Edits the properties the factory receives.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// Runs on the properties with the id and the description of the translation already set.
    ///
    /// @param edit The edit, called with the properties inside the registry event.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public ItemBuilder<ITEM, PARENT> properties(UnaryOperator<Item.Properties> edit) {
        this.checkUnregistered();
        this.edits.add(edit);
        return this;
    }
    
    ///
    /// Shows the item in the given tabs instead of the mod's first one.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// @param tabs The tabs.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public ItemBuilder<ITEM, PARENT> tab(TabEntry... tabs) {
        return this.addTabs(Arrays.stream(tabs).map(TabEntry::getKey).toList());
    }
    
    ///
    /// Shows the item in the given vanilla or foreign tabs instead of the mod's first one.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// @param tabs The keys of the tabs.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @SafeVarargs
    @CanIgnoreReturnValue
    public final ItemBuilder<ITEM, PARENT> tab(ResourceKey<CreativeModeTab>... tabs) {
        return this.addTabs(Arrays.asList(tabs));
    }
    
    ///
    /// Adds the given tabs to the ones the item shows in.
    ///
    private ItemBuilder<ITEM, PARENT> addTabs(Collection<ResourceKey<CreativeModeTab>> tabs) {
        this.checkUnregistered();
        Set<ResourceKey<CreativeModeTab>> recorded = Objects.requireNonNullElseGet(this.tabs, LinkedHashSet::new);
        recorded.addAll(tabs);
        this.tabs = recorded;
        return this;
    }
    
    ///
    /// Keeps the item out of every tab.
    ///
    /// Replaces previous calls of both `tab(...)` overloads.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public ItemBuilder<ITEM, PARENT> noTab() {
        this.checkUnregistered();
        this.tabs = new LinkedHashSet<>();
        return this;
    }
    
    ///
    /// Allows overriding previously declared translations.
    ///
    /// This is only really useful for overriding the name of sub-objects, such as buckets or block items.
    ///
    /// @param english The English name.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public ItemBuilder<ITEM, PARENT> named(String english) {
        this.checkUnregistered();
        this.translation = this.declare(english);
        return this;
    }
    
    ///
    /// The translation `item.<modid>.<name>` with the given English name, declared on the first call.
    ///
    private Supplier<Translation> declare(String english) {
        return Suppliers.memoize(() -> this.define("item", english));
    }
    
    ///
    /// Creates the item with the factory, from the properties with the id and the description of the translation set
    /// and then the edits applied.
    ///
    /// @return The created item.
    ///
    @Override
    protected ITEM create() {
        Item.Properties properties = this.properties.get()
                .setId(this.getKey())
                .overrideDescription(this.translation.get().getKey());
        for (UnaryOperator<Item.Properties> edit : this.edits) {
            properties = edit.apply(properties);
        }
        
        return this.factory.apply(properties);
    }
    
    ///
    /// Creates the entry with the translation, the creative tabs, and declares the english `item.<modid>.<name>` translation
    /// if the item has its own name.
    ///
    /// @param key The key of the item.
    ///
    /// @return The created entry.
    ///
    @Override
    protected ItemEntry<ITEM> createEntry(ResourceKey<Item> key) {
        return new ItemEntry<>(key, this, this.translation.get(), this.tabs);
    }
}
