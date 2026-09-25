package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.CodecError;
import dev.satherov.nexus.api.codec.CodecException;
import dev.satherov.nexus.api.codec.CodecFormat;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.StructCodec;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

///
/// Checks the codecs over DFU codecs and stream codecs on each format, their failures, and their use inside other codecs.
///
public class WrappersTest {
    
    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final CodecFormat<JsonElement, Access.Registries> JSON = CodecFormat.JSON.withRegistries(WrappersTest.REGISTRIES);
    private static final CodecFormat<Tag, Access.Registries> NBT = CodecFormat.NBT.withRegistries(WrappersTest.REGISTRIES);
    
    private static final Holder<Item> STONE = BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE);
    private static final Holder<Item> FIRST_ITEM = BuiltInRegistries.ITEM.get(1).orElseThrow();
    
    @Test
    public void dfuCodecRunsInJsonAndNbt() {
        NexusCodec<Integer, Access.Plain> codec = NexusCodec.ofDfu(Codec.INT);
        
        Assertions.assertThat(codec.encode(CodecFormat.JSON, 300)).hasToString("300");
        Assertions.assertThat(codec.decode(CodecFormat.JSON, new JsonPrimitive(300))).isEqualTo(300);
        Assertions.assertThat(codec.encode(CodecFormat.NBT, 300)).isEqualTo(IntTag.valueOf(300));
        Assertions.assertThat(codec.decode(CodecFormat.NBT, IntTag.valueOf(300))).isEqualTo(300);
    }
    
    @Test
    public void dfuCodecFailsWithTheMessageOfTheDfuError() {
        JsonElement input = new JsonPrimitive("many");
        String message = Codec.INT.parse(JsonOps.INSTANCE, input).error().orElseThrow().message();
        
        Assertions.assertThat(WrappersTest.errors(() -> NexusCodec.ofDfu(Codec.INT).decode(CodecFormat.JSON, input))).containsExactly(new CodecError("", message));
    }
    
    @Test
    public void dfuCodecOnNettyWritesWhatFromCodecWrites() {
        NexusCodec<Integer, Access.Plain> codec = NexusCodec.ofDfu(Codec.INT);
        FriendlyByteBuf nexus = new FriendlyByteBuf(Unpooled.buffer());
        FriendlyByteBuf vanilla = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(CodecFormat.netty(nexus), 300);
        ByteBufCodecs.fromCodec(Codec.INT).encode(vanilla, 300);
        FriendlyByteBuf read = new FriendlyByteBuf(Unpooled.wrappedBuffer(WrappersTest.bytes(0x03, 0x00, 0x00, 0x01, 0x2C)));
        
        Assertions.assertThat(ByteBufUtil.getBytes(nexus)).isEqualTo(WrappersTest.bytes(0x03, 0x00, 0x00, 0x01, 0x2C));
        Assertions.assertThat(ByteBufUtil.getBytes(nexus)).isEqualTo(ByteBufUtil.getBytes(vanilla));
        Assertions.assertThat(codec.decode(CodecFormat.netty(read))).isEqualTo(300);
        Assertions.assertThat(read.readableBytes()).isZero();
    }
    
    @Test
    public void registryDfuCodecRunsWithTheRegistriesOfTheFormat() {
        Codec<Holder<Item>> dfu = RegistryFixedCodec.create(Registries.ITEM);
        NexusCodec<Holder<Item>, Access.Registries> codec = NexusCodec.ofDfu(dfu, Access.Registries.class);
        RegistryFriendlyByteBuf vanilla = WrappersTest.writable();
        ByteBufCodecs.fromCodecWithRegistries(dfu).encode(vanilla, WrappersTest.STONE);
        
        Assertions.assertThat(codec.encode(WrappersTest.JSON, WrappersTest.STONE)).hasToString("\"minecraft:stone\"");
        Assertions.assertThat(codec.decode(WrappersTest.NBT, StringTag.valueOf("minecraft:stone"))).isEqualTo(WrappersTest.STONE);
        WrappersTest.assertNetty(codec, WrappersTest.STONE, 0x08, 0x00, 0x0F, "minecraft:stone");
        Assertions.assertThat(WrappersTest.bytes(0x08, 0x00, 0x0F, "minecraft:stone")).isEqualTo(ByteBufUtil.getBytes(vanilla));
    }
    
    @Test
    public void streamCodecAloneFailsInJsonAndNbt() {
        NexusCodec<Integer, Access.Plain> codec = NexusCodec.ofStream(ByteBufCodecs.VAR_INT);
        
        Assertions.assertThat(WrappersTest.errors(() -> codec.encode(CodecFormat.JSON, 300)))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.message()).contains("netty"));
        Assertions.assertThat(WrappersTest.errors(() -> codec.decode(CodecFormat.NBT, IntTag.valueOf(300))))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.message()).contains("netty"));
    }
    
    @Test
    public void streamCodecAloneRunsOnNetty() {
        NexusCodec<Integer, Access.Plain> codec = NexusCodec.ofStream(ByteBufCodecs.VAR_INT);
        FriendlyByteBuf written = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(CodecFormat.netty(written), 300);
        FriendlyByteBuf read = new FriendlyByteBuf(Unpooled.wrappedBuffer(WrappersTest.bytes(0xAC, 0x02)));
        
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(WrappersTest.bytes(0xAC, 0x02));
        Assertions.assertThat(codec.decode(CodecFormat.netty(read))).isEqualTo(300);
        Assertions.assertThat(read.readableBytes()).isZero();
    }
    
    @Test
    public void streamCodecExceptionsBecomeCodecExceptions() {
        FriendlyByteBuf empty = new FriendlyByteBuf(Unpooled.buffer());
        
        Assertions.assertThat(WrappersTest.errors(() -> NexusCodec.ofStream(ByteBufCodecs.VAR_INT).decode(CodecFormat.netty(empty))))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.path()).isEmpty());
    }
    
    @Test
    public void registryStreamCodecRunsOnTheRegistryBuffer() {
        NexusCodec<Holder<Item>, Access.Registries> codec = NexusCodec.ofStream(ByteBufCodecs.holderRegistry(Registries.ITEM), Access.Registries.class);
        
        WrappersTest.assertNetty(codec, WrappersTest.FIRST_ITEM, 0x01);
        Assertions.assertThat(WrappersTest.errors(() -> codec.encode(WrappersTest.JSON, WrappersTest.STONE)))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.message()).contains("netty"));
    }
    
    @Test
    public void vanillaPairUsesEachSideOnItsFormats() {
        NexusCodec<Integer, Access.Plain> codec = NexusCodec.ofVanilla(Codec.INT, ByteBufCodecs.VAR_INT);
        
        Assertions.assertThat(codec.encode(CodecFormat.JSON, 300)).hasToString("300");
        Assertions.assertThat(codec.decode(CodecFormat.NBT, IntTag.valueOf(300))).isEqualTo(300);
        WrappersTest.assertNetty(codec, 300, 0xAC, 0x02);
    }
    
    @Test
    public void registryPairRunsWithTheRegistriesOfTheFormatAndTheBuffer() {
        NexusCodec<Holder<Item>, Access.Registries> codec = NexusCodec.ofVanilla(
                RegistryFixedCodec.create(Registries.ITEM),
                ByteBufCodecs.holderRegistry(Registries.ITEM),
                Access.Registries.class
        );
        
        Assertions.assertThat(codec.encode(WrappersTest.JSON, WrappersTest.STONE)).hasToString("\"minecraft:stone\"");
        Assertions.assertThat(codec.decode(WrappersTest.JSON, JsonParser.parseString("\"minecraft:stone\""))).isEqualTo(WrappersTest.STONE);
        Assertions.assertThat(codec.encode(WrappersTest.NBT, WrappersTest.STONE)).isEqualTo(StringTag.valueOf("minecraft:stone"));
        WrappersTest.assertNetty(codec, WrappersTest.FIRST_ITEM, 0x01);
    }
    
    @Test
    public void wrappedCodecsWorkAsFieldsAndElements() {
        StructCodec<Tagged, Access.Plain> tagged = NexusCodec.struct(
                "tagged",
                NexusCodec.ofDfu(Codec.STRING).field("name", Tagged::name),
                NexusCodec.ofVanilla(Codec.INT, ByteBufCodecs.VAR_INT).field("count", Tagged::count),
                Tagged::new
        );
        NexusCodec<List<Integer>, Access.Plain> counts = NexusCodec.ofDfu(Codec.INT).list();
        
        Assertions.assertThat(tagged.encode(CodecFormat.JSON, new Tagged("a", 300))).hasToString("{\"name\":\"a\",\"count\":300}");
        WrappersTest.assertNetty(tagged, new Tagged("a", 300), 0x08, 0x00, 0x01, "a", 0xAC, 0x02);
        Assertions.assertThat(WrappersTest.errors(() -> tagged.decode(CodecFormat.JSON, JsonParser.parseString("{\"name\":\"a\",\"count\":\"many\"}"))))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.path()).isEqualTo("count"));
        Assertions.assertThat(counts.decode(CodecFormat.JSON, JsonParser.parseString("[1,2]"))).containsExactly(1, 2);
        Assertions.assertThat(WrappersTest.errors(() -> counts.decode(CodecFormat.JSON, JsonParser.parseString("[1,\"many\"]"))))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.path()).isEqualTo("[1]"));
    }
    
    private static <T> void assertNetty(NexusCodec<T, ? super Access.Registries> codec, T value, Object... bytes) {
        RegistryFriendlyByteBuf written = WrappersTest.writable();
        codec.encode(CodecFormat.netty(written), value);
        RegistryFriendlyByteBuf read = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(WrappersTest.bytes(bytes)), WrappersTest.REGISTRIES);
        
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(WrappersTest.bytes(bytes));
        Assertions.assertThat(codec.decode(CodecFormat.netty(read))).isEqualTo(value);
        Assertions.assertThat(read.readableBytes()).isZero();
    }
    
    private static List<CodecError> errors(ThrowableAssert.ThrowingCallable call) {
        return Assertions.catchThrowableOfType(CodecException.class, call).errors();
    }
    
    private static RegistryFriendlyByteBuf writable() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), WrappersTest.REGISTRIES);
    }
    
    private static byte[] bytes(Object... parts) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (Object part : parts) {
            switch (part) {
                case String text -> bytes.writeBytes(text.getBytes(StandardCharsets.UTF_8));
                case Integer value -> bytes.write(value);
                default -> throw new IllegalArgumentException("Could not write '" + part + "' as bytes");
            }
        }
        
        return bytes.toByteArray();
    }
    
    public record Tagged(String name, int count) { }
}
