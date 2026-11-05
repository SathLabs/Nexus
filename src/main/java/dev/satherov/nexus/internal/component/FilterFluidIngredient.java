package dev.satherov.nexus.internal.component;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.component.ComponentFilter;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredientType;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.ApiStatus;

import java.util.stream.Stream;

///
/// The fluid ingredient `nexus:filter`, which accepts a stack whose fluid is in the set and which passes the filter.
///
/// Written as `{"neoforge:ingredient_type": "nexus:filter", "fluids": ..., "filter": {...}}`.
///
@ApiStatus.Internal
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = false)
public final class FilterFluidIngredient extends FluidIngredient {

    ///
    /// The codec of the ingredient.
    ///
    private static final StructCodec<FilterFluidIngredient, Access.Registries> CODEC = NexusCodec.struct(
            "filterFluidIngredient",
            NexusCodec.holderSet(Registries.FLUID).field("fluids", ingredient -> ingredient.fluids),
            ComponentFilter.CODEC.field("filter", ingredient -> ingredient.filter),
            FilterFluidIngredient::new
    );

    ///
    /// The ingredient type, registered as `nexus:filter`.
    ///
    public static final FluidIngredientType<FilterFluidIngredient> TYPE = new FluidIngredientType<>(FilterFluidIngredient.CODEC.asMapCodec(), FilterFluidIngredient.CODEC.asStream());

    ///
    /// The fluids a stack must be one of.
    ///
    private final HolderSet<Fluid> fluids;

    ///
    /// The filter a stack must pass.
    ///
    private final ComponentFilter filter;

    ///
    /// If the fluid of the stack is in the set and the stack passes the filter.
    ///
    @Override
    public boolean test(FluidStack stack) {
        return this.fluids.contains(stack.typeHolder()) && this.filter.test(stack);
    }

    ///
    /// The fluids of the set.
    ///
    @Override
    protected Stream<Holder<Fluid>> generateFluids() {
        return this.fluids.stream();
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
    public FluidIngredientType<?> getType() {
        return FilterFluidIngredient.TYPE;
    }
}
