///
/// The builders and the entries of entity types.
///
/// An entity type with a spawn egg is built by a [MobEntityBuilder] and held by a [MobEntityEntry].
/// - [PlainEntityBuilder]: The builder, started through [Registrations#entity(String, String, MobCategory, EntityType.EntityFactory)].
/// - [LivingEntityBuilder]: The builder of living entities, started through [Registrations#livingEntity(String, String, MobCategory, EntityType.EntityFactory)].
/// - [EntityEntry]: The entry that both builders register.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.registry.entity;

import dev.satherov.nexus.api.registry.Registrations;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
