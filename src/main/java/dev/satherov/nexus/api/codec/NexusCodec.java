package dev.satherov.nexus.api.codec;

import dev.satherov.nexus.internal.codec.Scalars;

import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import com.mojang.serialization.Codec;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;

import java.util.UUID;

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
}
