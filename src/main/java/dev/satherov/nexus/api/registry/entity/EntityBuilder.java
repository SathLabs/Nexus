package dev.satherov.nexus.api.registry.entity;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.registry.item.ItemBuilder;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.internal.registry.EntryRegister;
import dev.satherov.nexus.internal.registry.client.ClientDispatch;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.levelgen.Heightmap;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

///
/// A builder of one entity type, with no spawn egg until [#egg()] opens one.
///
/// @param <ENTITY> The type of the entities of the type.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class EntityBuilder<ENTITY extends Entity, PARENT> extends EntryBuilder<EntityType<?>, EntityType<ENTITY>, EntityEntry<ENTITY>, PARENT, EntityBuilder<ENTITY, PARENT>> {
    
    ///
    /// The English name of the entity type.
    ///
    private final String english;
    
    ///
    /// The category of the entity type.
    ///
    private final MobCategory category;
    
    ///
    /// The factory of the entities of the type.
    ///
    private final EntityType.EntityFactory<ENTITY> factory;
    
    ///
    /// The edits of the vanilla type builder, in call order.
    ///
    private final List<UnaryOperator<EntityType.Builder<ENTITY>>> edits = new ArrayList<>();
    
    ///
    /// The size of the hitbox, applied to the vanilla type builder before the edits.
    ///
    private UnaryOperator<EntityType.Builder<ENTITY>> size = UnaryOperator.identity();
    
    ///
    /// The supplier of the attributes, or `null` if [#attributes(Supplier)] was not called.
    ///
    private @Nullable Supplier<AttributeSupplier.Builder> attributes;
    
    ///
    /// The registration of the spawn placement, called with the event and the created type, or `null` if
    /// [#spawnPlacement(SpawnPlacementType, Heightmap.Types, SpawnPlacements.SpawnPredicate)] was not called.
    ///
    private @Nullable BiConsumer<RegisterSpawnPlacementsEvent, EntityType<ENTITY>> spawnPlacement;
    
    ///
    /// The registration of the renderer, called on the client with the mod bus and the entry, or `null` if
    /// [#renderer(ClientOnly)] was not called.
    ///
    private @Nullable BiConsumer<IEventBus, EntityEntry<ENTITY>> renderer;
    
    ///
    /// The builder of the spawn egg, whose parent is the builder of entity type and egg, or `null` if [#egg(Function)]
    /// was not called.
    ///
    private @Nullable ItemBuilder<? extends SpawnEggItem, ? extends MobEntityBuilder<ENTITY, ?, PARENT>> egg;
    
    ///
    /// Starts the entity type `<modid>:<name>` of the given factory in the given category, under
    /// `entity.<modid>.<name>`.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// The type is built with its key, which gives it the translation key as its description id.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English name.
    /// @param parent        What [#end()] returns.
    /// @param category      The category of the entity type.
    /// @param factory       The factory of the entities, called each time an entity of the type is created.
    ///
    public EntityBuilder(Registrations registrations, String name, String english, PARENT parent, MobCategory category, EntityType.EntityFactory<ENTITY> factory) {
        super(registrations, Registries.ENTITY_TYPE, name, parent);
        this.english = english;
        this.category = category;
        this.factory = factory;
    }
    
    ///
    /// Sets the width and the height of the hitbox, in blocks.
    ///
    /// Applies before the edits of [#type(UnaryOperator)], whatever the call order.
    ///
    /// @param width  The width of the hitbox, in blocks.
    /// @param height The height of the hitbox, in blocks.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public EntityBuilder<ENTITY, PARENT> size(float width, float height) {
        this.checkUnregistered();
        this.size = builder -> builder.sized(width, height);
        return this;
    }
    
    ///
    /// Edits the vanilla type builder.
    ///
    /// Stacks with previous calls, in the order they were written.
    ///
    /// @param edit The edit, called with the vanilla type builder inside the registry event.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public EntityBuilder<ENTITY, PARENT> type(UnaryOperator<EntityType.Builder<ENTITY>> edit) {
        this.checkUnregistered();
        this.edits.add(edit);
        return this;
    }
    
    ///
    /// Registers the given attributes as the default attributes of the entities of the type.
    ///
    /// Should only ever be called for a type of living entities.
    ///
    /// @param attributes The supplier of the attributes, called once when the attributes are registered.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public EntityBuilder<ENTITY, PARENT> attributes(Supplier<AttributeSupplier.Builder> attributes) {
        this.checkUnregistered();
        this.attributes = attributes;
        return this;
    }
    
    ///
    /// Registers where and when the entities of the type spawn naturally.
    ///
    /// @param type      The placement type, which checks the blocks at a position.
    /// @param heightmap The heightmap that natural spawning takes the height of a position from.
    /// @param predicate The spawn rules that a position should pass for an entity of the type to spawn there.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public EntityBuilder<ENTITY, PARENT> spawnPlacement(SpawnPlacementType type, Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<ENTITY> predicate) {
        this.checkUnregistered();
        this.spawnPlacement = (event, entityType) -> event.register(entityType, type, heightmap, predicate, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        return this;
    }
    
    ///
    /// Registers the renderer of the given factory for the entities of the type on the client.
    ///
    /// @param renderer The factory of the renderer, called with the context of the renderer provider.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public <STATE extends EntityRenderState> EntityBuilder<ENTITY, PARENT> renderer(ClientOnly<Function<EntityRendererProvider.Context, EntityRenderer<ENTITY, STATE>>> renderer) {
        this.checkUnregistered();
        this.renderer = (bus, entry) -> ClientDispatch.entityRenderer(bus, entry, renderer);
        return this;
    }
    
    ///
    /// Opens a spawn egg of the factory's class as `<modid>:<name>_spawn_egg`, named `<english> Spawn Egg`.
    ///
    /// The builder of entity type and egg takes over everything recorded on this builder, and `end()` on the egg
    /// builder returns it.
    /// This builder will throw on every later call.
    ///
    /// The factory receives the properties with the id and the entity type set, even after
    /// [ItemBuilder#properties(Item.Properties)] replaced them.
    ///
    /// The properties of the egg start out requiring the features of the entity type.
    ///
    /// @param factory The factory of the spawn egg, called once inside the registry event.
    ///
    /// @return The builder of the spawn egg.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    public <EGG extends SpawnEggItem> ItemBuilder<EGG, MobEntityBuilder<ENTITY, EGG, PARENT>> egg(Function<Item.Properties, EGG> factory) {
        MobEntityBuilder<ENTITY, EGG, PARENT> builder = new MobEntityBuilder<>(this, this.end());
        DeferredHolder<EntityType<?>, EntityType<ENTITY>> type = DeferredHolder.create(this.getKey());
        ItemBuilder<EGG, MobEntityBuilder<ENTITY, EGG, PARENT>> egg = new ItemBuilder<>(
                this.getRegistrations(),
                this.getName() + "_spawn_egg",
                this.english + " Spawn Egg",
                builder,
                properties -> factory.apply(properties.component(DataComponents.ENTITY_DATA, TypedEntityData.of(type.value(), new CompoundTag())))
        );
        
        egg.properties(properties -> properties.spawnEgg(type.value()));
        this.egg = egg;
        return egg;
    }
    
    ///
    /// Opens a [SpawnEggItem] as `<modid>:<name>_spawn_egg`, named `<english> Spawn Egg`.
    ///
    /// The builder of entity type and egg takes over everything recorded on this builder, and `end()` on the egg
    /// builder returns it.
    /// This builder will throw on every later call.
    ///
    /// The properties of the egg start out requiring the features of the entity type.
    ///
    /// @return The builder of the spawn egg.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    public ItemBuilder<SpawnEggItem, MobEntityBuilder<ENTITY, SpawnEggItem, PARENT>> egg() {
        return this.egg(SpawnEggItem::new);
    }
    
    ///
    /// Creates the type with the vanilla type builder, sized and then edited, and builds it with the key.
    ///
    /// @return The created type.
    ///
    @Override
    protected EntityType<ENTITY> create() {
        EntityType.Builder<ENTITY> builder = this.size.apply(EntityType.Builder.of(this.factory, this.category));
        for (UnaryOperator<EntityType.Builder<ENTITY>> edit : this.edits) {
            builder = edit.apply(builder);
        }
        
        return builder.build(this.getKey());
    }
    
    ///
    /// Creates the entry for the given key under `entity.<modid>.<name>`, registering the spawn egg first if
    /// [#egg(Function)] opened one.
    /// Adds a listener for the attributes and the spawn placement to the event bus of the mod, and the renderer as a
    /// client hook, if they were given.
    ///
    /// @param key The key of the entity type.
    ///
    /// @return The created entry.
    ///
    @Override
    protected EntityEntry<ENTITY> createEntry(ResourceKey<EntityType<?>> key) {
        Translation translation = this.define("entity", this.english);
        ItemBuilder<? extends SpawnEggItem, ? extends MobEntityBuilder<ENTITY, ?, PARENT>> egg = this.egg;
        EntityEntry<ENTITY> entry = egg == null ?
                new EntityEntry<>(key, this, translation) :
                new MobEntityEntry<>(key, egg.end(), translation, EntryBuilder.registerSubObject(egg));
        
        IEventBus modBus = this.getRegistrations().getMod().getEventBus();
        Supplier<AttributeSupplier.Builder> attributes = this.attributes;
        if (attributes != null) {
            //noinspection unchecked Attributes are only ever given to a type of living entities.
            modBus.addListener(EntityAttributeCreationEvent.class, event -> event.put((EntityType<? extends LivingEntity>) entry.value(), attributes.get().build()));
        }
        
        BiConsumer<RegisterSpawnPlacementsEvent, EntityType<ENTITY>> spawnPlacement = this.spawnPlacement;
        if (spawnPlacement != null) {
            modBus.addListener(RegisterSpawnPlacementsEvent.class, event -> spawnPlacement.accept(event, entry.value()));
        }
        
        BiConsumer<IEventBus, EntityEntry<ENTITY>> renderer = this.renderer;
        if (renderer != null) {
            EntryRegister<EntityType<?>> register = (EntryRegister<EntityType<?>>) this.getRegistrations().getRegister(Registries.ENTITY_TYPE);
            register.addClientHook(bus -> renderer.accept(bus, entry));
        }
        
        return entry;
    }
}
