package dev.satherov.nexus.internal.codec;

import dev.satherov.nexus.api.codec.CodecException;
import dev.satherov.nexus.api.codec.CodecFormat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

///
/// A format as a codec uses it, with one method that encodes and one that decodes each kind of value.
///
/// A read that finds the wrong thing throws a [CodecException] with one error at an empty path.
/// An integer read also refuses a number that is not integral or outside the range of its type.
///
/// On the positional format every encode appends to the buffer and returns it, and every decode consumes from it.
/// Lists and the keys of objects only exist on the keyed formats, and element counts only on the positional one.
///
/// @param <V> The type of the values of the format.
///
@ApiStatus.Internal
public interface Operations<V> {

    ///
    /// The operations behind the given format.
    ///
    /// @param format The api format.
    ///
    /// @return The format itself, as its operations.
    ///
    static <V> Operations<V> of(CodecFormat<V, ?> format) {
        //noinspection unchecked Every format is one of the operations records, over the same value type.
        return (Operations<V>) format;
    }

    ///
    /// @return `true` if the fields of an object are written by their position instead of by a key.
    ///
    boolean isPositional();

    ///
    /// @return `true` if decoding refuses unknown keys.
    ///
    boolean isStrict();

    ///
    /// The registries of the format, or `null` if the format is plain.
    ///
    /// @return The registries, or `null` if the format is plain.
    ///
    RegistryOps.@Nullable RegistryInfoLookup registries();

    ///
    /// The registry access of a netty format with registries, or `null` on every other format.
    ///
    /// Meant to be used for the id maps of holders.
    ///
    /// @return The registry access, or `null` on every other format.
    ///
    @Nullable RegistryAccess registryAccess();

    ///
    /// Encodes the given boolean.
    ///
    /// @param value The boolean to encode.
    ///
    /// @return The encoded boolean.
    ///
    V ofBoolean(boolean value);

    ///
    /// Decodes a boolean from the given input.
    ///
    /// @param input The value to decode.
    ///
    /// @return The decoded boolean.
    ///
    boolean asBoolean(V input);

    ///
    /// Encodes the given byte.
    ///
    /// @param value The byte to encode.
    ///
    /// @return The encoded byte.
    ///
    V ofByte(byte value);

    ///
    /// Decodes a byte from the given input.
    ///
    /// @param input The value to decode.
    ///
    /// @return The decoded byte.
    ///
    byte asByte(V input);

    ///
    /// Encodes the given short.
    ///
    /// @param value The short to encode.
    ///
    /// @return The encoded short.
    ///
    V ofShort(short value);

    ///
    /// Decodes a short from the given input.
    ///
    /// @param input The value to decode.
    ///
    /// @return The decoded short.
    ///
    short asShort(V input);

    ///
    /// Encodes the given int.
    ///
    /// @param value The int to encode.
    ///
    /// @return The encoded int.
    ///
    V ofInt(int value);

    ///
    /// Decodes an int from the given input.
    ///
    /// @param input The value to decode.
    ///
    /// @return The decoded int.
    ///
    int asInt(V input);

    ///
    /// Encodes the given int as a VarInt on netty and as a plain int on the keyed formats.
    ///
    /// @param value The int to encode.
    ///
    /// @return The encoded int.
    ///
    V ofVarInt(int value);

    ///
    /// Decodes an int written by [#ofVarInt(int)] from the given input.
    ///
    /// @param input The value to decode.
    ///
    /// @return The decoded int.
    ///
    int asVarInt(V input);

    ///
    /// Encodes the given long.
    ///
    /// @param value The long to encode.
    ///
    /// @return The encoded long.
    ///
    V ofLong(long value);

    ///
    /// Decodes a long from the given input.
    ///
    /// @param input The value to decode.
    ///
    /// @return The decoded long.
    ///
    long asLong(V input);

    ///
    /// Encodes the given long as a VarLong on netty and as a plain long on the keyed formats.
    ///
    /// @param value The long to encode.
    ///
    /// @return The encoded long.
    ///
    V ofVarLong(long value);

    ///
    /// Decodes a long written by [#ofVarLong(long)] from the given input.
    ///
    /// @param input The value to decode.
    ///
    /// @return The decoded long.
    ///
    long asVarLong(V input);

    ///
    /// Encodes the given float.
    ///
    /// @param value The float to encode.
    ///
    /// @return The encoded float.
    ///
    V ofFloat(float value);

    ///
    /// Decodes a float from the given input.
    ///
    /// @param input The value to decode.
    ///
    /// @return The decoded float.
    ///
    float asFloat(V input);

    ///
    /// Encodes the given double.
    ///
    /// @param value The double to encode.
    ///
    /// @return The encoded double.
    ///
    V ofDouble(double value);

    ///
    /// Decodes a double from the given input.
    ///
    /// @param input The value to decode.
    ///
    /// @return The decoded double.
    ///
    double asDouble(V input);

    ///
    /// Encodes the given string.
    ///
    /// @param value The string to encode.
    /// @param limit The maximum number of characters of the string.
    ///
    /// @return The encoded string.
    ///
    /// @throws CodecException If the string has more than `limit` characters.
    ///
    V ofString(String value, int limit);

    ///
    /// Decodes a string from the given input.
    ///
    /// @param input The value to decode.
    /// @param limit The maximum number of characters of the string.
    ///
    /// @return The decoded string.
    ///
    /// @throws CodecException If the string has more than `limit` characters.
    ///
    String asString(V input, int limit);

    ///
    /// Encodes the given ints as an int array.
    ///
    /// @param value The ints to encode.
    ///
    /// @return The encoded int array.
    ///
    V ofIntArray(int[] value);

    ///
    /// Decodes an int array from the given input.
    ///
    /// @param input The value to decode.
    ///
    /// @return The decoded ints.
    ///
    int[] asIntArray(V input);

    ///
    /// Encodes the given elements as a list.
    ///
    /// @param elements The encoded elements, in order.
    ///
    /// @return The encoded list.
    ///
    /// @throws UnsupportedOperationException If the format is positional.
    ///
    V ofList(List<V> elements);

    ///
    /// Decodes the elements of a list from the given input.
    ///
    /// @param input The value to decode.
    ///
    /// @return The elements of the list, in order.
    ///
    /// @throws UnsupportedOperationException If the format is positional.
    ///
    List<V> asList(V input);

    ///
    /// Writes the given number of elements as a VarInt.
    ///
    /// @param count The number of elements.
    /// @param limit The maximum number of elements.
    ///
    /// @throws CodecException                If the count is above the limit.
    /// @throws UnsupportedOperationException If the format is keyed.
    ///
    void writeCount(int count, int limit);

    ///
    /// Reads a number of elements written by [#writeCount(int, int)].
    ///
    /// @param limit The maximum number of elements.
    ///
    /// @return The number of elements.
    ///
    /// @throws CodecException                If the count is negative or above the limit.
    /// @throws UnsupportedOperationException If the format is keyed.
    ///
    int readCount(int limit);

    ///
    /// Creates an empty object, which is the buffer with nothing appended on the positional format.
    ///
    /// @return The empty object.
    ///
    V emptyObject();

    ///
    /// Puts the given value under the given key of the given object.
    ///
    /// @param object The object, created by [#emptyObject()].
    /// @param key    The key to put the value under.
    /// @param value  The value to put.
    ///
    /// @return The given object.
    ///
    /// @throws UnsupportedOperationException If the format is positional.
    ///
    V put(V object, String key, V value);

    ///
    /// Gets the value under the given key of the given object.
    ///
    /// @param object The object to read.
    /// @param key    The key of the value.
    ///
    /// @return The value, or `null` if the object doesn't have the key.
    ///
    /// @throws UnsupportedOperationException If the format is positional.
    ///
    @Nullable V get(V object, String key);

    ///
    /// Gets all keys of the given object.
    ///
    /// @param object The object to read.
    ///
    /// @return Any keys of the object.
    ///
    /// @throws UnsupportedOperationException If the format is positional.
    ///
    Set<String> keys(V object);

    ///
    /// @param value The value to check.
    ///
    /// @return `true` if the given value is the null value of the format.
    ///
    /// @throws UnsupportedOperationException If the format is positional.
    ///
    boolean isNull(V value);
}
