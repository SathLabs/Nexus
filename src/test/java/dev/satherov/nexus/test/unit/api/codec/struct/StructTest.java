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
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

///
/// Checks how a struct writes and reads its fields in JSON, NBT, and on the network, and how it reports the errors of its fields.
///
public class StructTest {
    
    private static final StructCodec<Offer, Access.Plain> OFFER = NexusCodec.struct(
            "offer",
            NexusCodec.STRING.field("item", Offer::item),
            NexusCodec.INT.field("count", Offer::count),
            Offer::new
    );
    
    private static final StructCodec<Trade, Access.Plain> TRADE = NexusCodec.struct(
            "trade",
            StructTest.OFFER.field("cost", Trade::cost),
            StructTest.OFFER.field("result", Trade::result),
            Trade::new
    );
    
    private static final StructCodec<Priced, Access.Plain> PRICED = NexusCodec.struct(
            "priced",
            StructTest.OFFER.inline(Priced::offer),
            NexusCodec.INT.field("price", Priced::price),
            Priced::new
    );
    
    private static final StructCodec<Labels, Access.Plain> LABELS = NexusCodec.struct(
            "labels",
            NexusCodec.string(3).field("first", Labels::first),
            NexusCodec.string(3).field("second", Labels::second),
            Labels::new
    );
    
    @Test
    public void writesEveryFieldUnderItsKeyInJson() {
        StructTest.assertJson(StructTest.OFFER, new Offer("stone", 3), "{\"item\":\"stone\",\"count\":3}");
    }
    
    @Test
    public void writesEveryFieldUnderItsKeyInNbt() {
        StructTest.assertNbt(StructTest.OFFER, new Offer("stone", 3), StructTest.compound(Map.of("item", StringTag.valueOf("stone"), "count", IntTag.valueOf(3))));
    }
    
    @Test
    public void writesFieldsInOrderWithoutKeysOnNetty() {
        StructTest.assertNetty(StructTest.OFFER, new Offer("stone", 3), 0x05, 's', 't', 'o', 'n', 'e', 0x00, 0x00, 0x00, 0x03);
    }
    
    @Test
    public void nestsStructUnderTheKeyOfItsField() {
        Trade trade = new Trade(new Offer("stone", 3), new Offer("dirt", 1));
        
        StructTest.assertJson(StructTest.TRADE, trade, "{\"cost\":{\"item\":\"stone\",\"count\":3},\"result\":{\"item\":\"dirt\",\"count\":1}}");
        StructTest.assertNbt(
                StructTest.TRADE,
                trade,
                StructTest.compound(Map.of(
                        "cost",
                        StructTest.compound(Map.of("item", StringTag.valueOf("stone"), "count", IntTag.valueOf(3))),
                        "result",
                        StructTest.compound(Map.of("item", StringTag.valueOf("dirt"), "count", IntTag.valueOf(1)))
                ))
        );
        StructTest.assertNetty(StructTest.TRADE, trade, 0x05, 's', 't', 'o', 'n', 'e', 0x00, 0x00, 0x00, 0x03, 0x04, 'd', 'i', 'r', 't', 0x00, 0x00, 0x00, 0x01);
    }
    
    @Test
    public void inlineStructMergesItsKeysIntoTheOwner() {
        Priced priced = new Priced(new Offer("stone", 3), 5);
        
        StructTest.assertJson(StructTest.PRICED, priced, "{\"item\":\"stone\",\"count\":3,\"price\":5}");
        StructTest.assertNbt(
                StructTest.PRICED,
                priced,
                StructTest.compound(Map.of("item", StringTag.valueOf("stone"), "count", IntTag.valueOf(3), "price", IntTag.valueOf(5)))
        );
        StructTest.assertNetty(StructTest.PRICED, priced, 0x05, 's', 't', 'o', 'n', 'e', 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x05);
    }
    
    @Test
    public void lenientFormatIgnoresUnknownKey() {
        CompoundTag tag = StructTest.compound(Map.of("item", StringTag.valueOf("stone"), "count", IntTag.valueOf(3), "extra", IntTag.valueOf(1)));
        
        Assertions.assertThat(StructTest.OFFER.decode(CodecFormat.JSON, JsonParser.parseString("{\"item\":\"stone\",\"count\":3,\"extra\":true}")))
                .isEqualTo(new Offer("stone", 3));
        Assertions.assertThat(StructTest.OFFER.decode(CodecFormat.NBT, tag)).isEqualTo(new Offer("stone", 3));
    }
    
    @Test
    public void strictFormatRefusesUnknownKey() {
        CompoundTag tag = StructTest.compound(Map.of("item", StringTag.valueOf("stone"), "count", IntTag.valueOf(3), "extra", IntTag.valueOf(1)));
        JsonElement input = JsonParser.parseString("{\"item\":\"stone\",\"count\":3,\"extra\":true}");
        
        Assertions.assertThat(StructTest.decodeErrors(() -> StructTest.OFFER.decode(CodecFormat.JSON.strict(), input)))
                .containsExactly(new CodecError("extra", "Unknown key"));
        Assertions.assertThat(StructTest.decodeErrors(() -> StructTest.OFFER.decode(CodecFormat.NBT.strict(), tag)))
                .containsExactly(new CodecError("extra", "Unknown key"));
    }
    
    @Test
    public void strictFormatKnowsTheKeysOfInlineStruct() {
        JsonElement known = JsonParser.parseString("{\"item\":\"stone\",\"count\":3,\"price\":5}");
        JsonElement unknown = JsonParser.parseString("{\"item\":\"stone\",\"count\":3,\"price\":5,\"extra\":1}");
        
        Assertions.assertThat(StructTest.PRICED.decode(CodecFormat.JSON.strict(), known)).isEqualTo(new Priced(new Offer("stone", 3), 5));
        Assertions.assertThat(StructTest.decodeErrors(() -> StructTest.PRICED.decode(CodecFormat.JSON.strict(), unknown)))
                .containsExactly(new CodecError("extra", "Unknown key"));
    }
    
    @Test
    public void strictFormatReportsUnknownKeyWithFieldErrors() {
        Assertions.assertThat(StructTest.decodeErrors(() -> StructTest.OFFER.decode(CodecFormat.JSON.strict(), JsonParser.parseString("{\"item\":5,\"count\":3,\"extra\":1}"))))
                .extracting(CodecError::path)
                .containsExactlyInAnyOrder("item", "extra");
    }
    
    @Test
    public void refusesNullOnRequiredField() {
        Assertions.assertThat(StructTest.decodeErrors(() -> StructTest.OFFER.decode(CodecFormat.JSON, JsonParser.parseString("{\"item\":null,\"count\":3}"))))
                .extracting(CodecError::path)
                .containsExactly("item");
    }
    
    @Test
    public void refusesInputThatIsNotAnObjectOnce() {
        Assertions.assertThat(StructTest.decodeErrors(() -> StructTest.OFFER.decode(CodecFormat.JSON, JsonParser.parseString("5"))))
                .singleElement()
                .satisfies(error -> {
                    Assertions.assertThat(error.path()).isEmpty();
                    Assertions.assertThat(error.message()).contains("object", "5");
                });
    }
    
    @Test
    public void collectsTheErrorsOfEveryFieldInJson() {
        Assertions.assertThat(StructTest.decodeErrors(() -> StructTest.OFFER.decode(CodecFormat.JSON, JsonParser.parseString("{\"item\":5,\"count\":\"many\"}"))))
                .extracting(CodecError::path)
                .containsExactly("item", "count");
    }
    
    @Test
    public void collectsTheErrorsOfEveryFieldInNbt() {
        CompoundTag tag = StructTest.compound(Map.of("item", IntTag.valueOf(5), "count", StringTag.valueOf("many")));
        
        Assertions.assertThat(StructTest.decodeErrors(() -> StructTest.OFFER.decode(CodecFormat.NBT, tag)))
                .extracting(CodecError::path)
                .containsExactly("item", "count");
    }
    
    @Test
    public void joinsTheKeysOfNestedStructsIntoThePath() {
        JsonElement input = JsonParser.parseString("{\"cost\":{\"item\":\"stone\",\"count\":\"many\"},\"result\":{\"item\":\"dirt\"}}");
        
        Assertions.assertThat(StructTest.decodeErrors(() -> StructTest.TRADE.decode(CodecFormat.JSON, input)))
                .satisfiesExactly(
                        error -> Assertions.assertThat(error.path()).isEqualTo("cost.count"),
                        error -> Assertions.assertThat(error).isEqualTo(new CodecError("result.count", "Missing"))
                );
    }
    
    @Test
    public void inlineStructReportsItsErrorsAtTheKeysOfTheOwner() {
        JsonElement input = JsonParser.parseString("{\"item\":\"stone\",\"count\":\"many\",\"price\":\"high\"}");
        
        Assertions.assertThat(StructTest.decodeErrors(() -> StructTest.PRICED.decode(CodecFormat.JSON, input)))
                .extracting(CodecError::path)
                .containsExactly("count", "price");
    }
    
    @Test
    public void collectsTheErrorsOfEveryFieldOnEncode() {
        Labels labels = new Labels("long", "longer");
        
        Assertions.assertThat(Assertions.catchThrowableOfType(NexusCodecException.class, () -> StructTest.LABELS.encode(CodecFormat.JSON, labels)).errors())
                .extracting(CodecError::path)
                .containsExactly("first", "second");
        Assertions.assertThat(Assertions.catchThrowableOfType(NexusCodecException.class, () -> StructTest.LABELS.encode(CodecFormat.NBT, labels)).errors())
                .extracting(CodecError::path)
                .containsExactly("first", "second");
    }
    
    @Test
    public void stopsAtTheFirstErrorOnNetty() {
        FriendlyByteBuf truncated = StructTest.buffer(0x05, 's');
        Labels labels = new Labels("long", "longer");
        
        Assertions.assertThat(StructTest.decodeErrors(() -> StructTest.OFFER.decode(CodecFormat.netty(truncated)))).hasSize(1);
        Assertions.assertThat(Assertions.catchThrowableOfType(NexusCodecException.class, () -> StructTest.LABELS.encode(CodecFormat.netty(StructTest.buffer()), labels)).errors())
                .hasSize(1);
    }
    
    @Test
    public void failureNamesTheStructThatWasDecoded() {
        JsonElement input = JsonParser.parseString("{\"cost\":{\"item\":\"stone\",\"count\":\"many\"},\"result\":{\"item\":\"dirt\",\"count\":1}}");
        NexusCodecException failure = Assertions.catchThrowableOfType(NexusCodecException.class, () -> StructTest.TRADE.decode(CodecFormat.JSON, input));
        
        Assertions.assertThat(failure.getMessage().lines().findFirst())
                .hasValueSatisfying(line -> Assertions.assertThat(line).contains("trade", "decode", "JSON").doesNotContain("offer"));
        Assertions.assertThat(failure.errors()).extracting(CodecError::path).containsExactly("cost.count");
    }
    
    private static List<CodecError> decodeErrors(ThrowableAssert.ThrowingCallable call) {
        return Assertions.catchThrowableOfType(NexusCodecException.class, call).errors();
    }
    
    private static CompoundTag compound(Map<String, Tag> entries) {
        CompoundTag tag = new CompoundTag();
        entries.forEach(tag::put);
        return tag;
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
        FriendlyByteBuf read = StructTest.buffer(bytes);
        
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(ByteBufUtil.getBytes(StructTest.buffer(bytes)));
        Assertions.assertThat(codec.decode(CodecFormat.netty(read))).isEqualTo(value);
        Assertions.assertThat(read.readableBytes()).isZero();
    }
    
    private static FriendlyByteBuf buffer(int... bytes) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        for (int value : bytes) {
            buffer.writeByte(value);
        }
        
        return buffer;
    }
    
    public record Offer(String item, int count) { }
    
    public record Trade(Offer cost, Offer result) { }
    
    public record Priced(Offer offer, int price) { }
    
    public record Labels(String first, String second) { }
}
