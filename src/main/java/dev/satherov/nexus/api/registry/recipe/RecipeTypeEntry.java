package dev.satherov.nexus.api.registry.recipe;

import lombok.Getter;

import dev.satherov.nexus.api.registry.RegistryEntry;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import org.jetbrains.annotations.ApiStatus;

///
/// An entry of one recipe type that a [RecipeTypeBuilder] registered, with the entry of the serializer of its recipes.
///
/// @param <RECIPE> The type of the recipes of the type.
///
public final class RecipeTypeEntry<RECIPE extends Recipe<?>> extends RegistryEntry<RecipeType<?>, RecipeType<RECIPE>> {
    
    ///
    /// The entry of the serializer of the recipes, registered under the same name.
    ///
    @Getter
    private final RegistryEntry<RecipeSerializer<?>, RecipeSerializer<RECIPE>> serializer;
    
    ///
    /// Binds to the given key.
    ///
    /// Should only ever be called from [RecipeTypeBuilder#createEntry(ResourceKey)].
    ///
    /// @param key        The key of the recipe type.
    /// @param builder    The builder that registers the recipe type.
    /// @param serializer The entry of the serializer of the recipes.
    ///
    @ApiStatus.Internal
    public RecipeTypeEntry(ResourceKey<RecipeType<?>> key, RecipeTypeBuilder<RECIPE, ?> builder, RegistryEntry<RecipeSerializer<?>, RecipeSerializer<RECIPE>> serializer) {
        super(key, builder);
        this.serializer = serializer;
    }
    
    ///
    /// @param holder The holder of the recipe.
    ///
    /// @return `true` if the recipe of the given holder is of this type.
    ///
    public boolean is(RecipeHolder<?> holder) {
        return holder.value().getType() == this.value();
    }
}
