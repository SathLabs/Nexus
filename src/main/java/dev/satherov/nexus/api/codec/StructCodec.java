package dev.satherov.nexus.api.codec;

import dev.satherov.nexus.internal.codec.VanillaAdapters;

import net.minecraft.nbt.NbtOps;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
    /// Creates a recipe serializer with the map codec and the stream codec of the given struct.
    ///
    /// @param codec The codec of the recipe.
    ///
    /// @return The recipe serializer.
    ///
    /// @see #asMapCodec()
    /// @see #asStream()
    ///
    static <R extends Recipe<?>> RecipeSerializer<R> recipeSerializer(StructCodec<R, ? super Access.Registries> codec) {
        return VanillaAdapters.recipeSerializer(codec);
    }
    
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
    
    ///
    /// Creates the map codec of this struct, with the fields of this struct as its entries.
    /// Similar to the [MapCodec] of DFU's [RecordCodecBuilder#mapCodec(Function)].
    ///
    /// Meant to be used for vanilla code that takes a [MapCodec], such as a dispatch of DFU.
    ///
    /// Over the ops of json or NBT values, such as [JsonOps], [NbtOps], and [RegistryOps] over them, it writes and reads the values of the ops directly.
    /// Over any other ops, it converts the values to and from json, with json null as the empty value of the ops.
    ///
    /// If this struct needs registries, it will take them from the [RegistryOps], and fail over any other ops.
    /// Fails over ops that compress maps, such as [JsonOps#COMPRESSED].
    /// A failure is an error with the message of the [CodecException], without a partial result.
    ///
    /// @return The map codec, whose keys are the keys of this struct.
    ///
    MapCodec<T> asMapCodec();
}
