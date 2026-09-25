package dev.satherov.nexus.internal.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.CodecFormat;
import dev.satherov.nexus.api.codec.NexusCodecException;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

///
/// The json format over gson's [JsonElement], which encodes and decodes values the way [JsonOps] does.
///
/// @param isStrict   If decoding should refuse unknown keys.
/// @param registries The registries of the format, or `null` if the format is plain.
/// @param <A>        The access of the format.
///
@ApiStatus.Internal
public record JsonOperations<A extends Access.Plain>(
        boolean isStrict,
        RegistryOps.@Nullable RegistryInfoLookup registries
) implements Operations<JsonElement>, CodecFormat<JsonElement, A> {

    ///
    /// Will always return `JSON`.
    ///
    @Override
    public String name() {
        return "JSON";
    }

    ///
    /// Creates a copy of this format that refuses unknown keys, with the same registries.
    ///
    @Override
    public CodecFormat<JsonElement, A> strict() {
        return new JsonOperations<>(true, this.registries);
    }

    ///
    /// Creates a copy of this format with the given registries in vanilla's [RegistryOps.HolderLookupAdapter].
    ///
    @Override
    public CodecFormat<JsonElement, Access.Registries> withRegistries(HolderLookup.Provider registries) {
        return new JsonOperations<>(this.isStrict, new RegistryOps.HolderLookupAdapter(registries));
    }

    ///
    /// Will always return `false`.
    ///
    @Override
    public boolean isPositional() {
        return false;
    }

    ///
    /// Will always return `null`.
    ///
    @Override
    public @Nullable RegistryAccess registryAccess() {
        return null;
    }

    ///
    /// Will always return [JsonOps#INSTANCE].
    ///
    @Override
    public DynamicOps<JsonElement> dynamicOps() {
        return JsonOps.INSTANCE;
    }

    ///
    /// Encodes the given boolean as a json boolean.
    ///
    @Override
    public JsonElement ofBoolean(boolean value) {
        return new JsonPrimitive(value);
    }

    ///
    /// Decodes a json boolean.
    ///
    /// @throws NexusCodecException If the input is not a json boolean.
    ///
    @Override
    public boolean asBoolean(JsonElement input) {
        if (!(input instanceof JsonPrimitive primitive) || !primitive.isBoolean()) throw Errors.mismatch("a boolean", input);
        return primitive.getAsBoolean();
    }

    ///
    /// Encodes the given byte as a json number.
    ///
    @Override
    public JsonElement ofByte(byte value) {
        return new JsonPrimitive(value);
    }

    ///
    /// Decodes a json number that holds an integer in the range of a byte.
    ///
    @Override
    public byte asByte(JsonElement input) {
        return (byte) this.asIntegral(input, Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

    ///
    /// Encodes the given short as a json number.
    ///
    @Override
    public JsonElement ofShort(short value) {
        return new JsonPrimitive(value);
    }

    ///
    /// Decodes a json number that holds an integer in the range of a short.
    ///
    @Override
    public short asShort(JsonElement input) {
        return (short) this.asIntegral(input, Short.MIN_VALUE, Short.MAX_VALUE);
    }

    ///
    /// Encodes the given int as a json number.
    ///
    @Override
    public JsonElement ofInt(int value) {
        return new JsonPrimitive(value);
    }

    ///
    /// Decodes a json number that holds an integer in the range of an int.
    ///
    @Override
    public int asInt(JsonElement input) {
        return (int) this.asIntegral(input, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    ///
    /// Encodes the given int as a json number, the same as [#ofInt(int)].
    ///
    @Override
    public JsonElement ofVarInt(int value) {
        return this.ofInt(value);
    }

    ///
    /// Decodes an int the same as [#asInt(JsonElement)].
    ///
    @Override
    public int asVarInt(JsonElement input) {
        return this.asInt(input);
    }

    ///
    /// Encodes the given long as a json number.
    ///
    @Override
    public JsonElement ofLong(long value) {
        return new JsonPrimitive(value);
    }

    ///
    /// Decodes a json number that holds an integer in the range of a long.
    ///
    @Override
    public long asLong(JsonElement input) {
        return this.asIntegral(input, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    ///
    /// Encodes the given long as a json number, the same as [#ofLong(long)].
    ///
    @Override
    public JsonElement ofVarLong(long value) {
        return this.ofLong(value);
    }

    ///
    /// Decodes a long the same as [#asLong(JsonElement)].
    ///
    @Override
    public long asVarLong(JsonElement input) {
        return this.asLong(input);
    }

    ///
    /// Decodes a json number that holds an integer within the given range.
    ///
    private long asIntegral(JsonElement input, long min, long max) {
        if (!(input instanceof JsonPrimitive primitive) || !primitive.isNumber()) throw Errors.mismatch("an integer", input);
        long value = switch (primitive.getAsNumber()) {
            case Byte _, Short _, Integer _, Long _ -> primitive.getAsLong();
            case Double _, Float _ -> {
                double exact = primitive.getAsDouble();
                if (exact != Math.rint(exact)) throw Errors.mismatch("an integer", input);
                // 2^63 is the first double past the range of a long, where the conversion would clamp.
                if (exact >= 0x1p63 || exact < -0x1p63) throw Errors.outOfRange(min, max, input);
                yield (long) exact;
            }
            default -> {
                // Plain integer text is the common case, so the exact decimal value is only looked at if it isn't one.
                try {
                    yield Long.parseLong(primitive.getAsString());
                } catch (NumberFormatException _) {
                    yield this.asExactIntegral(primitive, min, max);
                }
            }
        };

        if (value < min || value > max) throw Errors.outOfRange(min, max, input);
        return value;
    }

    ///
    /// Decodes a json number of any other kind, such as one parsed from text, by its exact decimal value.
    ///
    private long asExactIntegral(JsonPrimitive input, long min, long max) {
        try {
            BigDecimal exact = input.getAsBigDecimal();
            if (exact.stripTrailingZeros().scale() > 0) throw Errors.mismatch("an integer", input);
            return exact.longValueExact();
        } catch (NumberFormatException _) {
            throw Errors.mismatch("an integer", input);
        } catch (ArithmeticException _) {
            throw Errors.outOfRange(min, max, input);
        }
    }

    ///
    /// Encodes the given float as a json number.
    ///
    @Override
    public JsonElement ofFloat(float value) {
        return new JsonPrimitive(value);
    }

    ///
    /// Decodes any json number as a float.
    ///
    @Override
    public float asFloat(JsonElement input) {
        return this.asNumber(input).floatValue();
    }

    ///
    /// Encodes the given double as a json number.
    ///
    @Override
    public JsonElement ofDouble(double value) {
        return new JsonPrimitive(value);
    }

    ///
    /// Decodes any json number as a double.
    ///
    @Override
    public double asDouble(JsonElement input) {
        return this.asNumber(input).doubleValue();
    }

    ///
    /// Decodes a json number.
    ///
    private Number asNumber(JsonElement input) {
        if (!(input instanceof JsonPrimitive primitive) || !primitive.isNumber()) throw Errors.mismatch("a number", input);
        return primitive.getAsNumber();
    }

    ///
    /// Encodes the given string as a json string.
    ///
    /// @throws NexusCodecException If the string has more than `limit` characters.
    ///
    @Override
    public JsonElement ofString(String value, int limit) {
        if (value.length() > limit) throw Errors.tooLong(limit, value.length());
        return new JsonPrimitive(value);
    }

    ///
    /// Decodes a json string.
    ///
    /// @throws NexusCodecException If the input is not a json string, or if it has more than `limit` characters.
    ///
    @Override
    public String asString(JsonElement input, int limit) {
        if (!(input instanceof JsonPrimitive primitive) || !primitive.isString()) throw Errors.mismatch("a string", input);
        String value = primitive.getAsString();
        if (value.length() > limit) throw Errors.tooLong(limit, value.length());
        return value;
    }

    ///
    /// Encodes the given ints as a json array of numbers.
    ///
    @Override
    public JsonElement ofIntArray(int[] value) {
        JsonArray array = new JsonArray(value.length);
        for (int element : value) {
            array.add(element);
        }

        return array;
    }

    ///
    /// Decodes a json array of numbers that each hold an integer in the range of an int.
    ///
    /// @throws NexusCodecException If the input is not a json array.
    ///
    @Override
    public int[] asIntArray(JsonElement input) {
        if (!(input instanceof JsonArray array)) throw Errors.mismatch("a list of integers", input);
        int[] values = new int[array.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = this.asInt(array.get(i));
        }

        return values;
    }

    ///
    /// Encodes the given elements as a json array.
    ///
    @Override
    public JsonElement ofList(List<JsonElement> elements) {
        JsonArray array = new JsonArray(elements.size());
        elements.forEach(array::add);
        return array;
    }

    ///
    /// Decodes the elements of a json array, as a view of the array.
    ///
    /// @throws NexusCodecException If the input is not a json array.
    ///
    @Override
    public List<JsonElement> asList(JsonElement input) {
        if (!(input instanceof JsonArray array)) throw Errors.mismatch("a list", input);
        return array.asList();
    }

    ///
    /// Throws an [UnsupportedOperationException] since json has no element counts.
    ///
    @Override
    @Contract("_, _ -> fail")
    public void writeCount(int count, int limit) {
        throw new UnsupportedOperationException("JSON has no element counts");
    }

    ///
    /// Throws an [UnsupportedOperationException] since json has no element counts.
    ///
    @Override
    @Contract("_ -> fail")
    public int readCount(int limit) {
        throw new UnsupportedOperationException("JSON has no element counts");
    }

    ///
    /// Creates an empty json object.
    ///
    @Override
    public JsonElement emptyObject() {
        return new JsonObject();
    }

    ///
    /// Adds the given value to the given json object under the given key.
    ///
    @Override
    public JsonElement put(JsonElement object, String key, JsonElement value) {
        ((JsonObject) object).add(key, value);
        return object;
    }

    ///
    /// Gets the member of the given json object under the given key.
    ///
    /// @throws NexusCodecException If the object is not a json object.
    ///
    @Override
    public @Nullable JsonElement get(JsonElement object, String key) {
        if (!(object instanceof JsonObject members)) throw Errors.mismatch("an object", object);
        return members.get(key);
    }

    ///
    /// Gets the keys of all members of the given json object.
    ///
    /// @throws NexusCodecException If the object is not a json object.
    ///
    @Override
    public Set<String> keys(JsonElement object) {
        if (!(object instanceof JsonObject members)) throw Errors.mismatch("an object", object);
        return members.keySet();
    }

    ///
    /// @return `true` if the given value is json `null`.
    ///
    @Override
    public boolean isNull(JsonElement value) {
        return value.isJsonNull();
    }
}
