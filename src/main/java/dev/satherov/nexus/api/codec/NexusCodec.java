package dev.satherov.nexus.api.codec;

import dev.satherov.nexus.internal.codec.CollectionCodecs;
import dev.satherov.nexus.internal.codec.Combinators;
import dev.satherov.nexus.internal.codec.HolderCodecs;
import dev.satherov.nexus.internal.codec.Scalars;
import dev.satherov.nexus.internal.codec.Structs;
import dev.satherov.nexus.internal.codec.VanillaAdapters;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Function10;
import com.mojang.datafixers.util.Function11;
import com.mojang.datafixers.util.Function12;
import com.mojang.datafixers.util.Function13;
import com.mojang.datafixers.util.Function14;
import com.mojang.datafixers.util.Function15;
import com.mojang.datafixers.util.Function16;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import com.mojang.datafixers.util.Function7;
import com.mojang.datafixers.util.Function8;
import com.mojang.datafixers.util.Function9;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;

///
/// A codec that encodes values of one type into JSON, NBT, and network buffers and decodes them back.
///
/// Similar to DFU's [Codec].
/// It writes the same JSON and NBT as the equivalent DFU codec, and the same bytes as the equivalent vanilla stream codec.
///
/// @param <T> The type of value this codec encodes and decodes.
/// @param <A> The access a format has to offer to be used with this codec.
///
@ApiStatus.NonExtendable
public interface NexusCodec<T, A extends Access.Plain> {
    
    ///
    /// The codec of a `boolean`, in the same form as vanilla.
    ///
    /// In JSON, it only reads `true` and `false`.
    /// In NBT, it reads any number, which is `true` if its lowest byte is not `0`.
    ///
    NexusCodec<Boolean, Access.Plain> BOOL = Scalars.BOOL;
    
    ///
    /// The codec of a `byte`, in the same form as vanilla.
    ///
    /// Fails if the number is not a whole number or doesn't fit into a `byte`.
    ///
    NexusCodec<Byte, Access.Plain> BYTE = Scalars.BYTE;
    
    ///
    /// The codec of a `short`, in the same form as vanilla.
    ///
    /// Fails if the number is not a whole number or doesn't fit into a `short`.
    ///
    NexusCodec<Short, Access.Plain> SHORT = Scalars.SHORT;
    
    ///
    /// The codec of an `int`, in the same form as vanilla.
    ///
    /// Fails if the number is not a whole number or doesn't fit into an `int`.
    ///
    NexusCodec<Integer, Access.Plain> INT = Scalars.INT;
    
    ///
    /// The codec of an `int`, in the same form as vanilla and as a VarInt on the network.
    ///
    /// Fails if the number is not a whole number or doesn't fit into an `int`.
    ///
    NexusCodec<Integer, Access.Plain> VAR_INT = Scalars.VAR_INT;
    
    ///
    /// The codec of a `long`, in the same form as vanilla.
    ///
    /// Fails if the number is not a whole number or doesn't fit into a `long`.
    ///
    NexusCodec<Long, Access.Plain> LONG = Scalars.LONG;
    
    ///
    /// The codec of a `long`, in the same form as vanilla and as a VarLong on the network.
    ///
    /// Fails if the number is not a whole number or doesn't fit into a `long`.
    ///
    NexusCodec<Long, Access.Plain> VAR_LONG = Scalars.VAR_LONG;
    
    ///
    /// The codec of a `float`, in the same form as vanilla.
    ///
    NexusCodec<Float, Access.Plain> FLOAT = Scalars.FLOAT;
    
    ///
    /// The codec of a `double`, in the same form as vanilla.
    ///
    NexusCodec<Double, Access.Plain> DOUBLE = Scalars.DOUBLE;
    
    ///
    /// The codec of a string of at most vanilla's 32767 characters, in the same form as vanilla.
    ///
    /// Fails if the string is longer than that, in every format.
    ///
    NexusCodec<String, Access.Plain> STRING = Scalars.STRING;
    
    ///
    /// The codec of an [Identifier], as its string form.
    ///
    /// Fails if the string is not a valid identifier.
    ///
    NexusCodec<Identifier, Access.Plain> IDENTIFIER = Scalars.IDENTIFIER;
    
    ///
    /// The codec of a [UUID], in the same form as vanilla.
    ///
    /// In JSON and NBT, it writes the four `int` values of the UUID as an int array and reads the string form of a UUID too.
    /// On the network, it writes the UUID as two `long` values.
    ///
    NexusCodec<UUID, Access.Plain> UUID = Scalars.UNIQUE_ID;
    
    ///
    /// Creates a codec of a string of at most the given number of characters, in the same form as vanilla.
    ///
    /// Fails if the string is longer than that, and the failure quotes the limit and the length it found.
    ///
    /// @param limit The maximum number of characters of the string.
    ///
    /// @return The codec of the string.
    ///
    /// @throws IllegalArgumentException If the limit is negative.
    ///
    static NexusCodec<String, Access.Plain> string(@Range(from = 0, to = Integer.MAX_VALUE) int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Could not create a string codec with the negative limit '" + limit + "'");
        }
        
        return Scalars.string(limit);
    }
    
    ///
    /// Creates a codec of the constants of the given enum.
    ///
    /// In JSON and NBT, a constant is written as:
    /// - Its [StringRepresentable#getSerializedName()] if the enum implements [StringRepresentable].
    /// - Its name in lower case otherwise.
    ///
    /// On the network, a constant is written as its ordinal, as a VarInt.
    ///
    /// If several constants share a name, the first of them will be decoded from it.
    ///
    /// Fails if a name or an ordinal belongs to none of the constants.
    /// The failure for an unknown name lists the names of all constants.
    ///
    /// @param type The class of the enum.
    ///
    /// @return The codec of the enum's constants.
    ///
    static <E extends Enum<E>> NexusCodec<E, Access.Plain> enumOf(Class<E> type) {
        return Scalars.enumOf(type);
    }
    
    ///
    /// Creates a codec that writes no data and always decodes to the given value.
    ///
    /// In JSON and NBT, it writes an empty object and reads any object, the same as DFU.
    /// On the network, it writes nothing.
    ///
    /// @param value The value to decode to.
    ///
    /// @return The codec of the value.
    ///
    static <T> NexusCodec<T, Access.Plain> unit(T value) {
        return Scalars.unit(value);
    }
    
    ///
    /// Creates the codec of a map of at most the given number of entries, with the given key and the values of the given codec.
    /// Similar to DFU's [Codec#unboundedMap(Codec, Codec)].
    ///
    /// In JSON and NBT, it writes an object with every value under the string form of its key.
    /// On the network, it writes the number of entries as a VarInt and then every key and its value, the same as [ByteBufCodecs#map(IntFunction, StreamCodec, StreamCodec, int)].
    ///
    /// Fails if the map has more than `limit` entries, before any entry is decoded, or if two decoded keys are equal.
    /// In JSON and NBT, a failure holds the errors of every entry that failed, each at its key.
    ///
    /// @param key   The key of the entries.
    /// @param value The codec of the values.
    /// @param limit The maximum number of entries of the map.
    ///
    /// @return The codec of the map, which decodes into a map that keeps the order of its entries in JSON and on the network.
    ///
    /// @throws IllegalArgumentException If the limit is negative.
    ///
    static <K, V, A extends Access.Plain> NexusCodec<Map<K, V>, A> mapOf(
            MapKey<K, ? super A> key,
            NexusCodec<V, ? super A> value,
            @Range(from = 0, to = Integer.MAX_VALUE) int limit
    ) {
        return CollectionCodecs.map(key, value, limit);
    }
    
    ///
    /// Creates the codec of a map of at most 32767 entries, with the given key and the values of the given codec.
    /// Similar to DFU's [Codec#unboundedMap(Codec, Codec)].
    ///
    /// In JSON and NBT, it writes an object with every value under the string form of its key.
    /// On the network, it writes the number of entries as a VarInt and then every key and its value, the same as [ByteBufCodecs#map(IntFunction, StreamCodec, StreamCodec, int)].
    ///
    /// Fails if the map has more than 32767 entries, before any entry is decoded, or if two decoded keys are equal.
    /// In JSON and NBT, a failure holds the errors of every entry that failed, each at its key.
    ///
    /// @param key   The key of the entries.
    /// @param value The codec of the values.
    ///
    /// @return The codec of the map, which decodes into a map that keeps the order of its entries in JSON and on the network.
    ///
    static <K, V, A extends Access.Plain> NexusCodec<Map<K, V>, A> mapOf(MapKey<K, ? super A> key, NexusCodec<V, ? super A> value) {
        return CollectionCodecs.map(key, value, CollectionCodecs.LIMIT);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    static <Z, A extends Access.Plain, B> StructCodec<Z, A> struct(String name, StructField<Z, B, ? super A> b, Function<B, Z> constructor) {
        return new Structs.Struct<>(name, 1, Structs.adapt(constructor), b);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            BiFunction<B, C, Z> constructor
    ) {
        return new Structs.Struct<>(name, 2, Structs.adapt(constructor), b, c);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            Function3<B, C, D, Z> constructor
    ) {
        return new Structs.Struct<>(name, 3, Structs.adapt(constructor), b, c, d);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            Function4<B, C, D, E, Z> constructor
    ) {
        return new Structs.Struct<>(name, 4, Structs.adapt(constructor), b, c, d, e);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            Function5<B, C, D, E, F, Z> constructor
    ) {
        return new Structs.Struct<>(name, 5, Structs.adapt(constructor), b, c, d, e, f);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param g           The sixth field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F, G> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            StructField<Z, G, ? super A> g,
            Function6<B, C, D, E, F, G, Z> constructor
    ) {
        return new Structs.Struct<>(name, 6, Structs.adapt(constructor), b, c, d, e, f, g);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param g           The sixth field.
    /// @param h           The seventh field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F, G, H> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            StructField<Z, G, ? super A> g,
            StructField<Z, H, ? super A> h,
            Function7<B, C, D, E, F, G, H, Z> constructor
    ) {
        return new Structs.Struct<>(name, 7, Structs.adapt(constructor), b, c, d, e, f, g, h);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param g           The sixth field.
    /// @param h           The seventh field.
    /// @param i           The eighth field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F, G, H, I> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            StructField<Z, G, ? super A> g,
            StructField<Z, H, ? super A> h,
            StructField<Z, I, ? super A> i,
            Function8<B, C, D, E, F, G, H, I, Z> constructor
    ) {
        return new Structs.Struct<>(name, 8, Structs.adapt(constructor), b, c, d, e, f, g, h, i);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param g           The sixth field.
    /// @param h           The seventh field.
    /// @param i           The eighth field.
    /// @param j           The ninth field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F, G, H, I, J> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            StructField<Z, G, ? super A> g,
            StructField<Z, H, ? super A> h,
            StructField<Z, I, ? super A> i,
            StructField<Z, J, ? super A> j,
            Function9<B, C, D, E, F, G, H, I, J, Z> constructor
    ) {
        return new Structs.Struct<>(name, 9, Structs.adapt(constructor), b, c, d, e, f, g, h, i, j);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param g           The sixth field.
    /// @param h           The seventh field.
    /// @param i           The eighth field.
    /// @param j           The ninth field.
    /// @param k           The tenth field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F, G, H, I, J, K> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            StructField<Z, G, ? super A> g,
            StructField<Z, H, ? super A> h,
            StructField<Z, I, ? super A> i,
            StructField<Z, J, ? super A> j,
            StructField<Z, K, ? super A> k,
            Function10<B, C, D, E, F, G, H, I, J, K, Z> constructor
    ) {
        return new Structs.Struct<>(name, 10, Structs.adapt(constructor), b, c, d, e, f, g, h, i, j, k);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param g           The sixth field.
    /// @param h           The seventh field.
    /// @param i           The eighth field.
    /// @param j           The ninth field.
    /// @param k           The tenth field.
    /// @param l           The eleventh field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F, G, H, I, J, K, L> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            StructField<Z, G, ? super A> g,
            StructField<Z, H, ? super A> h,
            StructField<Z, I, ? super A> i,
            StructField<Z, J, ? super A> j,
            StructField<Z, K, ? super A> k,
            StructField<Z, L, ? super A> l,
            Function11<B, C, D, E, F, G, H, I, J, K, L, Z> constructor
    ) {
        return new Structs.Struct<>(name, 11, Structs.adapt(constructor), b, c, d, e, f, g, h, i, j, k, l);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param g           The sixth field.
    /// @param h           The seventh field.
    /// @param i           The eighth field.
    /// @param j           The ninth field.
    /// @param k           The tenth field.
    /// @param l           The eleventh field.
    /// @param m           The twelfth field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F, G, H, I, J, K, L, M> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            StructField<Z, G, ? super A> g,
            StructField<Z, H, ? super A> h,
            StructField<Z, I, ? super A> i,
            StructField<Z, J, ? super A> j,
            StructField<Z, K, ? super A> k,
            StructField<Z, L, ? super A> l,
            StructField<Z, M, ? super A> m,
            Function12<B, C, D, E, F, G, H, I, J, K, L, M, Z> constructor
    ) {
        return new Structs.Struct<>(name, 12, Structs.adapt(constructor), b, c, d, e, f, g, h, i, j, k, l, m);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param g           The sixth field.
    /// @param h           The seventh field.
    /// @param i           The eighth field.
    /// @param j           The ninth field.
    /// @param k           The tenth field.
    /// @param l           The eleventh field.
    /// @param m           The twelfth field.
    /// @param n           The thirteenth field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F, G, H, I, J, K, L, M, N> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            StructField<Z, G, ? super A> g,
            StructField<Z, H, ? super A> h,
            StructField<Z, I, ? super A> i,
            StructField<Z, J, ? super A> j,
            StructField<Z, K, ? super A> k,
            StructField<Z, L, ? super A> l,
            StructField<Z, M, ? super A> m,
            StructField<Z, N, ? super A> n,
            Function13<B, C, D, E, F, G, H, I, J, K, L, M, N, Z> constructor
    ) {
        return new Structs.Struct<>(name, 13, Structs.adapt(constructor), b, c, d, e, f, g, h, i, j, k, l, m, n);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param g           The sixth field.
    /// @param h           The seventh field.
    /// @param i           The eighth field.
    /// @param j           The ninth field.
    /// @param k           The tenth field.
    /// @param l           The eleventh field.
    /// @param m           The twelfth field.
    /// @param n           The thirteenth field.
    /// @param o           The fourteenth field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F, G, H, I, J, K, L, M, N, O> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            StructField<Z, G, ? super A> g,
            StructField<Z, H, ? super A> h,
            StructField<Z, I, ? super A> i,
            StructField<Z, J, ? super A> j,
            StructField<Z, K, ? super A> k,
            StructField<Z, L, ? super A> l,
            StructField<Z, M, ? super A> m,
            StructField<Z, N, ? super A> n,
            StructField<Z, O, ? super A> o,
            Function14<B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z> constructor
    ) {
        return new Structs.Struct<>(name, 14, Structs.adapt(constructor), b, c, d, e, f, g, h, i, j, k, l, m, n, o);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param g           The sixth field.
    /// @param h           The seventh field.
    /// @param i           The eighth field.
    /// @param j           The ninth field.
    /// @param k           The tenth field.
    /// @param l           The eleventh field.
    /// @param m           The twelfth field.
    /// @param n           The thirteenth field.
    /// @param o           The fourteenth field.
    /// @param p           The fifteenth field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            StructField<Z, G, ? super A> g,
            StructField<Z, H, ? super A> h,
            StructField<Z, I, ? super A> i,
            StructField<Z, J, ? super A> j,
            StructField<Z, K, ? super A> k,
            StructField<Z, L, ? super A> l,
            StructField<Z, M, ? super A> m,
            StructField<Z, N, ? super A> n,
            StructField<Z, O, ? super A> o,
            StructField<Z, P, ? super A> p,
            Function15<B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z> constructor
    ) {
        return new Structs.Struct<>(name, 15, Structs.adapt(constructor), b, c, d, e, f, g, h, i, j, k, l, m, n, o, p);
    }
    
    ///
    /// Creates the codec of a struct with the given fields, built with the given constructor.
    /// Similar to DFU's [RecordCodecBuilder].
    ///
    /// In JSON and NBT, it writes an object with every field under its key.
    /// On the network, it writes the fields in the given order and nothing else.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name        The name of the struct, used in the failure message.
    /// @param b           The first field.
    /// @param c           The second field.
    /// @param d           The third field.
    /// @param e           The fourth field.
    /// @param f           The fifth field.
    /// @param g           The sixth field.
    /// @param h           The seventh field.
    /// @param i           The eighth field.
    /// @param j           The ninth field.
    /// @param k           The tenth field.
    /// @param l           The eleventh field.
    /// @param m           The twelfth field.
    /// @param n           The thirteenth field.
    /// @param o           The fourteenth field.
    /// @param p           The fifteenth field.
    /// @param q           The sixteenth field.
    /// @param constructor The constructor of the struct, called with the value of every field, in order.
    ///
    /// @return The codec of the struct.
    ///
    /// @throws IllegalArgumentException If two fields have the same key, counting the keys of the struct of an inline field.
    ///
    static <Z, A extends Access.Plain, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> StructCodec<Z, A> struct(
            String name,
            StructField<Z, B, ? super A> b,
            StructField<Z, C, ? super A> c,
            StructField<Z, D, ? super A> d,
            StructField<Z, E, ? super A> e,
            StructField<Z, F, ? super A> f,
            StructField<Z, G, ? super A> g,
            StructField<Z, H, ? super A> h,
            StructField<Z, I, ? super A> i,
            StructField<Z, J, ? super A> j,
            StructField<Z, K, ? super A> k,
            StructField<Z, L, ? super A> l,
            StructField<Z, M, ? super A> m,
            StructField<Z, N, ? super A> n,
            StructField<Z, O, ? super A> o,
            StructField<Z, P, ? super A> p,
            StructField<Z, Q, ? super A> q,
            Function16<B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z> constructor
    ) {
        return new Structs.Struct<>(name, 16, Structs.adapt(constructor), b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q);
    }
    
    ///
    /// Creates the codec of a value that is one of two types, with the codec of each.
    /// Similar to DFU's [Codec#either(Codec, Codec)].
    ///
    /// In JSON and NBT, it writes the value with the codec of its side, and reads with the left codec and then the right if the left fails.
    /// On the network, it writes a boolean that is `true` for the left side and then the value, the same as [ByteBufCodecs#either(StreamCodec, StreamCodec)].
    ///
    /// In JSON and NBT, if neither codec can read the input, the failure will hold the errors of both, the left ones first.
    ///
    /// @param left  The codec of the left side.
    /// @param right The codec of the right side.
    ///
    /// @return The codec of the value of either side.
    ///
    static <L, R, A extends Access.Plain> NexusCodec<Either<L, R>, A> either(NexusCodec<L, ? super A> left, NexusCodec<R, ? super A> right) {
        return Combinators.either(left, right);
    }
    
    ///
    /// Creates the codec of a struct that is one of the given subtypes, picked by the identifier under the given key.
    /// Similar to DFU's [Codec#dispatch(String, Function, Function)] on [Identifier#CODEC].
    ///
    /// In JSON and NBT, the fields of the subtype are in the same object as the identifier, and their errors are at their own keys.
    /// On the network, it writes the identifier and then the fields of the subtype, the same as [StreamCodec#dispatch(Function, Function)] on [Identifier#STREAM_CODEC].
    ///
    /// Fails if the key is missing, or if its identifier is not one of the subtypes, listing the identifiers of all subtypes.
    /// Fails to encode a value whose identifier is not one of the subtypes.
    /// A strict format refuses every key other than the key, the keys of the subtype it picked, and the keys of a struct it's inlined into.
    ///
    /// @param key      The key of the identifier in JSON and NBT.
    /// @param keyOf    The getter of the identifier of the subtype a value belongs to.
    /// @param subtypes The codec of every subtype, by its identifier.
    ///
    /// @return The codec of the struct, which can also be inlined into another struct.
    ///
    /// @throws IllegalArgumentException If a subtype has the key, counting the keys of the struct of an inline field.
    ///
    static <T, A extends Access.Plain> StructCodec<T, A> dispatch(
            String key,
            Function<? super T, Identifier> keyOf,
            Map<Identifier, ? extends StructCodec<? extends T, ? super A>> subtypes
    ) {
        return Combinators.dispatch(key, keyOf, subtypes);
    }
    
    ///
    /// Creates a codec that may refer to itself through the parameter of the given definition, such as the codec of a tree.
    /// Similar to DFU's [Codec#recursive(String, Function)] and vanilla's [StreamCodec#recursive(UnaryOperator)].
    ///
    /// It writes and reads the same as the codec the definition returns.
    /// The definition is called once, the first time the codec is used.
    ///
    /// If encode or decode is called on the returned codec, the given name will be on the first line of a failure.
    ///
    /// @param name       The name of the codec, used in the failure message.
    /// @param definition The definition of the codec, called with the codec itself.
    ///
    /// @return The codec.
    ///
    static <T, A extends Access.Plain> NexusCodec<T, A> recursive(String name, Function<NexusCodec<T, A>, NexusCodec<T, A>> definition) {
        return Combinators.recursive(name, definition);
    }
    
    ///
    /// Creates the codec of a holder of the given registry.
    /// Similar to vanilla's [RegistryFixedCodec].
    ///
    /// In JSON and NBT, it writes the identifier of a reference holder and fails on a direct holder.
    /// On the network, it writes the id of the holder's value as a VarInt, the same as [ByteBufCodecs#holderRegistry(ResourceKey)].
    ///
    /// Fails if an identifier or an id is not in the registry, and the failure quotes it and the registry.
    /// Fails if the format's registries don't have the registry, if the holder belongs to other registries, or on the network if the registry is a built-in one that isn't synced.
    ///
    /// @param registry The key of the registry.
    ///
    /// @return The codec of the holder, which decodes into a reference holder.
    ///
    static <T> NexusCodec<Holder<T>, Access.Registries> holder(ResourceKey<? extends Registry<T>> registry) {
        return HolderCodecs.holder(registry);
    }
    
    ///
    /// Creates the codec of a holder of the given registry, which writes a direct holder inline with the given codec.
    /// Similar to vanilla's [RegistryFileCodec].
    ///
    /// In JSON and NBT, it writes a reference holder as its identifier and a direct holder as its value.
    /// It reads a string that is a valid identifier as a reference holder, and anything else as the value of a direct holder.
    /// On the network, it writes the id of a reference holder's value plus one, or `0` and then the value of a direct holder, the same as [ByteBufCodecs#holder(ResourceKey, StreamCodec)].
    ///
    /// Fails if an identifier or an id is not in the registry, and the failure quotes it and the registry.
    /// Fails on a reference holder if the format's registries don't have the registry, if the holder belongs to other registries, or on the network if the registry is a built-in one that isn't synced.
    ///
    /// @param registry The key of the registry.
    /// @param element  The codec of the value of a direct holder.
    ///
    /// @return The codec of the holder.
    ///
    static <T> NexusCodec<Holder<T>, Access.Registries> holderOrInline(ResourceKey<? extends Registry<T>> registry, NexusCodec<T, ? super Access.Registries> element) {
        return HolderCodecs.holderOrInline(registry, element);
    }
    
    ///
    /// Creates the codec of a set of holders of the given registry.
    /// Similar to vanilla's [RegistryCodecs#homogeneousList(ResourceKey)].
    ///
    /// In JSON and NBT, it writes:
    /// - A tag as its identifier after a `#`.
    /// - A set of one holder as the identifier of the holder.
    /// - Any other set of holders as a list of their identifiers.
    /// - One of NeoForge's custom sets as an object with the identifier of its type under `type`, with the codec of that type.
    ///
    /// On the network, it writes the same as [ByteBufCodecs#holderSet(ResourceKey)].
    /// A custom set is only written as one if the buffer is for a connection to NeoForge, and as its holders otherwise.
    ///
    /// Fails if an identifier, an id, or a tag is not in the registry, and the failure quotes it and the registry.
    /// In JSON and NBT, a failure of a list holds the errors of every holder that failed, each at its index.
    ///
    /// A custom set fails at `type` if the key is missing or its type is unknown, and with NeoForge's messages for anything else inside it.
    /// A strict format refuses unknown keys at the top level of a custom set only.
    ///
    /// @param registry The key of the registry.
    ///
    /// @return The codec of the set.
    ///
    static <T> NexusCodec<HolderSet<T>, Access.Registries> holderSet(ResourceKey<? extends Registry<T>> registry) {
        return HolderCodecs.holderSet(registry);
    }
    
    ///
    /// Creates a codec that runs the given DFU codec.
    ///
    /// In JSON and NBT, it runs the DFU codec over [JsonOps] and [NbtOps].
    /// On the network, it writes the NBT of the DFU codec, the same as [ByteBufCodecs#fromCodec(Codec)].
    ///
    /// If the DFU codec returns an error, it will fail with the message of that error.
    ///
    /// @param codec The DFU codec.
    ///
    /// @return The codec that runs the DFU codec.
    ///
    /// @see #ofDfu(Codec, Class)
    ///
    static <T> NexusCodec<T, Access.Plain> ofDfu(Codec<T> codec) {
        return VanillaAdapters.ofDfu(codec);
    }
    
    ///
    /// Creates a codec that runs the given DFU codec with the registries of the format.
    ///
    /// In JSON and NBT, it runs the DFU codec over [RegistryOps] with the registries of the format.
    /// On the network, it writes the NBT of the DFU codec, the same as [ByteBufCodecs#fromCodecWithRegistries(Codec)].
    ///
    /// If the DFU codec returns an error, it will fail with the message of that error.
    ///
    /// @param codec  The DFU codec, which may need registry ops.
    /// @param access The access of the codec, which is always `Access.Registries.class`.
    ///
    /// @return The codec that runs the DFU codec.
    ///
    /// @see #ofDfu(Codec)
    ///
    static <T> NexusCodec<T, Access.Registries> ofDfu(Codec<T> codec, Class<Access.Registries> access) {
        return VanillaAdapters.ofRegistryDfu(codec);
    }
    
    ///
    /// Creates a codec that runs the given stream codec on the network.
    ///
    /// Fails to encode and decode in JSON and NBT.
    /// If the stream codec throws, it will fail with the message of that exception.
    ///
    /// @param codec The stream codec.
    ///
    /// @return The codec that runs the stream codec.
    ///
    /// @see #ofStream(StreamCodec, Class)
    ///
    static <T> NexusCodec<T, Access.Plain> ofStream(StreamCodec<? super FriendlyByteBuf, T> codec) {
        return VanillaAdapters.ofStream(codec);
    }
    
    ///
    /// Creates a codec that runs the given stream codec on the network, with the registries of the buffer.
    ///
    /// Fails to encode and decode in JSON and NBT.
    /// If the stream codec throws, it will fail with the message of that exception.
    ///
    /// @param codec  The stream codec, which may need a [RegistryFriendlyByteBuf].
    /// @param access The access of the codec, which is always `Access.Registries.class`.
    ///
    /// @return The codec that runs the stream codec.
    ///
    /// @see #ofStream(StreamCodec)
    ///
    static <T> NexusCodec<T, Access.Registries> ofStream(StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Class<Access.Registries> access) {
        return VanillaAdapters.ofRegistryStream(codec);
    }
    
    ///
    /// Creates a codec that runs the given DFU codec in JSON and NBT and the given stream codec on the network.
    ///
    /// In JSON and NBT, it runs the DFU codec over [JsonOps] and [NbtOps].
    ///
    /// If the DFU codec returns an error or the stream codec throws, it will fail with the message of that error or exception.
    ///
    /// @param codec  The DFU codec.
    /// @param stream The stream codec.
    ///
    /// @return The codec that runs both.
    ///
    /// @see #ofVanilla(Codec, StreamCodec, Class)
    ///
    static <T> NexusCodec<T, Access.Plain> ofVanilla(Codec<T> codec, StreamCodec<? super FriendlyByteBuf, T> stream) {
        return VanillaAdapters.ofVanilla(codec, stream);
    }
    
    ///
    /// Creates a codec that runs the given DFU codec in JSON and NBT and the given stream codec on the network, with the registries of the format.
    ///
    /// In JSON and NBT, it runs the DFU codec over [RegistryOps] with the registries of the format.
    ///
    /// If the DFU codec returns an error or the stream codec throws, it will fail with the message of that error or exception.
    ///
    /// @param codec  The DFU codec, which may need registry ops.
    /// @param stream The stream codec, which may need a [RegistryFriendlyByteBuf].
    /// @param access The access of the codec, which is always `Access.Registries.class`.
    ///
    /// @return The codec that runs both.
    ///
    /// @see #ofVanilla(Codec, StreamCodec)
    ///
    static <T> NexusCodec<T, Access.Registries> ofVanilla(Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> stream, Class<Access.Registries> access) {
        return VanillaAdapters.ofRegistryVanilla(codec, stream);
    }
    
    ///
    /// Creates the stream codec of the given codec over a buffer without registries.
    ///
    /// Meant to be used for the payloads of the configuration phase.
    ///
    /// If the codec fails, the stream codec will throw a [DecoderException] or an [EncoderException] with the message of the [CodecException].
    ///
    /// @param codec The codec, which needs no registries.
    ///
    /// @return The stream codec.
    ///
    /// @see #asStream()
    ///
    static <T> StreamCodec<FriendlyByteBuf, T> plainStream(NexusCodec<T, Access.Plain> codec) {
        return VanillaAdapters.plainStream(codec);
    }
    
    ///
    /// Encodes the given value in the given format.
    ///
    /// @param format The format to encode the value in.
    /// @param value  The value to encode.
    ///
    /// @return The encoded value, or the buffer written to on the network.
    ///
    /// @throws CodecException If the value could not be encoded, with every error that occurred during encoding.
    ///
    <V> V encode(CodecFormat<V, ? extends A> format, T value);
    
    ///
    /// Decodes a value from the given input in the given format.
    ///
    /// @param format The format the input is in.
    /// @param input  The input to decode, which is the buffer the format was built on for a [BufferFormat].
    ///
    /// @return The decoded value.
    ///
    /// @throws CodecException           If the input could not be decoded, with every error that occurred during decoding.
    /// @throws IllegalArgumentException If the format is a [BufferFormat] and the input is not the buffer it was built on.
    ///
    <V> T decode(CodecFormat<V, ? extends A> format, V input);
    
    ///
    /// Encodes the given value in the given format.
    ///
    /// Returns a failure holding the same [CodecException] that [#encode(CodecFormat, Object)] would throw.
    ///
    /// @param format The format to encode the value in.
    /// @param value  The value to encode.
    ///
    /// @return The encoded value, or the buffer written to on the network, or the failure.
    ///
    <V> CodecResult<V> tryEncode(CodecFormat<V, ? extends A> format, T value);
    
    ///
    /// Decodes a value from the given input in the given format.
    ///
    /// Returns a failure holding the same [CodecException] that [#decode(CodecFormat, Object)] would throw.
    ///
    /// @param format The format the input is in.
    /// @param input  The input to decode, which is the buffer the format was built on for a [BufferFormat].
    ///
    /// @return The decoded value, or the failure.
    ///
    /// @throws IllegalArgumentException If the format is a [BufferFormat] and the input is not the buffer it was built on.
    ///
    <V> CodecResult<T> tryDecode(CodecFormat<V, ? extends A> format, V input);
    
    ///
    /// Decodes a value from the buffer the given format was built on.
    ///
    /// @param format The format to decode from.
    ///
    /// @return The decoded value.
    ///
    /// @throws CodecException If the buffer could not be decoded, with every error that occurred during decoding.
    ///
    T decode(BufferFormat<?, ? extends A> format);
    
    ///
    /// Decodes a value from the buffer the given format was built on.
    ///
    /// Returns a failure holding the same [CodecException] that [#decode(BufferFormat)] would throw.
    ///
    /// @param format The format to decode from.
    ///
    /// @return The decoded value, or the failure.
    ///
    CodecResult<T> tryDecode(BufferFormat<?, ? extends A> format);
    
    ///
    /// Creates a field of a struct with the value of this codec under the given key.
    /// Similar to DFU's [Codec#fieldOf(String)].
    ///
    /// Fails to decode if the key is missing or holds `null`.
    ///
    /// @param name   The key of the field in JSON and NBT.
    /// @param getter The getter of the value from the struct.
    ///
    /// @return The field of the struct.
    ///
    <Z> StructField<Z, T, A> field(String name, Function<Z, T> getter);
    
    ///
    /// Creates a field of a struct with the value of this codec under the given key, or the given fallback if the key is absent.
    /// Similar to DFU's [Codec#optionalFieldOf(String, Object)].
    ///
    /// In JSON and NBT, a value equal to the fallback will be left out.
    /// Fails to decode if the key holds a value this codec can't decode, including `null`.
    ///
    /// On the network, the value is always written.
    ///
    /// @param name     The key of the field in JSON and NBT.
    /// @param fallback The value to decode if the key is absent.
    /// @param getter   The getter of the value from the struct.
    ///
    /// @return The field of the struct.
    ///
    <Z> StructField<Z, T, A> optionalField(String name, T fallback, Function<Z, T> getter);
    
    ///
    /// Creates a field of a struct with the value of this codec under the given key, or empty if the key is absent.
    /// Similar to DFU's [Codec#optionalFieldOf(String)].
    ///
    /// In JSON and NBT, an empty value will be left out and `null` is decoded as empty.
    /// Fails to decode if the key holds any other value this codec can't decode.
    ///
    /// On the network, it writes a boolean that is `true` if the value is present and then the value, the same as [ByteBufCodecs#optional(StreamCodec)].
    ///
    /// @param name   The key of the field in JSON and NBT.
    /// @param getter The getter of the value from the struct, which is empty if there is none.
    ///
    /// @return The field of the struct.
    ///
    <Z> StructField<Z, Optional<T>, A> optionalField(String name, Function<Z, Optional<T>> getter);
    
    ///
    /// Creates the codec of a list of at most the given number of values of this codec.
    /// Similar to DFU's [Codec#sizeLimitedListOf(int)].
    ///
    /// In JSON and NBT, it writes a list of the values.
    /// On the network, it writes the number of values as a VarInt and then every value, the same as [ByteBufCodecs#list(int)].
    ///
    /// Fails if the list has more than `limit` values, before any value is decoded.
    /// In JSON and NBT, a failure holds the errors of every value that failed, each at its index.
    ///
    /// @param limit The maximum number of values of the list.
    ///
    /// @return The codec of the list.
    ///
    /// @throws IllegalArgumentException If the limit is negative.
    ///
    NexusCodec<List<T>, A> list(@Range(from = 0, to = Integer.MAX_VALUE) int limit);
    
    ///
    /// Creates the codec of a list of at most 32767 values of this codec.
    /// Similar to DFU's [Codec#listOf()].
    ///
    /// In JSON and NBT, it writes a list of the values.
    /// On the network, it writes the number of values as a VarInt and then every value, the same as [ByteBufCodecs#list(int)].
    ///
    /// Fails if the list has more than 32767 values, before any value is decoded.
    /// In JSON and NBT, a failure holds the errors of every value that failed, each at its index.
    ///
    /// @return The codec of the list.
    ///
    NexusCodec<List<T>, A> list();
    
    ///
    /// Creates the codec of a set of at most the given number of values of this codec.
    /// Similar to DFU's [Codec#sizeLimitedListOf(int)] turned into a set.
    ///
    /// In JSON and NBT, it writes a list of the values, in the order of the set.
    /// On the network, it writes the number of values as a VarInt and then every value, the same as [ByteBufCodecs#collection(IntFunction, StreamCodec, int)].
    ///
    /// Fails if the set has more than `limit` values, before any value is decoded, or if a decoded value is equal to an earlier one.
    /// In JSON and NBT, a failure holds the errors of every value that failed, each at its index.
    ///
    /// @param limit The maximum number of values of the set.
    ///
    /// @return The codec of the set, which decodes into a set that keeps the order of its values.
    ///
    /// @throws IllegalArgumentException If the limit is negative.
    ///
    NexusCodec<Set<T>, A> set(@Range(from = 0, to = Integer.MAX_VALUE) int limit);
    
    ///
    /// Creates the codec of a set of at most 32767 values of this codec.
    /// Similar to DFU's [Codec#listOf()] turned into a set.
    ///
    /// In JSON and NBT, it writes a list of the values, in the order of the set.
    /// On the network, it writes the number of values as a VarInt and then every value, the same as [ByteBufCodecs#collection(IntFunction, StreamCodec, int)].
    ///
    /// Fails if the set has more than 32767 values, before any value is decoded, or if a decoded value is equal to an earlier one.
    /// In JSON and NBT, a failure holds the errors of every value that failed, each at its index.
    ///
    /// @return The codec of the set, which decodes into a set that keeps the order of its values.
    ///
    NexusCodec<Set<T>, A> set();
    
    ///
    /// Creates the codec of a list of at most 32767 values of this codec, which writes a single value bare.
    /// Similar to vanilla's [ExtraCodecs#compactListCodec(Codec)].
    ///
    /// In JSON and NBT, it writes a list of one value as the bare value and any other list as a list.
    /// It reads a list, and a bare value as a list of one if the input can't be read as a list.
    /// On the network, it always writes the list, the same as [ByteBufCodecs#list(int)].
    ///
    /// In JSON and NBT, if the input can be read neither as a list nor as a value, the failure will hold the errors of both.
    ///
    /// @return The codec of the list.
    ///
    NexusCodec<List<T>, A> oneOrMany();
    
    ///
    /// Creates the codec of the values that this codec writes and reads through the given mappings.
    /// Similar to DFU's [Codec#xmap(Function, Function)].
    ///
    /// @param to   The mapping from a value of this codec, called after reading.
    /// @param from The mapping to a value of this codec, called before writing.
    ///
    /// @return The codec of the mapped values.
    ///
    <R> NexusCodec<R, A> xmap(Function<? super T, ? extends R> to, Function<? super R, ? extends T> from);
    
    ///
    /// Creates the codec of the values that this codec writes and reads through the given mappings, which may refuse a value.
    /// Similar to DFU's [Codec#flatXmap(Function, Function)].
    ///
    /// A mapping refuses a value by throwing a [CodecException], whose errors will be at the path of the value.
    /// Any other exception a mapping throws will become a [CodecException] too.
    ///
    /// @param to   The mapping from a value of this codec, called after reading.
    /// @param from The mapping to a value of this codec, called before writing.
    ///
    /// @return The codec of the mapped values.
    ///
    <R> NexusCodec<R, A> flatXmap(Function<? super T, ? extends R> to, Function<? super R, ? extends T> from);
    
    ///
    /// Creates the codec of the values of this codec that the given check accepts.
    /// Similar to DFU's [Codec#validate(Function)].
    ///
    /// The check is called on every value before it's written and after it's read.
    /// If the check returns a message, the value will fail with that message at the path of the value.
    /// Any exception the check throws will become a [CodecException] too, if it isn't one already.
    ///
    /// @param check The check of a value, which returns the error message, or `null` if the value is valid.
    ///
    /// @return The codec of the checked values.
    ///
    NexusCodec<T, A> validate(Function<? super T, @Nullable String> check);
    
    ///
    /// Creates the DFU codec of this codec.
    ///
    /// If this codec is a [StructCodec], it will be the codec of [StructCodec#asMapCodec()], the same as a record codec of DFU.
    ///
    /// Over the ops of json or NBT values, such as [JsonOps], [NbtOps], and [RegistryOps] over them, it writes and reads the values of the ops directly.
    /// Over any other ops, it converts the values to and from json, with json null as the empty value of the ops.
    ///
    /// If this codec needs registries, it will take them from the [RegistryOps], and fail over any other ops.
    /// Fails over ops that compress maps, such as [JsonOps#COMPRESSED].
    /// A failure is an error with the message of the [CodecException], without a partial result.
    ///
    /// @return The DFU codec.
    ///
    Codec<T> asDfu();
    
    ///
    /// Creates the stream codec of this codec, which writes and reads the same as the netty format of the buffer.
    ///
    /// If this codec fails, the stream codec will throw a [DecoderException] or an [EncoderException] with the message of the [CodecException].
    ///
    /// @return The stream codec.
    ///
    /// @see #plainStream(NexusCodec)
    ///
    StreamCodec<RegistryFriendlyByteBuf, T> asStream();
    
    ///
    /// Creates a data component type that is saved with the DFU codec of this codec and synced with its stream codec.
    ///
    /// @return The data component type.
    ///
    /// @see #asDfu()
    /// @see #asStream()
    ///
    DataComponentType<T> asDataComponentType();
}
