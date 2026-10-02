package dev.satherov.nexus.api.registry;

import lombok.Getter;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.api.registry.attachment.AttachmentBuilder;
import dev.satherov.nexus.api.registry.attribute.AttributeBuilder;
import dev.satherov.nexus.api.registry.block.PlainBlockBuilder;
import dev.satherov.nexus.api.registry.block.entity.BlockEntityBuilder;
import dev.satherov.nexus.api.registry.component.DataComponentBuilder;
import dev.satherov.nexus.api.registry.effect.EffectBuilder;
import dev.satherov.nexus.api.registry.entity.EntityBuilder;
import dev.satherov.nexus.api.registry.fluid.PlainFluidBuilder;
import dev.satherov.nexus.api.registry.item.ItemBuilder;
import dev.satherov.nexus.api.registry.menu.MenuBuilder;
import dev.satherov.nexus.api.registry.particle.ParticleBuilder;
import dev.satherov.nexus.api.registry.potion.PotionBuilder;
import dev.satherov.nexus.api.registry.recipe.RecipeTypeBuilder;
import dev.satherov.nexus.api.registry.sound.SoundBuilder;
import dev.satherov.nexus.api.registry.tab.TabBuilder;
import dev.satherov.nexus.internal.registry.EntryRegister;
import dev.satherov.nexus.internal.registry.client.ClientDispatch;
import dev.satherov.nexus.internal.registry.tab.TabContents;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

///
/// A table that holds every entry one mod registers, and starts the builders that register them.
///
/// Every builder should be registered from the mod's constructor, before the registry events run.
///
public final class Registrations {
    
    ///
    /// The handle of the mod.
    ///
    @Getter
    private final NexusMod mod;
    
    ///
    /// Every register of the mod by the key of its registry.
    ///
    private final Map<ResourceKey<? extends Registry<?>>, EntryRegister<?>> registersByKey = new HashMap<>();
    
    ///
    /// Every entry of the mod, in registration order.
    ///
    private final List<RegistryEntry<?, ?>> entries = new ArrayList<>();
    
    ///
    /// Creates the empty registrations of the given mod and subscribes their listeners to the event bus of the mod.
    ///
    /// Should only ever be called from [NexusMod#getRegistrations()].
    ///
    /// @param mod The handle of the mod.
    ///
    @ApiStatus.Internal
    public Registrations(NexusMod mod) {
        this.mod = mod;
        
        mod.getEventBus().addListener(BuildCreativeModeTabContentsEvent.class, new TabContents(this)::onBuildContents);
        if (FMLEnvironment.getDist().isClient()) {
            ClientDispatch.listen(mod.getEventBus(), Collections.unmodifiableMap(this.registersByKey));
        }
    }
    
    ///
    /// The register of the given registry, created and subscribed to the event bus of the mod on the first call.
    ///
    /// Meant to be used for a mod's own builders.
    ///
    /// @param registry The key of the registry.
    ///
    /// @return The register of the registry.
    ///
    public <REGISTRY> DeferredRegister<REGISTRY> getRegister(ResourceKey<? extends Registry<REGISTRY>> registry) {
        //noinspection unchecked The register under a key is of the registry of that key.
        return (DeferredRegister<REGISTRY>) this.registersByKey.computeIfAbsent(registry, _ -> {
            EntryRegister<REGISTRY> register = new EntryRegister<>(registry, this.mod.getModId(), this.entries);
            register.register(this.mod.getEventBus());
            return register;
        });
    }
    
    ///
    /// Every entry registered so far, sub-objects included, in registration order.
    ///
    /// @return Every entry registered so far.
    ///
    public @Unmodifiable List<RegistryEntry<?, ?>> getAll() {
        return List.copyOf(this.entries);
    }
    
    ///
    /// Gets every entry of the given registry registered so far, in registration order.
    ///
    /// @param registry The key of the registry.
    ///
    /// @return Every entry of the registry registered so far.
    ///
    public <REGISTRY> @Unmodifiable List<RegistryEntry<REGISTRY, ? extends REGISTRY>> getAll(ResourceKey<? extends Registry<REGISTRY>> registry) {
        //noinspection unchecked An entry is of the registry that its key is for.
        return this.entries.stream()
                .filter(entry -> entry.getKey().isFor(registry))
                .<RegistryEntry<REGISTRY, ? extends REGISTRY>>map(entry -> (RegistryEntry<REGISTRY, ? extends REGISTRY>) entry)
                .toList();
    }
    
    ///
    /// Starts one object of any registry, which the given supplier creates inside the registry event.
    ///
    /// @param registry The key of the registry.
    /// @param name     The path of the identifier.
    /// @param supplier The supplier of the object, called once inside the registry event.
    ///
    /// @return The builder of the object.
    ///
    public <REGISTRY, VALUE extends REGISTRY> PlainBuilder<REGISTRY, VALUE, Registrations> register(
            ResourceKey<? extends Registry<REGISTRY>> registry,
            String name,
            Supplier<VALUE> supplier
    ) {
        return new PlainBuilder<>(this, registry, name, this, supplier);
    }
    
    ///
    /// Starts one map codec of a registry whose objects are map codecs, such as condition serializers or global loot
    /// modifier serializers.
    ///
    /// @param registry The key of the registry.
    /// @param name     The path of the identifier.
    /// @param codec    The map codec.
    ///
    /// @return The builder of the map codec.
    ///
    public <ELEMENT, VALUE extends ELEMENT> PlainBuilder<MapCodec<? extends ELEMENT>, MapCodec<VALUE>, Registrations> codec(
            ResourceKey<? extends Registry<MapCodec<? extends ELEMENT>>> registry,
            String name,
            MapCodec<VALUE> codec
    ) {
        return new PlainBuilder<>(this, registry, name, this, () -> codec);
    }
    
    ///
    /// Starts an attachment type with the given initial value.
    ///
    /// If [AttachmentBuilder#codec(StructCodec)] isn't called, the attachment will not be saved.
    ///
    /// @param name    The path of the identifier.
    /// @param initial The supplier of the initial value, called each time the attachment is read from a holder that doesn't have it.
    ///
    /// @return The builder of the attachment type.
    ///
    public <VALUE> AttachmentBuilder<VALUE, Registrations> attachment(String name, Supplier<VALUE> initial) {
        return new AttachmentBuilder<>(this, name, this, initial);
    }
    
    ///
    /// Starts a ranged entity attribute with the given default, minimum, and maximum value.
    ///
    /// @param name    The path of the identifier.
    /// @param english The English name.
    /// @param initial The default value.
    /// @param min     The minimum value.
    /// @param max     The maximum value.
    ///
    /// @return The builder of the attribute.
    ///
    public AttributeBuilder<Registrations> attribute(String name, String english, double initial, double min, double max) {
        return new AttributeBuilder<>(this, name, english, this, initial, min, max);
    }
    
    ///
    /// Starts a plain block with `BlockBehaviour.Properties.of()`.
    ///
    /// @param name    The path of the identifier.
    /// @param english The English name.
    ///
    /// @return The builder of the block.
    ///
    public PlainBlockBuilder<Block, Registrations> block(String name, String english) {
        return this.block(name, english, Block::new);
    }
    
    ///
    /// Starts a block of the factory's class.
    ///
    /// The factory receives the properties with the id set.
    ///
    /// @param name    The path of the identifier.
    /// @param english The English name.
    /// @param factory The factory of the block, called once inside the registry event.
    ///
    /// @return The builder of the block.
    ///
    public <BLOCK extends Block> PlainBlockBuilder<BLOCK, Registrations> block(String name, String english, Function<BlockBehaviour.Properties, BLOCK> factory) {
        return new PlainBlockBuilder<>(this, name, english, this, factory);
    }
    
    ///
    /// Starts a block entity type of the factory, valid for the blocks the builder is given.
    ///
    /// @param name    The path of the identifier.
    /// @param factory The factory of the block entity, called each time a block entity of the type is created.
    ///
    /// @return The builder of the block entity type.
    ///
    public <ENTITY extends BlockEntity> BlockEntityBuilder<ENTITY, Registrations> blockEntity(String name, BlockEntityType.BlockEntitySupplier<ENTITY> factory) {
        return new BlockEntityBuilder<>(this, name, this, factory);
    }
    
    ///
    /// Starts a data component type, saved with the DFU codec of the given codec and synced with its stream codec.
    ///
    /// @param name  The path of the identifier.
    /// @param codec The codec of the values of the component.
    ///
    /// @return The builder of the data component type.
    ///
    public <VALUE> DataComponentBuilder<VALUE, Registrations> component(String name, NexusCodec<VALUE, ?> codec) {
        return new DataComponentBuilder<>(this, name, this, codec);
    }
    
    ///
    /// Starts a mob effect of the given factory.
    ///
    /// @param name    The path of the identifier.
    /// @param english The English name.
    /// @param factory The factory of the mob effect, called once inside the registry event.
    ///
    /// @return The builder of the mob effect.
    ///
    public <EFFECT extends MobEffect> EffectBuilder<EFFECT, Registrations> effect(String name, String english, Supplier<EFFECT> factory) {
        return new EffectBuilder<>(this, name, english, this, factory);
    }
    
    ///
    /// Starts an entity type of the factory in the given category.
    ///
    /// @param name     The path of the identifier.
    /// @param english  The English name.
    /// @param category The category of the entity type.
    /// @param factory  The factory of the entities, called each time an entity of the type is created.
    ///
    /// @return The builder of the entity type.
    ///
    public <ENTITY extends Entity> EntityBuilder<ENTITY, Registrations> entity(String name, String english, MobCategory category, EntityType.EntityFactory<ENTITY> factory) {
        return new EntityBuilder<>(this, name, english, this, category, factory);
    }
    
    ///
    /// Starts a fluid with a plain [FluidType], a [BaseFlowingFluid.Source], and a [BaseFlowingFluid.Flowing].
    ///
    /// [PlainFluidBuilder#type(Function)] and [PlainFluidBuilder#fluids(Function, Function)] replace them.
    ///
    /// @param name    The path of the identifier.
    /// @param english The English name.
    ///
    /// @return The builder of the fluid.
    ///
    public PlainFluidBuilder<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing, Registrations> fluid(String name, String english) {
        return new PlainFluidBuilder<>(this, name, english, this, FluidType::new, BaseFlowingFluid.Source::new, BaseFlowingFluid.Flowing::new);
    }
    
    ///
    /// Starts a plain item with `new Item.Properties()`.
    ///
    /// @param name    The path of the identifier.
    /// @param english The English name.
    ///
    /// @return The builder of the item.
    ///
    public ItemBuilder<Item, Registrations> item(String name, String english) {
        return this.item(name, english, Item::new);
    }
    
    ///
    /// Starts an item of the factory's class.
    ///
    /// The factory receives the properties with the id set.
    ///
    /// @param name    The path of the identifier.
    /// @param english The English name.
    /// @param factory The factory of the item, called once inside the registry event.
    ///
    /// @return The builder of the item.
    ///
    public <ITEM extends Item> ItemBuilder<ITEM, Registrations> item(String name, String english, Function<Item.Properties, ITEM> factory) {
        return new ItemBuilder<>(this, name, english, this, factory);
    }
    
    ///
    /// Starts a menu type of the factory, titled with the given English name.
    ///
    /// If the server doesn't write any extra data when it opens the menu, the factory will receive `null` instead of it.
    ///
    /// @param name    The path of the identifier.
    /// @param english The English title.
    /// @param factory The factory of the menu, called on the client with the extra data that the server wrote.
    ///
    /// @return The builder of the menu type.
    ///
    public <MENU extends AbstractContainerMenu> MenuBuilder<MENU, Registrations> menu(String name, String english, IContainerFactory<MENU> factory) {
        return new MenuBuilder<>(this, name, english, this, factory);
    }
    
    ///
    /// Starts a simple particle type.
    ///
    /// The type doesn't override the particle limiter.
    ///
    /// @param name The path of the identifier.
    ///
    /// @return The builder of the particle type.
    ///
    public ParticleBuilder<SimpleParticleType, SimpleParticleType, Registrations> particle(String name) {
        return this.particle(name, () -> new SimpleParticleType(false));
    }
    
    ///
    /// Starts a particle type of the given factory, with its own options.
    ///
    /// @param name    The path of the identifier.
    /// @param factory The factory of the particle type, called once inside the registry event.
    ///
    /// @return The builder of the particle type.
    ///
    public <OPTIONS extends ParticleOptions, TYPE extends ParticleType<OPTIONS>> ParticleBuilder<OPTIONS, TYPE, Registrations> particle(String name, Supplier<TYPE> factory) {
        return new ParticleBuilder<>(this, name, this, factory);
    }
    
    ///
    /// Starts a potion of the given effects, named after the English name of the effect:
    /// - `Potion of <english>` as a potion.
    /// - `Splash Potion of <english>` as a splash potion.
    /// - `Lingering Potion of <english>` as a lingering potion.
    /// - `Arrow of <english>` as a tipped arrow.
    ///
    /// @param name    The path of the identifier.
    /// @param english The English name of the effect, `<english>` in the names.
    /// @param effects The suppliers of the effects, each called once inside the registry event.
    ///
    /// @return The builder of the potion.
    ///
    @SafeVarargs
    public final PotionBuilder<Registrations> potion(String name, String english, Supplier<MobEffectInstance>... effects) {
        return new PotionBuilder<>(this, name, english, this, effects);
    }
    
    ///
    /// Starts a recipe type and, under the same name, a recipe serializer that reads and writes the recipes with the
    /// given codec.
    ///
    /// @param name  The path of the identifier of both.
    /// @param codec The codec of the recipes of the type.
    ///
    /// @return The builder of the recipe type.
    ///
    public <RECIPE extends Recipe<?>> RecipeTypeBuilder<RECIPE, Registrations> recipe(String name, StructCodec<RECIPE, ?> codec) {
        return new RecipeTypeBuilder<>(this, name, this, codec);
    }
    
    ///
    /// Starts a sound event with the default range and the given English subtitle.
    ///
    /// @param name    The path of the identifier.
    /// @param english The English subtitle.
    ///
    /// @return The builder of the sound event.
    ///
    public SoundBuilder<Registrations> sound(String name, String english) {
        return new SoundBuilder<>(this, name, english, this);
    }
    
    ///
    /// Starts a creative tab titled with the given English name.
    ///
    /// The first tab the mod registers is where its items show by default.
    ///
    /// @param name    The path of the identifier.
    /// @param english The English title.
    ///
    /// @return The builder of the tab.
    ///
    public TabBuilder<Registrations> tab(String name, String english) {
        return new TabBuilder<>(this, name, english, this);
    }
}
