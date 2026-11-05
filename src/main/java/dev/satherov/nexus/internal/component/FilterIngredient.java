package dev.satherov.nexus.internal.component;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.component.ComponentFilter;

import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;

import java.util.stream.Stream;

///
/// The custom ingredient `nexus:filter`, which accepts a stack whose item is in the set and which passes the filter.
///
/// Written as `{"neoforge:ingredient_type": "nexus:filter", "items": ..., "filter": {...}}`.
///
@EqualsAndHashCode
@ApiStatus.Internal
@RequiredArgsConstructor
public final class FilterIngredient implements ICustomIngredient {

    ///
    /// The codec of the ingredient.
    ///
    private static final StructCodec<FilterIngredient, Access.Registries> CODEC = NexusCodec.struct(
            "filterIngredient",
            NexusCodec.holderSet(Registries.ITEM).field("items", ingredient -> ingredient.items),
            ComponentFilter.CODEC.field("filter", ingredient -> ingredient.filter),
            FilterIngredient::new
    );

    ///
    /// The ingredient type, registered as `nexus:filter`.
    ///
    public static final IngredientType<FilterIngredient> TYPE = new IngredientType<>(FilterIngredient.CODEC.asMapCodec(), FilterIngredient.CODEC.asStream());

    ///
    /// The items a stack must be one of.
    ///
    private final HolderSet<Item> items;

    ///
    /// The filter a stack must pass.
    ///
    private final ComponentFilter filter;

    ///
    /// If the item of the stack is in the set and the stack passes the filter.
    ///
    @Override
    public boolean test(ItemStack stack) {
        return this.items.contains(stack.typeHolder()) && this.filter.test(stack);
    }

    ///
    /// The items of the set.
    ///
    @Override
    public Stream<Holder<Item>> items() {
        return this.items.stream();
    }

    ///
    /// Will always return `false`.
    ///
    @Override
    public boolean isSimple() {
        return false;
    }

    ///
    /// Will always return [#TYPE].
    ///
    @Override
    public IngredientType<?> getType() {
        return FilterIngredient.TYPE;
    }
}
