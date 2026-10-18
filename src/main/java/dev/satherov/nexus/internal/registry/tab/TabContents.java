package dev.satherov.nexus.internal.registry.tab;

import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.item.ItemEntry;
import dev.satherov.nexus.api.registry.tab.TabEntry;
import dev.satherov.zelqro.mapping.ObjectMapping;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

///
/// The contents that one mod adds to every creative tab it has items for.
///
@ApiStatus.Internal
@RequiredArgsConstructor
public final class TabContents {
    
    ///
    /// The registrations of the mod.
    ///
    private final Registrations registrations;
    
    ///
    /// Gets every item entry of the given registrations, in registration order.
    ///
    /// @param registrations The registrations of the mod.
    ///
    /// @return The item entries.
    ///
    public static List<ItemEntry<? extends Item>> getItemEntries(Registrations registrations) {
        return registrations.getAll(Registries.ITEM).stream()
                .filter(ItemEntry.class::isInstance)
                .<ItemEntry<? extends Item>>map(entry -> (ItemEntry<? extends Item>) entry)
                .toList();
    }
    
    ///
    /// Selects the given item entries that the given tab shows, in display order:
    /// - If the tab has a `contents` predicate, the entries the predicate accepts.
    /// - Otherwise the entries recorded in the tab.
    ///
    /// If the tab has an `order`, the entries will be sorted by it, and otherwise stay in the given order.
    ///
    /// @param entries  The item entries of the mod, in registration order.
    /// @param tab      The key of the tab.
    /// @param contents The `contents` predicate of the tab, or `null` if it doesn't have one.
    /// @param order    The `order` of the tab, or `null` if it doesn't have one.
    ///
    /// @return The shown item entries.
    ///
    public static List<ItemEntry<? extends Item>> select(
            List<ItemEntry<? extends Item>> entries,
            ResourceKey<CreativeModeTab> tab,
            @Nullable Predicate<ItemEntry<? extends Item>> contents,
            @Nullable Comparator<ItemEntry<? extends Item>> order
    ) {
        Predicate<ItemEntry<? extends Item>> shown = Objects.requireNonNullElse(contents, entry -> entry.getTabs().contains(tab));
        Stream<ItemEntry<? extends Item>> selected = entries.stream().filter(shown);
        if (order == null) {
            return selected.toList();
        }
        
        return selected.sorted(order).toList();
    }
    
    ///
    /// Adds the item entries of the mod to the tab of the current event and any extra ones if the tab belongs to the mod.
    ///
    /// Skips every stack the tab already has, so each one shows once.
    ///
    /// @param event The event of the tab.
    ///
    public void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        TabEntry own = this.registrations.getAll(Registries.CREATIVE_MODE_TAB).stream()
                .filter(entry -> entry.getKey().equals(event.getTabKey()))
                .filter(TabEntry.class::isInstance)
                .map(TabEntry.class::cast)
                .findFirst()
                .orElse(null);
        
        List<ItemEntry<? extends Item>> shown = TabContents.select(
                TabContents.getItemEntries(this.registrations),
                event.getTabKey(),
                ObjectMapping.map(own, TabEntry::getContents),
                ObjectMapping.map(own, TabEntry::getOrder)
        );
        
        CreativeModeTab.Output output = (stack, visibility) -> TabContents.acceptOnce(event, stack, visibility);
        shown.forEach(entry -> output.accept(entry.stack()));
        CreativeModeTab.DisplayItemsGenerator extra = ObjectMapping.map(own, TabEntry::getExtra);
        if (extra != null) {
            extra.accept(event.getParameters(), output);
        }
    }
    
    ///
    /// Adds the given stack to the parts of the tab that the given visibility selects and that don't have it yet.
    ///
    private static void acceptOnce(BuildCreativeModeTabContentsEvent event, ItemStack stack, CreativeModeTab.TabVisibility visibility) {
        if (visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY && !event.getParentEntries().contains(stack)) {
            event.accept(stack, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        }
        
        if (visibility != CreativeModeTab.TabVisibility.PARENT_TAB_ONLY && !event.getSearchEntries().contains(stack)) {
            event.accept(stack, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
        }
    }
}
