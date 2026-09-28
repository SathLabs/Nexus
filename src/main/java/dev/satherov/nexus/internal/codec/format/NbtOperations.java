package dev.satherov.nexus.internal.codec.format;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.internal.codec.CodecErrors;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;

import com.mojang.serialization.DynamicOps;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.AbstractList;
import java.util.List;
import java.util.Set;

///
/// The NBT format over [Tag], which encodes and decodes values the way [NbtOps] does.
///
/// @param isStrict If decoding should refuse unknown keys.
/// @param lookup   The registries of the format, or `null` if the format is plain.
/// @param <A>      The access of the format.
///
@ApiStatus.Internal
public record NbtOperations<A extends Access.Plain>(
        boolean isStrict,
        RegistryOps.@Nullable RegistryInfoLookup lookup
) implements Operations<Tag>, CodecFormat<Tag, A> {
    
    ///
    /// Will always return `NBT`.
    ///
    @Override
    public String name() {
        return "NBT";
    }
    
    ///
    /// Creates a copy of this format that refuses unknown keys, with the same registries.
    ///
    @Override
    public CodecFormat<Tag, A> strict() {
        return new NbtOperations<>(true, this.lookup);
    }
    
    ///
    /// Creates a copy of this format with the given registries in vanilla's [RegistryOps.HolderLookupAdapter].
    ///
    @Override
    public CodecFormat<Tag, Access.Registries> withRegistries(HolderLookup.Provider registries) {
        return new NbtOperations<>(this.isStrict, new RegistryOps.HolderLookupAdapter(registries));
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
    /// Will always return [NbtOps#INSTANCE].
    ///
    @Override
    public DynamicOps<Tag> dynamicOps() {
        return NbtOps.INSTANCE;
    }
    
    ///
    /// Encodes the given `boolean` as a `byte` tag of `1` or `0`.
    ///
    @Override
    public Tag ofBoolean(boolean value) {
        return ByteTag.valueOf(value);
    }
    
    ///
    /// Decodes any numeric tag as a `boolean`, which is `true` if its value cast to a `byte` isn't `0`.
    ///
    @Override
    public boolean asBoolean(Tag input) {
        return this.asNumeric(input, "a boolean").box().byteValue() != 0;
    }
    
    ///
    /// Encodes the given `byte` as a `byte` tag.
    ///
    @Override
    public Tag ofByte(byte value) {
        return ByteTag.valueOf(value);
    }
    
    ///
    /// Decodes a numeric tag that holds an integer in the range of a `byte`.
    ///
    @Override
    public byte asByte(Tag input) {
        return (byte) this.asIntegral(input, Byte.MIN_VALUE, Byte.MAX_VALUE);
    }
    
    ///
    /// Encodes the given `short` as a `short` tag.
    ///
    @Override
    public Tag ofShort(short value) {
        return ShortTag.valueOf(value);
    }
    
    ///
    /// Decodes a numeric tag that holds an integer in the range of a `short`.
    ///
    @Override
    public short asShort(Tag input) {
        return (short) this.asIntegral(input, Short.MIN_VALUE, Short.MAX_VALUE);
    }
    
    ///
    /// Encodes the given `int` as an `int` tag.
    ///
    @Override
    public Tag ofInt(int value) {
        return IntTag.valueOf(value);
    }
    
    ///
    /// Decodes a numeric tag that holds an integer in the range of an `int`.
    ///
    @Override
    public int asInt(Tag input) {
        return (int) this.asIntegral(input, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
    
    ///
    /// Encodes the given `int` as an `int` tag, the same as [#ofInt(int)].
    ///
    @Override
    public Tag ofVarInt(int value) {
        return this.ofInt(value);
    }
    
    ///
    /// Decodes an `int` the same as [#asInt(Tag)].
    ///
    @Override
    public int asVarInt(Tag input) {
        return this.asInt(input);
    }
    
    ///
    /// Encodes the given `long` as a `long` tag.
    ///
    @Override
    public Tag ofLong(long value) {
        return LongTag.valueOf(value);
    }
    
    ///
    /// Decodes a numeric tag that holds an integer in the range of a `long`.
    ///
    @Override
    public long asLong(Tag input) {
        return this.asIntegral(input, Long.MIN_VALUE, Long.MAX_VALUE);
    }
    
    ///
    /// Encodes the given `long` as a `long` tag, the same as [#ofLong(long)].
    ///
    @Override
    public Tag ofVarLong(long value) {
        return this.ofLong(value);
    }
    
    ///
    /// Decodes a `long` the same as [#asLong(Tag)].
    ///
    @Override
    public long asVarLong(Tag input) {
        return this.asLong(input);
    }
    
    ///
    /// Decodes a numeric tag that holds an integer within the given range.
    ///
    private long asIntegral(Tag input, long min, long max) {
        NumericTag numeric = this.asNumeric(input, "an integer");
        if (numeric instanceof FloatTag || numeric instanceof DoubleTag) {
            double exact = numeric.doubleValue();
            if (exact != Math.rint(exact)) {
                throw CodecErrors.mismatch("an integer", input);
            }
            // 2^63 is out of range for a long value.
            if (exact >= 0x1p63 || exact < -0x1p63) {
                throw CodecErrors.outOfRange(min, max, input);
            }
        }
        
        long value = numeric.longValue();
        if (value < min || value > max) {
            throw CodecErrors.outOfRange(min, max, input);
        }
        
        return value;
    }
    
    ///
    /// Encodes the given `float` as a `float` tag.
    ///
    @Override
    public Tag ofFloat(float value) {
        return FloatTag.valueOf(value);
    }
    
    ///
    /// Decodes any numeric tag as a `float`.
    ///
    @Override
    public float asFloat(Tag input) {
        return this.asNumeric(input, "a number").floatValue();
    }
    
    ///
    /// Encodes the given `double` as a `double` tag.
    ///
    @Override
    public Tag ofDouble(double value) {
        return DoubleTag.valueOf(value);
    }
    
    ///
    /// Decodes any numeric tag as a `double`.
    ///
    @Override
    public double asDouble(Tag input) {
        return this.asNumeric(input, "a number").doubleValue();
    }
    
    ///
    /// Decodes a numeric tag, with the given expected kind in the failure message.
    ///
    private NumericTag asNumeric(Tag input, String expected) {
        if (!(input instanceof NumericTag numeric)) {
            throw CodecErrors.mismatch(expected, input);
        }
        
        return numeric;
    }
    
    ///
    /// Encodes the given string as a string tag.
    ///
    /// @throws NexusCodecException If the string has more than `limit` characters.
    ///
    @Override
    public Tag ofString(String value, int limit) {
        if (value.length() > limit) {
            throw CodecErrors.tooLong(limit, value.length());
        }
        
        return StringTag.valueOf(value);
    }
    
    ///
    /// Decodes a string tag.
    ///
    /// @throws NexusCodecException If the input is not a string tag, or if it has more than `limit` characters.
    ///
    @Override
    public String asString(Tag input, int limit) {
        if (!(input instanceof StringTag(String value))) throw CodecErrors.mismatch("a string", input);
        if (value.length() > limit) {
            throw CodecErrors.tooLong(limit, value.length());
        }
        
        return value;
    }
    
    ///
    /// Encodes a copy of the given ints as an `int` array tag.
    ///
    @Override
    public Tag ofIntArray(int[] value) {
        return new IntArrayTag(value.clone());
    }
    
    ///
    /// Decodes a copy of the ints of an `int` array tag, or the elements of any other collection tag as ints.
    ///
    /// @throws NexusCodecException If the input is not a collection tag.
    ///
    @Override
    public int[] asIntArray(Tag input) {
        if (input instanceof IntArrayTag array) {
            return array.getAsIntArray().clone();
        }
        
        if (!(input instanceof CollectionTag collection)) {
            throw CodecErrors.mismatch("a list of integers", input);
        }
        
        int[] values = new int[collection.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = this.asInt(collection.get(i));
        }
        
        return values;
    }
    
    ///
    /// Encodes the given elements as a list tag, which may mix element types.
    ///
    @Override
    public Tag ofList(List<Tag> elements) {
        ListTag list = new ListTag(elements.size());
        list.addAll(elements);
        return list;
    }
    
    ///
    /// Decodes the elements of any collection tag, which are the list tag itself or a view of the elements of an array tag.
    ///
    /// The view creates the tag of an element each time it is read.
    ///
    /// @throws NexusCodecException If the input is not a collection tag.
    ///
    @Override
    public List<Tag> asList(Tag input) {
        return switch (input) {
            case ListTag list -> list;
            case CollectionTag collection -> new AbstractList<>() {
                
                ///
                /// Creates the tag of the element at the given index of the array tag.
                ///
                @Override
                public Tag get(int index) {
                    return collection.get(index);
                }
                
                ///
                /// The number of elements of the array tag.
                ///
                @Override
                public int size() {
                    return collection.size();
                }
            };
            default -> throw CodecErrors.mismatch("a list", input);
        };
    }
    
    ///
    /// Throws an [UnsupportedOperationException] since NBT has no element counts.
    ///
    @Override
    @Contract("_, _ -> fail")
    public void writeCount(int count, int limit) {
        throw new UnsupportedOperationException("NBT has no element counts");
    }
    
    ///
    /// Throws an [UnsupportedOperationException] since NBT has no element counts.
    ///
    @Override
    @Contract("_ -> fail")
    public int readCount(int limit) {
        throw new UnsupportedOperationException("NBT has no element counts");
    }
    
    ///
    /// Creates an empty compound tag.
    ///
    @Override
    public Tag emptyObject() {
        return new CompoundTag();
    }
    
    ///
    /// Puts the given value into the given compound tag under the given key.
    ///
    @Override
    public Tag put(Tag object, String key, Tag value) {
        if (!(object instanceof CompoundTag compound)) {
            throw CodecErrors.mismatch("an object", object);
        }
        
        compound.put(key, value);
        return object;
    }
    
    ///
    /// Gets the tag under the given key of the given compound tag.
    ///
    /// @throws NexusCodecException If the object is not a compound tag.
    ///
    @Override
    public @Nullable Tag get(Tag object, String key) {
        if (!(object instanceof CompoundTag compound)) {
            throw CodecErrors.mismatch("an object", object);
        }
        
        return compound.get(key);
    }
    
    ///
    /// Gets all keys of the given compound tag.
    ///
    /// @throws NexusCodecException If the object is not a compound tag.
    ///
    @Override
    public Set<String> keys(Tag object) {
        if (!(object instanceof CompoundTag compound)) {
            throw CodecErrors.mismatch("an object", object);
        }
        
        return compound.keySet();
    }
    
    ///
    /// @return `true` if the given value is the end tag, which is NBT's `null`.
    ///
    @Override
    public boolean isNull(Tag value) {
        return value instanceof EndTag;
    }
    
    ///
    /// @return The end tag, which is NBT's `null`.
    ///
    @Override
    public Tag ofNull() {
        return EndTag.INSTANCE;
    }
}
