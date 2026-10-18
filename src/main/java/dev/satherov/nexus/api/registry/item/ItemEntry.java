package dev.satherov.nexus.api.registry.item;

import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.registry.tab.TabBuilder;
import dev.satherov.nexus.api.registry.tab.TabEntry;
import dev.satherov.nexus.api.text.Translatable;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.zelqro.mapping.ObjectMapping;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

///
/// An entry of one item that an [ItemBuilder] registered.
///
/// @param <ITEM> The type of the item.
///
public final class ItemEntry<ITEM extends Item> extends RegistryEntry<Item, ITEM> implements ItemLike, Translatable {
    
    ///
    /// The translation of the item.
    ///
    private final Translation translation;
    
    ///
    /// The tabs the builder recorded, in call order, or `null` if the item shows in the mod's first tab.
    ///
    private final @Nullable Set<ResourceKey<CreativeModeTab>> tabs;
    
    ///
    /// Should only ever be called from [ItemBuilder#createEntry(ResourceKey)].
    ///
    /// @param key         The key of the item.
    /// @param builder     The builder that registers the item.
    /// @param translation The translation of the item.
    /// @param tabs        The tabs the builder recorded, or `null` if the item shows in the mod's first tab.
    ///
    @ApiStatus.Internal
    public ItemEntry(ResourceKey<Item> key, ItemBuilder<ITEM, ?> builder, Translation translation, @Nullable Set<ResourceKey<CreativeModeTab>> tabs) {
        super(key, builder);
        this.translation = translation;
        this.tabs = ObjectMapping.mapNonNull(tabs, recorded -> Collections.unmodifiableSequencedSet(new LinkedHashSet<>(recorded)));
    }
    
    ///
    /// The item.
    ///
    /// @return The item.
    ///
    @Override
    public ITEM asItem() {
        return this.value();
    }
    
    ///
    /// Creates a stack of one of the item.
    ///
    /// @return The created stack.
    ///
    public ItemStack stack() {
        return this.stack(1);
    }
    
    ///
    /// Creates a stack of the item with the given count.
    ///
    /// @param count The number of items in the stack.
    ///
    /// @return The created stack.
    ///
    public ItemStack stack(int count) {
        return new ItemStack(this.value(), count);
    }
    
    ///
    /// If the given stack is of the item.
    ///
    /// @param stack The stack.
    ///
    /// @return `true` if the stack is of the item.
    ///
    public boolean is(ItemStack stack) {
        return stack.is(this.value());
    }
    
    ///
    /// The tabs the builder recorded the item in:
    /// - The tabs given to [ItemBuilder#tab(TabEntry...)] and [ItemBuilder#tab(ResourceKey...)], in call order.
    /// - The mod's first tab if the builder didn't name any, or none if the mod has no tab.
    /// - No tab after [ItemBuilder#noTab()].
    ///
    /// If a tab has a [TabBuilder#contents(Predicate)] predicate, it shows the entries the predicate accepts instead.
    ///
    /// @return The recorded tabs.
    ///
    /// @see TabEntry#getItems()
    ///
    public @Unmodifiable Set<ResourceKey<CreativeModeTab>> getTabs() {
        if (this.tabs != null) {
            return this.tabs;
        }
        
        List<RegistryEntry<CreativeModeTab, ? extends CreativeModeTab>> registered = this.getMod().getRegistrations().getAll(Registries.CREATIVE_MODE_TAB);
        return registered.isEmpty() ? Set.of() : Set.of(registered.getFirst().getKey());
    }
    
    ///
    /// The `item.<modid>.<name>` translation, or the derived translations of a sub-object,
    /// e.g., the bucket of a liquid or block item of a block.
    ///
    /// @return The translation of the item.
    ///
    @Override
    public Translation getTranslation() {
        return this.translation;
    }
}
