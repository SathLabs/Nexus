package dev.satherov.nexus.internal.codec.format;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.format.BufferFormat;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.internal.codec.CodecErrors;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.VarLong;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.RegistryOps;

import com.mojang.serialization.DynamicOps;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import io.netty.buffer.ByteBufUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

///
/// The netty format over a buffer, which encodes and decodes values in the layout of [ByteBufCodecs].
///
/// Every read consumes from the buffer handed to it, which is the buffer of this format.
/// A buffer that ends before a value does, or that has no room left for one, throws a [NexusCodecException].
///
/// @param buffer         The buffer this format reads from and appends to.
/// @param registryAccess The registry access of the buffer, or `null` if the format is plain.
/// @param <B>            The type of the buffer.
/// @param <A>            The access of the format.
///
@ApiStatus.Internal
public record NettyOperations<B extends FriendlyByteBuf, A extends Access.Plain>(
        B buffer,
        @Nullable RegistryAccess registryAccess
) implements Operations<B>, BufferFormat<B, A> {
    
    ///
    /// The maximum number of bytes of a VarLong, the same as vanilla's private constant.
    ///
    private static final int MAX_VARLONG_SIZE = 10;
    
    ///
    /// Will always return `netty`.
    ///
    @Override
    public String name() {
        return "netty";
    }
    
    ///
    /// Will always return this format.
    ///
    @Override
    public CodecFormat<B, A> strict() {
        return this;
    }
    
    ///
    /// Throws an [UnsupportedOperationException] since a netty format takes its registries from its buffer.
    ///
    @Override
    @Contract("_ -> fail")
    public CodecFormat<B, Access.Registries> withRegistries(HolderLookup.Provider registries) {
        throw new UnsupportedOperationException("Could not add registries to a netty format");
    }
    
    ///
    /// Will always return `true`.
    ///
    @Override
    public boolean isPositional() {
        return true;
    }
    
    ///
    /// Will always return `false`.
    ///
    @Override
    public boolean isStrict() {
        return false;
    }
    
    ///
    /// Creates a new [RegistryOps.HolderLookupAdapter] over the registry access or returns `null` if the format is plain.
    ///
    @Override
    public RegistryOps.@Nullable RegistryInfoLookup lookup() {
        if (this.registryAccess == null) {
            return null;
        }
        
        return new RegistryOps.HolderLookupAdapter(this.registryAccess);
    }
    
    ///
    /// Throws an [UnsupportedOperationException] since the netty format has no DFU ops.
    ///
    @Override
    @Contract("-> fail")
    public DynamicOps<B> dynamicOps() {
        throw new UnsupportedOperationException("Netty has no DFU ops");
    }
    
    ///
    /// Appends the given `boolean` as one `byte`.
    ///
    @Override
    public B ofBoolean(boolean value) {
        this.requireWritable(Byte.BYTES, "a `boolean`").writeBoolean(value);
        return this.buffer;
    }
    
    ///
    /// Reads a boolean from one `byte`, which is `true` if the `byte` isn't `0`.
    ///
    @Override
    public boolean asBoolean(B input) {
        return this.requireReadable(input, Byte.BYTES, "a boolean").readBoolean();
    }
    
    ///
    /// Appends the given `byte`.
    ///
    @Override
    public B ofByte(byte value) {
        this.requireWritable(Byte.BYTES, "a byte").writeByte(value);
        return this.buffer;
    }
    
    ///
    /// Reads a `byte`.
    ///
    @Override
    public byte asByte(B input) {
        return this.requireReadable(input, Byte.BYTES, "a byte").readByte();
    }
    
    ///
    /// Appends the given `short` as two big-endian bytes.
    ///
    @Override
    public B ofShort(short value) {
        this.requireWritable(Short.BYTES, "a short").writeShort(value);
        return this.buffer;
    }
    
    ///
    /// Reads a `short` from two big-endian bytes.
    ///
    @Override
    public short asShort(B input) {
        return this.requireReadable(input, Short.BYTES, "a short").readShort();
    }
    
    ///
    /// Appends the given `int` as four big-endian bytes.
    ///
    @Override
    public B ofInt(int value) {
        this.requireWritable(Integer.BYTES, "an int").writeInt(value);
        return this.buffer;
    }
    
    ///
    /// Reads an `int` from four big-endian bytes.
    ///
    @Override
    public int asInt(B input) {
        return this.requireReadable(input, Integer.BYTES, "an int").readInt();
    }
    
    ///
    /// Appends the given `int` as a VarInt.
    ///
    @Override
    public B ofVarInt(int value) {
        this.requireWritable(VarInt.getByteSize(value), "a VarInt").writeVarInt(value);
        return this.buffer;
    }
    
    ///
    /// Reads a VarInt of at most five bytes.
    ///
    /// @throws NexusCodecException If the VarInt runs past its fifth `byte`.
    ///
    @Override
    public int asVarInt(B input) {
        int value = 0;
        for (int size = 0; size < VarInt.MAX_VARINT_SIZE; size++) {
            byte next = this.requireReadable(input, Byte.BYTES, "a VarInt").readByte();
            value |= (next & 0x7F) << (size * 7);
            if (!VarInt.hasContinuationBit(next)) {
                return value;
            }
        }
        
        throw new NexusCodecException("expected a VarInt, found more than " + VarInt.MAX_VARINT_SIZE + " bytes");
    }
    
    ///
    /// Appends the given `long` as eight big-endian bytes.
    ///
    @Override
    public B ofLong(long value) {
        this.requireWritable(Long.BYTES, "a long").writeLong(value);
        return this.buffer;
    }
    
    ///
    /// Reads a `long` from eight big-endian bytes.
    ///
    @Override
    public long asLong(B input) {
        return this.requireReadable(input, Long.BYTES, "a long").readLong();
    }
    
    ///
    /// Appends the given `long` as a VarLong.
    ///
    @Override
    public B ofVarLong(long value) {
        this.requireWritable(VarLong.getByteSize(value), "a VarLong").writeVarLong(value);
        return this.buffer;
    }
    
    ///
    /// Reads a VarLong of at most ten bytes.
    ///
    /// @throws NexusCodecException If the VarLong runs past its tenth `byte`.
    ///
    @Override
    public long asVarLong(B input) {
        long value = 0L;
        for (int size = 0; size < NettyOperations.MAX_VARLONG_SIZE; size++) {
            byte next = this.requireReadable(input, Byte.BYTES, "a VarLong").readByte();
            value |= (long) (next & 0x7F) << (size * 7);
            if (!VarLong.hasContinuationBit(next)) {
                return value;
            }
        }
        
        throw new NexusCodecException("expected a VarLong, found more than " + NettyOperations.MAX_VARLONG_SIZE + " bytes");
    }
    
    ///
    /// Appends the given `float` as four big-endian bytes.
    ///
    @Override
    public B ofFloat(float value) {
        this.requireWritable(Float.BYTES, "a float").writeFloat(value);
        return this.buffer;
    }
    
    ///
    /// Reads a `float` from four big-endian bytes.
    ///
    @Override
    public float asFloat(B input) {
        return this.requireReadable(input, Float.BYTES, "a float").readFloat();
    }
    
    ///
    /// Appends the given `double` as eight big-endian bytes.
    ///
    @Override
    public B ofDouble(double value) {
        this.requireWritable(Double.BYTES, "a double").writeDouble(value);
        return this.buffer;
    }
    
    ///
    /// Reads a `double` from eight big-endian bytes.
    ///
    @Override
    public double asDouble(B input) {
        return this.requireReadable(input, Double.BYTES, "a double").readDouble();
    }
    
    ///
    /// Appends the number of UTF-8 bytes of the given string as a VarInt, and then the bytes themselves.
    ///
    /// @throws NexusCodecException If the string has more than `limit` characters.
    ///
    @Override
    public B ofString(String value, int limit) {
        if (value.length() > limit) {
            throw CodecErrors.tooLong(limit, value.length());
        }
        
        int length = ByteBufUtil.utf8Bytes(value);
        this.requireWritable(VarInt.getByteSize(length) + length, "a string").writeVarInt(length);
        this.buffer.writeCharSequence(value, StandardCharsets.UTF_8);
        return this.buffer;
    }
    
    ///
    /// Checks that the buffer has room for the given number of bytes, with the expected kind in the failure message.
    ///
    private B requireWritable(int bytes, String expected) {
        if (this.buffer.maxWritableBytes() < bytes) {
            throw new NexusCodecException("expected room for " + expected + ", found a full buffer");
        }
        
        return this.buffer;
    }
    
    ///
    /// Reads a string written by [#ofString(String, int)].
    ///
    /// @throws NexusCodecException If the string has more than `limit` characters.
    ///
    @Override
    public String asString(B input, int limit) {
        // A character takes at most three bytes, and the product could overflow an `int`.
        int length = this.readCount(input, (int) Math.min(3L * limit, Integer.MAX_VALUE));
        String value = this.requireReadable(input, length, "a string").readCharSequence(length, StandardCharsets.UTF_8).toString();
        if (value.length() > limit) {
            throw CodecErrors.tooLong(limit, value.length());
        }
        
        return value;
    }
    
    ///
    /// Checks that the given buffer has the given number of bytes left, with the expected kind in the failure message.
    ///
    private B requireReadable(B input, int bytes, String expected) {
        if (input.readableBytes() < bytes) {
            throw new NexusCodecException("expected " + expected + ", found the end of the buffer");
        }
        
        return input;
    }
    
    ///
    /// Appends the number of the given ints as a VarInt, and then each `int` as a VarInt.
    ///
    @Override
    public B ofIntArray(int[] value) {
        this.ofVarInt(value.length);
        for (int element : value) {
            this.ofVarInt(element);
        }
        
        return this.buffer;
    }
    
    ///
    /// Reads an `int` array written by [#ofIntArray(int[])], refusing more ints than there are readable bytes.
    ///
    @Override
    public int[] asIntArray(B input) {
        int[] values = new int[this.readCount(input, input.readableBytes())];
        for (int i = 0; i < values.length; i++) {
            values[i] = this.asVarInt(input);
        }
        
        return values;
    }
    
    ///
    /// Throws an [UnsupportedOperationException] since netty has no lists.
    ///
    @Override
    @Contract("_ -> fail")
    public B ofList(List<B> elements) {
        throw new UnsupportedOperationException("Netty has no lists");
    }
    
    ///
    /// Throws an [UnsupportedOperationException] since netty has no lists.
    ///
    @Override
    @Contract("_ -> fail")
    public List<B> asList(B input) {
        throw new UnsupportedOperationException("Netty has no lists");
    }
    
    ///
    /// Appends the given number of elements as a VarInt.
    ///
    /// @throws NexusCodecException If the count is above the limit.
    ///
    @Override
    public void writeCount(int count, int limit) {
        if (count > limit) {
            throw CodecErrors.outOfRange(0, limit, count);
        }
        
        this.ofVarInt(count);
    }
    
    ///
    /// Reads a number of elements from the buffer of this format.
    ///
    @Override
    public int readCount(int limit) {
        return this.readCount(this.buffer, limit);
    }
    
    ///
    /// Reads a VarInt count from the given buffer, refusing it if it's negative or above the given limit.
    ///
    private int readCount(B input, int limit) {
        int count = this.asVarInt(input);
        if (count < 0 || count > limit) {
            throw CodecErrors.outOfRange(0, limit, count);
        }
        
        return count;
    }
    
    ///
    /// Appends nothing and returns the buffer, which is an empty object on netty.
    ///
    @Override
    public B emptyObject() {
        return this.buffer;
    }
    
    ///
    /// Throws an [UnsupportedOperationException] since netty has no keys.
    ///
    @Override
    @Contract("_, _, _ -> fail")
    public B put(B object, String key, B value) {
        throw new UnsupportedOperationException("Netty has no keys");
    }
    
    ///
    /// Throws an [UnsupportedOperationException] since netty has no keys.
    ///
    @Override
    @Contract("_, _ -> fail")
    public @Nullable B get(B object, String key) {
        throw new UnsupportedOperationException("Netty has no keys");
    }
    
    ///
    /// Throws an [UnsupportedOperationException] since netty has no keys.
    ///
    @Override
    @Contract("_ -> fail")
    public Set<String> keys(B object) {
        throw new UnsupportedOperationException("Netty has no keys");
    }
    
    ///
    /// Throws an [UnsupportedOperationException] since netty has no null value.
    ///
    @Override
    @Contract("_ -> fail")
    public boolean isNull(B value) {
        throw new UnsupportedOperationException("Netty has no null value");
    }
    
    ///
    /// Throws an [UnsupportedOperationException] since netty has no null value.
    ///
    @Override
    @Contract("-> fail")
    public B ofNull() {
        throw new UnsupportedOperationException("Netty has no null value");
    }
}
