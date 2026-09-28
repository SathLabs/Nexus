package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.BufferFormat;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.CodecResult;
import dev.satherov.nexus.api.codec.result.NexusCodecException;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

///
/// Checks the form every scalar codec reads and writes in JSON, NBT, and on the network, its refusals, and the entry points of a codec.
///
public class ScalarsTest {
    
    private static final UUID SAMPLE_UUID = new UUID(0x0000000100000002L, 0x0000000300000004L);
    
    @Test
    public void boolUsesVanillaForm() {
        ScalarsTest.assertJson(NexusCodec.BOOL, true, "true");
        ScalarsTest.assertJson(NexusCodec.BOOL, false, "false");
        ScalarsTest.assertNbt(NexusCodec.BOOL, true, ByteTag.valueOf((byte) 1));
        ScalarsTest.assertNbt(NexusCodec.BOOL, false, ByteTag.valueOf((byte) 0));
        ScalarsTest.assertNetty(NexusCodec.BOOL, true, 0x01);
        ScalarsTest.assertNetty(NexusCodec.BOOL, false, 0x00);
    }
    
    @Test
    public void boolReadsAnyNumericTagByItsLowestByte() {
        Assertions.assertThat(NexusCodec.BOOL.decode(CodecFormat.NBT, IntTag.valueOf(2))).isTrue();
        Assertions.assertThat(NexusCodec.BOOL.decode(CodecFormat.NBT, IntTag.valueOf(256))).isFalse();
    }
    
    @Test
    public void boolRefusesJsonNumber() {
        Assertions.assertThatThrownBy(() -> NexusCodec.BOOL.decode(CodecFormat.JSON, JsonParser.parseString("1")))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContaining("found 1");
    }
    
    @Test
    public void byteUsesVanillaForm() {
        ScalarsTest.assertJson(NexusCodec.BYTE, (byte) -5, "-5");
        ScalarsTest.assertNbt(NexusCodec.BYTE, (byte) -5, ByteTag.valueOf((byte) -5));
        ScalarsTest.assertNetty(NexusCodec.BYTE, (byte) -5, 0xFB);
    }
    
    @Test
    public void byteRefusesNumberOutOfRange() {
        Assertions.assertThatThrownBy(() -> NexusCodec.BYTE.decode(CodecFormat.JSON, JsonParser.parseString("300")))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContainingAll("-128", "127", "300");
    }
    
    @Test
    public void shortUsesVanillaForm() {
        ScalarsTest.assertJson(NexusCodec.SHORT, (short) 1234, "1234");
        ScalarsTest.assertNbt(NexusCodec.SHORT, (short) 1234, ShortTag.valueOf((short) 1234));
        ScalarsTest.assertNetty(NexusCodec.SHORT, (short) 1234, 0x04, 0xD2);
    }
    
    @Test
    public void shortRefusesNumberOutOfRange() {
        Assertions.assertThatThrownBy(() -> NexusCodec.SHORT.decode(CodecFormat.NBT, IntTag.valueOf(40000)))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContainingAll("-32768", "32767", "40000");
    }
    
    @Test
    public void intUsesVanillaForm() {
        ScalarsTest.assertJson(NexusCodec.INT, 300, "300");
        ScalarsTest.assertNbt(NexusCodec.INT, 300, IntTag.valueOf(300));
        ScalarsTest.assertNetty(NexusCodec.INT, 300, 0x00, 0x00, 0x01, 0x2C);
    }
    
    @Test
    public void intRefusesNonIntegralNumber() {
        Assertions.assertThatThrownBy(() -> NexusCodec.INT.decode(CodecFormat.JSON, JsonParser.parseString("2.5")))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContaining("2.5");
    }
    
    @Test
    public void intRefusesNumberOutOfRange() {
        Assertions.assertThatThrownBy(() -> NexusCodec.INT.decode(CodecFormat.NBT, LongTag.valueOf(1L << 40)))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContainingAll("-2147483648", "2147483647", "1099511627776");
    }
    
    @Test
    public void intRefusesString() {
        Assertions.assertThatThrownBy(() -> NexusCodec.INT.decode(CodecFormat.JSON, JsonParser.parseString("\"5\"")))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContaining("\"5\"");
        Assertions.assertThatThrownBy(() -> NexusCodec.INT.decode(CodecFormat.NBT, StringTag.valueOf("5")))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContaining("\"5\"");
    }
    
    @Test
    public void varIntIsIntExceptOnNetty() {
        ScalarsTest.assertJson(NexusCodec.VAR_INT, 300, "300");
        ScalarsTest.assertNbt(NexusCodec.VAR_INT, 300, IntTag.valueOf(300));
        ScalarsTest.assertNetty(NexusCodec.VAR_INT, 300, 0xAC, 0x02);
        ScalarsTest.assertNetty(NexusCodec.VAR_INT, -1, 0xFF, 0xFF, 0xFF, 0xFF, 0x0F);
    }
    
    @Test
    public void longUsesVanillaForm() {
        ScalarsTest.assertJson(NexusCodec.LONG, 1L << 40, "1099511627776");
        ScalarsTest.assertNbt(NexusCodec.LONG, 1L << 40, LongTag.valueOf(1L << 40));
        ScalarsTest.assertNetty(NexusCodec.LONG, 1L << 40, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00);
    }
    
    @Test
    public void longRefusesNonIntegralNumber() {
        Assertions.assertThatThrownBy(() -> NexusCodec.LONG.decode(CodecFormat.NBT, DoubleTag.valueOf(1.25D)))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContaining("1.25");
    }
    
    @Test
    public void varLongIsLongExceptOnNetty() {
        ScalarsTest.assertJson(NexusCodec.VAR_LONG, 300L, "300");
        ScalarsTest.assertNbt(NexusCodec.VAR_LONG, 300L, LongTag.valueOf(300L));
        ScalarsTest.assertNetty(NexusCodec.VAR_LONG, 300L, 0xAC, 0x02);
        ScalarsTest.assertNetty(NexusCodec.VAR_LONG, -1L, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x01);
    }
    
    @Test
    public void floatUsesVanillaForm() {
        ScalarsTest.assertJson(NexusCodec.FLOAT, 1.5F, "1.5");
        ScalarsTest.assertNbt(NexusCodec.FLOAT, 1.5F, FloatTag.valueOf(1.5F));
        ScalarsTest.assertNetty(NexusCodec.FLOAT, 1.5F, 0x3F, 0xC0, 0x00, 0x00);
    }
    
    @Test
    public void doubleUsesVanillaForm() {
        ScalarsTest.assertJson(NexusCodec.DOUBLE, 1.5D, "1.5");
        ScalarsTest.assertNbt(NexusCodec.DOUBLE, 1.5D, DoubleTag.valueOf(1.5D));
        ScalarsTest.assertNetty(NexusCodec.DOUBLE, 1.5D, 0x3F, 0xF8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
    }
    
    @Test
    public void stringUsesVanillaForm() {
        ScalarsTest.assertJson(NexusCodec.STRING, "hi", "\"hi\"");
        ScalarsTest.assertNbt(NexusCodec.STRING, "hi", StringTag.valueOf("hi"));
        ScalarsTest.assertNetty(NexusCodec.STRING, "hi", 0x02, 'h', 'i');
    }
    
    @Test
    public void stringRefusesNumber() {
        Assertions.assertThatThrownBy(() -> NexusCodec.STRING.decode(CodecFormat.JSON, JsonParser.parseString("5")))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContaining("found 5");
    }
    
    @Test
    public void stringRefusesMoreThanVanillaLimit() {
        String text = "a".repeat(32768);
        
        Assertions.assertThatThrownBy(() -> NexusCodec.STRING.decode(CodecFormat.JSON, new JsonPrimitive(text)))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContainingAll("32767", "32768");
        Assertions.assertThatThrownBy(() -> NexusCodec.STRING.encode(CodecFormat.NBT, text))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContainingAll("32767", "32768");
    }
    
    @Test
    public void stringWithLimitAcceptsUpToLimit() {
        ScalarsTest.assertJson(NexusCodec.string(3), "abc", "\"abc\"");
        ScalarsTest.assertNbt(NexusCodec.string(3), "abc", StringTag.valueOf("abc"));
        ScalarsTest.assertNetty(NexusCodec.string(3), "abc", 0x03, 'a', 'b', 'c');
    }
    
    @Test
    public void stringWithLimitRefusesLongerString() {
        Assertions.assertThatThrownBy(() -> NexusCodec.string(3).decode(CodecFormat.JSON, JsonParser.parseString("\"abcd\"")))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContainingAll("3", "4");
        Assertions.assertThatThrownBy(() -> NexusCodec.string(3).encode(CodecFormat.JSON, "abcd"))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContainingAll("3", "4");
        Assertions.assertThatThrownBy(() -> NexusCodec.string(3).decode(CodecFormat.netty(ScalarsTest.buffer(0x04, 'a', 'b', 'c', 'd'))))
                .isInstanceOf(NexusCodecException.class);
    }
    
    @Test
    public void stringWithZeroLimitAcceptsOnlyEmptyString() {
        ScalarsTest.assertJson(NexusCodec.string(0), "", "\"\"");
        ScalarsTest.assertNetty(NexusCodec.string(0), "", 0x00);
        Assertions.assertThatThrownBy(() -> NexusCodec.string(0).decode(CodecFormat.JSON, JsonParser.parseString("\"a\"")))
                .isInstanceOf(NexusCodecException.class);
    }
    
    @Test
    public void stringRefusesNegativeLimit() {
        Assertions.assertThatThrownBy(() -> NexusCodec.string(-1)).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void identifierUsesStringForm() {
        Identifier identifier = Identifier.fromNamespaceAndPath("nexus", "thing");
        
        ScalarsTest.assertJson(NexusCodec.IDENTIFIER, identifier, "\"nexus:thing\"");
        ScalarsTest.assertNbt(NexusCodec.IDENTIFIER, identifier, StringTag.valueOf("nexus:thing"));
        ScalarsTest.assertNetty(NexusCodec.IDENTIFIER, identifier, 0x0B, 'n', 'e', 'x', 'u', 's', ':', 't', 'h', 'i', 'n', 'g');
    }
    
    @Test
    public void identifierReadsDefaultNamespace() {
        Assertions.assertThat(NexusCodec.IDENTIFIER.decode(CodecFormat.JSON, JsonParser.parseString("\"stone\""))).isEqualTo(Identifier.withDefaultNamespace("stone"));
    }
    
    @Test
    public void identifierRefusesInvalidString() {
        Assertions.assertThatThrownBy(() -> NexusCodec.IDENTIFIER.decode(CodecFormat.JSON, JsonParser.parseString("\"Not Valid\"")))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContaining("\"Not Valid\"");
    }
    
    @Test
    public void uuidUsesVanillaForm() {
        ScalarsTest.assertJson(NexusCodec.UUID, ScalarsTest.SAMPLE_UUID, "[1,2,3,4]");
        ScalarsTest.assertNbt(NexusCodec.UUID, ScalarsTest.SAMPLE_UUID, new IntArrayTag(new int[]{ 1, 2, 3, 4 }));
        ScalarsTest.assertNetty(NexusCodec.UUID, ScalarsTest.SAMPLE_UUID, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0, 4);
    }
    
    @Test
    public void uuidReadsStringForm() {
        Assertions.assertThat(NexusCodec.UUID.decode(CodecFormat.JSON, JsonParser.parseString("\"00000001-0000-0002-0000-000300000004\""))).isEqualTo(ScalarsTest.SAMPLE_UUID);
        Assertions.assertThat(NexusCodec.UUID.decode(CodecFormat.NBT, StringTag.valueOf("00000001-0000-0002-0000-000300000004"))).isEqualTo(ScalarsTest.SAMPLE_UUID);
    }
    
    @Test
    public void uuidRefusesWrongLengthAndInvalidString() {
        Assertions.assertThatThrownBy(() -> NexusCodec.UUID.decode(CodecFormat.JSON, JsonParser.parseString("[1,2,3]")))
                .isInstanceOf(NexusCodecException.class);
        Assertions.assertThatThrownBy(() -> NexusCodec.UUID.decode(CodecFormat.NBT, new IntArrayTag(new int[]{ 1, 2, 3, 4, 5 })))
                .isInstanceOf(NexusCodecException.class);
        Assertions.assertThatThrownBy(() -> NexusCodec.UUID.decode(CodecFormat.JSON, JsonParser.parseString("\"not-a-uuid\"")))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContaining("\"not-a-uuid\"");
        Assertions.assertThatThrownBy(() -> NexusCodec.UUID.decode(CodecFormat.JSON, JsonParser.parseString("5")))
                .isInstanceOf(NexusCodecException.class);
    }
    
    @Test
    public void enumOfUsesSerializedNames() {
        NexusCodec<ParityCases.Weight, Access.Plain> codec = NexusCodec.enumOf(ParityCases.Weight.class);
        
        ScalarsTest.assertJson(codec, ParityCases.Weight.FEATHER, "\"light\"");
        ScalarsTest.assertNbt(codec, ParityCases.Weight.ANVIL, StringTag.valueOf("heavy"));
        ScalarsTest.assertNetty(codec, ParityCases.Weight.FEATHER, 0x00);
        ScalarsTest.assertNetty(codec, ParityCases.Weight.ANVIL, 0x01);
    }
    
    @Test
    public void enumOfUsesLowerCaseNamesOfPlainEnum() {
        NexusCodec<ParityCases.Phase, Access.Plain> codec = NexusCodec.enumOf(ParityCases.Phase.class);
        
        ScalarsTest.assertJson(codec, ParityCases.Phase.NEW_MOON, "\"new_moon\"");
        ScalarsTest.assertNbt(codec, ParityCases.Phase.FULL_MOON, StringTag.valueOf("full_moon"));
        ScalarsTest.assertNetty(codec, ParityCases.Phase.FULL_MOON, 0x01);
    }
    
    @Test
    public void enumOfRefusesUnknownNameListingConstants() {
        Assertions.assertThatThrownBy(() -> NexusCodec.enumOf(ParityCases.Weight.class).decode(CodecFormat.JSON, JsonParser.parseString("\"FEATHER\"")))
                .isInstanceOf(NexusCodecException.class)
                .hasMessageContainingAll("light", "heavy", "\"FEATHER\"");
    }
    
    @Test
    public void enumOfRefusesUnknownOrdinal() {
        Assertions.assertThatThrownBy(() -> NexusCodec.enumOf(ParityCases.Weight.class).decode(CodecFormat.netty(ScalarsTest.buffer(0x02))))
                .isInstanceOf(NexusCodecException.class);
    }
    
    @Test
    public void enumOfDecodesSharedNameToFirstConstant() {
        NexusCodec<ParityCases.Alias, Access.Plain> codec = NexusCodec.enumOf(ParityCases.Alias.class);
        
        Assertions.assertThat(codec.encode(CodecFormat.JSON, ParityCases.Alias.SECOND)).hasToString("\"shared\"");
        Assertions.assertThat(codec.decode(CodecFormat.JSON, JsonParser.parseString("\"shared\""))).isEqualTo(ParityCases.Alias.FIRST);
        ScalarsTest.assertNetty(codec, ParityCases.Alias.SECOND, 0x01);
    }
    
    @Test
    public void unitWritesEmptyObjectAndNothingOnNetty() {
        NexusCodec<String, Access.Plain> codec = NexusCodec.unit("constant");
        
        ScalarsTest.assertJson(codec, "constant", "{}");
        ScalarsTest.assertNbt(codec, "constant", new CompoundTag());
        ScalarsTest.assertNetty(codec, "constant");
    }
    
    @Test
    public void unitReadsAnyObject() {
        NexusCodec<String, Access.Plain> codec = NexusCodec.unit("constant");
        CompoundTag tag = new CompoundTag();
        tag.putInt("key", 1);
        
        Assertions.assertThat(codec.decode(CodecFormat.JSON, JsonParser.parseString("{\"key\":1}"))).isEqualTo("constant");
        Assertions.assertThat(codec.decode(CodecFormat.NBT, tag)).isEqualTo("constant");
    }
    
    @Test
    public void unitRefusesNonObject() {
        Assertions.assertThatThrownBy(() -> NexusCodec.unit("constant").decode(CodecFormat.JSON, JsonParser.parseString("5")))
                .isInstanceOf(NexusCodecException.class);
        Assertions.assertThatThrownBy(() -> NexusCodec.unit("constant").decode(CodecFormat.NBT, IntTag.valueOf(5)))
                .isInstanceOf(NexusCodecException.class);
    }
    
    @Test
    public void decodeFailureNamesCodecDirectionAndFormat() {
        NexusCodecException failure = Assertions.catchThrowableOfType(NexusCodecException.class, () -> NexusCodec.INT.decode(CodecFormat.JSON, JsonParser.parseString("true")));
        
        Assertions.assertThat(failure.getMessage().lines().findFirst()).hasValueSatisfying(line -> Assertions.assertThat(line).contains("INT", "decode", "JSON"));
    }
    
    @Test
    public void encodeFailureNamesCodecDirectionAndFormat() {
        NexusCodecException failure = Assertions.catchThrowableOfType(NexusCodecException.class, () -> NexusCodec.string(3).encode(CodecFormat.NBT, "abcd"));
        
        Assertions.assertThat(failure.getMessage().lines().findFirst()).hasValueSatisfying(line -> Assertions.assertThat(line).contains("encode", "NBT"));
    }
    
    @Test
    public void nettyFailureNamesFormat() {
        NexusCodecException failure = Assertions.catchThrowableOfType(NexusCodecException.class, () -> NexusCodec.INT.decode(CodecFormat.netty(ScalarsTest.buffer(0x00, 0x01))));
        
        Assertions.assertThat(failure.getMessage().lines().findFirst()).hasValueSatisfying(line -> Assertions.assertThat(line).contains("INT", "decode", "netty"));
    }
    
    @Test
    public void tryDecodeHoldsTheExceptionDecodeThrows() {
        JsonPrimitive input = new JsonPrimitive(2.5D);
        NexusCodecException thrown = Assertions.catchThrowableOfType(NexusCodecException.class, () -> NexusCodec.INT.decode(CodecFormat.JSON, input));
        
        if (!(NexusCodec.INT.tryDecode(CodecFormat.JSON, input) instanceof CodecResult.Failure<Integer>(NexusCodecException failure))) {
            throw new AssertionError("tryDecode did not return a failure");
        }
        
        Assertions.assertThat(failure.getMessage()).isEqualTo(thrown.getMessage());
        Assertions.assertThat(failure.errors()).isEqualTo(thrown.errors());
    }
    
    @Test
    public void tryEncodeHoldsTheExceptionEncodeThrows() {
        NexusCodecException thrown = Assertions.catchThrowableOfType(NexusCodecException.class, () -> NexusCodec.string(3).encode(CodecFormat.JSON, "abcd"));
        
        if (!(NexusCodec.string(3).tryEncode(CodecFormat.JSON, "abcd") instanceof CodecResult.Failure<?>(NexusCodecException failure))) {
            throw new AssertionError("tryEncode did not return a failure");
        }
        
        Assertions.assertThat(failure.getMessage()).isEqualTo(thrown.getMessage());
    }
    
    @Test
    public void trySucceedsWithTheValue() {
        Assertions.assertThat(NexusCodec.INT.tryEncode(CodecFormat.NBT, 5)).isEqualTo(new CodecResult.Success<Tag>(IntTag.valueOf(5)));
        Assertions.assertThat(NexusCodec.INT.tryDecode(CodecFormat.NBT, IntTag.valueOf(5))).isEqualTo(new CodecResult.Success<>(5));
        Assertions.assertThat(NexusCodec.INT.tryDecode(CodecFormat.netty(ScalarsTest.buffer(0x00, 0x00, 0x00, 0x05)))).isEqualTo(new CodecResult.Success<>(5));
    }
    
    @Test
    public void encodeOnNettyReturnsTheBuffer() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        
        Assertions.assertThat(NexusCodec.INT.encode(CodecFormat.netty(buffer), 5)).isSameAs(buffer);
    }
    
    @Test
    public void decodeRefusesInputOtherThanTheFormatBuffer() {
        FriendlyByteBuf buffer = ScalarsTest.buffer(0x00, 0x00, 0x00, 0x05);
        FriendlyByteBuf other = ScalarsTest.buffer(0x00, 0x00, 0x00, 0x05);
        
        Assertions.assertThatThrownBy(() -> NexusCodec.INT.decode(CodecFormat.netty(buffer), other)).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThat(NexusCodec.INT.decode(CodecFormat.netty(buffer), buffer)).isEqualTo(5);
    }
    
    @Test
    public void decodeReadsTheFormatBufferInOrder() {
        FriendlyByteBuf buffer = ScalarsTest.buffer(0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x06);
        BufferFormat<FriendlyByteBuf, Access.Plain> format = CodecFormat.netty(buffer);
        
        Assertions.assertThat(NexusCodec.INT.decode(format)).isEqualTo(5);
        Assertions.assertThat(NexusCodec.INT.decode(format)).isEqualTo(6);
        Assertions.assertThat(buffer.readableBytes()).isZero();
    }
    
    private static <T> void assertJson(NexusCodec<T, Access.Plain> codec, T value, String json) {
        Assertions.assertThat(codec.encode(CodecFormat.JSON, value)).hasToString(json);
        Assertions.assertThat(codec.decode(CodecFormat.JSON, JsonParser.parseString(json))).isEqualTo(value);
    }
    
    private static <T> void assertNbt(NexusCodec<T, Access.Plain> codec, T value, Tag tag) {
        Assertions.assertThat(codec.encode(CodecFormat.NBT, value)).isEqualTo(tag);
        Assertions.assertThat(codec.decode(CodecFormat.NBT, tag)).isEqualTo(value);
    }
    
    private static <T> void assertNetty(NexusCodec<T, Access.Plain> codec, T value, int... bytes) {
        FriendlyByteBuf written = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(CodecFormat.netty(written), value);
        FriendlyByteBuf read = ScalarsTest.buffer(bytes);
        
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(ScalarsTest.bytes(bytes));
        Assertions.assertThat(codec.decode(CodecFormat.netty(read))).isEqualTo(value);
        Assertions.assertThat(read.readableBytes()).isZero();
    }
    
    private static FriendlyByteBuf buffer(int... bytes) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(ScalarsTest.bytes(bytes)));
    }
    
    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        
        return bytes;
    }
}
