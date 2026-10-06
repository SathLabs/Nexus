package dev.satherov.nexus.api.registry.block.entity;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.registry.client.ClientDispatch;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.ICapabilityProvider;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

///
/// A builder of one block entity type, valid for the blocks given to [#blocks(Holder...)].
///
/// @param <ENTITY> The type of the block entities of the type.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class BlockEntityBuilder<ENTITY extends BlockEntity, PARENT> extends EntryBuilder<BlockEntityType<?>, BlockEntityType<ENTITY>, BlockEntityEntry<ENTITY>, PARENT, BlockEntityBuilder<ENTITY, PARENT>> {
    
    ///
    /// The factory of the block entities of the type.
    ///
    private final BlockEntityType.BlockEntitySupplier<ENTITY> factory;
    
    ///
    /// The holders of the blocks the type is valid for.
    ///
    private final List<Holder<Block>> blocks = new ArrayList<>();
    
    ///
    /// The registrations of the capabilities, called with the event and the created type, in call order.
    ///
    private final List<BiConsumer<RegisterCapabilitiesEvent, BlockEntityType<ENTITY>>> capabilities = new ArrayList<>();
    
    ///
    /// The registration of the renderer, called on the client with the mod bus and the entry, or `null` if
    /// [#renderer(ClientOnly)] was not called.
    ///
    private @Nullable BiConsumer<IEventBus, BlockEntityEntry<ENTITY>> renderer;
    
    ///
    /// Starts the block entity type `<modid>:<name>` of the given factory.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// The type has no translation.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param parent        What [#end()] returns.
    /// @param factory       The factory of the block entities of the type.
    ///
    public BlockEntityBuilder(Registrations registrations, String name, PARENT parent, BlockEntityType.BlockEntitySupplier<ENTITY> factory) {
        super(registrations, Registries.BLOCK_ENTITY_TYPE, name, parent);
        this.factory = factory;
    }
    
    ///
    /// Adds the given blocks to the ones the type is valid for.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// The holders are resolved once the type is created, so the entries of blocks may still be unbound.
    ///
    /// @param blocks The holders of the blocks.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @SafeVarargs
    @CanIgnoreReturnValue
    public final BlockEntityBuilder<ENTITY, PARENT> blocks(Holder<Block>... blocks) {
        this.checkUnregistered();
        Collections.addAll(this.blocks, blocks);
        return this;
    }
    
    ///
    /// Registers the given capability for every block entity of the type.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// @param capability The block capability.
    /// @param provider   The provider of the capability, called with the block entity and the context.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public <CAPABILITY, CONTEXT extends @Nullable Object> BlockEntityBuilder<ENTITY, PARENT> capability(BlockCapability<CAPABILITY, CONTEXT> capability, ICapabilityProvider<ENTITY, CONTEXT, CAPABILITY> provider) {
        this.checkUnregistered();
        this.capabilities.add((event, type) -> event.registerBlockEntity(capability, type, provider));
        return this;
    }
    
    ///
    /// Registers the renderer of the given factory for the block entities of the type on the client.
    ///
    /// @param renderer The factory of the renderer, called with the context of the renderer provider.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public <STATE extends BlockEntityRenderState> BlockEntityBuilder<ENTITY, PARENT> renderer(ClientOnly<Function<BlockEntityRendererProvider.Context, BlockEntityRenderer<ENTITY, STATE>>> renderer) {
        this.checkUnregistered();
        this.renderer = (bus, entry) -> ClientDispatch.blockEntityRenderer(bus, entry, renderer);
        return this;
    }
    
    ///
    /// Creates the type with the factory, valid for the blocks of the holders.
    ///
    /// @return The created type.
    ///
    @Override
    protected BlockEntityType<ENTITY> create() {
        return new BlockEntityType<>(
                this.factory,
                this.blocks.stream()
                        .map(Holder::value)
                        .collect(Collectors.toUnmodifiableSet())
        );
    }
    
    ///
    /// Creates the entry for the given key, adds a listener for every capability to the event bus of the mod, and adds
    /// the renderer as a client hook if [#renderer(ClientOnly)] was called.
    ///
    /// @param key The key of the block entity type.
    ///
    /// @return The created entry.
    ///
    @Override
    protected BlockEntityEntry<ENTITY> createEntry(ResourceKey<BlockEntityType<?>> key) {
        BlockEntityEntry<ENTITY> entry = new BlockEntityEntry<>(key, this);
        IEventBus modBus = this.getRegistrations().getMod().getEventBus();
        this.capabilities.forEach(capability -> modBus.addListener(RegisterCapabilitiesEvent.class, event -> capability.accept(event, entry.value())));
        BiConsumer<IEventBus, BlockEntityEntry<ENTITY>> renderer = this.renderer;
        if (renderer != null) {
            this.addClientHook(bus -> renderer.accept(bus, entry));
        }
        
        return entry;
    }
}
