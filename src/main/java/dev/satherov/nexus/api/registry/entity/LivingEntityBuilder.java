package dev.satherov.nexus.api.registry.entity;

import dev.satherov.nexus.api.registry.Registrations;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

///
/// A builder of one entity type of living entities, with their default attributes.
///
/// @param <ENTITY> The type of the entities of the type.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class LivingEntityBuilder<ENTITY extends LivingEntity, PARENT>
        // @formatter:off
        extends EntityBuilder
        <
            ENTITY,
            PARENT,
            LivingEntityBuilder<ENTITY, PARENT>
        >
        // @formatter:on
{
    
    ///
    /// The supplier of the attributes, or `null` if [#attributes(Supplier)] was not called.
    ///
    private @Nullable Supplier<AttributeSupplier.Builder> attributes;
    
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
    public LivingEntityBuilder(Registrations registrations, String name, String english, PARENT parent, MobCategory category, EntityType.EntityFactory<ENTITY> factory) {
        super(registrations, name, english, parent, category, factory);
    }
    
    ///
    /// Registers the given attributes as the default attributes of the entities of the type.
    ///
    /// @param attributes The supplier of the attributes, called once when the attributes are registered.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public LivingEntityBuilder<ENTITY, PARENT> attributes(Supplier<AttributeSupplier.Builder> attributes) {
        this.checkUnregistered();
        this.attributes = attributes;
        return this;
    }
    
    ///
    /// Creates the entry as [EntityBuilder#createEntry(ResourceKey)] does, and adds a listener for the attributes to
    /// the event bus of the mod if they were given.
    ///
    /// @param key The key of the entity type.
    ///
    /// @return The created entry.
    ///
    @Override
    protected EntityEntry<ENTITY> createEntry(ResourceKey<EntityType<?>> key) {
        EntityEntry<ENTITY> entry = super.createEntry(key);
        Supplier<AttributeSupplier.Builder> attributes = this.attributes;
        if (attributes != null) {
            this.getRegistrations().getMod().getEventBus().addListener(EntityAttributeCreationEvent.class, event -> event.put(entry.value(), attributes.get().build()));
        }
        
        return entry;
    }
}
