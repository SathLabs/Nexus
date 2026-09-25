package dev.satherov.nexus.api.codec;

import dev.satherov.nexus.internal.codec.Scalars;
import dev.satherov.nexus.internal.codec.Structs;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

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
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

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
    /// Fails if the string is longer than that, naming the limit and the length it found.
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
}
