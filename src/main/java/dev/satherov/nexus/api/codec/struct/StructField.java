package dev.satherov.nexus.api.codec.struct;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

///
/// A field of a struct, with the codec of its value and the getter of the value from the struct.
///
/// Created by one of the following:
/// - [NexusCodec#field(String, Function)]
/// - [NexusCodec#optionalField(String, Object, Function)]
/// - [NexusCodec#optionalField(String, Function)]
/// - [StructCodec#inline(Function)]
///
/// @param <Z> The type of the struct the field belongs to.
/// @param <T> The type of the value of the field.
/// @param <A> The access a format has to offer to be used with this field.
///
@ApiStatus.NonExtendable
public interface StructField<Z, T, A extends Access.Plain> { }
