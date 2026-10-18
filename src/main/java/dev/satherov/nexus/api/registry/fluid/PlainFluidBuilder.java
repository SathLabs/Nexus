package dev.satherov.nexus.api.registry.fluid;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.registry.block.PlainBlockBuilder;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.registry.item.ItemBuilder;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.internal.registry.client.ClientDispatch;

import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

import com.google.common.base.Suppliers;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

///
/// A builder of one source fluid with its type and its flowing fluid.
///
/// A block for in-world placement can be registered through [PlainFluidBuilder#block()].
///
/// @param <TYPE>    The type of the fluid type.
/// @param <SOURCE>  The type of the source fluid.
/// @param <FLOWING> The type of the flowing fluid.
/// @param <PARENT>  The type of what [#end()] returns.
///
public final class PlainFluidBuilder
        // @formatter:off
        <
            TYPE extends FluidType,
            SOURCE extends FlowingFluid,
            FLOWING extends FlowingFluid,
            PARENT
        >
        extends EntryBuilder
        <
            Fluid,
            SOURCE,
            PlainFluidEntry<TYPE, SOURCE, FLOWING>,
            PARENT,
            PlainFluidBuilder<TYPE, SOURCE, FLOWING, PARENT>
        >
        // @formatter:on
{
    
    ///
    /// The English name of the fluid.
    ///
    private final String english;
    
    ///
    /// The edits of the properties the type factory receives, in call order.
    ///
    private final List<UnaryOperator<FluidType.Properties>> typeEdits = new ArrayList<>();
    
    ///
    /// The edits of the properties both fluid factories receive, in call order.
    ///
    private final List<UnaryOperator<BaseFlowingFluid.Properties>> edits = new ArrayList<>();
    
    ///
    /// The properties both fluid factories receive, linked on the first call.
    ///
    private final Supplier<BaseFlowingFluid.Properties> linked = Suppliers.memoize(this::link);
    
    ///
    /// The factory of the type, called with the properties once the description id is set.
    ///
    private Function<FluidType.Properties, TYPE> typeFactory;
    
    ///
    /// The factory of the source, called with the linked properties.
    ///
    private Function<BaseFlowingFluid.Properties, SOURCE> sourceFactory;
    
    ///
    /// The factory of the flowing fluid, called with the linked properties.
    ///
    private Function<BaseFlowingFluid.Properties, FLOWING> flowingFactory;
    
    ///
    /// The model of both fluids, or `null` if [#model(ClientOnly)] was not called.
    ///
    private @Nullable ClientOnly<Supplier<FluidModel.Unbaked>> model;
    
    ///
    /// The client extensions of the type, or `null` if [#extensions(ClientOnly)] was not called.
    ///
    private @Nullable ClientOnly<Supplier<IClientFluidTypeExtensions>> extensions;
    
    ///
    /// The builder of the liquid block, or `null` if [#block(BiFunction)] was not called.
    ///
    private @Nullable PlainBlockBuilder<? extends LiquidBlock, ? extends FluidBuilder<TYPE, SOURCE, FLOWING, ?, ?, PARENT>> block;
    
    ///
    /// The entry of the fluid, or `null` until [#createEntry(ResourceKey)] created it.
    ///
    private @Nullable PlainFluidEntry<TYPE, SOURCE, FLOWING> entry;
    
    ///
    /// Starts the fluid under `fluid_type.<modid>.<name>`, with the type and the source as `<modid>:<name>`,
    /// and the flowing fluid as `<modid>:flowing_<name>`.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// Both fluid factories receive the same properties, which link the type, the source, and the flowing fluid together.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English name.
    /// @param parent        What [#end()] returns.
    /// @param type          The factory of the type, called once inside the registry event with the properties with the description id set.
    /// @param source        The factory of the source, called once inside the registry event with the linked properties.
    /// @param flowing       The factory of the flowing fluid, called once inside the registry event with the linked properties.
    ///
    public PlainFluidBuilder(
            Registrations registrations,
            String name,
            String english,
            PARENT parent,
            Function<FluidType.Properties, TYPE> type,
            Function<BaseFlowingFluid.Properties, SOURCE> source,
            Function<BaseFlowingFluid.Properties, FLOWING> flowing
    ) {
        super(registrations, Registries.FLUID, name, parent);
        this.english = english;
        this.typeFactory = type;
        this.sourceFactory = source;
        this.flowingFactory = flowing;
    }
    
    ///
    /// Replaces the fluid type with the custom one supplied through the factory.
    ///
    /// Any callbacks or client code called before this will receive the new type despite the lambda claiming otherwise, so call this first to avoid issues!
    ///
    /// @param factory The factory of the type, called once inside the registry event.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    public <CUSTOM extends FluidType> PlainFluidBuilder<CUSTOM, SOURCE, FLOWING, PARENT> type(Function<FluidType.Properties, CUSTOM> factory) {
        this.checkUnregistered();
        //noinspection unchecked The old factory of the type is replaced below.
        PlainFluidBuilder<CUSTOM, SOURCE, FLOWING, PARENT> builder = (PlainFluidBuilder<CUSTOM, SOURCE, FLOWING, PARENT>) this;
        builder.typeFactory = factory;
        return builder;
    }
    
    ///
    /// Edits the properties the type factory receives.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// Runs on the properties with the description id already set.
    ///
    /// @param edit The edit, called with the properties inside the registry event.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public PlainFluidBuilder<TYPE, SOURCE, FLOWING, PARENT> typeProperties(UnaryOperator<FluidType.Properties> edit) {
        this.checkUnregistered();
        this.typeEdits.add(edit);
        return this;
    }
    
    ///
    /// Replaces the source fluid and flowing fluid with the custom ones supplied through the factories.
    ///
    /// Any callbacks or client code called before this will receive the new values despite the lambda claiming otherwise, so call this first to avoid issues!
    ///
    /// @param source  The factory of the source, called once inside the registry event.
    /// @param flowing The factory of the flowing fluid, called once inside the registry event.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    public <STILL extends FlowingFluid, MOVING extends FlowingFluid> PlainFluidBuilder<TYPE, STILL, MOVING, PARENT> fluids(
            Function<BaseFlowingFluid.Properties, STILL> source,
            Function<BaseFlowingFluid.Properties, MOVING> flowing
    ) {
        this.checkUnregistered();
        //noinspection unchecked The old factories of both fluids are replaced below.
        PlainFluidBuilder<TYPE, STILL, MOVING, PARENT> builder = (PlainFluidBuilder<TYPE, STILL, MOVING, PARENT>) this;
        builder.sourceFactory = source;
        builder.flowingFactory = flowing;
        return builder;
    }
    
    ///
    /// Edits the properties both fluid factories receive.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// Fluid type, source, and flowing fluid will already be linked when this is called, bucket and block will be set if they exist.
    ///
    /// @param edit The edit, called with the properties inside the registry event.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public PlainFluidBuilder<TYPE, SOURCE, FLOWING, PARENT> properties(UnaryOperator<BaseFlowingFluid.Properties> edit) {
        this.checkUnregistered();
        this.edits.add(edit);
        return this;
    }
    
    ///
    /// Registers the given model for the source and the flowing fluid on the client.
    ///
    /// If no model is registered, the fluid will render as the missing model.
    ///
    /// @param model The model of both fluids.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public PlainFluidBuilder<TYPE, SOURCE, FLOWING, PARENT> model(ClientOnly<Supplier<FluidModel.Unbaked>> model) {
        this.checkUnregistered();
        this.model = model;
        return this;
    }
    
    ///
    /// Registers the given client extensions for the fluid type on the client.
    ///
    /// @param extensions The client extensions of the type.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public PlainFluidBuilder<TYPE, SOURCE, FLOWING, PARENT> extensions(ClientOnly<Supplier<IClientFluidTypeExtensions>> extensions) {
        this.checkUnregistered();
        this.extensions = extensions;
        return this;
    }
    
    ///
    /// Starts a new liquid block builder, with [LiquidBlock].
    ///
    /// A [BucketItem] will be created as `<modid>:<name>_bucket`, named `<english> Bucket`, unless [FluidBuilder#bucket(BiFunction)] is called with a custom value.
    ///
    /// This builder will throw on every later call.
    ///
    /// @return The builder of the block.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    public PlainBlockBuilder<LiquidBlock, FluidBuilder<TYPE, SOURCE, FLOWING, LiquidBlock, BucketItem, PARENT>> block() {
        return this.block(LiquidBlock::new);
    }
    
    ///
    /// Starts a new liquid block builder, with the class of the provided factory result.
    ///
    /// A [BucketItem] will be created as `<modid>:<name>_bucket`, named `<english> Bucket`, unless [FluidBuilder#bucket(BiFunction)] is called with a custom value.
    ///
    /// This builder will throw on every later call.
    ///
    /// @param factory The factory of the block, called once inside the registry event with the source and the properties with the id set.
    ///
    /// @return The builder of the block.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    public <BLOCK extends LiquidBlock> PlainBlockBuilder<BLOCK, FluidBuilder<TYPE, SOURCE, FLOWING, BLOCK, BucketItem, PARENT>> block(BiFunction<SOURCE, BlockBehaviour.Properties, BLOCK> factory) {
        FluidBuilder<TYPE, SOURCE, FLOWING, BLOCK, BucketItem, PARENT> builder = new FluidBuilder<>(this, this.end(), this.english, BucketItem::new);
        DeferredHolder<Fluid, SOURCE> source = DeferredHolder.create(this.getKey());
        PlainBlockBuilder<BLOCK, FluidBuilder<TYPE, SOURCE, FLOWING, BLOCK, BucketItem, PARENT>> block = new PlainBlockBuilder<>(
                this.getRegistrations(),
                this.getName(),
                this.english,
                builder,
                properties -> factory.apply(source.value(), properties)
        );
        
        this.block = block;
        return block.properties(BlockBehaviour.Properties.ofFullCopy(Blocks.WATER));
    }
    
    ///
    /// Creates the source with the factory, from the linked properties.
    ///
    /// @return The created source.
    ///
    @Override
    protected SOURCE create() {
        return this.sourceFactory.apply(this.linked.get());
    }
    
    ///
    /// Links the type, source and flowing fluid, as well as the block and the bucket if there are any,
    /// and then applies any property edits.
    ///
    private BaseFlowingFluid.Properties link() {
        PlainFluidEntry<TYPE, SOURCE, FLOWING> entry = Objects.requireNonNull(this.entry);
        BaseFlowingFluid.Properties properties = new BaseFlowingFluid.Properties(entry.getType(), entry, entry.getFlowing());
        if (entry instanceof FluidEntry<?, ?, ?, ?, ?> full) {
            properties.block(full.getBlock()).bucket(full.getBucket());
        }
        
        for (UnaryOperator<BaseFlowingFluid.Properties> edit : this.edits) {
            properties = edit.apply(properties);
        }
        
        return properties;
    }
    
    ///
    /// Creates the entry for the given key, registering the type, the flowing fluid, and the block and the bucket if set.
    ///
    /// Adds the model and the client extensions as client hooks if they were given.
    ///
    /// @param key The key of the source.
    ///
    /// @return The created entry.
    ///
    @Override
    protected PlainFluidEntry<TYPE, SOURCE, FLOWING> createEntry(ResourceKey<Fluid> key) {
        Registrations registrations = this.getRegistrations();
        Translation translation = this.define("fluid_type", this.english);
        RegistryEntry<FluidType, TYPE> type = registrations.register(NeoForgeRegistries.Keys.FLUID_TYPES, this.getName(), () -> this.createType(translation)).register();
        RegistryEntry<Fluid, FLOWING> flowing = registrations.register(Registries.FLUID, "flowing_" + this.getName(), () -> this.flowingFactory.apply(this.linked.get())).register();
        PlainBlockBuilder<? extends LiquidBlock, ? extends FluidBuilder<TYPE, SOURCE, FLOWING, ?, ?, PARENT>> block = this.block;
        PlainFluidEntry<TYPE, SOURCE, FLOWING> entry = block == null ?
                new PlainFluidEntry<>(key, this, translation, type, flowing) :
                new FluidEntry<>(key, block.end(), translation, type, flowing, EntryBuilder.registerSubObject(block), EntryBuilder.registerSubObject(block.end().bucket()));
        
        this.entry = entry;
        ClientOnly<Supplier<FluidModel.Unbaked>> model = this.model;
        if (model != null) {
            this.addClientHook(bus -> ClientDispatch.fluidModel(bus, entry, model));
        }
        
        ClientOnly<Supplier<IClientFluidTypeExtensions>> extensions = this.extensions;
        if (extensions != null) {
            this.addClientHook(bus -> ClientDispatch.fluidExtensions(bus, entry, extensions));
        }
        
        return entry;
    }
    
    ///
    /// Creates the type with the factory applying the properties with the description id of the given translation set
    /// and then applies any type edits.
    ///
    private TYPE createType(Translation translation) {
        FluidType.Properties properties = FluidType.Properties.create().descriptionId(translation.getKey());
        for (UnaryOperator<FluidType.Properties> edit : this.typeEdits) {
            properties = edit.apply(properties);
        }
        
        return this.typeFactory.apply(properties);
    }
}
