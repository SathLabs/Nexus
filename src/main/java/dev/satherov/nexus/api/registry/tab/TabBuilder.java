package dev.satherov.nexus.api.registry.tab;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.item.ItemEntry;
import dev.satherov.nexus.api.text.Translation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import com.google.common.base.Suppliers;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

///
/// A builder of one creative tab.
///
/// @param <PARENT> The type of what [#end()] returns.
///
public final class TabBuilder<PARENT>
        // @formatter:off
        extends EntryBuilder
        <
            CreativeModeTab,
            CreativeModeTab,
            TabEntry,
            PARENT,
            TabBuilder<PARENT>
        >
        // @formatter:on
{
    
    ///
    /// The translation of the title, declared on the first call.
    ///
    private final Supplier<Translation> translation;
    
    ///
    /// The edits of the vanilla tab builder, in call order.
    ///
    private final List<UnaryOperator<CreativeModeTab.Builder>> edits = new ArrayList<>();
    
    ///
    /// The supplier of the icon, or `null` if the tab shows its first item.
    ///
    private @Nullable Supplier<ItemStack> icon;
    
    ///
    /// The predicate of the item entries the tab shows, or `null` if it only shows the ones recorded in it.
    ///
    private @Nullable Predicate<ItemEntry<? extends Item>> contents;
    
    ///
    /// The order of the items of the tab, or `null` if they are in registration order.
    ///
    private @Nullable Comparator<ItemEntry<? extends Item>> order;
    
    ///
    /// The generator of the stacks after the mod's items, or `null` if the tab doesn't have any.
    ///
    private CreativeModeTab.@Nullable DisplayItemsGenerator extra;
    
    ///
    /// Starts the tab titled `itemGroup.<modid>.<name>`.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English title.
    /// @param parent        What [#end()] returns.
    ///
    public TabBuilder(Registrations registrations, String name, String english, PARENT parent) {
        super(registrations, Registries.CREATIVE_MODE_TAB, name, parent);
        this.translation = Suppliers.memoize(() -> this.define("itemGroup", english));
    }
    
    ///
    /// Shows the given item as the icon.
    ///
    /// If the tab has no icon, it will show its first item.
    ///
    /// @param icon The item of the icon.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public TabBuilder<PARENT> icon(ItemLike icon) {
        this.checkUnregistered();
        this.icon = () -> new ItemStack(icon);
        return this;
    }
    
    ///
    /// Shows the stack of the given supplier as the icon.
    ///
    /// @param icon The supplier of the icon, called once when the icon is first shown.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public TabBuilder<PARENT> icon(Supplier<ItemStack> icon) {
        this.checkUnregistered();
        this.icon = icon;
        return this;
    }
    
    ///
    /// Edits the vanilla tab builder after the title and the icon are set.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// @param edit The edit, called with the vanilla tab builder inside the registry event.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public TabBuilder<PARENT> properties(UnaryOperator<CreativeModeTab.Builder> edit) {
        this.checkUnregistered();
        this.edits.add(edit);
        return this;
    }
    
    ///
    /// Shows every item entry of the mod that the given predicate accepts, instead of only the entries recorded in this tab.
    ///
    /// @param filter The predicate of the shown item entries.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public TabBuilder<PARENT> contents(Predicate<ItemEntry<? extends Item>> filter) {
        this.checkUnregistered();
        this.contents = filter;
        return this;
    }
    
    ///
    /// Orders the items of the tab by the given comparator.
    ///
    /// If the tab has no order, its items will be in registration order.
    ///
    /// @param order The comparator of the item entries.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public TabBuilder<PARENT> order(Comparator<ItemEntry<? extends Item>> order) {
        this.checkUnregistered();
        this.order = order;
        return this;
    }
    
    ///
    /// Adds the stacks of the given generator after the mod's items.
    /// Replaces previous calls.
    ///
    /// Meant to be used for vanilla items or stacks that do not belong to any entry.
    ///
    /// @param extra The generator of the stacks, called each time the tab is built.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called.
    ///
    @CanIgnoreReturnValue
    public TabBuilder<PARENT> extra(CreativeModeTab.DisplayItemsGenerator extra) {
        this.checkUnregistered();
        this.extra = extra;
        return this;
    }
    
    ///
    /// Creates the tab from the vanilla tab builder with the title and the icon set, and then the edits applied.
    ///
    /// @return The created tab.
    ///
    @Override
    protected CreativeModeTab create() {
        CreativeModeTab.Builder builder = CreativeModeTab.builder()
                .title(this.translation.get().component())
                .icon(Objects.requireNonNullElse(this.icon, this::getFirstItem));
        for (UnaryOperator<CreativeModeTab.Builder> edit : this.edits) {
            builder = edit.apply(builder);
        }
        
        return builder.build();
    }
    
    ///
    /// The first stack the tab shows, or an empty stack if it shows nothing.
    ///
    private ItemStack getFirstItem() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(this.getKey())
                .getDisplayItems()
                .stream()
                .findFirst()
                .orElse(ItemStack.EMPTY);
    }
    
    ///
    /// Creates the entry with the title, the predicate, the order, and the extra stacks, and declares the title.
    ///
    /// @param key The key of the tab.
    ///
    /// @return The created entry.
    ///
    @Override
    protected TabEntry createEntry(ResourceKey<CreativeModeTab> key) {
        return new TabEntry(key, this, this.translation.get(), this.contents, this.order, this.extra);
    }
}
