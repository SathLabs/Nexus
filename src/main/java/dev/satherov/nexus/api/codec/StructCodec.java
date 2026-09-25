package dev.satherov.nexus.api.codec;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

///
/// A codec of a struct with a fixed list of fields, which can also be inlined into another struct.
///
/// @param <T> The type of the struct.
/// @param <A> The access a format has to offer to be used with this codec.
///
/// @see NexusCodec#struct(String, StructField, Function)
///
@ApiStatus.NonExtendable
public interface StructCodec<T, A extends Access.Plain> extends NexusCodec<T, A> {
    
    ///
    /// Creates a field that merges the fields of this struct into the struct that holds it.
    /// Similar to passing DFU's [MapCodec] to a record codec builder with [MapCodec#forGetter(Function)].
    ///
    /// In JSON and NBT, the keys of this struct will be in the object of the owner.
    /// On the network, the fields of this struct are written in their order, at the position of this field.
    ///
    /// Defining the owner fails if another of its fields has one of the keys of this struct.
    ///
    /// @param getter The getter of the value of this struct from the owner.
    ///
    /// @return The field of the owner.
    ///
    <Z> StructField<Z, T, A> inline(Function<Z, T> getter);
}
