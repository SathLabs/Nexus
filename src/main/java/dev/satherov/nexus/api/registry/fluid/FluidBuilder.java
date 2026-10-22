package dev.satherov.nexus.api.registry.fluid;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.item.ItemBuilder;

import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.BiFunction;

///
/// A builder of one source fluid with its type, its flowing fluid, its block, and its bucket, reached through
/// [PlainFluidBuilder#block(BiFunction)].
///
/// @param <TYPE>    The type of the fluid type.
/// @param <SOURCE>  The type of the source fluid.
/// @param <FLOWING> The type of the flowing fluid.
/// @param <BLOCK>   The type of the block.
/// @param <BUCKET>  The type of the bucket.
/// @param <PARENT>  The type of what [#end()] returns.
///
public final class FluidBuilder
        // @formatter:off
        <
            TYPE extends FluidType,
            SOURCE extends FlowingFluid,
            FLOWING extends FlowingFluid,
            BLOCK extends LiquidBlock,
            BUCKET extends BucketItem,
            PARENT
        >
        extends EntryBuilder
        <
            Fluid,
            SOURCE,
            FluidEntry<TYPE, SOURCE, FLOWING, BLOCK, BUCKET>,
            PARENT,
            FluidBuilder<TYPE, SOURCE, FLOWING, BLOCK, BUCKET, PARENT>
        >
        // @formatter:on
{
    
    ///
    /// The builder of the fluid, which creates the fluid and its entry.
    ///
    private final PlainFluidBuilder<TYPE, SOURCE, FLOWING, PARENT> fluid;
    
    ///
    /// The English name of the bucket.
    ///
    private final String english;
    
    ///
    /// The builder of the bucket.
    ///
    private ItemBuilder<BUCKET, FluidBuilder<TYPE, SOURCE, FLOWING, BLOCK, BUCKET, PARENT>> bucket;
    
    ///
    /// Takes over the given builder of the fluid and starts the builder of the bucket with the given factory.
    ///
    /// Should only ever be called from [PlainFluidBuilder#block(BiFunction)].
    ///
    /// @param fluid   The builder of the fluid.
    /// @param parent  What [#end()] returns.
    /// @param english The English name of the fluid.
    /// @param bucket  The factory of the bucket, called once inside the registry event with the source and the properties.
    ///
    @ApiStatus.Internal
    public FluidBuilder(PlainFluidBuilder<TYPE, SOURCE, FLOWING, PARENT> fluid, PARENT parent, String english, BiFunction<SOURCE, Item.Properties, BUCKET> bucket) {
        super(fluid, parent);
        this.fluid = fluid;
        this.english = english + " Bucket";
        this.bucket = this.openBucket(bucket);
    }
    
    ///
    /// Starts a new bucket item builder, with the class of the provided factory result.
    ///
    /// Any callbacks or client code called before this will receive the new values despite the lambda claiming otherwise.
    /// This should be called first.
    ///
    /// @param factory The factory of the bucket, called once inside the registry event.
    ///
    /// @return The builder of the bucket.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    public <CUSTOM extends BucketItem> ItemBuilder<CUSTOM, FluidBuilder<TYPE, SOURCE, FLOWING, BLOCK, CUSTOM, PARENT>> bucket(BiFunction<SOURCE, Item.Properties, CUSTOM> factory) {
        this.checkUnregistered();
        //noinspection unchecked The old bucket is replaced right below.
        FluidBuilder<TYPE, SOURCE, FLOWING, BLOCK, CUSTOM, PARENT> builder = (FluidBuilder<TYPE, SOURCE, FLOWING, BLOCK, CUSTOM, PARENT>) this;
        builder.bucket = builder.openBucket(factory);
        return builder.bucket;
    }
    
    ///
    /// The builder of the bucket, for its tabs or its tags.
    ///
    /// @return The builder of the bucket.
    ///
    public ItemBuilder<BUCKET, FluidBuilder<TYPE, SOURCE, FLOWING, BLOCK, BUCKET, PARENT>> bucket() {
        return this.bucket;
    }
    
    ///
    /// Starts a new bucket item builder, with the class of the provided factory result as `<modid>:<name>_bucket`.
    ///
    /// The default properties will be set to `stacksTo(1)` and `craftRemainder(Items.BUCKET)`.
    ///
    private ItemBuilder<BUCKET, FluidBuilder<TYPE, SOURCE, FLOWING, BLOCK, BUCKET, PARENT>> openBucket(BiFunction<SOURCE, Item.Properties, BUCKET> factory) {
        DeferredHolder<Fluid, SOURCE> source = DeferredHolder.create(this.getKey());
        ItemBuilder<BUCKET, FluidBuilder<TYPE, SOURCE, FLOWING, BLOCK, BUCKET, PARENT>> bucket = new ItemBuilder<>(
                this.getRegistrations(),
                this.getName() + "_bucket",
                this.english,
                this,
                properties -> factory.apply(source.value(), properties)
        );
        
        return bucket.properties(properties -> properties.stacksTo(1).craftRemainder(Items.BUCKET));
    }
    
    ///
    /// Creates the source with the builder of the fluid.
    ///
    /// @return The created source.
    ///
    @Override
    protected SOURCE create() {
        return this.fluid.create();
    }
    
    ///
    /// Creates the entry of the full fluid with the builder of the fluid, which registers the block and the bucket first.
    ///
    /// @param key The key of the source.
    ///
    /// @return The created entry.
    ///
    @Override
    protected FluidEntry<TYPE, SOURCE, FLOWING, BLOCK, BUCKET> createEntry(ResourceKey<Fluid> key) {
        return (FluidEntry<TYPE, SOURCE, FLOWING, BLOCK, BUCKET>) this.fluid.createEntry(key);
    }
}
