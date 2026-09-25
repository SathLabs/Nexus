package dev.satherov.nexus.api.codec;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.mojang.serialization.Codec;

///
/// A codec with the key its value is stored under and the fallback it reads if the key is missing.
///
/// @param <T> The type of value this codec reads and writes.
/// @param <A> The access a format has to offer to be used with this codec.
///
/// @see NexusCodec#keyed(String, Object)
///
public final class KeyedCodec<T, A extends Access.Plain> {
    
    ///
    /// The codec of the value.
    ///
    private final NexusCodec<T, A> codec;
    
    ///
    /// The key the value is stored under.
    ///
    private final String key;
    
    ///
    /// The value to read if the key is missing.
    ///
    private final T fallback;
    
    ///
    /// The DFU codec of the value.
    ///
    private final Codec<T> dfu;
    
    ///
    /// Creates a keyed codec of the value of the given codec under the given key, with the given fallback if the key is missing.
    ///
    /// @param codec    The codec of the value.
    /// @param key      The key the value is stored under.
    /// @param fallback The value to read if the key is missing.
    ///
    KeyedCodec(NexusCodec<T, A> codec, String key, T fallback) {
        this.codec = codec;
        this.key = key;
        this.fallback = fallback;
        
        this.dfu = codec.asDfu();
    }
    
    ///
    /// Reads the value under the key from the given input, or the fallback if the key is missing.
    ///
    /// If the value could not be decoded, this will report the failure to the problem reporter of the input and return the fallback.
    ///
    /// If this codec needs registries, the input should have them, such as one from [TagValueInput#create(ProblemReporter, HolderLookup.Provider, CompoundTag)].
    /// Otherwise the value will fail to decode.
    ///
    /// @param input The input to read from.
    ///
    /// @return The value under the key, or the fallback.
    ///
    public T read(ValueInput input) {
        return input.read(this.key, this.dfu).orElse(this.fallback);
    }
    
    ///
    /// Stores the given value under the key of the given output.
    ///
    /// If the value could not be encoded, this will report the failure to the problem reporter of the output and store nothing.
    ///
    /// If this codec needs registries, the output should have them, such as one from [TagValueOutput#createWithContext(ProblemReporter, HolderLookup.Provider)].
    /// Otherwise the value will fail to encode.
    ///
    /// @param output The output to store the value in.
    /// @param value  The value to store.
    ///
    public void write(ValueOutput output, T value) {
        output.store(this.key, this.dfu, value);
    }
    
    ///
    /// Reads the value under the key of the given tag in the given format, or the fallback if the key is missing.
    ///
    /// @param format The format the value is in.
    /// @param tag    The tag to read from.
    ///
    /// @return The value under the key, or the fallback.
    ///
    /// @throws NexusCodecException If the value under the key could not be decoded, with every error that occurred during decoding.
    ///
    public T read(CodecFormat<Tag, ? extends A> format, CompoundTag tag) {
        Tag value = tag.get(this.key);
        if (value == null) {
            return this.fallback;
        }
        
        return this.codec.decode(format, value);
    }
    
    ///
    /// Encodes the given value in the given format and then puts it under the key of the given tag.
    ///
    /// If the value could not be encoded, the tag will be left unchanged.
    ///
    /// @param format The format to encode the value in.
    /// @param tag    The tag to put the value into.
    /// @param value  The value to encode.
    ///
    /// @throws NexusCodecException If the value could not be encoded, with every error that occurred during encoding.
    ///
    public void write(CodecFormat<Tag, ? extends A> format, CompoundTag tag, T value) {
        tag.put(this.key, this.codec.encode(format, value));
    }
}
