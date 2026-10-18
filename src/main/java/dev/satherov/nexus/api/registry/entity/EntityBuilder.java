package dev.satherov.nexus.api.registry.entity;

import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.api.registry.item.ItemBuilder;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.internal.registry.client.ClientDispatch;

import net.neoforged.bus.api.IEventBus;
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
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
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
import java.util.function.UnaryOperator;

///
/// A builder of one entity type.
///
/// A mob entity with a spawn egg can be registered through [EntityBuilder#egg(Function, CompoundTag)] or one of its overloads.
///
/// @param <ENTITY> The type of the entities of the type.
/// @param <PARENT> The type of what [#end()] returns.
/// @param <SELF>   The type of this builder.
///
/// @see LivingEntityBuilder
/// @see PlainEntityBuilder
///
public abstract class EntityBuilder
        // @formatter:off
        <
            ENTITY extends Entity,
            PARENT,
            SELF extends EntityBuilder<ENTITY, PARENT, SELF>
        >
        extends EntryBuilder
        <
            EntityType<?>,
            EntityType<ENTITY>,
            EntityEntry<ENTITY>,
            PARENT,
            SELF
        >
        // @formatter:on
{
    
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
    /// The registration of the spawn placement, called with the event and the created type,
    /// or `null` if [#spawnPlacement(SpawnPlacementType, Heightmap.Types, SpawnPlacements.SpawnPredicate)] was not called.
    ///
    private @Nullable BiConsumer<RegisterSpawnPlacementsEvent, EntityType<ENTITY>> spawnPlacement;
    
    ///
    /// The registration of the renderer, called on the client with the mod bus and the entry,
    /// or `null` if [#renderer(ClientOnly)] was not called.
    ///
    private @Nullable BiConsumer<IEventBus, EntityEntry<ENTITY>> renderer;
    
    ///
    /// The builder of the spawn egg, whose parent is the builder of entity type and egg,
    /// or `null` if [#egg(Function)] was not called.
    ///
    private @Nullable ItemBuilder<? extends SpawnEggItem, ? extends MobEntityBuilder<ENTITY, ?, PARENT>> egg;
    
    ///
    /// Starts a new entity type `<modid>:<name>`, with the class of the provided factory result, under
    /// `entity.<modid>.<name>`.
    /// - `<modid>` is the id of the mod of the given registrations.
    /// - `<name>` is the given name.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param english       The English name.
    /// @param parent        What [#end()] returns.
    /// @param category      The category of the entity type.
    /// @param factory       The factory of the entities, called each time an entity of the type is created.
    ///
    protected EntityBuilder(Registrations registrations, String name, String english, PARENT parent, MobCategory category, EntityType.EntityFactory<ENTITY> factory) {
        super(registrations, Registries.ENTITY_TYPE, name, parent);
        this.english = english;
        this.category = category;
        this.factory = factory;
    }
    
    ///
    /// Sets the width and the height of the hitbox, in blocks.
    ///
    /// Applies before the edits of [#type(UnaryOperator)].
    ///
    /// @param width  The width of the hitbox, in blocks.
    /// @param height The height of the hitbox, in blocks.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public SELF size(float width, float height) {
        this.checkUnregistered();
        this.size = builder -> builder.sized(width, height);
        return this.self();
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
    public SELF type(UnaryOperator<EntityType.Builder<ENTITY>> edit) {
        this.checkUnregistered();
        this.edits.add(edit);
        return this.self();
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
    public SELF spawnPlacement(SpawnPlacementType type, Heightmap.Types heightmap, SpawnPlacements.SpawnPredicate<ENTITY> predicate) {
        this.checkUnregistered();
        this.spawnPlacement = (event, entityType) -> event.register(entityType, type, heightmap, predicate, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        return this.self();
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
    public <STATE extends EntityRenderState> SELF renderer(ClientOnly<Function<EntityRendererProvider.Context, EntityRenderer<ENTITY, STATE>>> renderer) {
        this.checkUnregistered();
        this.renderer = (bus, entry) -> ClientDispatch.entityRenderer(bus, entry, renderer);
        return this.self();
    }
    
    ///
    /// Starts a new spawn egg item builder, with the class of the provided factory result.
    ///
    /// The provided compound tag data will be merged into the entity data when the entity is spawned,
    /// as to provide default settings that differ from the entity's default data.
    ///
    /// The identifier will be `<modid>:<name>_spawn_egg`.
    /// The translation will be `<english> Spawn Egg` but can be overwritten with [ItemBuilder#named(String)].
    ///
    /// The spawn egg requires the features of the type, until an edit of its properties sets others.
    ///
    /// This builder will throw on every later call.
    ///
    /// @param factory     The factory of the spawn egg, called once inside the registry event.
    /// @param defaultData The default data merged into the entity data when the entity is spawned.
    ///
    /// @return The builder of the spawn egg.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    public <EGG extends SpawnEggItem> ItemBuilder<EGG, MobEntityBuilder<ENTITY, EGG, PARENT>> egg(Function<Item.Properties, EGG> factory, CompoundTag defaultData) {
        MobEntityBuilder<ENTITY, EGG, PARENT> builder = new MobEntityBuilder<>(this, this.end());
        DeferredHolder<EntityType<?>, EntityType<ENTITY>> type = DeferredHolder.create(this.getKey());
        ItemBuilder<EGG, MobEntityBuilder<ENTITY, EGG, PARENT>> egg = new ItemBuilder<>(
                this.getRegistrations(),
                this.getName() + "_spawn_egg",
                this.english + " Spawn Egg",
                builder,
                properties -> factory.apply(properties.component(DataComponents.ENTITY_DATA, TypedEntityData.of(type.value(), defaultData)))
        );
        
        egg.properties(properties -> properties.requiredFeatures(type.value().requiredFeatures()));
        this.egg = egg;
        return egg;
    }
    
    ///
    /// Starts a new spawn egg item builder, with the class of the provided factory result.
    ///
    /// The identifier will be `<modid>:<name>_spawn_egg`.
    /// The translation will be `<english> Spawn Egg` but can be overwritten with [ItemBuilder#named(String)].
    ///
    /// This builder will throw on every later call.
    ///
    /// @param factory The factory of the spawn egg, called once inside the registry event.
    ///
    /// @return The builder of the spawn egg.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    public <EGG extends SpawnEggItem> ItemBuilder<EGG, MobEntityBuilder<ENTITY, EGG, PARENT>> egg(Function<Item.Properties, EGG> factory) {
        return this.egg(factory, new CompoundTag());
    }
    
    ///
    /// Starts a new spawn egg item builder, with [SpawnEggItem].
    ///
    /// The identifier will be `<modid>:<name>_spawn_egg`.
    /// The translation will be `<english> Spawn Egg` but can be overwritten with [ItemBuilder#named(String)].
    ///
    /// This builder will throw on every later call.
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
    /// Creates the entry for the given key under `entity.<modid>.<name>`, registering the spawn egg first if [#egg(Function)] set one.
    /// Adds a listener for the spawn placement to the event bus of the mod, and the renderer as a client hook, if they were given.
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
        
        BiConsumer<RegisterSpawnPlacementsEvent, EntityType<ENTITY>> spawnPlacement = this.spawnPlacement;
        if (spawnPlacement != null) {
            this.getRegistrations()
                    .getMod()
                    .getEventBus()
                    .addListener(RegisterSpawnPlacementsEvent.class, event -> spawnPlacement.accept(event, entry.value()));
        }
        
        BiConsumer<IEventBus, EntityEntry<ENTITY>> renderer = this.renderer;
        if (renderer != null) {
            this.addClientHook(bus -> renderer.accept(bus, entry));
        }
        
        return entry;
    }
}
