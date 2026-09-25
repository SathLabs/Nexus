package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.CodecError;
import dev.satherov.nexus.api.codec.CodecException;
import dev.satherov.nexus.api.codec.CodecFormat;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.StructCodec;

import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.registries.holdersets.AndHolderSet;
import net.neoforged.neoforge.registries.holdersets.AnyHolderSet;
import net.neoforged.neoforge.registries.holdersets.NotHolderSet;
import net.neoforged.neoforge.registries.holdersets.OrHolderSet;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gameevent.GameEvent;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
import java.util.Map;
import java.util.Optional;

///
/// Checks the forms holders and holder sets take in JSON, NBT, and on the network, their failures, and plain codecs on formats with registries.
///
public class HoldersTest {
    
    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final CodecFormat<JsonElement, Access.Registries> JSON = CodecFormat.JSON.withRegistries(HoldersTest.REGISTRIES);
    private static final CodecFormat<Tag, Access.Registries> NBT = CodecFormat.NBT.withRegistries(HoldersTest.REGISTRIES);
    private static final RegistryOps<JsonElement> JSON_OPS = RegistryOps.create(JsonOps.INSTANCE, HoldersTest.REGISTRIES);
    
    private static final Holder<Item> STONE = BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE);
    private static final Holder<Item> DIRT = BuiltInRegistries.ITEM.wrapAsHolder(Items.DIRT);
    private static final Holder<Item> FIRST_ITEM = BuiltInRegistries.ITEM.get(1).orElseThrow();
    private static final Holder<Item> LATER_ITEM = BuiltInRegistries.ITEM.get(200).orElseThrow();
    private static final HolderSet<Item> TOOL_MATERIALS = BuiltInRegistries.ITEM.getOrThrow(ItemTags.WOODEN_TOOL_MATERIALS);
    private static final SoundEvent BOOM = new SoundEvent(Identifier.fromNamespaceAndPath("nexus", "boom"), Optional.empty());
    
    private static final NexusCodec<Holder<Item>, Access.Registries> ITEM = NexusCodec.holder(Registries.ITEM);
    private static final NexusCodec<HolderSet<Item>, Access.Registries> ITEMS = NexusCodec.holderSet(Registries.ITEM);
    private static final Codec<HolderSet<Item>> ITEMS_NEOFORGE = RegistryCodecs.homogeneousList(Registries.ITEM);
    private static final StreamCodec<RegistryFriendlyByteBuf, HolderSet<Item>> ITEMS_STREAM = ByteBufCodecs.holderSet(Registries.ITEM);
    
    private static final NexusCodec<Holder<SoundEvent>, Access.Registries> SOUND = NexusCodec.holderOrInline(
            Registries.SOUND_EVENT,
            NexusCodec.struct(
                    "sound_event",
                    NexusCodec.IDENTIFIER.field("sound_id", SoundEvent::location),
                    NexusCodec.FLOAT.optionalField("range", SoundEvent::fixedRange),
                    SoundEvent::new
            )
    );
    
    private static final StructCodec<Stack, Access.Registries> STACK = NexusCodec.struct(
            "stack",
            NexusCodec.holder(Registries.ITEM).field("item", Stack::item),
            NexusCodec.INT.field("count", Stack::count),
            Stack::new
    );
    
    @Test
    public void holderIsItsIdentifierInJsonAndNbt() {
        HoldersTest.assertJson(HoldersTest.ITEM, HoldersTest.STONE, "\"minecraft:stone\"");
        HoldersTest.assertNbt(HoldersTest.ITEM, HoldersTest.DIRT, StringTag.valueOf("minecraft:dirt"));
    }
    
    @Test
    public void holderIsItsRegistryIdAsVarIntOnNetwork() {
        HoldersTest.assertNetty(HoldersTest.ITEM, HoldersTest.FIRST_ITEM, 0x01);
        HoldersTest.assertNetty(HoldersTest.ITEM, HoldersTest.LATER_ITEM, 0xC8, 0x01);
    }
    
    @Test
    public void holderFailsNamingUnknownIdentifierAndRegistry() {
        List<CodecError> errors = HoldersTest.errors(() -> HoldersTest.ITEM.decode(HoldersTest.JSON, JsonParser.parseString("\"nexus:nope\"")));
        
        Assertions.assertThat(errors).singleElement().satisfies(error -> {
            Assertions.assertThat(error.path()).isEmpty();
            Assertions.assertThat(error.message()).contains("\"nexus:nope\"", "minecraft:item");
        });
    }
    
    @Test
    public void holderFailsNamingUnknownIdAndRegistryOnNetwork() {
        RegistryFriendlyByteBuf buffer = HoldersTest.readable(0xFF, 0xFF, 0x03);
        
        Assertions.assertThatThrownBy(() -> HoldersTest.ITEM.decode(CodecFormat.netty(buffer)))
                .isInstanceOf(CodecException.class)
                .hasMessageContainingAll("65535", "minecraft:item");
    }
    
    @Test
    public void holderRefusesDirectHolderInJsonAndNbt() {
        Holder<Item> direct = Holder.direct(Items.STONE);
        
        Assertions.assertThatThrownBy(() -> HoldersTest.ITEM.encode(HoldersTest.JSON, direct)).isInstanceOf(CodecException.class);
        Assertions.assertThatThrownBy(() -> HoldersTest.ITEM.encode(HoldersTest.NBT, direct)).isInstanceOf(CodecException.class);
    }
    
    @Test
    public void holderFailsNamingRegistryTheFormatDoesNotHave() {
        NexusCodec<Holder<Biome>, Access.Registries> biome = NexusCodec.holder(Registries.BIOME);
        
        Assertions.assertThatThrownBy(() -> biome.decode(HoldersTest.JSON, JsonParser.parseString("\"minecraft:plains\"")))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining("minecraft:worldgen/biome");
    }
    
    @Test
    public void holderRefusesBuiltInRegistryThatIsNotSyncedOnNetwork() {
        NexusCodec<Holder<GameEvent>, Access.Registries> codec = NexusCodec.holder(Registries.GAME_EVENT);
        
        Assertions.assertThatThrownBy(() -> ByteBufCodecs.holderRegistry(Registries.GAME_EVENT).encode(HoldersTest.writable(ConnectionType.OTHER), GameEvent.STEP))
                .hasMessageContaining("minecraft:game_event");
        Assertions.assertThatThrownBy(() -> codec.encode(CodecFormat.netty(HoldersTest.writable(ConnectionType.OTHER)), GameEvent.STEP))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining("minecraft:game_event");
    }
    
    @Test
    public void holderOrInlineWritesReferenceAsIdentifierAndDirectHolderInline() {
        Holder<SoundEvent> anvil = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ANVIL_LAND);
        Holder<SoundEvent> ranged = Holder.direct(new SoundEvent(Identifier.fromNamespaceAndPath("nexus", "boom"), Optional.of(2.5F)));
        
        HoldersTest.assertJson(HoldersTest.SOUND, anvil, "\"minecraft:block.anvil.land\"");
        HoldersTest.assertJson(HoldersTest.SOUND, Holder.direct(HoldersTest.BOOM), "{\"sound_id\":\"nexus:boom\"}");
        HoldersTest.assertNbt(HoldersTest.SOUND, anvil, StringTag.valueOf("minecraft:block.anvil.land"));
        HoldersTest.assertNbt(HoldersTest.SOUND, ranged, HoldersTest.compound(Map.of("sound_id", StringTag.valueOf("nexus:boom"), "range", FloatTag.valueOf(2.5F))));
    }
    
    @Test
    public void holderOrInlineWritesZeroBeforeDirectHolderAndIdPlusOneOnNetwork() {
        HoldersTest.assertNetty(HoldersTest.SOUND, Holder.direct(HoldersTest.BOOM), 0x00, 0x0A, "nexus:boom", 0x00);
        HoldersTest.assertNetty(HoldersTest.SOUND, BuiltInRegistries.SOUND_EVENT.get(5).orElseThrow(), 0x06);
    }
    
    @Test
    public void holderOrInlineReadsIdentifierAsReferenceEvenIfUnknown() {
        List<CodecError> errors = HoldersTest.errors(() -> HoldersTest.SOUND.decode(HoldersTest.JSON, JsonParser.parseString("\"nexus:boom\"")));
        
        Assertions.assertThat(errors).singleElement().satisfies(error -> Assertions.assertThat(error.message()).contains("\"nexus:boom\"", "minecraft:sound_event"));
    }
    
    @Test
    public void holderOrInlineOnlyNeedsRegistryForReferenceHolders() {
        NexusCodec<Holder<String>, Access.Registries> words = NexusCodec.holderOrInline(ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("nexus", "words")), NexusCodec.STRING);
        
        HoldersTest.assertJson(words, Holder.direct("Not an identifier"), "\"Not an identifier\"");
        Assertions.assertThatThrownBy(() -> words.decode(HoldersTest.JSON, JsonParser.parseString("\"nexus:word\"")))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining("nexus:words");
    }
    
    @Test
    public void holderSetIsTagIdentifierOrListInJson() {
        HoldersTest.assertJson(HoldersTest.ITEMS, HoldersTest.TOOL_MATERIALS, "\"#minecraft:wooden_tool_materials\"");
        HoldersTest.assertJson(HoldersTest.ITEMS, HoldersTest.set(HoldersTest.STONE), "\"minecraft:stone\"");
        HoldersTest.assertJson(HoldersTest.ITEMS, HoldersTest.set(HoldersTest.STONE, HoldersTest.DIRT), "[\"minecraft:stone\",\"minecraft:dirt\"]");
        HoldersTest.assertJson(HoldersTest.ITEMS, HoldersTest.set(), "[]");
        Assertions.assertThat(HoldersTest.ITEMS.decode(HoldersTest.JSON, JsonParser.parseString("[\"minecraft:stone\"]"))).isEqualTo(HoldersTest.set(HoldersTest.STONE));
    }
    
    @Test
    public void holderSetIsTagIdentifierOrListInNbt() {
        ListTag list = new ListTag();
        list.add(StringTag.valueOf("minecraft:stone"));
        list.add(StringTag.valueOf("minecraft:dirt"));
        
        HoldersTest.assertNbt(HoldersTest.ITEMS, HoldersTest.TOOL_MATERIALS, StringTag.valueOf("#minecraft:wooden_tool_materials"));
        HoldersTest.assertNbt(HoldersTest.ITEMS, HoldersTest.set(HoldersTest.STONE), StringTag.valueOf("minecraft:stone"));
        HoldersTest.assertNbt(HoldersTest.ITEMS, HoldersTest.set(HoldersTest.STONE, HoldersTest.DIRT), list);
    }
    
    @Test
    public void holderSetIsTagOrCountedIdsOnNetwork() {
        HoldersTest.assertNetty(HoldersTest.ITEMS, HoldersTest.TOOL_MATERIALS, 0x00, 0x1F, "minecraft:wooden_tool_materials");
        HoldersTest.assertNetty(HoldersTest.ITEMS, HoldersTest.set(HoldersTest.FIRST_ITEM, HoldersTest.LATER_ITEM), 0x03, 0x01, 0xC8, 0x01);
        HoldersTest.assertNetty(HoldersTest.ITEMS, HoldersTest.set(), 0x01);
    }
    
    @Test
    public void holderSetFailsNamingUnknownTagAndRegistry() {
        RegistryFriendlyByteBuf buffer = HoldersTest.readable(0x00, 0x0A, "nexus:nope");
        
        Assertions.assertThatThrownBy(() -> HoldersTest.ITEMS.decode(HoldersTest.JSON, JsonParser.parseString("\"#nexus:nope\"")))
                .isInstanceOf(CodecException.class)
                .hasMessageContainingAll("\"#nexus:nope\"", "minecraft:item");
        Assertions.assertThatThrownBy(() -> HoldersTest.ITEMS.decode(CodecFormat.netty(buffer)))
                .isInstanceOf(CodecException.class)
                .hasMessageContainingAll("nexus:nope", "minecraft:item");
    }
    
    @Test
    public void holderSetCollectsErrorOfEveryHolderAtItsIndex() {
        JsonElement input = JsonParser.parseString("[\"nexus:first\",\"minecraft:stone\",\"nexus:third\"]");
        
        Assertions.assertThat(HoldersTest.errors(() -> HoldersTest.ITEMS.decode(HoldersTest.JSON, input)))
                .extracting(CodecError::path)
                .containsExactly("[0]", "[2]");
    }
    
    @Test
    public void anySetIsObjectWithItsType() {
        HolderSet<Item> any = new AnyHolderSet<>(HoldersTest.REGISTRIES.lookupOrThrow(Registries.ITEM));
        
        HoldersTest.assertJson(HoldersTest.ITEMS, any, "{\"type\":\"neoforge:any\"}");
    }
    
    @Test
    public void strictFormatRefusesUnknownKeyOfCustomSet() {
        CodecFormat<JsonElement, Access.Registries> strict = HoldersTest.JSON.strict();
        JsonElement junk = JsonParser.parseString("{\"type\":\"neoforge:any\",\"junk\":1}");
        
        Assertions.assertThat(HoldersTest.errors(() -> HoldersTest.ITEMS.decode(strict, junk))).containsExactly(new CodecError("junk", "unknown key"));
        Assertions.assertThat(HoldersTest.ITEMS.decode(HoldersTest.JSON, junk)).isInstanceOf(AnyHolderSet.class);
        HoldersTest.assertSameSet(
                HoldersTest.ITEMS.decode(strict, JsonParser.parseString("{\"type\":\"neoforge:or\",\"values\":[\"minecraft:stone\"]}")),
                new OrHolderSet<>(List.of(HoldersTest.set(HoldersTest.STONE)))
        );
    }
    
    @Test
    public void customSetFailsAtItsTypeKey() {
        List<CodecError> unknown = HoldersTest.errors(() -> HoldersTest.ITEMS.decode(HoldersTest.JSON, JsonParser.parseString("{\"type\":\"nexus:nope\",\"values\":[]}")));
        List<CodecError> malformed = HoldersTest.errors(() -> HoldersTest.ITEMS.decode(HoldersTest.NBT, HoldersTest.compound(Map.of("type", FloatTag.valueOf(1.0F)))));
        
        Assertions.assertThat(HoldersTest.errors(() -> HoldersTest.ITEMS.decode(HoldersTest.JSON, JsonParser.parseString("{\"values\":[]}"))))
                .containsExactly(new CodecError("type", "missing"));
        Assertions.assertThat(unknown).singleElement().satisfies(error -> {
            Assertions.assertThat(error.path()).isEqualTo("type");
            Assertions.assertThat(error.message()).contains("\"nexus:nope\"", "neoforge:any", "neoforge:or").doesNotContain("values");
        });
        Assertions.assertThat(malformed).extracting(CodecError::path).containsExactly("type");
    }
    
    @Test
    public void customSetsMatchNeoForgeInJson() {
        HoldersTest.customSets().forEach(set -> {
            JsonElement json = HoldersTest.ITEMS.encode(HoldersTest.JSON, set);
            
            Assertions.assertThat(json).isEqualTo(HoldersTest.ITEMS_NEOFORGE.encodeStart(HoldersTest.JSON_OPS, set).getOrThrow());
            HoldersTest.assertSameSet(HoldersTest.ITEMS.decode(HoldersTest.JSON, json), set);
        });
    }
    
    @Test
    public void customSetsMatchNeoForgeOnNeoForgeConnection() {
        HoldersTest.customSets().forEach(set -> {
            RegistryFriendlyByteBuf nexus = HoldersTest.writable(ConnectionType.NEOFORGE);
            RegistryFriendlyByteBuf vanilla = HoldersTest.writable(ConnectionType.NEOFORGE);
            HoldersTest.ITEMS.encode(CodecFormat.netty(nexus), set);
            HoldersTest.ITEMS_STREAM.encode(vanilla, set);
            
            Assertions.assertThat(ByteBufUtil.getBytes(nexus)).isEqualTo(ByteBufUtil.getBytes(vanilla));
            HoldersTest.assertSameSet(HoldersTest.ITEMS.decode(CodecFormat.netty(vanilla)), set);
            Assertions.assertThat(vanilla.readableBytes()).isZero();
        });
    }
    
    @Test
    public void customSetIsItsHoldersOnOtherConnection() {
        RegistryFriendlyByteBuf buffer = HoldersTest.writable(ConnectionType.OTHER);
        HoldersTest.ITEMS.encode(CodecFormat.netty(buffer), new OrHolderSet<>(List.of(HoldersTest.set(HoldersTest.FIRST_ITEM))));
        
        Assertions.assertThat(ByteBufUtil.getBytes(buffer)).isEqualTo(HoldersTest.bytes(0x02, 0x01));
    }
    
    @Test
    public void plainCodecRunsOnFormatWithRegistries() {
        HoldersTest.assertJson(NexusCodec.INT, 5, "5");
        HoldersTest.assertNbt(NexusCodec.STRING, "stone", StringTag.valueOf("stone"));
        HoldersTest.assertNetty(NexusCodec.INT, 5, 0x00, 0x00, 0x00, 0x05);
    }
    
    @Test
    public void registryStructReadsHolderNextToPlainField() {
        HoldersTest.assertJson(HoldersTest.STACK, new Stack(HoldersTest.STONE, 3), "{\"item\":\"minecraft:stone\",\"count\":3}");
        HoldersTest.assertNetty(HoldersTest.STACK, new Stack(HoldersTest.FIRST_ITEM, 3), 0x01, 0x00, 0x00, 0x00, 0x03);
        
        Assertions.assertThat(HoldersTest.errors(() -> HoldersTest.STACK.decode(HoldersTest.JSON, JsonParser.parseString("{\"item\":\"nexus:nope\",\"count\":3}"))))
                .extracting(CodecError::path)
                .containsExactly("item");
    }
    
    private static List<HolderSet<Item>> customSets() {
        return List.of(
                new AnyHolderSet<>(HoldersTest.REGISTRIES.lookupOrThrow(Registries.ITEM)),
                new OrHolderSet<>(List.of(HoldersTest.set(HoldersTest.STONE), HoldersTest.set(HoldersTest.DIRT))),
                new OrHolderSet<>(List.of(HoldersTest.set(HoldersTest.STONE), HoldersTest.TOOL_MATERIALS)),
                new AndHolderSet<>(List.of(HoldersTest.set(HoldersTest.STONE, HoldersTest.DIRT), HoldersTest.set(HoldersTest.STONE))),
                new NotHolderSet<>(HoldersTest.REGISTRIES.lookupOrThrow(Registries.ITEM), HoldersTest.set(HoldersTest.STONE))
        );
    }
    
    @SafeVarargs
    private static HolderSet<Item> set(Holder<Item>... holders) {
        return HolderSet.direct(List.of(holders));
    }
    
    private static void assertSameSet(HolderSet<Item> actual, HolderSet<Item> expected) {
        Assertions.assertThat(actual).isInstanceOf(expected.getClass());
        Assertions.assertThat(actual.stream().toList()).containsExactlyInAnyOrderElementsOf(expected.stream().toList());
    }
    
    private static CompoundTag compound(Map<String, Tag> entries) {
        CompoundTag tag = new CompoundTag();
        entries.forEach(tag::put);
        return tag;
    }
    
    private static <T> void assertJson(NexusCodec<T, ? super Access.Registries> codec, T value, String json) {
        Assertions.assertThat(codec.encode(HoldersTest.JSON, value)).hasToString(json);
        Assertions.assertThat(codec.decode(HoldersTest.JSON, JsonParser.parseString(json))).isEqualTo(value);
    }
    
    private static <T> void assertNbt(NexusCodec<T, ? super Access.Registries> codec, T value, Tag tag) {
        Assertions.assertThat(codec.encode(HoldersTest.NBT, value)).isEqualTo(tag);
        Assertions.assertThat(codec.decode(HoldersTest.NBT, tag)).isEqualTo(value);
    }
    
    private static <T> void assertNetty(NexusCodec<T, ? super Access.Registries> codec, T value, Object... bytes) {
        RegistryFriendlyByteBuf written = HoldersTest.writable(ConnectionType.OTHER);
        codec.encode(CodecFormat.netty(written), value);
        RegistryFriendlyByteBuf read = HoldersTest.readable(bytes);
        
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(HoldersTest.bytes(bytes));
        Assertions.assertThat(codec.decode(CodecFormat.netty(read))).isEqualTo(value);
        Assertions.assertThat(read.readableBytes()).isZero();
    }
    
    private static List<CodecError> errors(ThrowableAssert.ThrowingCallable call) {
        return Assertions.catchThrowableOfType(CodecException.class, call).errors();
    }
    
    private static RegistryFriendlyByteBuf writable(ConnectionType connection) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), HoldersTest.REGISTRIES, connection);
    }
    
    private static RegistryFriendlyByteBuf readable(Object... bytes) {
        return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(HoldersTest.bytes(bytes)), HoldersTest.REGISTRIES, ConnectionType.OTHER);
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
    
    public record Stack(Holder<Item> item, int count) { }
}
