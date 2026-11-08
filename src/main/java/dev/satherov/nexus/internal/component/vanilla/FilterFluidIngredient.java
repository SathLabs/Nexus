package dev.satherov.nexus.internal.component.vanilla;

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
/// The `nexus:filter` fluid ingredient, which passes a fluid stack whose fluid is in the set and which passes the component filter.
///
@ApiStatus.Internal
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = false)
public final class FilterFluidIngredient extends FluidIngredient {

    ///
    /// The codec of the fluid ingredient, with the set under `fluids` and the component filter under `filter`.
    ///
    private static final StructCodec<FilterFluidIngredient, Access.Registries> CODEC = NexusCodec.struct(
            "FilterFluidIngredient",
            NexusCodec.holderSet(Registries.FLUID).field("fluids", ingredient -> ingredient.fluids),
            ComponentFilter.CODEC.field("filter", ingredient -> ingredient.filter),
            FilterFluidIngredient::new
    );

    ///
    /// The `nexus:filter` fluid ingredient type, saved and synced with the codec of the fluid ingredient.
    ///
    public static final FluidIngredientType<FilterFluidIngredient> TYPE = new FluidIngredientType<>(FilterFluidIngredient.CODEC.asMapCodec(), FilterFluidIngredient.CODEC.asStream());

    ///
    /// The set that the fluid of a passing fluid stack is in.
    ///
    private final HolderSet<Fluid> fluids;

    ///
    /// The component filter that a passing fluid stack passes.
    ///
    private final ComponentFilter filter;

    ///
    /// Tests if the fluid of the given fluid stack is in the set and the fluid stack passes the component filter.
    ///
    /// @param fluidStack The fluid stack.
    ///
    /// @return `true` if the fluid stack passes.
    ///
    @Override
    public boolean test(FluidStack fluidStack) {
        return this.fluids.contains(fluidStack.typeHolder()) && this.filter.test(fluidStack);
    }

    ///
    /// Creates a stream of the fluids of the set.
    ///
    /// @return The fluids of the set.
    ///
    @Override
    protected Stream<Holder<Fluid>> generateFluids() {
        return this.fluids.stream();
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
    /// The `nexus:filter` fluid ingredient type.
    ///
    /// @return The fluid ingredient type.
    ///
    @Override
    public FluidIngredientType<?> getType() {
        return FilterFluidIngredient.TYPE;
    }
}
