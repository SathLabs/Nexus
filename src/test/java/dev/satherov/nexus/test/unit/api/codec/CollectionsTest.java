package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.key.MapKey;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.sun.management.ThreadMXBean;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

///
/// Checks how lists, sets, and maps write and read their entries in JSON, NBT, and on the network, how they hold to their limits, and how they report the errors of their entries.
///
public class CollectionsTest {
    
    private static final StructCodec<ParityCases.Bundle, Access.Plain> BUNDLE = NexusCodec.struct(
            "bundle",
            NexusCodec.STRING.field("name", ParityCases.Bundle::name),
            NexusCodec.INT.list().field("counts", ParityCases.Bundle::counts),
            ParityCases.Bundle::new
    );
    
    private static final MapKey<Integer, Access.Plain> SLOT = MapKey.of(NexusCodec.VAR_INT, slot -> "slot" + slot, CollectionsTest::parseSlot);
    
    @Test
    public void listRoundTripsInJson() {
        CollectionsTest.assertJson(NexusCodec.INT.list(), List.of(1, -1, 300, 1), "[1,-1,300,1]");
        CollectionsTest.assertJson(NexusCodec.INT.list(), List.of(), "[]");
    }
    
    @Test
    public void listRoundTripsInNbt() {
        CollectionsTest.assertNbt(NexusCodec.INT.list(), List.of(1, -1, 300, 1), CollectionsTest.list(IntTag.valueOf(1), IntTag.valueOf(-1), IntTag.valueOf(300), IntTag.valueOf(1)));
        CollectionsTest.assertNbt(NexusCodec.INT.list(), List.of(), new ListTag());
    }
    
    @Test
    public void listReadsArrayTag() {
        Assertions.assertThat(NexusCodec.INT.list().decode(CodecFormat.NBT, new IntArrayTag(new int[]{ 1, 2 }))).containsExactly(1, 2);
    }
    
    @Test
    public void listWritesCountAndThenElementsOnNetty() {
        CollectionsTest.assertNetty(NexusCodec.INT.list(), List.of(1, -1, 300, 1), 0x04, 0x00, 0x00, 0x00, 0x01, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x01, 0x2C, 0x00, 0x00, 0x00, 0x01);
        CollectionsTest.assertNetty(NexusCodec.INT.list(), List.of(), 0x00);
    }
    
    @Test
    public void setRoundTripsInItsOrder() {
        Set<String> set = ImmutableSet.of("stone", "dirt");
        
        CollectionsTest.assertJson(NexusCodec.STRING.set(), set, "[\"stone\",\"dirt\"]");
        CollectionsTest.assertNbt(NexusCodec.STRING.set(), set, CollectionsTest.list(StringTag.valueOf("stone"), StringTag.valueOf("dirt")));
        CollectionsTest.assertNetty(NexusCodec.STRING.set(), set, 0x02, 0x05, 's', 't', 'o', 'n', 'e', 0x04, 'd', 'i', 'r', 't');
    }
    
    @Test
    public void decodedSetKeepsTheOrderOfItsElements() {
        Assertions.assertThat(NexusCodec.STRING.set().decode(CodecFormat.JSON, JsonParser.parseString("[\"dirt\",\"stone\",\"clay\"]")))
                .containsExactly("dirt", "stone", "clay");
        Assertions.assertThat(NexusCodec.STRING.set().decode(CodecFormat.netty(CollectionsTest.buffer(0x02, 0x01, 'b', 0x01, 'a')))).containsExactly("b", "a");
    }
    
    @Test
    public void setRefusesDuplicateAtItsIndex() {
        ListTag tag = CollectionsTest.list(StringTag.valueOf("a"), StringTag.valueOf("b"), StringTag.valueOf("a"));
        
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.STRING.set().decode(CodecFormat.JSON, JsonParser.parseString("[\"a\",\"b\",\"a\"]"))))
                .extracting(CodecError::path)
                .containsExactly("[2]");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.STRING.set().decode(CodecFormat.NBT, tag)))
                .extracting(CodecError::path)
                .containsExactly("[2]");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.STRING.set().decode(CodecFormat.netty(CollectionsTest.buffer(0x02, 0x01, 'a', 0x01, 'a')))))
                .hasSize(1);
    }
    
    @Test
    public void refusesListAboveTheLimitBeforeReadingAnyElement() {
        ListTag tag = CollectionsTest.list(StringTag.valueOf("a"), StringTag.valueOf("b"), StringTag.valueOf("c"), StringTag.valueOf("d"));
        
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.INT.list(3).decode(CodecFormat.JSON, JsonParser.parseString("[\"a\",\"b\",\"c\",\"d\"]"))))
                .singleElement()
                .satisfies(error -> CollectionsTest.assertLimit(error, 3, 4));
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.INT.list(3).decode(CodecFormat.NBT, tag)))
                .singleElement()
                .satisfies(error -> CollectionsTest.assertLimit(error, 3, 4));
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.INT.list(3).decode(CodecFormat.netty(CollectionsTest.buffer(0xE8, 0x07)))))
                .singleElement()
                .satisfies(error -> CollectionsTest.assertLimit(error, 3, 1000));
    }
    
    @Test
    public void refusesArrayTagAboveTheLimitBeforeCopyingIt() {
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        NexusCodec<List<Integer>, Access.Plain> codec = NexusCodec.INT.list(3);
        IntArrayTag tag = new IntArrayTag(new int[1_000_000]);
        CollectionsTest.errors(() -> codec.decode(CodecFormat.NBT, new IntArrayTag(new int[4])));
        
        long before = threads.getCurrentThreadAllocatedBytes();
        List<CodecError> errors = CollectionsTest.errors(() -> codec.decode(CodecFormat.NBT, tag));
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        
        Assertions.assertThat(errors).singleElement().satisfies(error -> CollectionsTest.assertLimit(error, 3, 1_000_000));
        Assertions.assertThat(allocated).isLessThan(100_000L);
    }
    
    @Test
    public void refusesSetAndMapAboveTheLimit() {
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.STRING.set(1).decode(CodecFormat.JSON, JsonParser.parseString("[\"a\",\"b\"]"))))
                .singleElement()
                .satisfies(error -> CollectionsTest.assertLimit(error, 1, 2));
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.STRING.set(1).decode(CodecFormat.netty(CollectionsTest.buffer(0xE8, 0x07)))))
                .singleElement()
                .satisfies(error -> CollectionsTest.assertLimit(error, 1, 1000));
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT, 2).decode(CodecFormat.JSON, JsonParser.parseString("{\"a\":\"x\",\"b\":\"y\",\"c\":\"z\"}"))))
                .singleElement()
                .satisfies(error -> CollectionsTest.assertLimit(error, 2, 3));
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT, 2).decode(CodecFormat.netty(CollectionsTest.buffer(0xE8, 0x07)))))
                .singleElement()
                .satisfies(error -> CollectionsTest.assertLimit(error, 2, 1000));
    }
    
    @Test
    public void defaultLimitIs32767() {
        JsonArray array = new JsonArray();
        for (int i = 0; i < 32768; i++) {
            array.add(0);
        }
        
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.INT.list().decode(CodecFormat.JSON, array)))
                .singleElement()
                .satisfies(error -> CollectionsTest.assertLimit(error, 32767, 32768));
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.INT.list().decode(CodecFormat.netty(CollectionsTest.buffer(0x80, 0x80, 0x02)))))
                .singleElement()
                .satisfies(error -> CollectionsTest.assertLimit(error, 32767, 32768));
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.INT.list().decode(CodecFormat.netty(CollectionsTest.buffer(0xFF, 0xFF, 0x01)))))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.message()).contains("end of the buffer"));
    }
    
    @Test
    public void countWithinTheLimitFailsAtTheEndOfTheBuffer() {
        FriendlyByteBuf list = CollectionsTest.buffer(0xFF, 0xFF, 0xFF, 0xFF, 0x07);
        FriendlyByteBuf map = CollectionsTest.buffer(0xFF, 0xFF, 0xFF, 0xFF, 0x07);
        
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.INT.list(Integer.MAX_VALUE).decode(CodecFormat.netty(list))))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.message()).contains("end of the buffer"));
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT, Integer.MAX_VALUE).decode(CodecFormat.netty(map))))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.message()).contains("end of the buffer"));
    }
    
    @Test
    public void refusesToWriteAboveTheLimit() {
        NexusCodec<List<Integer>, Access.Plain> list = NexusCodec.INT.list(2);
        NexusCodec<Map<String, Integer>, Access.Plain> map = NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT, 1);
        
        Assertions.assertThatThrownBy(() -> list.encode(CodecFormat.JSON, List.of(1, 2, 3))).isInstanceOf(NexusCodecException.class);
        Assertions.assertThatThrownBy(() -> list.encode(CodecFormat.NBT, List.of(1, 2, 3))).isInstanceOf(NexusCodecException.class);
        Assertions.assertThatThrownBy(() -> list.encode(CodecFormat.netty(CollectionsTest.buffer()), List.of(1, 2, 3))).isInstanceOf(NexusCodecException.class);
        Assertions.assertThatThrownBy(() -> map.encode(CodecFormat.JSON, ImmutableMap.of("a", 1, "b", 2))).isInstanceOf(NexusCodecException.class);
        Assertions.assertThatThrownBy(() -> map.encode(CodecFormat.netty(CollectionsTest.buffer()), ImmutableMap.of("a", 1, "b", 2))).isInstanceOf(NexusCodecException.class);
    }
    
    @Test
    public void refusesNegativeLimit() {
        Assertions.assertThatThrownBy(() -> NexusCodec.INT.list(-1)).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> NexusCodec.INT.set(-1)).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT, -1)).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void mapWritesValuesUnderTheStringFormOfTheirKeys() {
        Map<String, Integer> map = ImmutableMap.of("stone", 1, "dirt", 64);
        
        CollectionsTest.assertJson(NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT), map, "{\"stone\":1,\"dirt\":64}");
        CollectionsTest.assertNbt(NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT), map, CollectionsTest.compound(Map.of("stone", IntTag.valueOf(1), "dirt", IntTag.valueOf(64))));
    }
    
    @Test
    public void mapWritesCountAndThenKeysAndValuesOnNetty() {
        CollectionsTest.assertNetty(
                NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT),
                ImmutableMap.of("stone", 1, "dirt", 64),
                0x02, 0x05, 's', 't', 'o', 'n', 'e', 0x00, 0x00, 0x00, 0x01, 0x04, 'd', 'i', 'r', 't', 0x00, 0x00, 0x00, 0x40
        );
        CollectionsTest.assertNetty(NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT), Map.of(), 0x00);
    }
    
    @Test
    public void decodedMapKeepsTheOrderOfItsEntries() {
        Assertions.assertThat(NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT).decode(CodecFormat.JSON, JsonParser.parseString("{\"b\":1,\"a\":2,\"c\":3}")).keySet())
                .containsExactly("b", "a", "c");
        Assertions.assertThat(NexusCodec.mapOf(MapKey.STRING, NexusCodec.BYTE).decode(CodecFormat.netty(CollectionsTest.buffer(0x02, 0x01, 'b', 0x01, 0x01, 'a', 0x02))).keySet())
                .containsExactly("b", "a");
    }
    
    @Test
    public void everyConstantKeyRoundTrips() {
        CollectionsTest.assertKey(MapKey.STRING, "stone", "stone", 0x05, 's', 't', 'o', 'n', 'e');
        CollectionsTest.assertKey(
                MapKey.IDENTIFIER,
                Identifier.withDefaultNamespace("stone"),
                "minecraft:stone",
                0x0F, 'm', 'i', 'n', 'e', 'c', 'r', 'a', 'f', 't', ':', 's', 't', 'o', 'n', 'e'
        );
        CollectionsTest.assertKey(
                MapKey.UUID,
                new UUID(1L, 2L),
                "00000000-0000-0001-0000-000000000002",
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02
        );
        CollectionsTest.assertKey(MapKey.INT, -3, "-3", 0xFF, 0xFF, 0xFF, 0xFD);
        CollectionsTest.assertKey(MapKey.LONG, 9_000_000_000L, "9000000000", 0x00, 0x00, 0x00, 0x02, 0x18, 0x71, 0x1A, 0x00);
    }
    
    @Test
    public void enumKeyUsesTheNameOfTheEnumCodec() {
        CollectionsTest.assertKey(MapKey.enumOf(ParityCases.Weight.class), ParityCases.Weight.ANVIL, "heavy", 0x01);
        CollectionsTest.assertKey(MapKey.enumOf(ParityCases.Phase.class), ParityCases.Phase.FULL_MOON, "full_moon", 0x01);
    }
    
    @Test
    public void keyOfCodecUsesItsStringForm() {
        CollectionsTest.assertKey(CollectionsTest.SLOT, 300, "slot300", 0xAC, 0x02);
    }
    
    @Test
    public void refusesKeyThatItsStringFormCannotRead() {
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.IDENTIFIER, NexusCodec.INT).decode(CodecFormat.JSON, JsonParser.parseString("{\"stone\":1,\"Bad Key\":2}"))))
                .extracting(CodecError::path)
                .containsExactly("['Bad Key']");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.INT, NexusCodec.INT).decode(CodecFormat.JSON, JsonParser.parseString("{\"five\":1}"))))
                .extracting(CodecError::path)
                .containsExactly("['five']");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.UUID, NexusCodec.INT).decode(CodecFormat.JSON, JsonParser.parseString("{\"five\":1}"))))
                .extracting(CodecError::path)
                .containsExactly("['five']");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.enumOf(ParityCases.Weight.class), NexusCodec.INT).decode(CodecFormat.JSON, JsonParser.parseString("{\"medium\":1}"))))
                .singleElement()
                .satisfies(error -> {
                    Assertions.assertThat(error.path()).isEqualTo("['medium']");
                    Assertions.assertThat(error.message()).contains("light", "heavy");
                });
    }
    
    @Test
    public void refusesKeyThatTheReaderOfTheStringFormRefuses() {
        CompoundTag tag = CollectionsTest.compound(Map.of("five", IntTag.valueOf(1)));
        
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(CollectionsTest.SLOT, NexusCodec.INT).decode(CodecFormat.JSON, JsonParser.parseString("{\"five\":1}"))))
                .containsExactly(new CodecError("['five']", "expected a slot"));
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(CollectionsTest.SLOT, NexusCodec.INT).decode(CodecFormat.NBT, tag)))
                .containsExactly(new CodecError("['five']", "expected a slot"));
    }
    
    @Test
    public void reportsAnyOtherExceptionOfTheReaderAsNexusCodecException() {
        MapKey<String, Access.Plain> key = MapKey.of(NexusCodec.STRING, Function.identity(), text -> {
            throw new IllegalStateException("no key in " + text);
        });
        
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(key, NexusCodec.INT).decode(CodecFormat.JSON, JsonParser.parseString("{\"slot\":1}"))))
                .singleElement()
                .satisfies(error -> {
                    Assertions.assertThat(error.path()).isEqualTo("['slot']");
                    Assertions.assertThat(error.message()).contains("no key in slot");
                });
    }
    
    @Test
    public void refusesDecodedKeyThatIsAlreadyInTheMap() {
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.INT, NexusCodec.INT).decode(CodecFormat.JSON, JsonParser.parseString("{\"5\":1,\"05\":2}"))))
                .extracting(CodecError::path)
                .containsExactly("['05']");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.STRING, NexusCodec.BYTE).decode(CodecFormat.netty(CollectionsTest.buffer(0x02, 0x01, 'a', 0x01, 0x01, 'a', 0x02)))))
                .hasSize(1);
    }
    
    @Test
    public void reportsKeyWithoutStringFormAtTheIndexOfItsEntry() {
        Map<String, String> labels = ImmutableMap.of("a".repeat(32768), "ok", "b", "long");
        MapKey<String, Access.Plain> unwritable = MapKey.of(
                NexusCodec.STRING,
                text -> {
                    throw new IllegalStateException("no string form");
                },
                Function.identity()
        );
        
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.STRING, NexusCodec.string(3)).encode(CodecFormat.JSON, labels)))
                .extracting(CodecError::path)
                .containsExactly("[0]", "['b']");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(unwritable, NexusCodec.INT).encode(CodecFormat.NBT, ImmutableMap.of("a", 1, "b", 2))))
                .satisfiesExactly(
                        error -> Assertions.assertThat(error.path()).isEqualTo("[0]"),
                        error -> Assertions.assertThat(error).satisfies(second -> {
                            Assertions.assertThat(second.path()).isEqualTo("[1]");
                            Assertions.assertThat(second.message()).contains("no string form");
                        })
                );
    }
    
    @Test
    public void reportsTheIndexOfTheElementThatFailed() {
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.INT.list().decode(CodecFormat.JSON, JsonParser.parseString("[1,2,\"three\"]"))))
                .extracting(CodecError::path)
                .containsExactly("[2]");
    }
    
    @Test
    public void reportsTheKeyOfTheValueThatFailed() {
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT).decode(CodecFormat.JSON, JsonParser.parseString("{\"a\":1,\"k\":\"x\"}"))))
                .extracting(CodecError::path)
                .containsExactly("['k']");
    }
    
    @Test
    public void joinsNestedPaths() {
        NexusCodec<List<Map<String, Integer>>, Access.Plain> maps = NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT).list();
        NexusCodec<Map<String, List<Integer>>, Access.Plain> lists = NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT.list());
        
        Assertions.assertThat(CollectionsTest.errors(() -> CollectionsTest.BUNDLE.decode(CodecFormat.JSON, JsonParser.parseString("{\"name\":\"box\",\"counts\":[1,\"two\"]}"))))
                .extracting(CodecError::path)
                .containsExactly("counts[1]");
        Assertions.assertThat(CollectionsTest.errors(() -> maps.decode(CodecFormat.JSON, JsonParser.parseString("[{\"a\":1},{\"k\":\"x\"}]"))))
                .extracting(CodecError::path)
                .containsExactly("[1]['k']");
        Assertions.assertThat(CollectionsTest.errors(() -> lists.decode(CodecFormat.JSON, JsonParser.parseString("{\"k\":[1,\"x\"]}"))))
                .extracting(CodecError::path)
                .containsExactly("['k'][1]");
    }
    
    @Test
    public void collectsTheErrorsOfEveryElementInJsonAndNbt() {
        ListTag tag = CollectionsTest.list(StringTag.valueOf("a"), IntTag.valueOf(1), StringTag.valueOf("b"));
        List<String> labels = List.of("long", "ok", "longer");
        
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.INT.list().decode(CodecFormat.JSON, JsonParser.parseString("[\"a\",1,\"b\"]"))))
                .extracting(CodecError::path)
                .containsExactly("[0]", "[2]");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.INT.list().decode(CodecFormat.NBT, tag)))
                .extracting(CodecError::path)
                .containsExactly("[0]", "[2]");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.string(3).list().encode(CodecFormat.JSON, labels)))
                .extracting(CodecError::path)
                .containsExactly("[0]", "[2]");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.string(3).list().encode(CodecFormat.NBT, labels)))
                .extracting(CodecError::path)
                .containsExactly("[0]", "[2]");
    }
    
    @Test
    public void collectsTheErrorsOfEveryEntryInJsonAndNbt() {
        CompoundTag tag = CollectionsTest.compound(Map.of("a", StringTag.valueOf("x"), "b", IntTag.valueOf(1), "c", StringTag.valueOf("y")));
        Map<String, String> labels = ImmutableMap.of("a", "long", "b", "ok", "c", "longer");
        
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT).decode(CodecFormat.JSON, JsonParser.parseString("{\"a\":\"x\",\"b\":1,\"c\":\"y\"}"))))
                .extracting(CodecError::path)
                .containsExactly("['a']", "['c']");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT).decode(CodecFormat.NBT, tag)))
                .extracting(CodecError::path)
                .containsExactlyInAnyOrder("['a']", "['c']");
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.STRING, NexusCodec.string(3)).encode(CodecFormat.JSON, labels)))
                .extracting(CodecError::path)
                .containsExactly("['a']", "['c']");
    }
    
    @Test
    public void stopsAtTheFirstErrorOnNetty() {
        FriendlyByteBuf input = CollectionsTest.buffer(0x02, 0x04, 'l', 'o', 'n', 'g', 0x06, 'l', 'o', 'n', 'g', 'e', 'r');
        
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.string(3).list().decode(CodecFormat.netty(input)))).hasSize(1);
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.string(3).list().encode(CodecFormat.netty(CollectionsTest.buffer()), List.of("long", "longer"))))
                .hasSize(1);
        Assertions.assertThat(CollectionsTest.errors(() -> NexusCodec.mapOf(MapKey.STRING, NexusCodec.string(3)).encode(CodecFormat.netty(CollectionsTest.buffer()), ImmutableMap.of("a", "long", "b", "longer"))))
                .hasSize(1);
    }
    
    private static Integer parseSlot(String text) {
        if (!text.startsWith("slot")) throw new NexusCodecException("expected a slot");
        return Integer.parseInt(text.substring(4));
    }
    
    private static void assertLimit(CodecError error, int limit, int count) {
        Assertions.assertThat(error.path()).isEmpty();
        Assertions.assertThat(error.message()).contains(String.valueOf(limit), String.valueOf(count)).doesNotContain("end of the buffer");
    }
    
    private static <K> void assertKey(MapKey<K, Access.Plain> key, K value, String text, int... keyBytes) {
        NexusCodec<Map<K, Byte>, Access.Plain> codec = NexusCodec.mapOf(key, NexusCodec.BYTE);
        int[] bytes = new int[keyBytes.length + 2];
        bytes[0] = 0x01;
        System.arraycopy(keyBytes, 0, bytes, 1, keyBytes.length);
        bytes[bytes.length - 1] = 0x07;
        
        CollectionsTest.assertJson(codec, Map.of(value, (byte) 7), "{\"" + text + "\":7}");
        CollectionsTest.assertNbt(codec, Map.of(value, (byte) 7), CollectionsTest.compound(Map.of(text, ByteTag.valueOf((byte) 7))));
        CollectionsTest.assertNetty(codec, Map.of(value, (byte) 7), bytes);
    }
    
    private static List<CodecError> errors(ThrowableAssert.ThrowingCallable call) {
        return Assertions.catchThrowableOfType(NexusCodecException.class, call).errors();
    }
    
    private static ListTag list(Tag... elements) {
        ListTag tag = new ListTag();
        tag.addAll(List.of(elements));
        return tag;
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
        FriendlyByteBuf read = CollectionsTest.buffer(bytes);
        
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(ByteBufUtil.getBytes(CollectionsTest.buffer(bytes)));
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
}
