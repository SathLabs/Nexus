package dev.satherov.nexus.internal.component.vanilla;

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
/// The `nexus:filter` custom ingredient, which passes an item stack whose item is in the set and which passes the component filter.
///
/// The ingredient is a class, since a record component `items` would clash with [ICustomIngredient#items()].
///
@EqualsAndHashCode
@ApiStatus.Internal
@RequiredArgsConstructor
public final class FilterIngredient implements ICustomIngredient {

    ///
    /// The codec of the ingredient, with the set under `items` and the component filter under `filter`.
    ///
    private static final StructCodec<FilterIngredient, Access.Registries> CODEC = NexusCodec.struct(
            "FilterIngredient",
            NexusCodec.holderSet(Registries.ITEM).field("items", ingredient -> ingredient.items),
            ComponentFilter.CODEC.field("filter", ingredient -> ingredient.filter),
            FilterIngredient::new
    );

    ///
    /// The `nexus:filter` ingredient type, saved and synced with the codec of the ingredient.
    ///
    public static final IngredientType<FilterIngredient> TYPE = new IngredientType<>(FilterIngredient.CODEC.asMapCodec(), FilterIngredient.CODEC.asStream());

    ///
    /// The set that the item of a passing item stack is in.
    ///
    private final HolderSet<Item> items;

    ///
    /// The component filter that a passing item stack passes.
    ///
    private final ComponentFilter filter;

    ///
    /// Tests if the item of the given item stack is in the set and the item stack passes the component filter.
    ///
    /// @param stack The item stack.
    ///
    /// @return `true` if the item stack passes.
    ///
    @Override
    public boolean test(ItemStack stack) {
        return this.items.contains(stack.typeHolder()) && this.filter.test(stack);
    }

    ///
    /// The items of the set.
    ///
    /// @return The items of the set.
    ///
    @Override
    public Stream<Holder<Item>> items() {
        return this.items.stream();
    }

    ///
    /// Will always return `false`.
    ///
    /// @return `false`.
    ///
    @Override
    public boolean isSimple() {
        return false;
    }

    ///
    /// The `nexus:filter` ingredient type.
    ///
    /// @return The ingredient type.
    ///
    @Override
    public IngredientType<?> getType() {
        return FilterIngredient.TYPE;
    }
}
