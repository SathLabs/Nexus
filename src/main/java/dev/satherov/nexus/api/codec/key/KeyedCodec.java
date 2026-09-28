package dev.satherov.nexus.api.codec.key;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.mojang.serialization.Codec;

import org.jetbrains.annotations.ApiStatus;

///
/// A codec with the key its value is stored under, and the fallback it reads if the key is missing.
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
    @ApiStatus.Internal
    public KeyedCodec(NexusCodec<T, A> codec, String key, T fallback) {
        this.codec = codec;
        this.key = key;
        this.fallback = fallback;
        
        this.dfu = codec.asDfu();
    }
    
    ///
    /// Reads the value of the key from the given value input or returns the fallback if the key is missing.
    ///
    /// If the value could not be decoded, the failure will be reported to the problem reporter of the input and return the fallback.
    ///
    /// If this codec requires registries to decode the value, then the ValueInput must provide them or the value will fail to decode.
    ///
    /// @param input The input to read from.
    ///
    /// @return The value under the key, or the fallback.
    ///
    /// @see TagValueInput#create(ProblemReporter, HolderLookup.Provider, CompoundTag)
    ///
    public T read(ValueInput input) {
        return input.read(this.key, this.dfu).orElse(this.fallback);
    }
    
    ///
    /// Stores the value of the key in the given value output.
    ///
    /// If the value could not be encoded, the failure will be reported to the problem reporter of the output.
    ///
    /// If this codec requires registries to encode the value, then the ValueOutput must provide them or the value will fail to encode.
    ///
    /// @param output The output to store the value in.
    /// @param value  The value to store.
    ///
    /// @see TagValueOutput#createWithContext(ProblemReporter, HolderLookup.Provider)
    ///
    public void write(ValueOutput output, T value) {
        output.store(this.key, this.dfu, value);
    }
    
    ///
    /// Reads the value with the given key from the tag using the given format or return the fallback if the key is missing.
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
    /// Writes the value with the given key to the tag using the given format.
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
