package dev.satherov.nexus.api.registry.recipe;

import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

///
/// A builder of one recipe type and the serializer of its recipes.
///
/// @param <RECIPE> The type of the recipes of the type.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class RecipeTypeBuilder<RECIPE extends Recipe<?>, PARENT>
        // @formatter:off
        extends EntryBuilder
        <
            RecipeType<?>,
            RecipeType<RECIPE>,
            RecipeTypeEntry<RECIPE>,
            PARENT,
            RecipeTypeBuilder<RECIPE, PARENT>
        >
        // @formatter:on
{
    
    ///
    /// The codec of the recipes of the type.
    ///
    private final StructCodec<RECIPE, ?> codec;
    
    ///
    /// Starts the recipe type and recipe serializer `<modid>:<name>`,
    /// which reads and writes the recipes with the given codec.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// The type has no translation.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param parent        What [#end()] returns.
    /// @param codec         The codec of the recipes of the type.
    ///
    public RecipeTypeBuilder(Registrations registrations, String name, PARENT parent, StructCodec<RECIPE, ?> codec) {
        super(registrations, Registries.RECIPE_TYPE, name, parent);
        this.codec = codec;
    }
    
    ///
    /// Creates the type, named after the identifier.
    ///
    /// @return The created type.
    ///
    @Override
    protected RecipeType<RECIPE> create() {
        return RecipeType.simple(this.getKey().identifier());
    }
    
    ///
    /// Creates the entry for the given key and registers the serializer under the same name.
    ///
    /// @param key The key of the recipe type.
    ///
    /// @return The created entry.
    ///
    @Override
    protected RecipeTypeEntry<RECIPE> createEntry(ResourceKey<RecipeType<?>> key) {
        return new RecipeTypeEntry<>(
                key,
                this,
                this.getRegistrations().register(Registries.RECIPE_SERIALIZER, this.getName(), () -> VanillaAdapters.recipeSerializer(this.codec)).register()
        );
    }
}
