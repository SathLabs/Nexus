///
/// The builder and the entry of block entity types.
/// - [BlockEntityBuilder]: The builder, started through [Registrations#blockEntity(String, BlockEntityType.BlockEntitySupplier)].
/// - [BlockEntityEntry]: The entry that the builder registers.
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.registry.block.entity;

import dev.satherov.nexus.api.registry.Registrations;

import net.minecraft.world.level.block.entity.BlockEntityType;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;
