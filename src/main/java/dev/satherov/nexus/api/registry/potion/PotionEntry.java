package dev.satherov.nexus.api.registry.potion;

import dev.satherov.nexus.api.registry.RegistryEntry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import org.jetbrains.annotations.ApiStatus;

///
/// An entry of one potion that a [PotionBuilder] registered.
///
public final class PotionEntry extends RegistryEntry<Potion, Potion> {
    
    ///
    /// Binds to the given key.
    ///
    /// Should only ever be called from [PotionBuilder#createEntry(ResourceKey)].
    ///
    /// @param key     The key of the potion.
    /// @param builder The builder that registers the potion.
    ///
    @ApiStatus.Internal
    public PotionEntry(ResourceKey<Potion> key, PotionBuilder<?> builder) {
        super(key, builder);
    }
    
    ///
    /// Creates a stack of the given container that holds this potion.
    ///
    /// Meant to be used for [Items#POTION], [Items#SPLASH_POTION], [Items#LINGERING_POTION], and [Items#TIPPED_ARROW].
    ///
    /// @param container The item of the stack.
    ///
    /// @return The created stack, with a count of `1`.
    ///
    public ItemStack stack(Item container) {
        return PotionContents.createItemStack(container, this);
    }
    
    ///
    /// @param stack The stack.
    ///
    /// @return `true` if the potion contents of the given stack are this potion with no custom effects.
    ///
    public boolean is(ItemStack stack) {
        return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(this);
    }
}
