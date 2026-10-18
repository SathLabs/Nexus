package dev.satherov.nexus.api.registry.fluid;

import lombok.Getter;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.registry.block.PlainBlockEntry;
import dev.satherov.nexus.api.registry.item.ItemEntry;
import dev.satherov.nexus.api.text.Translation;

import net.neoforged.neoforge.fluids.FluidType;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.ApiStatus;

///
/// A holder of one source fluid with its block and bucket that a builder registered, bound once the fluid registry
/// event has run, with the entries of its type, its flowing fluid, its block, and its bucket.
///
/// @param <TYPE>    The type of the fluid type.
/// @param <SOURCE>  The type of the source fluid.
/// @param <FLOWING> The type of the flowing fluid.
/// @param <BLOCK>   The type of the block.
/// @param <BUCKET>  The type of the bucket.
///
public final class FluidEntry
        // @formatter:off
        <
            TYPE extends FluidType,
            SOURCE extends FlowingFluid,
            FLOWING extends FlowingFluid,
            BLOCK extends LiquidBlock,
            BUCKET extends BucketItem
        >
        extends PlainFluidEntry<TYPE, SOURCE, FLOWING>
        // @formatter:on
{
    
    ///
    /// The block's entry.
    ///
    @Getter
    private final PlainBlockEntry<BLOCK> block;
    
    ///
    /// The bucket's entry.
    ///
    @Getter
    private final ItemEntry<BUCKET> bucket;
    
    ///
    /// Should only ever be called from [PlainFluidBuilder#createEntry(ResourceKey)].
    ///
    /// @param key         The key of the source.
    /// @param builder     The builder that registers the source.
    /// @param translation The `fluid_type.<modid>.<name>` translation.
    /// @param type        The type's entry.
    /// @param flowing     The flowing fluid's entry.
    /// @param block       The block's entry.
    /// @param bucket      The bucket's entry.
    ///
    @ApiStatus.Internal
    public FluidEntry(
            ResourceKey<Fluid> key,
            EntryBuilder<Fluid, SOURCE, ?, ?, ?> builder,
            Translation translation,
            RegistryEntry<FluidType, TYPE> type,
            RegistryEntry<Fluid, FLOWING> flowing,
            PlainBlockEntry<BLOCK> block,
            ItemEntry<BUCKET> bucket
    ) {
        super(key, builder, translation, type, flowing);
        this.block = block;
        this.bucket = bucket;
    }
}
