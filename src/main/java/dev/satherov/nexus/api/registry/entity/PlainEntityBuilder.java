package dev.satherov.nexus.api.registry.entity;

import dev.satherov.nexus.api.registry.Registrations;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

///
/// A builder of one entity type of any entities.
///
/// @param <ENTITY> The type of the entities of the type.
/// @param <PARENT> The type of what [#end()] returns.
///
/// @see LivingEntityBuilder
///
public final class PlainEntityBuilder<ENTITY extends Entity, PARENT>
        // @formatter:off
        extends EntityBuilder
        <
            ENTITY,
            PARENT,
            PlainEntityBuilder<ENTITY, PARENT>
        >
        // @formatter:on
{
    
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
    public PlainEntityBuilder(Registrations registrations, String name, String english, PARENT parent, MobCategory category, EntityType.EntityFactory<ENTITY> factory) {
        super(registrations, name, english, parent, category, factory);
    }
}
