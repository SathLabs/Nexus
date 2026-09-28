package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.HashOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.google.common.hash.HashCode;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// Checks the DFU codec, map codec, and stream codec views of codecs, and the data component types and recipe serializers built from them.
///
public class ViewsTest {
    
    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final Holder<Item> STONE = BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE);
    private static final Holder<Item> FIRST_ITEM = BuiltInRegistries.ITEM.get(1).orElseThrow();
    
    private static final ParityCases.Listing DIRT = new ParityCases.Listing("dirt", 64, Optional.of("cheap"));
    private static final String DIRT_JSON = "{\"item\":\"dirt\",\"count\":64,\"label\":\"cheap\"}";
    private static final Object[] DIRT_BYTES = { 0x04, "dirt", 0x00, 0x00, 0x00, 0x40, 0x01, 0x05, "cheap" };
    
    private static final StructCodec<ParityCases.Listing, Access.Plain> LISTING = NexusCodec.struct(
            "listing",
            NexusCodec.STRING.field("item", ParityCases.Listing::item),
            NexusCodec.INT.optionalField("count", 1, ParityCases.Listing::count),
            NexusCodec.STRING.optionalField("label", ParityCases.Listing::label),
            ParityCases.Listing::new
    );
    
    private static final StructCodec<ParityCases.Circle, Access.Plain> CIRCLE = NexusCodec.struct(
            "circle",
            NexusCodec.INT.field("radius", ParityCases.Circle::radius),
            ParityCases.Circle::new
    );
    
    private static final StructCodec<ParityCases.Square, Access.Plain> SQUARE = NexusCodec.struct(
            "square",
            NexusCodec.INT.field("side", ParityCases.Square::side),
            ParityCases.Square::new
    );
    
    private static final StructCodec<ParityCases.Shape, Access.Plain> SHAPE = NexusCodec.dispatch(
            "type",
            ParityCases::typeOf,
            Map.of(ParityCases.CIRCLE, ViewsTest.CIRCLE, ParityCases.SQUARE, ViewsTest.SQUARE)
    );
    
    private static final StructCodec<Smelting, Access.Registries> SMELTING = NexusCodec.struct(
            "smelting",
            NexusCodec.holder(Registries.ITEM).field("ingredient", Smelting::ingredient),
            NexusCodec.VAR_INT.field("time", Smelting::time),
            Smelting::new
    );
    
    @Test
    public void dfuViewWritesAndReadsJsonAndNbt() {
        Codec<ParityCases.Listing> view = ViewsTest.LISTING.asDfu();
        CompoundTag tag = new CompoundTag();
        tag.putString("item", "dirt");
        tag.putInt("count", 64);
        tag.putString("label", "cheap");
        
        Assertions.assertThat(view.encodeStart(JsonOps.INSTANCE, ViewsTest.DIRT).getOrThrow()).hasToString(ViewsTest.DIRT_JSON);
        Assertions.assertThat(view.parse(JsonOps.INSTANCE, JsonParser.parseString(ViewsTest.DIRT_JSON)).getOrThrow()).isEqualTo(ViewsTest.DIRT);
        Assertions.assertThat(view.encodeStart(NbtOps.INSTANCE, ViewsTest.DIRT).getOrThrow()).isEqualTo(tag);
        Assertions.assertThat(view.parse(NbtOps.INSTANCE, tag).getOrThrow()).isEqualTo(ViewsTest.DIRT);
    }
    
    @Test
    public void dfuViewKeepsTheTagTypesOfNbt() {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, ViewsTest.REGISTRIES);
        
        Assertions.assertThat(NexusCodec.LONG.asDfu().encodeStart(NbtOps.INSTANCE, 5L).getOrThrow()).isEqualTo(LongTag.valueOf(5L));
        Assertions.assertThat(NexusCodec.LONG.asDfu().encodeStart(ops, 5L).getOrThrow()).isEqualTo(LongTag.valueOf(5L));
        Assertions.assertThat(NexusCodec.SHORT.asDfu().parse(ops, ShortTag.valueOf((short) 5)).getOrThrow()).isEqualTo((short) 5);
    }
    
    @Test
    public void dfuViewConvertsOtherOpsThroughJson() {
        Codec<ParityCases.Listing> view = ViewsTest.LISTING.asDfu();
        
        Assertions.assertThat(view.encodeStart(JavaOps.INSTANCE, ViewsTest.DIRT).getOrThrow()).isEqualTo(Map.of("item", "dirt", "count", 64, "label", "cheap"));
        Assertions.assertThat(view.parse(JavaOps.INSTANCE, Map.of("item", "dirt", "count", 64, "label", "cheap")).getOrThrow()).isEqualTo(ViewsTest.DIRT);
    }
    
    @Test
    public void viewsHashThroughRegistryOpsOverHashOps() {
        RegistryOps<HashCode> ops = ViewsTest.REGISTRIES.createSerializationContext(HashOps.CRC32C_INSTANCE);
        DataComponentType<ParityCases.Listing> type = ViewsTest.LISTING.asDataComponentType();
        Codec<List<Integer>> counts = NexusCodec.INT.list().asDfu();
        HashCode dirt = new TypedDataComponent<>(type, ViewsTest.DIRT).encodeValue(ops).getOrThrow();
        HashCode ascending = counts.encodeStart(ops, List.of(1, 2)).getOrThrow();
        
        Assertions.assertThat(new TypedDataComponent<>(type, new ParityCases.Listing("dirt", 64, Optional.of("cheap"))).encodeValue(ops).getOrThrow()).isEqualTo(dirt);
        Assertions.assertThat(new TypedDataComponent<>(type, new ParityCases.Listing("dirt", 63, Optional.of("cheap"))).encodeValue(ops).getOrThrow()).isNotEqualTo(dirt);
        Assertions.assertThat(counts.encodeStart(ops, List.of(1, 2)).getOrThrow()).isEqualTo(ascending);
        Assertions.assertThat(counts.encodeStart(ops, List.of(2, 1)).getOrThrow()).isNotEqualTo(ascending);
    }
    
    @Test
    public void viewsOverOtherOpsWriteWhatTheDfuTwinWrites() {
        RegistryOps<HashCode> hash = ViewsTest.REGISTRIES.createSerializationContext(HashOps.CRC32C_INSTANCE);
        Codec<ParityCases.Listing> view = ViewsTest.LISTING.asDfu();
        Codec<ParityCases.Listing> twin = ParityCases.LISTING_DFU.codec();
        List<Double> doubles = List.of(0.5D, -2.0D);
        
        Assertions.assertThat(view.encodeStart(hash, ViewsTest.DIRT).getOrThrow()).isEqualTo(twin.encodeStart(hash, ViewsTest.DIRT).getOrThrow());
        Assertions.assertThat(view.encodeStart(JavaOps.INSTANCE, ViewsTest.DIRT).getOrThrow()).isEqualTo(twin.encodeStart(JavaOps.INSTANCE, ViewsTest.DIRT).getOrThrow());
        Assertions.assertThat(NexusCodec.BYTE.asDfu().encodeStart(hash, (byte) 5).getOrThrow()).isEqualTo(Codec.BYTE.encodeStart(hash, (byte) 5).getOrThrow());
        Assertions.assertThat(NexusCodec.SHORT.asDfu().encodeStart(hash, (short) 5).getOrThrow()).isEqualTo(Codec.SHORT.encodeStart(hash, (short) 5).getOrThrow());
        Assertions.assertThat(NexusCodec.LONG.asDfu().encodeStart(hash, 5L).getOrThrow()).isEqualTo(Codec.LONG.encodeStart(hash, 5L).getOrThrow());
        Assertions.assertThat(NexusCodec.FLOAT.asDfu().encodeStart(hash, 5.0F).getOrThrow()).isEqualTo(Codec.FLOAT.encodeStart(hash, 5.0F).getOrThrow());
        Assertions.assertThat(NexusCodec.DOUBLE.list().asDfu().encodeStart(hash, doubles).getOrThrow()).isEqualTo(Codec.DOUBLE.listOf().encodeStart(hash, doubles).getOrThrow());
        Assertions.assertThat(NexusCodec.DOUBLE.list().asDfu().encodeStart(JavaOps.INSTANCE, doubles).getOrThrow()).isEqualTo(Codec.DOUBLE.listOf().encodeStart(JavaOps.INSTANCE, doubles).getOrThrow());
    }
    
    @Test
    public void otherOpsTakeJsonNullAsTheirEmptyValue() {
        StructCodec<Note, Access.Plain> note = NexusCodec.struct(
                "note",
                NexusCodec.STRING.field("name", Note::name),
                NexusCodec.ofDfu(ExtraCodecs.JSON).field("value", Note::value),
                Note::new
        );
        Codec<List<JsonElement>> values = NexusCodec.ofDfu(ExtraCodecs.JSON).list().asDfu();
        RegistryOps<HashCode> hash = ViewsTest.REGISTRIES.createSerializationContext(HashOps.CRC32C_INSTANCE);
        Map<String, @Nullable Object> withNull = new HashMap<>();
        withNull.put("name", "empty");
        withNull.put("value", null);
        
        Assertions.assertThat(note.asDfu().encodeStart(JavaOps.INSTANCE, new Note("empty", JsonNull.INSTANCE)).getOrThrow()).isEqualTo(Map.of("name", "empty"));
        Assertions.assertThat(note.asDfu().encodeStart(hash, new Note("empty", JsonNull.INSTANCE)).isSuccess()).isTrue();
        Assertions.assertThat(values.encodeStart(JavaOps.INSTANCE, List.of(JsonNull.INSTANCE, new JsonPrimitive("x"))).getOrThrow()).isEqualTo(Arrays.asList(null, "x"));
        Assertions.assertThat(values.parse(JavaOps.INSTANCE, Arrays.asList(null, "x")).getOrThrow()).containsExactly(JsonNull.INSTANCE, new JsonPrimitive("x"));
        Assertions.assertThat(ViewsTest.errorOf(note.asDfu().parse(JavaOps.INSTANCE, withNull))).contains("value", "Missing");
    }
    
    @Test
    public void dfuViewFailsOverOpsThatCompressMaps() {
        Codec<ParityCases.Listing> view = ViewsTest.LISTING.asDfu();
        DataResult<JsonElement> encoded = view.encodeStart(JsonOps.COMPRESSED, ViewsTest.DIRT);
        DataResult<ParityCases.Listing> decoded = view.parse(JsonOps.COMPRESSED, JsonParser.parseString("[\"dirt\",64,\"cheap\"]"));
        
        Assertions.assertThat(ViewsTest.errorOf(encoded)).contains("compress");
        Assertions.assertThat(ViewsTest.errorOf(decoded)).contains("compress");
        Assertions.assertThat(encoded.hasResultOrPartial()).isFalse();
        Assertions.assertThat(decoded.hasResultOrPartial()).isFalse();
    }
    
    @Test
    public void dfuViewFailsWithTheMessageOfTheNexusCodecException() {
        NexusCodec<Integer, Access.Plain> codec = NexusCodec.INT.validate(value -> value < 0 ? "negative" : null);
        DataResult<JsonElement> encoded = codec.asDfu().encodeStart(JsonOps.INSTANCE, -1);
        NexusCodecException failure = Assertions.catchThrowableOfType(NexusCodecException.class, () -> codec.encode(CodecFormat.JSON, -1));
        
        Assertions.assertThat(ViewsTest.errorOf(encoded)).isEqualTo(failure.getMessage());
        Assertions.assertThat(encoded.hasResultOrPartial()).isFalse();
    }
    
    @Test
    public void dfuViewReadsJsonNullElementsAsJsonNull() {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, ViewsTest.REGISTRIES);
        DataResult<List<HolderSet<Item>>> sets = NexusCodec.holderSet(Registries.ITEM).asDfu().listOf().parse(ops, JsonParser.parseString("[\"minecraft:stone\",null]"));
        DataResult<List<String>> strings = NexusCodec.STRING.asDfu().listOf().parse(JsonOps.INSTANCE, JsonParser.parseString("[\"stone\",null]"));
        
        Assertions.assertThat(ViewsTest.errorOf(sets)).contains("Expected a list, found null");
        Assertions.assertThat(ViewsTest.errorOf(strings)).contains("Expected a string, found null");
    }
    
    @Test
    public void registryViewFailsOverPlainOps() {
        Codec<Holder<Item>> view = NexusCodec.holder(Registries.ITEM).asDfu();
        
        Assertions.assertThat(ViewsTest.errorOf(view.encodeStart(JsonOps.INSTANCE, ViewsTest.STONE))).contains("minecraft:item", "registry ops");
        Assertions.assertThat(ViewsTest.errorOf(view.parse(NbtOps.INSTANCE, StringTag.valueOf("minecraft:stone")))).contains("minecraft:item", "registry ops");
    }
    
    @Test
    public void registryViewTakesTheRegistriesOfRegistryOps() {
        Codec<Holder<Item>> view = NexusCodec.holder(Registries.ITEM).asDfu();
        RegistryOps<JsonElement> json = RegistryOps.create(JsonOps.INSTANCE, ViewsTest.REGISTRIES);
        RegistryOps<Tag> nbt = RegistryOps.create(NbtOps.INSTANCE, new RegistryOps.HolderLookupAdapter(ViewsTest.REGISTRIES));
        RegistryOps<Object> java = RegistryOps.create(JavaOps.INSTANCE, ViewsTest.REGISTRIES);
        
        Assertions.assertThat(view.encodeStart(json, ViewsTest.STONE).getOrThrow()).hasToString("\"minecraft:stone\"");
        Assertions.assertThat(view.parse(nbt, StringTag.valueOf("minecraft:stone")).getOrThrow()).isEqualTo(ViewsTest.STONE);
        Assertions.assertThat(view.encodeStart(java, ViewsTest.STONE).getOrThrow()).isEqualTo("minecraft:stone");
        Assertions.assertThat(view.parse(java, "minecraft:stone").getOrThrow()).isEqualTo(ViewsTest.STONE);
    }
    
    @Test
    public void dfuViewOfStructInlinesIntoDfuDispatchLikeARecordCodec() {
        Codec<ParityCases.Circle> circle = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("radius").forGetter(ParityCases.Circle::radius)
        ).apply(instance, ParityCases.Circle::new));
        Codec<ParityCases.Square> square = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("side").forGetter(ParityCases.Square::side)
        ).apply(instance, ParityCases.Square::new));
        Codec<ParityCases.Shape> twin = Identifier.CODEC.dispatch(
                "type",
                ParityCases::typeOf,
                id -> id.equals(ParityCases.CIRCLE) ? ViewsTest.mapCodecOf(circle) : ViewsTest.mapCodecOf(square)
        );
        Codec<ParityCases.Shape> nexus = Identifier.CODEC.dispatch(
                "type",
                ParityCases::typeOf,
                id -> id.equals(ParityCases.CIRCLE) ? ViewsTest.mapCodecOf(ViewsTest.CIRCLE.asDfu()) : ViewsTest.mapCodecOf(ViewsTest.SQUARE.asDfu())
        );
        
        for (ParityCases.Shape shape : List.of(new ParityCases.Circle(3), new ParityCases.Square(4))) {
            JsonElement json = twin.encodeStart(JsonOps.INSTANCE, shape).getOrThrow();
            Tag tag = twin.encodeStart(NbtOps.INSTANCE, shape).getOrThrow();
            
            Assertions.assertThat(nexus.encodeStart(JsonOps.INSTANCE, shape).getOrThrow()).hasToString(json.toString());
            Assertions.assertThat(nexus.encodeStart(NbtOps.INSTANCE, shape).getOrThrow()).isEqualTo(tag);
            Assertions.assertThat(nexus.parse(JsonOps.INSTANCE, json).getOrThrow()).isEqualTo(shape);
            Assertions.assertThat(nexus.parse(NbtOps.INSTANCE, tag).getOrThrow()).isEqualTo(shape);
        }
    }
    
    @Test
    public void streamViewWritesAndReadsTheNettyLayout() {
        StreamCodec<RegistryFriendlyByteBuf, ParityCases.Listing> stream = ViewsTest.LISTING.asStream();
        RegistryFriendlyByteBuf written = ViewsTest.writable();
        stream.encode(written, ViewsTest.DIRT);
        RegistryFriendlyByteBuf read = ViewsTest.readable(ViewsTest.DIRT_BYTES);
        
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(ViewsTest.bytes(ViewsTest.DIRT_BYTES));
        Assertions.assertThat(stream.decode(read)).isEqualTo(ViewsTest.DIRT);
        Assertions.assertThat(read.readableBytes()).isZero();
    }
    
    @Test
    public void streamViewOfRegistryCodecUsesTheRegistriesOfTheBuffer() {
        StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> stream = NexusCodec.holder(Registries.ITEM).asStream();
        RegistryFriendlyByteBuf written = ViewsTest.writable();
        stream.encode(written, ViewsTest.FIRST_ITEM);
        
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(ViewsTest.bytes(0x01));
        Assertions.assertThat(stream.decode(ViewsTest.readable(0x01))).isEqualTo(ViewsTest.FIRST_ITEM);
    }
    
    @Test
    public void streamViewThrowsVanillaExceptionsWithTheMessage() {
        StreamCodec<RegistryFriendlyByteBuf, Integer> stream = NexusCodec.INT.validate(value -> value < 0 ? "negative" : null).asStream();
        
        Assertions.assertThatThrownBy(() -> stream.encode(ViewsTest.writable(), -1))
                .isInstanceOf(EncoderException.class)
                .hasMessageContaining("Could not encode")
                .hasMessageContaining("negative")
                .hasCauseInstanceOf(NexusCodecException.class);
        Assertions.assertThatThrownBy(() -> stream.decode(ViewsTest.readable(0xFF, 0xFF, 0xFF, 0xFF)))
                .isInstanceOf(DecoderException.class)
                .hasMessageContaining("Could not decode")
                .hasMessageContaining("negative")
                .hasCauseInstanceOf(NexusCodecException.class);
    }
    
    @Test
    public void plainStreamWritesAndReadsOnAPlainBuffer() {
        StreamCodec<FriendlyByteBuf, ParityCases.Listing> stream = NexusCodec.plainStream(ViewsTest.LISTING);
        FriendlyByteBuf written = new FriendlyByteBuf(Unpooled.buffer());
        stream.encode(written, ViewsTest.DIRT);
        FriendlyByteBuf read = new FriendlyByteBuf(Unpooled.wrappedBuffer(ViewsTest.bytes(ViewsTest.DIRT_BYTES)));
        
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(ViewsTest.bytes(ViewsTest.DIRT_BYTES));
        Assertions.assertThat(stream.decode(read)).isEqualTo(ViewsTest.DIRT);
        Assertions.assertThat(read.readableBytes()).isZero();
        Assertions.assertThatThrownBy(() -> stream.decode(new FriendlyByteBuf(Unpooled.buffer()))).isInstanceOf(DecoderException.class);
    }
    
    @Test
    public void mapCodecHasTheKeysOfTheStruct() {
        Assertions.assertThat(ViewsTest.LISTING.asMapCodec().keys(JsonOps.INSTANCE).map(JsonElement::getAsString)).containsExactlyInAnyOrder("item", "count", "label");
        Assertions.assertThat(ViewsTest.SHAPE.asMapCodec().keys(JsonOps.INSTANCE).map(JsonElement::getAsString)).containsExactlyInAnyOrder("type", "radius", "side");
    }
    
    @Test
    public void mapCodecMergesIntoTheObjectOfADfuRecord() {
        Codec<ParityCases.Offer> offer = RecordCodecBuilder.create(instance -> instance.group(
                ViewsTest.LISTING.asMapCodec().forGetter(ParityCases.Offer::listing),
                Codec.INT.fieldOf("price").forGetter(ParityCases.Offer::price)
        ).apply(instance, ParityCases.Offer::new));
        String json = "{\"item\":\"dirt\",\"count\":64,\"label\":\"cheap\",\"price\":5}";
        
        Assertions.assertThat(offer.encodeStart(JsonOps.INSTANCE, new ParityCases.Offer(ViewsTest.DIRT, 5)).getOrThrow()).hasToString(json);
        Assertions.assertThat(offer.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow()).isEqualTo(new ParityCases.Offer(ViewsTest.DIRT, 5));
    }
    
    @Test
    public void mapCodecOfDispatchReadsTheSubtypeFromTheMap() {
        Codec<ParityCases.Shape> codec = ViewsTest.SHAPE.asMapCodec().codec();
        
        Assertions.assertThat(codec.encodeStart(JsonOps.INSTANCE, new ParityCases.Circle(3)).getOrThrow()).hasToString("{\"radius\":3,\"type\":\"nexus:circle\"}");
        Assertions.assertThat(codec.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"nexus:square\",\"side\":4}")).getOrThrow()).isEqualTo(new ParityCases.Square(4));
    }
    
    @Test
    public void dataComponentTypeRoundTripsThroughItsCodecAndStreamCodec() {
        DataComponentType<ParityCases.Listing> type = ViewsTest.LISTING.asDataComponentType();
        RegistryFriendlyByteBuf written = ViewsTest.writable();
        type.streamCodec().encode(written, ViewsTest.DIRT);
        RegistryFriendlyByteBuf read = ViewsTest.readable(ViewsTest.DIRT_BYTES);
        
        Assertions.assertThat(type.isTransient()).isFalse();
        Assertions.assertThat(type.codecOrThrow().encodeStart(JsonOps.INSTANCE, ViewsTest.DIRT).getOrThrow()).hasToString(ViewsTest.DIRT_JSON);
        Assertions.assertThat(type.codecOrThrow().parse(JsonOps.INSTANCE, JsonParser.parseString(ViewsTest.DIRT_JSON)).getOrThrow()).isEqualTo(ViewsTest.DIRT);
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(ViewsTest.bytes(ViewsTest.DIRT_BYTES));
        Assertions.assertThat(type.streamCodec().decode(read)).isEqualTo(ViewsTest.DIRT);
    }
    
    @Test
    public void recipeSerializerRunsTheMapCodecAndStreamCodecOfTheStruct() {
        RecipeSerializer<Smelting> serializer = StructCodec.recipeSerializer(ViewsTest.SMELTING);
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, ViewsTest.REGISTRIES);
        String json = "{\"ingredient\":\"minecraft:stone\",\"time\":200}";
        //noinspection deprecation Vanilla deprecated the stream codec of a recipe serializer.
        StreamCodec<RegistryFriendlyByteBuf, Smelting> stream = serializer.streamCodec();
        RegistryFriendlyByteBuf written = ViewsTest.writable();
        stream.encode(written, new Smelting(ViewsTest.FIRST_ITEM, 200));
        
        Assertions.assertThat(serializer.codec().codec().encodeStart(ops, new Smelting(ViewsTest.STONE, 200)).getOrThrow()).hasToString(json);
        Assertions.assertThat(serializer.codec().codec().parse(ops, JsonParser.parseString(json)).getOrThrow()).isEqualTo(new Smelting(ViewsTest.STONE, 200));
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(ViewsTest.bytes(0x01, 0xC8, 0x01));
        Assertions.assertThat(stream.decode(ViewsTest.readable(0x01, 0xC8, 0x01))).isEqualTo(new Smelting(ViewsTest.FIRST_ITEM, 200));
    }
    
    private static MapCodec<? extends ParityCases.Shape> mapCodecOf(Codec<? extends ParityCases.Shape> codec) {
        Assertions.assertThat(codec).isInstanceOf(MapCodec.MapCodecCodec.class);
        return ((MapCodec.MapCodecCodec<? extends ParityCases.Shape>) codec).codec();
    }
    
    private static String errorOf(DataResult<?> result) {
        return result.error().orElseThrow().message();
    }
    
    private static RegistryFriendlyByteBuf writable() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), ViewsTest.REGISTRIES);
    }
    
    private static RegistryFriendlyByteBuf readable(Object... bytes) {
        return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(ViewsTest.bytes(bytes)), ViewsTest.REGISTRIES);
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
    
    public record Note(String name, JsonElement value) { }
    
    public record Smelting(Holder<Item> ingredient, int time) implements Recipe<RecipeInput> {
        
        @Override
        public boolean matches(RecipeInput input, Level level) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public ItemStack assemble(RecipeInput input) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public boolean showNotification() {
            return false;
        }
        
        @Override
        public String group() {
            return "";
        }
        
        @Override
        public RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public RecipeType<? extends Recipe<RecipeInput>> getType() {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public PlacementInfo placementInfo() {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public RecipeBookCategory recipeBookCategory() {
            throw new UnsupportedOperationException();
        }
    }
}
