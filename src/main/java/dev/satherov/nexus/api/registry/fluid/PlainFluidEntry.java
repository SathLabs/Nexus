package dev.satherov.nexus.api.registry.fluid;

import lombok.Getter;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.text.Translatable;
import dev.satherov.nexus.api.text.Translation;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import org.jetbrains.annotations.ApiStatus;

///
/// A holder of one source fluid that a builder registered, bound once the fluid registry event has run,
/// with the entries of its type and its flowing fluid.
///
/// @param <TYPE>    The type of the fluid type.
/// @param <SOURCE>  The type of the source fluid.
/// @param <FLOWING> The type of the flowing fluid.
///
public class PlainFluidEntry
        // @formatter:off
        <
            TYPE extends FluidType,
            SOURCE extends FlowingFluid,
            FLOWING extends FlowingFluid
        >
        extends RegistryEntry<Fluid, SOURCE>
        implements Translatable
        // @formatter:on
{
    
    ///
    /// The `fluid_type.<modid>.<name>` translation.
    ///
    private final Translation translation;
    
    ///
    /// The type's entry.
    ///
    @Getter
    private final RegistryEntry<FluidType, TYPE> type;
    
    ///
    /// The flowing fluid's entry.
    ///
    @Getter
    private final RegistryEntry<Fluid, FLOWING> flowing;
    
    ///
    /// Should only ever be called from [PlainFluidBuilder#createEntry(ResourceKey)].
    ///
    /// @param key         The key of the source.
    /// @param builder     The builder that registers the source.
    /// @param translation The `fluid_type.<modid>.<name>` translation.
    /// @param type        The type's entry.
    /// @param flowing     The flowing fluid's entry.
    ///
    @ApiStatus.Internal
    public PlainFluidEntry(ResourceKey<Fluid> key, EntryBuilder<Fluid, SOURCE, ?, ?, ?> builder, Translation translation, RegistryEntry<FluidType, TYPE> type, RegistryEntry<Fluid, FLOWING> flowing) {
        super(key, builder);
        this.translation = translation;
        this.type = type;
        this.flowing = flowing;
    }
    
    ///
    /// Creates a stack of the source with {@value FluidType#BUCKET_VOLUME} millibuckets.
    ///
    /// @return The created stack.
    ///
    public FluidStack stack() {
        return new FluidStack(this.value(), FluidType.BUCKET_VOLUME);
    }
    
    ///
    /// Creates a stack of the source with the given amount.
    ///
    /// @param amount The amount in millibuckets.
    ///
    /// @return The created stack.
    ///
    public FluidStack stack(int amount) {
        return new FluidStack(this.value(), amount);
    }
    
    ///
    /// @param state The fluid state.
    ///
    /// @return `true` if the given state is of the source or the flowing fluid.
    ///
    public boolean is(FluidState state) {
        return state.is(this) || state.is(this.flowing);
    }
    
    ///
    /// The `fluid_type.<modid>.<name>` translation.
    ///
    /// @return The translation.
    ///
    @Override
    public Translation getTranslation() {
        return this.translation;
    }
}
