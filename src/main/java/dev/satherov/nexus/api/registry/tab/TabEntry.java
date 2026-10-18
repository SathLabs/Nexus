package dev.satherov.nexus.api.registry.tab;

import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.registry.item.ItemEntry;
import dev.satherov.nexus.api.text.Translatable;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.internal.registry.tab.TabContents;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

///
/// An entry of one creative tab that a [TabBuilder] registered.
///
public final class TabEntry extends RegistryEntry<CreativeModeTab, CreativeModeTab> implements Translatable {
    
    ///
    /// The translation of the title.
    ///
    private final Translation translation;
    
    ///
    /// The predicate of the item entries the tab shows, or `null` if it only shows the ones recorded in it.
    ///
    private final @Nullable Predicate<ItemEntry<? extends Item>> contents;
    
    ///
    /// The order of the items of the tab, or `null` if they are in registration order.
    ///
    private final @Nullable Comparator<ItemEntry<? extends Item>> order;
    
    ///
    /// The generator of the stacks after the mod's items, or `null` if the tab doesn't have any.
    ///
    private final CreativeModeTab.@Nullable DisplayItemsGenerator extra;
    
    ///
    /// Should only ever be called from [TabBuilder#createEntry(ResourceKey)].
    ///
    /// @param key         The key of the tab.
    /// @param builder     The builder that registers the tab.
    /// @param translation The translation of the title.
    /// @param contents    The predicate of the item entries the tab shows, or `null` if it only shows the ones recorded in it.
    /// @param order       The order of the items of the tab, or `null` if they are in registration order.
    /// @param extra       The generator of the stacks after the mod's items, or `null` if the tab doesn't have any.
    ///
    @ApiStatus.Internal
    public TabEntry(
            ResourceKey<CreativeModeTab> key,
            TabBuilder<?> builder,
            Translation translation,
            @Nullable Predicate<ItemEntry<? extends Item>> contents,
            @Nullable Comparator<ItemEntry<? extends Item>> order,
            CreativeModeTab.@Nullable DisplayItemsGenerator extra
    ) {
        super(key, builder);
        this.translation = translation;
        this.contents = contents;
        this.order = order;
        this.extra = extra;
    }
    
    ///
    /// The item entries of the mod that the tab shows, in display order.
    ///
    /// Will be empty until the tabs are built.
    ///
    /// @return The shown item entries.
    ///
    public @Unmodifiable List<ItemEntry<? extends Item>> getItems() {
        if (!this.isBound()) {
            return List.of();
        }
        
        Map<Item, ItemEntry<? extends Item>> entriesByItem = TabContents.getItemEntries(this.getMod().getRegistrations())
                .stream()
                .collect(Collectors.toMap(ItemEntry::asItem, Function.identity()));
        
        return this.value()
                .getDisplayItems()
                .stream()
                .<ItemEntry<? extends Item>>map(stack -> entriesByItem.get(stack.getItem()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
    
    ///
    /// The predicate of the item entries the tab shows, or `null` if it shows the ones recorded in it.
    ///
    /// @return The predicate, or `null` if the tab shows the item entries recorded in it.
    ///
    @ApiStatus.Internal
    public @Nullable Predicate<ItemEntry<? extends Item>> getContents() {
        return this.contents;
    }
    
    ///
    /// The order of the items of the tab, or `null` if they are in registration order.
    ///
    /// @return The order, or `null` if the items are in registration order.
    ///
    @ApiStatus.Internal
    public @Nullable Comparator<ItemEntry<? extends Item>> getOrder() {
        return this.order;
    }
    
    ///
    /// The generator of the stacks after the mod's items, or `null` if the tab doesn't have any.
    ///
    /// @return The generator, or `null` if the tab doesn't have any extra stacks.
    ///
    @ApiStatus.Internal
    public CreativeModeTab.@Nullable DisplayItemsGenerator getExtra() {
        return this.extra;
    }
    
    ///
    /// The `itemGroup.<modid>.<name>` translation.
    ///
    /// @return The translation of the title.
    ///
    @Override
    public Translation getTranslation() {
        return this.translation;
    }
}
