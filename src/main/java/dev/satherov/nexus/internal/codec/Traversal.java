package dev.satherov.nexus.internal.codec;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.BufferFormat;
import dev.satherov.nexus.api.codec.CodecException;
import dev.satherov.nexus.api.codec.CodecFormat;
import dev.satherov.nexus.api.codec.CodecResult;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.StructField;

import net.minecraft.network.FriendlyByteBuf;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

///
/// A codec that every internal codec extends, with the entry points of [NexusCodec] written once.
///
/// A subclass writes [#write(Operations, Object)] and [#read(Operations, Object)] and holds its children as [Traversal].
///
/// @param <T> The type of value this codec encodes and decodes.
/// @param <A> The access a format has to offer to be used with this codec.
///
@ApiStatus.Internal
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Traversal<T, A extends Access.Plain> implements NexusCodec<T, A> {
    
    ///
    /// The name of this codec, used in the failure message.
    ///
    private final String name;
    
    ///
    /// Writes through the operations of the format and puts this codec's name and the format on top of a failure.
    ///
    @Override
    public <V> V encode(CodecFormat<V, ? extends A> format, T value) {
        try {
            return this.write(Operations.of(format), value);
        } catch (CodecException failure) {
            throw Errors.encodeFailure(this.name, format.name(), failure);
        }
    }
    
    ///
    /// Reads through the operations of the format and puts this codec's name and the format on top of a failure.
    ///
    @Override
    public <V> T decode(CodecFormat<V, ? extends A> format, V input) {
        if (format instanceof BufferFormat<?, ?> buffered && buffered.buffer() != input) {
            throw new IllegalArgumentException("Could not decode '" + this.name + "', the input is not the buffer of the format");
        }
        
        try {
            return this.read(Operations.of(format), input);
        } catch (CodecException failure) {
            throw Errors.decodeFailure(this.name, format.name(), failure);
        }
    }
    
    ///
    /// Encodes the value and wraps a failure into a result.
    ///
    @Override
    public <V> CodecResult<V> tryEncode(CodecFormat<V, ? extends A> format, T value) {
        try {
            return new CodecResult.Success<>(this.encode(format, value));
        } catch (CodecException failure) {
            return new CodecResult.Failure<>(failure);
        }
    }
    
    ///
    /// Decodes the input and wraps a failure into a result.
    ///
    @Override
    public <V> CodecResult<T> tryDecode(CodecFormat<V, ? extends A> format, V input) {
        try {
            return new CodecResult.Success<>(this.decode(format, input));
        } catch (CodecException failure) {
            return new CodecResult.Failure<>(failure);
        }
    }
    
    ///
    /// Decodes the buffer of the format.
    ///
    @Override
    public T decode(BufferFormat<?, ? extends A> format) {
        return this.decodeBuffer(format);
    }
    
    ///
    /// Decodes the buffer the given format was built on, with the type of the buffer captured.
    ///
    private <B extends FriendlyByteBuf> T decodeBuffer(BufferFormat<B, ? extends A> format) {
        return this.decode(format, format.buffer());
    }
    
    ///
    /// Decodes the buffer of the format and wraps a failure into a result.
    ///
    @Override
    public CodecResult<T> tryDecode(BufferFormat<?, ? extends A> format) {
        try {
            return new CodecResult.Success<>(this.decode(format));
        } catch (CodecException failure) {
            return new CodecResult.Failure<>(failure);
        }
    }
    
    ///
    /// Creates a field with this codec and no fallback, which fails to decode if its key is missing.
    ///
    @Override
    public <Z> StructField<Z, T, A> field(String name, Function<Z, T> getter) {
        return new Structs.BoundField<>(this, name, getter, null);
    }
    
    ///
    /// Creates a field with this codec and the given fallback.
    ///
    @Override
    public <Z> StructField<Z, T, A> optionalField(String name, T fallback, Function<Z, T> getter) {
        return new Structs.BoundField<>(this, name, getter, fallback);
    }
    
    ///
    /// Creates a field with the optional codec over this codec and empty as the fallback.
    ///
    @Override
    public <Z> StructField<Z, Optional<T>, A> optionalField(String name, Function<Z, Optional<T>> getter) {
        return new Structs.BoundField<>(Structs.optional(this), name, getter, Optional.empty());
    }
    
    ///
    /// Creates the codec of a list of at most the given number of values of this codec.
    ///
    @Override
    public NexusCodec<List<T>, A> list(int limit) {
        return CollectionCodecs.list(this, limit);
    }
    
    ///
    /// Creates the codec of a list of at most 32767 values of this codec.
    ///
    @Override
    public NexusCodec<List<T>, A> list() {
        return CollectionCodecs.list(this, CollectionCodecs.LIMIT);
    }
    
    ///
    /// Creates the codec of a set of at most the given number of values of this codec.
    ///
    @Override
    public NexusCodec<Set<T>, A> set(int limit) {
        return CollectionCodecs.set(this, limit);
    }
    
    ///
    /// Creates the codec of a set of at most 32767 values of this codec.
    ///
    @Override
    public NexusCodec<Set<T>, A> set() {
        return CollectionCodecs.set(this, CollectionCodecs.LIMIT);
    }
    
    ///
    /// Writes the given value through the given operations.
    ///
    /// @param operations The operations of the format to write in.
    /// @param value      The value to write.
    ///
    /// @return The written value, or the buffer on the network.
    ///
    /// @throws CodecException If the value could not be written, with every error at its path below this codec.
    ///
    protected abstract <V> V write(Operations<V> operations, T value);
    
    ///
    /// Reads a value from the given input through the given operations.
    ///
    /// @param operations The operations of the format to read from.
    /// @param input      The input to read, which is the buffer on the network.
    ///
    /// @return The value read.
    ///
    /// @throws CodecException If the input could not be read, with every error at its path below this codec.
    ///
    protected abstract <V> T read(Operations<V> operations, V input);
}
