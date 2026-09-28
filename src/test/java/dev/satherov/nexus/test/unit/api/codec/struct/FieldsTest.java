package dev.satherov.nexus.test.unit.api.codec.struct;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

///
/// Checks the rules of every kind of struct field in JSON, NBT, and on the network.
///
public class FieldsTest {
    
    private static final StructCodec<Stack, Access.Plain> STACK = NexusCodec.struct(
            "stack",
            NexusCodec.STRING.field("item", Stack::item),
            NexusCodec.INT.optionalField("count", 1, Stack::count),
            Stack::new
    );
    
    private static final StructCodec<Note, Access.Plain> NOTE = NexusCodec.struct(
            "note",
            NexusCodec.STRING.field("item", Note::item),
            NexusCodec.STRING.optionalField("label", Note::label),
            Note::new
    );
    
    @Test
    public void requiredFieldFailsIfItsKeyIsMissing() {
        CompoundTag tag = new CompoundTag();
        tag.put("count", IntTag.valueOf(2));
        
        Assertions.assertThat(FieldsTest.errors(() -> FieldsTest.STACK.decode(CodecFormat.JSON, JsonParser.parseString("{\"count\":2}"))))
                .containsExactly(new CodecError("item", "Missing"));
        Assertions.assertThat(FieldsTest.errors(() -> FieldsTest.STACK.decode(CodecFormat.NBT, tag)))
                .containsExactly(new CodecError("item", "Missing"));
    }
    
    @Test
    public void defaultedFieldReadsTheDefaultIfItsKeyIsAbsent() {
        Assertions.assertThat(FieldsTest.STACK.decode(CodecFormat.JSON, JsonParser.parseString("{\"item\":\"stone\"}"))).isEqualTo(new Stack("stone", 1));
        Assertions.assertThat(FieldsTest.STACK.decode(CodecFormat.NBT, FieldsTest.item("stone"))).isEqualTo(new Stack("stone", 1));
    }
    
    @Test
    public void defaultedFieldLeavesOutValueEqualToTheDefault() {
        Assertions.assertThat(FieldsTest.STACK.encode(CodecFormat.JSON, new Stack("stone", 1))).hasToString("{\"item\":\"stone\"}");
        Assertions.assertThat(FieldsTest.STACK.encode(CodecFormat.NBT, new Stack("stone", 1))).isEqualTo(FieldsTest.item("stone"));
    }
    
    @Test
    public void defaultedFieldWritesOtherValue() {
        CompoundTag tag = FieldsTest.item("stone");
        tag.put("count", IntTag.valueOf(5));
        
        Assertions.assertThat(FieldsTest.STACK.encode(CodecFormat.JSON, new Stack("stone", 5))).hasToString("{\"item\":\"stone\",\"count\":5}");
        Assertions.assertThat(FieldsTest.STACK.decode(CodecFormat.JSON, JsonParser.parseString("{\"item\":\"stone\",\"count\":5}"))).isEqualTo(new Stack("stone", 5));
        Assertions.assertThat(FieldsTest.STACK.encode(CodecFormat.NBT, new Stack("stone", 5))).isEqualTo(tag);
        Assertions.assertThat(FieldsTest.STACK.decode(CodecFormat.NBT, tag)).isEqualTo(new Stack("stone", 5));
    }
    
    @Test
    public void defaultedFieldRefusesPresentBadValue() {
        CompoundTag tag = FieldsTest.item("stone");
        tag.put("count", StringTag.valueOf("many"));
        
        Assertions.assertThat(FieldsTest.errors(() -> FieldsTest.STACK.decode(CodecFormat.JSON, JsonParser.parseString("{\"item\":\"stone\",\"count\":\"many\"}"))))
                .extracting(CodecError::path)
                .containsExactly("count");
        Assertions.assertThat(FieldsTest.errors(() -> FieldsTest.STACK.decode(CodecFormat.NBT, tag)))
                .extracting(CodecError::path)
                .containsExactly("count");
    }
    
    @Test
    public void defaultedFieldRefusesNull() {
        Assertions.assertThat(FieldsTest.errors(() -> FieldsTest.STACK.decode(CodecFormat.JSON, JsonParser.parseString("{\"item\":\"stone\",\"count\":null}"))))
                .extracting(CodecError::path)
                .containsExactly("count");
    }
    
    @Test
    public void defaultedFieldIsAlwaysWrittenOnNetty() {
        FieldsTest.assertNetty(FieldsTest.STACK, new Stack("stone", 1), 0x05, 's', 't', 'o', 'n', 'e', 0x00, 0x00, 0x00, 0x01);
        FieldsTest.assertNetty(FieldsTest.STACK, new Stack("stone", 5), 0x05, 's', 't', 'o', 'n', 'e', 0x00, 0x00, 0x00, 0x05);
    }
    
    @Test
    public void optionalFieldReadsEmptyIfItsKeyIsAbsent() {
        Assertions.assertThat(FieldsTest.NOTE.decode(CodecFormat.JSON, JsonParser.parseString("{\"item\":\"stone\"}"))).isEqualTo(new Note("stone", Optional.empty()));
        Assertions.assertThat(FieldsTest.NOTE.decode(CodecFormat.NBT, FieldsTest.item("stone"))).isEqualTo(new Note("stone", Optional.empty()));
    }
    
    @Test
    public void optionalFieldLeavesOutEmptyValue() {
        Assertions.assertThat(FieldsTest.NOTE.encode(CodecFormat.JSON, new Note("stone", Optional.empty()))).hasToString("{\"item\":\"stone\"}");
        Assertions.assertThat(FieldsTest.NOTE.encode(CodecFormat.NBT, new Note("stone", Optional.empty()))).isEqualTo(FieldsTest.item("stone"));
    }
    
    @Test
    public void optionalFieldWritesPresentValue() {
        Note labelled = new Note("stone", Optional.of("red"));
        CompoundTag tag = FieldsTest.item("stone");
        tag.put("label", StringTag.valueOf("red"));
        
        Assertions.assertThat(FieldsTest.NOTE.encode(CodecFormat.JSON, labelled)).hasToString("{\"item\":\"stone\",\"label\":\"red\"}");
        Assertions.assertThat(FieldsTest.NOTE.decode(CodecFormat.JSON, JsonParser.parseString("{\"item\":\"stone\",\"label\":\"red\"}"))).isEqualTo(labelled);
        Assertions.assertThat(FieldsTest.NOTE.encode(CodecFormat.NBT, labelled)).isEqualTo(tag);
        Assertions.assertThat(FieldsTest.NOTE.decode(CodecFormat.NBT, tag)).isEqualTo(labelled);
    }
    
    @Test
    public void optionalFieldReadsNullAsEmpty() {
        JsonElement input = JsonParser.parseString("{\"item\":\"stone\",\"label\":null}");
        
        Assertions.assertThat(FieldsTest.NOTE.decode(CodecFormat.JSON, input)).isEqualTo(new Note("stone", Optional.empty()));
    }
    
    @Test
    public void optionalFieldRefusesPresentBadValue() {
        CompoundTag tag = FieldsTest.item("stone");
        tag.put("label", IntTag.valueOf(5));
        
        Assertions.assertThat(FieldsTest.errors(() -> FieldsTest.NOTE.decode(CodecFormat.JSON, JsonParser.parseString("{\"item\":\"stone\",\"label\":5}"))))
                .extracting(CodecError::path)
                .containsExactly("label");
        Assertions.assertThat(FieldsTest.errors(() -> FieldsTest.NOTE.decode(CodecFormat.NBT, tag)))
                .extracting(CodecError::path)
                .containsExactly("label");
    }
    
    @Test
    public void optionalFieldWritesPresenceByteOnNetty() {
        FieldsTest.assertNetty(FieldsTest.NOTE, new Note("stone", Optional.empty()), 0x05, 's', 't', 'o', 'n', 'e', 0x00);
        FieldsTest.assertNetty(FieldsTest.NOTE, new Note("stone", Optional.of("red")), 0x05, 's', 't', 'o', 'n', 'e', 0x01, 0x03, 'r', 'e', 'd');
    }
    
    @Test
    public void inlineFieldRefusesKeyTheOwnerAlreadyHas() {
        Assertions.assertThatThrownBy(() -> NexusCodec.struct("clash", FieldsTest.STACK.inline(Clash::stack), NexusCodec.INT.field("count", Clash::count), Clash::new))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> NexusCodec.struct(
                "clash",
                NexusCodec.INT.field("count", Clash::count),
                FieldsTest.STACK.inline(Clash::stack),
                (count, stack) -> new Clash(stack, count)
        )).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void structFieldWithEmptyKeyNestsUnderIt() {
        StructCodec<Crate, Access.Plain> crate = NexusCodec.struct("crate", FieldsTest.STACK.field("", Crate::stack), Crate::new);
        JsonElement nested = JsonParser.parseString("{\"\":{\"item\":\"stone\",\"count\":5}}");
        CompoundTag tag = new CompoundTag();
        CompoundTag stack = FieldsTest.item("stone");
        stack.put("count", IntTag.valueOf(5));
        tag.put("", stack);
        
        Assertions.assertThat(crate.encode(CodecFormat.JSON, new Crate(new Stack("stone", 5)))).isEqualTo(nested);
        Assertions.assertThat(crate.decode(CodecFormat.JSON, nested)).isEqualTo(new Crate(new Stack("stone", 5)));
        Assertions.assertThat(crate.encode(CodecFormat.NBT, new Crate(new Stack("stone", 5)))).isEqualTo(tag);
        Assertions.assertThat(FieldsTest.errors(() -> crate.decode(CodecFormat.JSON, JsonParser.parseString("{\"item\":\"stone\",\"count\":5}"))))
                .containsExactly(new CodecError("", "Missing"));
    }
    
    @Test
    public void defaultedStructFieldWithEmptyKeyReadsItsFallback() {
        StructCodec<Crate, Access.Plain> crate = NexusCodec.struct("crate", FieldsTest.STACK.optionalField("", new Stack("dirt", 2), Crate::stack), Crate::new);
        
        Assertions.assertThat(crate.decode(CodecFormat.JSON, JsonParser.parseString("{}"))).isEqualTo(new Crate(new Stack("dirt", 2)));
        Assertions.assertThat(crate.decode(CodecFormat.NBT, new CompoundTag())).isEqualTo(new Crate(new Stack("dirt", 2)));
        Assertions.assertThat(crate.encode(CodecFormat.JSON, new Crate(new Stack("dirt", 2)))).hasToString("{}");
        Assertions.assertThat(crate.encode(CodecFormat.JSON, new Crate(new Stack("stone", 5)))).hasToString("{\"\":{\"item\":\"stone\",\"count\":5}}");
    }
    
    private static List<CodecError> errors(ThrowableAssert.ThrowingCallable call) {
        return Assertions.catchThrowableOfType(NexusCodecException.class, call).errors();
    }
    
    private static CompoundTag item(String item) {
        CompoundTag tag = new CompoundTag();
        tag.put("item", StringTag.valueOf(item));
        return tag;
    }
    
    private static <T> void assertNetty(NexusCodec<T, Access.Plain> codec, T value, int... bytes) {
        FriendlyByteBuf written = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(CodecFormat.netty(written), value);
        FriendlyByteBuf read = new FriendlyByteBuf(Unpooled.buffer());
        for (int b : bytes) {
            read.writeByte(b);
        }
        
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(ByteBufUtil.getBytes(read));
        Assertions.assertThat(codec.decode(CodecFormat.netty(read))).isEqualTo(value);
        Assertions.assertThat(read.readableBytes()).isZero();
    }
    
    public record Stack(String item, int count) { }
    
    public record Note(String item, Optional<String> label) { }
    
    public record Clash(Stack stack, int count) { }
    
    public record Crate(Stack stack) { }
}
