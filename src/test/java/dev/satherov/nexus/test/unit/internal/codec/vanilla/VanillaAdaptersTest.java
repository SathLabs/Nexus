package dev.satherov.nexus.test.unit.internal.codec.vanilla;

import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.path.Encoding;
import dev.satherov.nexus.internal.codec.path.TagReader;
import dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters;

import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

///
/// Checks that the readers of the codecs over DFU codecs pass exactly what encoding the value to NBT and reading the tag passes.
///
public class VanillaAdaptersTest {
    
    private static final Comparison EQUAL_THREE = new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(3.0D));
    private static final Comparison NOT_EQUAL_THREE = new Comparison(Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Numeric(3.0D));
    
    private static final List<Comparison> COMPARISONS = List.of(
            VanillaAdaptersTest.EQUAL_THREE,
            VanillaAdaptersTest.NOT_EQUAL_THREE,
            new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Numeric(3.0D)),
            new Comparison(Comparison.Operator.GREATER_OR_EQUAL, new Comparison.Literal.Numeric(3.0D)),
            new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Text("a")),
            new Comparison(Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Text("a")),
            new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true)),
            new Comparison(Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Flag(true))
    );
    
    private static final List<Comparison> NUMBER_COMPARISONS = VanillaAdaptersTest.comparisons(
            new Comparison.Literal.Numeric(3.0D),
            new Comparison.Literal.Numeric(-3.0D),
            new Comparison.Literal.Numeric(0.0D),
            new Comparison.Literal.Numeric(2.5D),
            new Comparison.Literal.Text("3"),
            new Comparison.Literal.Flag(true),
            new Comparison.Literal.Flag(false)
    );
    
    private static final List<Comparison> FLAG_COMPARISONS = VanillaAdaptersTest.comparisons(
            new Comparison.Literal.Flag(true),
            new Comparison.Literal.Flag(false),
            new Comparison.Literal.Numeric(1.0D),
            new Comparison.Literal.Numeric(0.0D),
            new Comparison.Literal.Numeric(3.0D),
            new Comparison.Literal.Numeric(-1.0D),
            new Comparison.Literal.Numeric(0.5D),
            new Comparison.Literal.Text("1")
    );
    
    private static final List<Comparison> TEXT_COMPARISONS = VanillaAdaptersTest.comparisons(
            new Comparison.Literal.Text("a"),
            new Comparison.Literal.Text("ab"),
            new Comparison.Literal.Text(""),
            new Comparison.Literal.Text("minecraft:stone"),
            new Comparison.Literal.Text("minecraft:s"),
            new Comparison.Literal.Text("stone"),
            new Comparison.Literal.Text("night"),
            new Comparison.Literal.Text("dark"),
            new Comparison.Literal.Numeric(3.0D),
            new Comparison.Literal.Flag(true)
    );
    
    private static final Codec<Shade> SHADES = StringRepresentable.fromEnum(Shade::values);
    
    private static final StructCodec<Named, Access.Plain> NAMED = NexusCodec.struct(
            "named",
            NexusCodec.INT.field("count", Named::count),
            NexusCodec.STRING.field("name", Named::name),
            Named::new
    );
    
    @Test
    public void xmapLayerIsUnwrapped() {
        Traversal<Box, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.INT.listOf().xmap(Box::new, Box::values));
        List<Box> values = List.of(new Box(List.of(1, 3)), new Box(List.of(3)), new Box(List.of()));
        
        VanillaAdaptersTest.assertWalked(codec, VanillaAdaptersTest.index(1));
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.index(1)), values);
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.any()), values);
        Assertions.assertThat(codec.reader(VanillaAdaptersTest.steps(VanillaAdaptersTest.index(1)), 0).bind(VanillaAdaptersTest.EQUAL_THREE).test(values.getFirst())).isTrue();
    }
    
    @Test
    public void comapFlatMapLayerIsUnwrapped() {
        Traversal<Box, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.INT.listOf().comapFlatMap(list -> DataResult.success(new Box(list)), Box::values));
        
        VanillaAdaptersTest.assertWalked(codec, VanillaAdaptersTest.index(0));
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.index(0)), List.of(new Box(List.of(3)), new Box(List.of())));
    }
    
    @Test
    public void flatXmapLayerIsUnwrappedAndARefusedValueReadsAsAbsent() {
        Codec<Box> dfu = Codec.INT.listOf().flatXmap(
                list -> DataResult.success(new Box(list)),
                box -> box.values().size() > 1 ? DataResult.error(() -> "Too many values") : DataResult.success(box.values())
        );
        Traversal<Box, Access.Plain> codec = VanillaAdapters.ofDfu(dfu);
        List<ComponentPath.Step> steps = VanillaAdaptersTest.steps(VanillaAdaptersTest.any());
        
        VanillaAdaptersTest.assertWalked(codec, VanillaAdaptersTest.any());
        VanillaAdaptersTest.assertParity(codec, steps, List.of(new Box(List.of(3)), new Box(List.of(3, 1)), new Box(List.of())));
        VanillaAdaptersTest.assertAbsent(codec, steps, new Box(List.of(3, 1)));
    }
    
    @Test
    public void validateLayerIsUnwrappedAndAnInvalidValueReadsAsAbsent() {
        Codec<List<Integer>> dfu = Codec.INT.listOf().validate(list -> list.size() > 2 ? DataResult.error(() -> "Too many values") : DataResult.success(list));
        Traversal<List<Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(dfu);
        List<ComponentPath.Step> steps = VanillaAdaptersTest.steps(VanillaAdaptersTest.index(0));
        
        VanillaAdaptersTest.assertWalked(codec, VanillaAdaptersTest.index(0));
        VanillaAdaptersTest.assertParity(codec, steps, List.of(List.of(3), List.of(3, 3), List.of(3, 3, 3)));
        VanillaAdaptersTest.assertAbsent(codec, steps, List.of(3, 3, 3));
    }
    
    @Test
    public void stackedLayersAreUnwrapped() {
        Codec<Box> dfu = Codec.INT.listOf()
                .validate(list -> list.isEmpty() ? DataResult.error(() -> "No values") : DataResult.success(list))
                .xmap(Box::new, Box::values);
        Traversal<Box, Access.Plain> codec = VanillaAdapters.ofDfu(dfu);
        
        VanillaAdaptersTest.assertWalked(codec, VanillaAdaptersTest.index(0));
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.index(0)), List.of(new Box(List.of(3)), new Box(List.of())));
    }
    
    @Test
    public void layerThatThrowsReadsAsAbsent() {
        Codec<Box> dfu = Codec.INT.listOf().xmap(Box::new, box -> {
            throw new IllegalStateException("Could not map the box");
        });
        Traversal<Box, Access.Plain> codec = VanillaAdapters.ofDfu(dfu);
        List<ComponentPath.Step> steps = VanillaAdaptersTest.steps(VanillaAdaptersTest.index(0));
        
        VanillaAdaptersTest.assertParity(codec, steps, List.of(new Box(List.of(3))));
        VanillaAdaptersTest.assertAbsent(codec, steps, new Box(List.of(3)));
    }
    
    @Test
    public void listReadsTheElementAtAnIndexOrAnyElement() {
        Traversal<List<Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.INT.listOf());
        List<List<Integer>> values = List.of(List.of(1, 3), List.of(3), List.of());
        
        VanillaAdaptersTest.assertWalked(codec, VanillaAdaptersTest.index(1));
        VanillaAdaptersTest.assertWalked(codec, VanillaAdaptersTest.any());
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.index(1)), values);
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.index(5)), values);
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.any()), values);
    }
    
    @Test
    public void listOutsideTheSizeOfTheCodecReadsAsAbsent() {
        Traversal<List<Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.INT.listOf(1, 2));
        List<List<Integer>> values = List.of(List.of(3), List.of(3, 3), List.of(3, 3, 3), List.of());
        
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.index(0)), values);
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.any()), values);
        VanillaAdaptersTest.assertAbsent(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.index(0)), List.of(3, 3, 3));
    }
    
    @Test
    public void stringKeyIsLookedUpInEveryKindOfMap() {
        Traversal<Map<String, Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.unboundedMap(Codec.STRING, Codec.INT));
        List<Map<String, Integer>> values = List.of(
                new LinkedHashMap<>(Map.of("a", 3, "b", 1)),
                Map.of("a", 3),
                ImmutableMap.of("a", 1, "b", 3),
                new TreeMap<>(Map.of("a", 3, "b", 1)),
                Map.of()
        );
        
        VanillaAdaptersTest.assertWalked(codec, VanillaAdaptersTest.key("a"));
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("a")), values);
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("missing")), values);
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.any()), values);
    }
    
    @Test
    public void keyIsComparedAsItEncodesInAMapWithItsOwnOrder() {
        Traversal<Map<String, Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.unboundedMap(Codec.STRING, Codec.INT));
        TreeMap<String, Integer> folded = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        folded.put("A", 3);
        
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("a")), List.of(folded));
        VanillaAdaptersTest.assertAbsent(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("a")), folded);
        Assertions.assertThat(codec.reader(VanillaAdaptersTest.steps(VanillaAdaptersTest.key("A")), 0).bind(VanillaAdaptersTest.EQUAL_THREE).test(folded)).isTrue();
    }
    
    @Test
    public void identifierKeyOnlyMatchesTheNameItEncodesTo() {
        Traversal<Map<Identifier, Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.unboundedMap(Identifier.CODEC, Codec.INT));
        List<Map<Identifier, Integer>> values = List.of(
                Map.of(Identifier.withDefaultNamespace("stone"), 3),
                new TreeMap<>(Map.of(Identifier.withDefaultNamespace("stone"), 3, Identifier.withDefaultNamespace("dirt"), 1))
        );
        
        VanillaAdaptersTest.assertWalked(codec, VanillaAdaptersTest.key("minecraft:stone"));
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("minecraft:stone")), values);
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("stone")), values);
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("Not An Identifier")), values);
        VanillaAdaptersTest.assertAbsent(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("stone")), values.getFirst());
    }
    
    @Test
    public void keyOfAnyOtherKeyCodecIsEncodedToCompare() {
        Codec<String> upper = Codec.STRING.xmap(name -> name.toLowerCase(Locale.ROOT), name -> name.toUpperCase(Locale.ROOT));
        Traversal<Map<String, Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.unboundedMap(upper, Codec.INT));
        Map<String, Integer> clashing = new LinkedHashMap<>();
        clashing.put("a", 1);
        clashing.put("A", 3);
        List<Map<String, Integer>> values = List.of(Map.of("a", 3), clashing);
        
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("A")), values);
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("a")), values);
        Assertions.assertThat(codec.reader(VanillaAdaptersTest.steps(VanillaAdaptersTest.key("A")), 0).getCost()).isEqualTo(PathReader.Cost.ENCODE);
        Assertions.assertThat(codec.reader(VanillaAdaptersTest.steps(VanillaAdaptersTest.key("A")), 0).bind(VanillaAdaptersTest.EQUAL_THREE).test(values.getFirst())).isTrue();
    }
    
    @Test
    public void strictMapReadsLikeAnUnboundedMap() {
        Traversal<Map<String, Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(ExtraCodecs.strictUnboundedMap(Codec.STRING, Codec.INT));
        List<Map<String, Integer>> values = List.of(Map.of("a", 3), Map.of("b", 3));
        
        VanillaAdaptersTest.assertWalked(codec, VanillaAdaptersTest.key("a"));
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("a")), values);
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.any()), values);
    }
    
    @Test
    public void elementReadsContinueInsideTheElement() {
        Traversal<Map<String, List<Integer>>, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.unboundedMap(Codec.STRING, Codec.INT.listOf()));
        List<Map<String, List<Integer>>> values = List.of(Map.of("a", List.of(1, 3)), Map.of("a", List.of(3)), Map.of("b", List.of(3, 3)));
        
        VanillaAdaptersTest.assertWalked(codec, VanillaAdaptersTest.key("a"), VanillaAdaptersTest.index(1));
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("a"), VanillaAdaptersTest.index(1)), values);
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.any(), VanillaAdaptersTest.any()), values);
    }
    
    @Test
    public void wildcardCostsAtLeastAScan() {
        Traversal<Map<String, Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.unboundedMap(Codec.STRING, NexusCodec.INT.asDfu()));
        
        Assertions.assertThat(codec.reader(VanillaAdaptersTest.steps(VanillaAdaptersTest.any()), 0).getCost()).isNotEqualTo(PathReader.Cost.DIRECT);
        Assertions.assertThat(VanillaAdapters.ofDfu(Codec.INT.listOf()).reader(VanillaAdaptersTest.steps(VanillaAdaptersTest.any()), 0).getCost()).isNotEqualTo(PathReader.Cost.DIRECT);
    }
    
    @Test
    public void keyLookupCostsAtLeastAScan() {
        Traversal<Map<String, Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.unboundedMap(Codec.STRING, NexusCodec.INT.asDfu()));
        List<ComponentPath.Step> steps = VanillaAdaptersTest.steps(VanillaAdaptersTest.key("a"));
        PathReader<Integer> value = VanillaAdaptersTest.traversal(NexusCodec.INT).reader(steps, 1);
        
        Assertions.assertThat(codec.reader(steps, 0).getCost()).isEqualTo(Comparators.max(PathReader.Cost.SCAN, value.getCost()));
        Assertions.assertThat(codec.reader(steps, 0).isOrdered()).isEqualTo(value.isOrdered());
        VanillaAdaptersTest.assertParity(codec, steps, List.of(Map.of("a", 3), Map.of("a", 1), Map.of()));
    }
    
    @Test
    public void dfuCodecOfACodecIsReadAsThatCodec() {
        NexusCodec<List<Integer>, Access.Plain> nexus = NexusCodec.INT.list();
        Traversal<List<Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(nexus.asDfu());
        List<ComponentPath.Step> steps = VanillaAdaptersTest.steps(VanillaAdaptersTest.index(1));
        PathReader<List<Integer>> expected = VanillaAdaptersTest.traversal(nexus).reader(steps, 0);
        
        Assertions.assertThat(codec.reader(steps, 0).getCost()).isEqualTo(expected.getCost());
        Assertions.assertThat(codec.reader(steps, 0).isOrdered()).isEqualTo(expected.isOrdered());
        VanillaAdaptersTest.assertParity(codec, steps, List.of(List.of(1, 3), List.of(3), List.of()));
    }
    
    @Test
    public void dfuCodecOfACodecIsReadAsThatCodecBelowALayer() {
        NexusCodec<List<Integer>, Access.Plain> nexus = NexusCodec.INT.list();
        Traversal<Box, Access.Plain> codec = VanillaAdapters.ofDfu(nexus.asDfu().xmap(Box::new, Box::values));
        List<ComponentPath.Step> steps = VanillaAdaptersTest.steps(VanillaAdaptersTest.index(1));
        PathReader<List<Integer>> expected = VanillaAdaptersTest.traversal(nexus).reader(steps, 0);
        
        Assertions.assertThat(codec.reader(steps, 0).getCost()).isEqualTo(Comparators.max(PathReader.Cost.SCAN, expected.getCost()));
        VanillaAdaptersTest.assertParity(codec, steps, List.of(new Box(List.of(1, 3)), new Box(List.of())));
    }
    
    @Test
    public void dfuCodecOfAStructIsReadAsTheStruct() {
        Traversal<Named, Access.Plain> codec = VanillaAdapters.ofDfu(VanillaAdaptersTest.NAMED.asDfu());
        List<Named> values = List.of(new Named(3, "a"), new Named(1, "b"));
        
        for (String key : List.of("count", "name")) {
            List<ComponentPath.Step> steps = VanillaAdaptersTest.steps(VanillaAdaptersTest.key(key));
            PathReader<Named> expected = VanillaAdaptersTest.traversal(VanillaAdaptersTest.NAMED).reader(steps, 0);
            
            Assertions.assertThat(codec.reader(steps, 0).getCost()).as(key).isEqualTo(expected.getCost());
            Assertions.assertThat(codec.reader(steps, 0).isOrdered()).as(key).isEqualTo(expected.isOrdered());
            VanillaAdaptersTest.assertParity(codec, steps, values);
        }
    }
    
    @Test
    public void codecThatCanNotBeWalkedEncodesTheWholeValue() {
        Codec<Named> record = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("count").forGetter(Named::count),
                Codec.STRING.fieldOf("name").forGetter(Named::name)
        ).apply(instance, Named::new));
        Traversal<Named, Access.Plain> codec = VanillaAdapters.ofDfu(record);
        
        VanillaAdaptersTest.assertEncodesWhole(codec, VanillaAdaptersTest.key("count"));
        VanillaAdaptersTest.assertParity(codec, VanillaAdaptersTest.steps(VanillaAdaptersTest.key("count")), List.of(new Named(3, "a"), new Named(1, "b")));
    }
    
    @Test
    public void stepThatTheCodecCanNotWalkEncodesTheWholeValue() {
        Traversal<Box, Access.Plain> layered = VanillaAdapters.ofDfu(Codec.INT.listOf().xmap(Box::new, Box::values));
        
        VanillaAdaptersTest.assertEncodesWhole(VanillaAdapters.ofDfu(Codec.INT.listOf()), VanillaAdaptersTest.key("a"));
        VanillaAdaptersTest.assertEncodesWhole(VanillaAdapters.ofDfu(Codec.unboundedMap(Codec.STRING, Codec.INT)), VanillaAdaptersTest.index(0));
        VanillaAdaptersTest.assertEncodesWhole(layered, VanillaAdaptersTest.key("a"));
        VanillaAdaptersTest.assertEncodesWhole(VanillaAdapters.ofStream(ByteBufCodecs.VAR_INT));
    }
    
    @Test
    public void boolLeafComparesAsAFlagAndAsTheNumberOfItsByte() {
        Traversal<Boolean, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.BOOL);
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.DIRECT, List.of(true, false), VanillaAdaptersTest.FLAG_COMPARISONS);
        Assertions.assertThat(codec.reader(List.of(), 0).isOrdered()).isFalse();
    }
    
    @Test
    public void byteLeafComparesAsANumberAndAsAFlag() {
        Traversal<Byte, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.BYTE);
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.DIRECT, List.of((byte) 0, (byte) 1, (byte) 3, (byte) -1), VanillaAdaptersTest.FLAG_COMPARISONS);
        Assertions.assertThat(codec.reader(List.of(), 0).isOrdered()).isTrue();
    }
    
    @Test
    public void shortLeafComparesAsANumber() {
        Traversal<Short, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.SHORT);
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.DIRECT, List.of((short) 3, (short) 2, (short) -3, Short.MAX_VALUE), VanillaAdaptersTest.NUMBER_COMPARISONS);
        Assertions.assertThat(codec.reader(List.of(), 0).isOrdered()).isTrue();
    }
    
    @Test
    public void intLeafComparesAsANumber() {
        Traversal<Integer, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.INT);
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.DIRECT, List.of(3, 2, 4, 0, -3, Integer.MIN_VALUE), VanillaAdaptersTest.NUMBER_COMPARISONS);
        Assertions.assertThat(codec.reader(List.of(), 0).isOrdered()).isTrue();
    }
    
    @Test
    public void longLeafComparesAsANumber() {
        Traversal<Long, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.LONG);
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.DIRECT, List.of(3L, -3L, 0L, Long.MAX_VALUE), VanillaAdaptersTest.NUMBER_COMPARISONS);
        Assertions.assertThat(codec.reader(List.of(), 0).isOrdered()).isTrue();
    }
    
    @Test
    public void floatLeafComparesAsANumber() {
        Traversal<Float, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.FLOAT);
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.DIRECT, List.of(3.0F, 2.5F, -0.0F, 3.0000002F, Float.NaN), VanillaAdaptersTest.NUMBER_COMPARISONS);
        Assertions.assertThat(codec.reader(List.of(), 0).isOrdered()).isTrue();
    }
    
    @Test
    public void doubleLeafComparesAsANumber() {
        Traversal<Double, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.DOUBLE);
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.DIRECT, List.of(3.0D, 2.5D, -0.0D, Double.MIN_VALUE, Double.NaN), VanillaAdaptersTest.NUMBER_COMPARISONS);
        Assertions.assertThat(codec.reader(List.of(), 0).isOrdered()).isTrue();
    }
    
    @Test
    public void stringLeafComparesAsTextOfAnyLength() {
        Traversal<String, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.STRING);
        List<String> values = List.of("a", "ab", "", "A", "b", "3", "a".repeat(FriendlyByteBuf.MAX_STRING_LENGTH + 1));
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.DIRECT, values, VanillaAdaptersTest.TEXT_COMPARISONS);
        Assertions.assertThat(codec.reader(List.of(), 0).isOrdered()).isTrue();
    }
    
    @Test
    public void identifierCodecComparesTheTextOfTheIdentifier() {
        Traversal<Identifier, Access.Plain> codec = VanillaAdapters.ofDfu(Identifier.CODEC);
        List<Identifier> values = List.of(
                Identifier.withDefaultNamespace("stone"),
                Identifier.withDefaultNamespace("s"),
                Identifier.withDefaultNamespace("stone_bricks"),
                Identifier.fromNamespaceAndPath("a", "stone")
        );
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.DIRECT, values, VanillaAdaptersTest.TEXT_COMPARISONS);
        Assertions.assertThat(codec.reader(List.of(), 0).isOrdered()).isTrue();
    }
    
    @Test
    public void enumCodecComparesTheSerializedName() {
        Traversal<Shade, Access.Plain> codec = VanillaAdapters.ofDfu(VanillaAdaptersTest.SHADES);
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.DIRECT, List.of(Shade.values()), VanillaAdaptersTest.TEXT_COMPARISONS);
        Assertions.assertThat(codec.reader(List.of(), 0).isOrdered()).isTrue();
    }
    
    @Test
    public void rangedIntReadsAnOutOfRangeValueAsAbsent() {
        Traversal<Integer, Access.Plain> codec = VanillaAdapters.ofDfu(ExtraCodecs.NON_NEGATIVE_INT);
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.SCAN, List.of(3, 0, -1, -3), VanillaAdaptersTest.NUMBER_COMPARISONS);
        VanillaAdaptersTest.assertAbsent(codec, List.of(), -3);
    }
    
    @Test
    public void leafBehindXmapComparesTheMappedValue() {
        Traversal<Count, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.INT.xmap(Count::new, Count::value));
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.SCAN, List.of(new Count(3), new Count(-3)), VanillaAdaptersTest.NUMBER_COMPARISONS);
    }
    
    @Test
    public void leafBehindFlatXmapReadsARefusedValueAsAbsent() {
        Codec<Count> dfu = Codec.INT.flatXmap(
                value -> DataResult.success(new Count(value)),
                count -> count.value() < 0 ? DataResult.error(() -> "Negative count") : DataResult.success(count.value())
        );
        Traversal<Count, Access.Plain> codec = VanillaAdapters.ofDfu(dfu);
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.SCAN, List.of(new Count(3), new Count(0), new Count(-3)), VanillaAdaptersTest.NUMBER_COMPARISONS);
        VanillaAdaptersTest.assertAbsent(codec, List.of(), new Count(-3));
    }
    
    @Test
    public void unsignedByteComparesTheByteItEncodes() {
        Traversal<Integer, Access.Plain> codec = VanillaAdapters.ofDfu(ExtraCodecs.UNSIGNED_BYTE);
        
        VanillaAdaptersTest.assertLeafParity(codec, PathReader.Cost.SCAN, List.of(3, 200, 255, 256), VanillaAdaptersTest.FLAG_COMPARISONS);
        VanillaAdaptersTest.assertAbsent(codec, List.of(), 256);
    }
    
    @Test
    public void leafInsideAListCostsWhatTheLeafCosts() {
        Traversal<List<Integer>, Access.Plain> codec = VanillaAdapters.ofDfu(Codec.INT.listOf());
        
        Assertions.assertThat(codec.reader(VanillaAdaptersTest.steps(VanillaAdaptersTest.index(0)), 0).getCost()).isEqualTo(PathReader.Cost.DIRECT);
    }
    
    @Test
    public void stepPastALeafThrowsLikeTheNativeScalar() {
        List<Map.Entry<Traversal<?, ?>, NexusCodec<?, ?>>> leaves = List.of(
                Map.entry(VanillaAdapters.ofDfu(Codec.BOOL), NexusCodec.BOOL),
                Map.entry(VanillaAdapters.ofDfu(Codec.DOUBLE), NexusCodec.DOUBLE),
                Map.entry(VanillaAdapters.ofDfu(ExtraCodecs.NON_NEGATIVE_INT), NexusCodec.INT),
                Map.entry(VanillaAdapters.ofDfu(Codec.STRING), NexusCodec.STRING),
                Map.entry(VanillaAdapters.ofDfu(Identifier.CODEC), NexusCodec.IDENTIFIER),
                Map.entry(VanillaAdapters.ofDfu(VanillaAdaptersTest.SHADES), NexusCodec.enumOf(Shade.class))
        );
        
        for (ComponentPath.Step step : List.of(VanillaAdaptersTest.key("a"), VanillaAdaptersTest.index(0), VanillaAdaptersTest.any())) {
            List<ComponentPath.Step> steps = VanillaAdaptersTest.steps(step);
            for (Map.Entry<Traversal<?, ?>, NexusCodec<?, ?>> leaf : leaves) {
                String expected = Assertions.catchIllegalArgumentException(() -> VanillaAdaptersTest.traversal(leaf.getValue()).reader(steps, 0)).getMessage();
                Assertions.assertThatIllegalArgumentException()
                        .as(leaf.getValue() + " " + step)
                        .isThrownBy(() -> leaf.getKey().reader(steps, 0))
                        .withMessage(expected);
            }
        }
        
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> VanillaAdapters.ofDfu(Codec.INT.listOf()).reader(VanillaAdaptersTest.steps(VanillaAdaptersTest.index(0), VanillaAdaptersTest.key("a")), 0));
    }
    
    private static void assertWalked(Traversal<?, ?> codec, ComponentPath.Step... steps) {
        Assertions.assertThat(codec.reader(List.of(steps), 0)).isNotInstanceOf(Traversal.EncodedReader.class);
    }
    
    private static void assertEncodesWhole(Traversal<?, ?> codec, ComponentPath.Step... steps) {
        Assertions.assertThat(codec.reader(List.of(steps), 0)).isInstanceOfSatisfying(Traversal.EncodedReader.class, reader -> Assertions.assertThat(reader.codec()).isSameAs(codec));
    }
    
    private static <T> void assertLeafParity(Traversal<T, ?> codec, PathReader.Cost cost, List<T> values, List<Comparison> comparisons) {
        Assertions.assertThat(codec.reader(List.of(), 0).getCost()).isEqualTo(cost);
        VanillaAdaptersTest.assertParity(codec, List.of(), values, comparisons);
    }
    
    private static <T> void assertParity(Traversal<T, ?> codec, List<ComponentPath.Step> steps, List<T> values) {
        VanillaAdaptersTest.assertParity(codec, steps, values, VanillaAdaptersTest.COMPARISONS);
    }
    
    private static <T> void assertParity(Traversal<T, ?> codec, List<ComponentPath.Step> steps, List<T> values, List<Comparison> comparisons) {
        PathReader<T> reader = codec.reader(steps, 0);
        TagReader tagReader = new TagReader(steps);
        for (T value : values) {
            Tag tag = Encoding.encode(codec, value);
            for (Comparison comparison : comparisons) {
                boolean expected = tag != null && tagReader.test(tag, comparison);
                Assertions.assertThat(reader.bind(comparison).test(value)).as(value + " " + steps + " " + comparison).isEqualTo(expected);
            }
        }
    }
    
    private static <T> void assertAbsent(Traversal<T, ?> codec, List<ComponentPath.Step> steps, T value) {
        PathReader<T> reader = codec.reader(steps, 0);
        for (Comparison comparison : VanillaAdaptersTest.COMPARISONS) {
            Assertions.assertThat(reader.bind(comparison).test(value)).as(value + " " + steps + " " + comparison).isFalse();
        }
    }
    
    private static <T> Traversal<T, ?> traversal(NexusCodec<T, ?> codec) {
        return (Traversal<T, ?>) codec;
    }
    
    private static List<Comparison> comparisons(Comparison.Literal... literals) {
        return Arrays.stream(literals)
                .flatMap(literal -> Arrays.stream(Comparison.Operator.values())
                        .filter(operator -> !operator.isOrdered() || !(literal instanceof Comparison.Literal.Flag))
                        .map(operator -> new Comparison(operator, literal))
                )
                .toList();
    }
    
    private static List<ComponentPath.Step> steps(ComponentPath.Step... steps) {
        return List.of(steps);
    }
    
    private static ComponentPath.Step key(String name) {
        return new ComponentPath.Step.Key(name);
    }
    
    private static ComponentPath.Step index(int index) {
        return new ComponentPath.Step.Index(index);
    }
    
    private static ComponentPath.Step any() {
        return new ComponentPath.Step.Wildcard();
    }
    
    private record Box(List<Integer> values) { }
    
    private record Named(int count, String name) { }
    
    private record Count(int value) { }
    
    @RequiredArgsConstructor
    private enum Shade implements StringRepresentable {
        DARK("night"),
        BRIGHT("day");
        
        private final String serializedName;
        
        @Override
        public String getSerializedName() {
            return this.serializedName;
        }
    }
}
