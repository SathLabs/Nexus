package dev.satherov.nexus.internal.codec;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;

import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

///
/// Utility for building codec failures, their messages, and their paths.
///
@UtilityClass
@ApiStatus.Internal
public class CodecErrors {
    
    ///
    /// Creates a failure for a value that is not of the expected kind.
    ///
    /// @param expected The expected kind of value, such as `a number`.
    /// @param found    The value found instead.
    ///
    /// @return The failure, with one error at an empty path.
    ///
    public static NexusCodecException mismatch(String expected, @Nullable Object found) {
        return new NexusCodecException("Expected " + expected + ", found " + CodecErrors.describe(found));
    }
    
    ///
    /// Creates a failure for an integer outside the given range.
    ///
    /// @param min   The smallest allowed value.
    /// @param max   The largest allowed value.
    /// @param found The value found instead.
    ///
    /// @return The failure, with one error at an empty path.
    ///
    public static NexusCodecException outOfRange(long min, long max, Object found) {
        return new NexusCodecException(String.format(Locale.ROOT, "Expected an integer in [%d, %d], found %s", min, max, CodecErrors.describe(found)));
    }
    
    ///
    /// Creates a failure for a string with more characters than the given limit.
    ///
    /// @param limit  The maximum number of characters.
    /// @param length The number of characters of the string.
    ///
    /// @return The failure, with one error at an empty path.
    ///
    public static NexusCodecException tooLong(int limit, int length) {
        return new NexusCodecException("Expected at most " + limit + " characters, found " + length);
    }
    
    ///
    /// Creates a failure for a name that is not one of the known names.
    ///
    /// @param known The known names, in the order the message should list them.
    /// @param found The name found instead.
    ///
    /// @return The failure, with one error at an empty path.
    ///
    public static NexusCodecException unknownName(Collection<String> known, String found) {
        return new NexusCodecException("Expected one of [" + String.join(", ", known) + "], found " + CodecErrors.describe(found));
    }
    
    ///
    /// Describes the given value the way an error message quotes it:
    /// - A string, a json string, or a string tag is its escaped text in double quotes.
    /// - A json number, a json boolean, or a numeric tag is its value.
    /// - A json object or a compound tag is `an object`.
    /// - A json array or any other collection tag is `a list`.
    /// - Anything else, such as a number, a boolean, or `null`, is its string form.
    ///
    /// @param value The value to describe.
    ///
    /// @return The description of the value.
    ///
    public static String describe(@Nullable Object value) {
        return switch (value) {
            case String text -> CodecErrors.quote(text, '"');
            case StringTag(String text) -> CodecErrors.quote(text, '"');
            case JsonPrimitive primitive when primitive.isString() -> CodecErrors.quote(primitive.getAsString(), '"');
            case JsonPrimitive primitive -> primitive.getAsString();
            case NumericTag number -> String.valueOf(number.box());
            case JsonObject _, CompoundTag _ -> "an object";
            case JsonArray _, CollectionTag _ -> "a list";
            case null, default -> String.valueOf(value);
        };
    }
    
    ///
    /// Puts the given text between two of the given marks, escaping backslashes, that mark, and control characters.
    ///
    private static String quote(String text, char mark) {
        StringBuilder quoted = new StringBuilder().append(mark);
        for (char c : text.toCharArray()) {
            if (c == mark || c == '\\') {
                quoted.append('\\').append(c);
            } else if (Character.isISOControl(c)) {
                quoted.append(switch (c) {
                    case '\n' -> "\\n";
                    case '\r' -> "\\r";
                    case '\t' -> "\\t";
                    default -> String.format(Locale.ROOT, "\\u%04x", (int) c);
                });
            } else {
                quoted.append(c);
            }
        }
        
        return quoted.append(mark).toString();
    }
    
    ///
    /// Puts the given key in front of the path of every error of the given failure.
    ///
    /// @param failure The failure that the value under the key threw.
    /// @param key     The key of the value.
    ///
    /// @return A new failure holding the prefixed errors.
    ///
    public static NexusCodecException prefixKey(NexusCodecException failure, String key) {
        return CodecErrors.prefix(failure, key);
    }
    
    ///
    /// Puts the given index in front of the path of every error of the given failure.
    ///
    /// @param failure The failure that the element at the index threw.
    /// @param index   The index of the element in its list.
    ///
    /// @return A new failure holding the prefixed errors.
    ///
    public static NexusCodecException prefixIndex(NexusCodecException failure, int index) {
        return CodecErrors.prefix(failure, "[" + index + "]");
    }
    
    ///
    /// Puts the given map key in front of the path of every error of the given failure.
    ///
    /// The map key will be in single quotes, with backslashes, single quotes, and control characters escaped.
    ///
    /// @param failure The failure that the entry under the map key threw.
    /// @param key     The map key of the entry.
    ///
    /// @return A new failure holding the prefixed errors.
    ///
    public static NexusCodecException prefixMapKey(NexusCodecException failure, String key) {
        return CodecErrors.prefix(failure, "[" + CodecErrors.quote(key, '\'') + "]");
    }
    
    ///
    /// Puts the given segment in front of the path of every error, with a dot between them if the path starts with a key.
    ///
    private static NexusCodecException prefix(NexusCodecException failure, String segment) {
        List<CodecError> prefixed = failure.errors().stream()
                .map(error -> {
                    String separator = error.path().isEmpty() || error.path().startsWith("[") ? "" : ".";
                    return new CodecError(segment + separator + error.path(), error.message());
                })
                .toList();
        
        return new NexusCodecException(prefixed);
    }
    
    ///
    /// Creates a failure with the errors of the given failure, below the header of a failed encoding attempt.
    ///
    /// @param codec   The name of the codec that could not encode.
    /// @param format  The name of the format, such as `JSON`.
    /// @param failure The failure that the codec threw.
    ///
    /// @return The failure with the header.
    ///
    public static NexusCodecException encodeFailure(String codec, String format, NexusCodecException failure) {
        return new NexusCodecException("Could not encode '" + codec + "' to " + format, failure.errors());
    }
    
    ///
    /// Creates a failure with the errors of the given failure, below the header of a failed decoding attempt.
    ///
    /// @param codec   The name of the codec that could not decode.
    /// @param format  The name of the format, such as `JSON`.
    /// @param failure The failure that the codec threw.
    ///
    /// @return The failure with the header.
    ///
    public static NexusCodecException decodeFailure(String codec, String format, NexusCodecException failure) {
        return new NexusCodecException("Could not decode '" + codec + "' from " + format, failure.errors());
    }
}
