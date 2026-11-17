package dev.satherov.nexus.test.unit.api.codec.struct;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.codec.struct.StructField;
import dev.satherov.nexus.internal.codec.struct.Structs;
import dev.satherov.nexus.test.unit.api.codec.ParityCases;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

///
/// Checks that a struct of every arity round trips with a value of a distinct type at every position,
/// and that a struct of more than four fields writes its keys in the same order as its DFU twin.
///
public class ArityTest {
    
    private static final StructCodec<Point, Access.Plain> POINT_CODEC = NexusCodec.struct(
            "point",
            NexusCodec.INT.field("x", Point::x),
            NexusCodec.INT.field("y", Point::y),
            Point::new
    );
    
    private static final StructField<List<Object>, Boolean, Access.Plain> FLAG = NexusCodec.BOOL.field("flag", values -> (Boolean) values.get(0));
    private static final StructField<List<Object>, Byte, Access.Plain> SMALL = NexusCodec.BYTE.field("small", values -> (Byte) values.get(1));
    private static final StructField<List<Object>, Short, Access.Plain> MEDIUM = NexusCodec.SHORT.field("medium", values -> (Short) values.get(2));
    private static final StructField<List<Object>, Integer, Access.Plain> NUMBER = NexusCodec.INT.field("number", values -> (Integer) values.get(3));
    private static final StructField<List<Object>, Long, Access.Plain> BIG = NexusCodec.LONG.field("big", values -> (Long) values.get(4));
    private static final StructField<List<Object>, Float, Access.Plain> FRACTION = NexusCodec.FLOAT.field("fraction", values -> (Float) values.get(5));
    private static final StructField<List<Object>, Double, Access.Plain> PRECISE = NexusCodec.DOUBLE.field("precise", values -> (Double) values.get(6));
    private static final StructField<List<Object>, String, Access.Plain> TEXT = NexusCodec.STRING.field("text", values -> (String) values.get(7));
    private static final StructField<List<Object>, Identifier, Access.Plain> ID = NexusCodec.IDENTIFIER.field("id", values -> (Identifier) values.get(8));
    private static final StructField<List<Object>, UUID, Access.Plain> UNIQUE_ID = NexusCodec.UUID.field("uuid", values -> (UUID) values.get(9));
    private static final StructField<List<Object>, ParityCases.Weight, Access.Plain> WEIGHT = NexusCodec.enumOf(ParityCases.Weight.class)
            .field("weight", values -> (ParityCases.Weight) values.get(10));
    private static final StructField<List<Object>, ParityCases.Phase, Access.Plain> PHASE = NexusCodec.enumOf(ParityCases.Phase.class)
            .field("phase", values -> (ParityCases.Phase) values.get(11));
    private static final StructField<List<Object>, ParityCases.Alias, Access.Plain> ALIAS = NexusCodec.enumOf(ParityCases.Alias.class)
            .field("alias", values -> (ParityCases.Alias) values.get(12));
    private static final StructField<List<Object>, Unit, Access.Plain> UNIT = NexusCodec.unit(Unit.INSTANCE).field("unit", values -> (Unit) values.get(13));
    private static final StructField<List<Object>, Side, Access.Plain> SIDE = NexusCodec.enumOf(Side.class).field("side", values -> (Side) values.get(14));
    private static final StructField<List<Object>, Point, Access.Plain> POINT = ArityTest.POINT_CODEC.field("point", values -> (Point) values.get(15));
    
    private static final List<Object> SAMPLE = List.of(
            true,
            (byte) 2,
            (short) 3,
            4,
            5L,
            1.5F,
            2.5D,
            "text",
            Identifier.fromNamespaceAndPath("nexus", "arity"),
            new UUID(6L, 7L),
            ParityCases.Weight.ANVIL,
            ParityCases.Phase.FULL_MOON,
            ParityCases.Alias.FIRST,
            Unit.INSTANCE,
            Side.RIGHT,
            new Point(8, 9)
    );
    
    private static final List<StructCodec<List<Object>, Access.Plain>> STRUCTS = List.of(
            NexusCodec.struct("one", ArityTest.FLAG, a -> List.of(a)),
            NexusCodec.struct("two", ArityTest.FLAG, ArityTest.SMALL, (a, b) -> List.of(a, b)),
            NexusCodec.struct("three", ArityTest.FLAG, ArityTest.SMALL, ArityTest.MEDIUM, (a, b, c) -> List.of(a, b, c)),
            NexusCodec.struct("four", ArityTest.FLAG, ArityTest.SMALL, ArityTest.MEDIUM, ArityTest.NUMBER, (a, b, c, d) -> List.of(a, b, c, d)),
            NexusCodec.struct(
                    "five",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    (a, b, c, d, e) -> List.of(a, b, c, d, e)
            ),
            NexusCodec.struct(
                    "six",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    ArityTest.FRACTION,
                    (a, b, c, d, e, f) -> List.of(a, b, c, d, e, f)
            ),
            NexusCodec.struct(
                    "seven",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    ArityTest.FRACTION,
                    ArityTest.PRECISE,
                    (a, b, c, d, e, f, g) -> List.of(a, b, c, d, e, f, g)
            ),
            NexusCodec.struct(
                    "eight",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    ArityTest.FRACTION,
                    ArityTest.PRECISE,
                    ArityTest.TEXT,
                    (a, b, c, d, e, f, g, h) -> List.of(a, b, c, d, e, f, g, h)
            ),
            NexusCodec.struct(
                    "nine",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    ArityTest.FRACTION,
                    ArityTest.PRECISE,
                    ArityTest.TEXT,
                    ArityTest.ID,
                    (a, b, c, d, e, f, g, h, i) -> List.of(a, b, c, d, e, f, g, h, i)
            ),
            NexusCodec.struct(
                    "ten",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    ArityTest.FRACTION,
                    ArityTest.PRECISE,
                    ArityTest.TEXT,
                    ArityTest.ID,
                    ArityTest.UNIQUE_ID,
                    (a, b, c, d, e, f, g, h, i, j) -> List.of(a, b, c, d, e, f, g, h, i, j)
            ),
            NexusCodec.struct(
                    "eleven",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    ArityTest.FRACTION,
                    ArityTest.PRECISE,
                    ArityTest.TEXT,
                    ArityTest.ID,
                    ArityTest.UNIQUE_ID,
                    ArityTest.WEIGHT,
                    (a, b, c, d, e, f, g, h, i, j, k) -> List.of(a, b, c, d, e, f, g, h, i, j, k)
            ),
            NexusCodec.struct(
                    "twelve",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    ArityTest.FRACTION,
                    ArityTest.PRECISE,
                    ArityTest.TEXT,
                    ArityTest.ID,
                    ArityTest.UNIQUE_ID,
                    ArityTest.WEIGHT,
                    ArityTest.PHASE,
                    (a, b, c, d, e, f, g, h, i, j, k, l) -> List.of(a, b, c, d, e, f, g, h, i, j, k, l)
            ),
            NexusCodec.struct(
                    "thirteen",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    ArityTest.FRACTION,
                    ArityTest.PRECISE,
                    ArityTest.TEXT,
                    ArityTest.ID,
                    ArityTest.UNIQUE_ID,
                    ArityTest.WEIGHT,
                    ArityTest.PHASE,
                    ArityTest.ALIAS,
                    (a, b, c, d, e, f, g, h, i, j, k, l, m) -> List.of(a, b, c, d, e, f, g, h, i, j, k, l, m)
            ),
            NexusCodec.struct(
                    "fourteen",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    ArityTest.FRACTION,
                    ArityTest.PRECISE,
                    ArityTest.TEXT,
                    ArityTest.ID,
                    ArityTest.UNIQUE_ID,
                    ArityTest.WEIGHT,
                    ArityTest.PHASE,
                    ArityTest.ALIAS,
                    ArityTest.UNIT,
                    (a, b, c, d, e, f, g, h, i, j, k, l, m, n) -> List.of(a, b, c, d, e, f, g, h, i, j, k, l, m, n)
            ),
            NexusCodec.struct(
                    "fifteen",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    ArityTest.FRACTION,
                    ArityTest.PRECISE,
                    ArityTest.TEXT,
                    ArityTest.ID,
                    ArityTest.UNIQUE_ID,
                    ArityTest.WEIGHT,
                    ArityTest.PHASE,
                    ArityTest.ALIAS,
                    ArityTest.UNIT,
                    ArityTest.SIDE,
                    (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o) -> List.of(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o)
            ),
            NexusCodec.struct(
                    "sixteen",
                    ArityTest.FLAG,
                    ArityTest.SMALL,
                    ArityTest.MEDIUM,
                    ArityTest.NUMBER,
                    ArityTest.BIG,
                    ArityTest.FRACTION,
                    ArityTest.PRECISE,
                    ArityTest.TEXT,
                    ArityTest.ID,
                    ArityTest.UNIQUE_ID,
                    ArityTest.WEIGHT,
                    ArityTest.PHASE,
                    ArityTest.ALIAS,
                    ArityTest.UNIT,
                    ArityTest.SIDE,
                    ArityTest.POINT,
                    (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p) -> List.of(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)
            )
    );
    
    private static final StructCodec<List<Integer>, Access.Plain> FIVE_NUMBERS = NexusCodec.struct(
            "five_numbers",
            ArityTest.number(0),
            ArityTest.number(1),
            ArityTest.number(2),
            ArityTest.number(3),
            ArityTest.number(4),
            (a, b, c, d, e) -> List.of(a, b, c, d, e)
    );
    
    private static final Codec<List<Integer>> FIVE_NUMBERS_DFU = RecordCodecBuilder.create(instance -> instance.group(
            ArityTest.dfuNumber(0),
            ArityTest.dfuNumber(1),
            ArityTest.dfuNumber(2),
            ArityTest.dfuNumber(3),
            ArityTest.dfuNumber(4)
    ).apply(instance, (a, b, c, d, e) -> List.of(a, b, c, d, e)));
    
    private static final StructCodec<List<Integer>, Access.Plain> SIXTEEN_NUMBERS = NexusCodec.struct(
            "sixteen_numbers",
            ArityTest.number(0),
            ArityTest.number(1),
            ArityTest.number(2),
            ArityTest.number(3),
            ArityTest.number(4),
            ArityTest.number(5),
            ArityTest.number(6),
            ArityTest.number(7),
            ArityTest.number(8),
            ArityTest.number(9),
            ArityTest.number(10),
            ArityTest.number(11),
            ArityTest.number(12),
            ArityTest.number(13),
            ArityTest.number(14),
            ArityTest.number(15),
            (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p) -> List.of(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)
    );
    
    private static final Codec<List<Integer>> SIXTEEN_NUMBERS_DFU = RecordCodecBuilder.create(instance -> instance.group(
            ArityTest.dfuNumber(0),
            ArityTest.dfuNumber(1),
            ArityTest.dfuNumber(2),
            ArityTest.dfuNumber(3),
            ArityTest.dfuNumber(4),
            ArityTest.dfuNumber(5),
            ArityTest.dfuNumber(6),
            ArityTest.dfuNumber(7),
            ArityTest.dfuNumber(8),
            ArityTest.dfuNumber(9),
            ArityTest.dfuNumber(10),
            ArityTest.dfuNumber(11),
            ArityTest.dfuNumber(12),
            ArityTest.dfuNumber(13),
            ArityTest.dfuNumber(14),
            ArityTest.dfuNumber(15)
    ).apply(instance, (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p) -> List.of(a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p)));
    
    private static StructField<List<Integer>, Integer, Access.Plain> number(int index) {
        return NexusCodec.INT.field("number_" + index, values -> values.get(index));
    }
    
    private static RecordCodecBuilder<List<Integer>, Integer> dfuNumber(int index) {
        return Codec.INT.fieldOf("number_" + index).forGetter(values -> values.get(index));
    }
    
    public static Stream<Arguments> structs() {
        return IntStream.rangeClosed(1, ArityTest.STRUCTS.size()).mapToObj(arity -> Arguments.of(arity, ArityTest.STRUCTS.get(arity - 1)));
    }
    
    @ParameterizedTest(name = "arity {0}")
    @MethodSource("structs")
    public void roundTripsInJson(int arity, StructCodec<List<Object>, Access.Plain> codec) {
        List<Object> value = ArityTest.SAMPLE.subList(0, arity);
        JsonElement json = codec.encode(CodecFormat.JSON, value);
        
        Assertions.assertThat(json.getAsJsonObject().keySet()).hasSize(arity);
        Assertions.assertThat(codec.decode(CodecFormat.JSON, json)).isEqualTo(value);
    }
    
    @ParameterizedTest(name = "arity {0}")
    @MethodSource("structs")
    public void roundTripsInNbt(int arity, StructCodec<List<Object>, Access.Plain> codec) {
        List<Object> value = ArityTest.SAMPLE.subList(0, arity);
        Tag tag = codec.encode(CodecFormat.NBT, value);
        
        Assertions.assertThat(tag).isInstanceOfSatisfying(CompoundTag.class, compound -> Assertions.assertThat(compound.size()).isEqualTo(arity));
        Assertions.assertThat(codec.decode(CodecFormat.NBT, tag)).isEqualTo(value);
    }
    
    @ParameterizedTest(name = "arity {0}")
    @MethodSource("structs")
    public void roundTripsOnNetty(int arity, StructCodec<List<Object>, Access.Plain> codec) {
        List<Object> value = ArityTest.SAMPLE.subList(0, arity);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(CodecFormat.netty(buffer), value);
        
        Assertions.assertThat(codec.decode(CodecFormat.netty(buffer))).isEqualTo(value);
        Assertions.assertThat(buffer.readableBytes()).isZero();
    }
    
    public static Stream<Arguments> twins() {
        return Stream.of(
                Arguments.of(5, ArityTest.FIVE_NUMBERS, ArityTest.FIVE_NUMBERS_DFU),
                Arguments.of(16, ArityTest.SIXTEEN_NUMBERS, ArityTest.SIXTEEN_NUMBERS_DFU)
        );
    }
    
    @ParameterizedTest(name = "arity {0}")
    @MethodSource("twins")
    public void writesKeysInTheOrderOfItsDfuTwinInJson(int arity, StructCodec<List<Integer>, Access.Plain> codec, Codec<List<Integer>> dfu) {
        List<Integer> value = IntStream.range(0, arity).boxed().toList();
        String json = dfu.encodeStart(JsonOps.INSTANCE, value).getOrThrow().toString();
        
        Assertions.assertThat(codec.encode(CodecFormat.JSON, value)).hasToString(json);
        Assertions.assertThat(codec.asDfu().encodeStart(JsonOps.INSTANCE, value).getOrThrow()).hasToString(json);
    }
    
    @Test
    public void refusesFieldsOtherThanItsArity() {
        Assertions.assertThatThrownBy(() -> new Structs.Struct<>("pair", 2, values -> ArityTest.SAMPLE, ArityTest.FLAG))
                .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> new Structs.Struct<>("single", 1, values -> ArityTest.SAMPLE, ArityTest.FLAG, ArityTest.SMALL))
                .isInstanceOf(IllegalArgumentException.class);
    }
    
    public enum Side {
        LEFT,
        RIGHT
    }
    
    public record Point(int x, int y) { }
}
