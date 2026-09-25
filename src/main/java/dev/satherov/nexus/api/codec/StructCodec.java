package dev.satherov.nexus.api.codec;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;
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
    
    ///
    /// Creates the codec of this struct that also writes and reads a short form of it, with the given codec.
    ///
    /// In JSON and NBT, it writes the short form if `toShort` returns one and this struct otherwise.
    /// It reads the short form, and this struct if the input can't be read as the short form.
    /// On the network, it writes a boolean that is `true` for the short form and then the short form or this struct, the same as [ByteBufCodecs#either(StreamCodec, StreamCodec)].
    ///
    /// In JSON and NBT, if the input can be read neither as the short form nor as this struct, the failure will hold the errors of both.
    ///
    /// @param shortForm The codec of the short form.
    /// @param fromShort The mapping from the short form to a struct.
    /// @param toShort   The mapping from a struct to its short form, which is empty if the struct has none.
    ///
    /// @return The codec of this struct or its short form.
    ///
    <S> NexusCodec<T, A> orShort(NexusCodec<S, ? super A> shortForm, Function<? super S, ? extends T> fromShort, Function<? super T, Optional<S>> toShort);
}
