package dev.satherov.nexus.test.unit.internal.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.key.MapKey;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.path.Encoding;
import dev.satherov.nexus.internal.codec.path.TagReader;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.mojang.datafixers.util.Either;
import com.sun.management.ThreadMXBean;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

///
/// Checks that the reader of every codec kind passes exactly what encoding the value to NBT and reading the tag passes, and what it refuses when built.
///
public class TraversalReaderTest {
    
    private static final List<ComponentPath.Step> ROOT = List.of();
    private static final ComponentPath.Step ANY = new ComponentPath.Step.Wildcard();
    private static final List<Object> COMMON_LITERALS = List.of(0, 1, -1, "", true, false);
    
    private static final Identifier CIRCLE_ID = Identifier.fromNamespaceAndPath("nexus", "circle");
    private static final Identifier SQUARE_ID = Identifier.fromNamespaceAndPath("nexus", "square");
    private static final Identifier TRIANGLE_ID = Identifier.fromNamespaceAndPath("nexus", "triangle");
    
    private static final NexusCodec<List<Integer>, Access.Plain> NUMBERS = NexusCodec.INT.list();
    private static final NexusCodec<Set<String>, Access.Plain> WORDS = NexusCodec.STRING.set();
    private static final NexusCodec<Map<String, Integer>, Access.Plain> COUNTS = NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT);
    private static final NexusCodec<Map<Integer, String>, Access.Plain> NAMES = NexusCodec.mapOf(MapKey.INT, NexusCodec.STRING);
    
    private static final StructCodec<Listing, Access.Plain> LISTING = NexusCodec.struct(
            "listing",
            NexusCodec.STRING.field("id", Listing::id),
            NexusCodec.INT.optionalField("count", 1, Listing::count),
            NexusCodec.STRING.optionalField("label", Listing::label),
            Listing::new
    );
    
    private static final StructCodec<Tagged, Access.Plain> TAGGED = NexusCodec.struct(
            "tagged",
            NexusCodec.STRING.field("name", Tagged::name),
            TraversalReaderTest.LISTING.inline(Tagged::listing),
            Tagged::new
    );
    
    private static final StructCodec<Labeled, Access.Plain> LABELED = NexusCodec.struct(
            "labeled",
            NexusCodec.INT.field("count", Labeled::count),
            NexusCodec.string(3).field("name", Labeled::name),
            Labeled::new
    );
    
    private static final StructCodec<Pair, Access.Plain> BROKEN_PAIR = NexusCodec.struct(
            "pair",
            NexusCodec.INT.field("left", Pair::left),
            NexusCodec.INT.field("right", TraversalReaderTest::refuseRight),
            Pair::new
    );
    
    private static final StructCodec<Circle, Access.Plain> CIRCLE = NexusCodec.struct(
            "circle",
            NexusCodec.STRING.field("color", Circle::color),
            NexusCodec.INT.field("radius", Circle::radius),
            Circle::new
    );
    
    private static final StructCodec<Square, Access.Plain> SQUARE = NexusCodec.struct(
            "square",
            NexusCodec.STRING.field("color", Square::color),
            NexusCodec.INT.field("side", Square::side),
            NexusCodec.BOOL.optionalField("filled", false, Square::filled),
            Square::new
    );
    
    private static final StructCodec<Shape, Access.Plain> SHAPE = NexusCodec.dispatch(
            "type",
            TraversalReaderTest::typeOf,
            Map.of(TraversalReaderTest.CIRCLE_ID, TraversalReaderTest.CIRCLE, TraversalReaderTest.SQUARE_ID, TraversalReaderTest.SQUARE)
    );
    
    private static final StructCodec<Named, Access.Plain> INLINE_NAMED = NexusCodec.struct(
            "named",
            NexusCodec.STRING.field("name", Named::name),
            TraversalReaderTest.SHAPE.inline(Named::shape),
            Named::new
    );
    
    private static final NexusCodec<Either<Integer, Listing>, Access.Plain> NUMBER_OR_LISTING = NexusCodec.either(NexusCodec.INT, TraversalReaderTest.LISTING);
    
    private static final NexusCodec<Listing, Access.Plain> SHORT_LISTING = TraversalReaderTest.LISTING.orShort(
            NexusCodec.STRING,
            id -> new Listing(id, 1, Optional.empty()),
            listing -> listing.count() == 1 && listing.label().isEmpty() ? Optional.of(listing.id()) : Optional.empty()
    );
    
    private static final NexusCodec<Node, Access.Plain> TREE = NexusCodec.recursive(
            "node",
            self -> NexusCodec.struct(
                    "node",
                    NexusCodec.INT.field("value", Node::value),
                    self.list().field("children", Node::children),
                    Node::new
            )
    );
    
    private static final NexusCodec<Chain, Access.Plain> CHAIN = NexusCodec.recursive(
            "chain",
            self -> NexusCodec.either(NexusCodec.INT, self).xmap(Chain::new, Chain::link)
    );
    
    private static final NexusCodec<Amount, Access.Plain> AMOUNT = NexusCodec.INT.xmap(Amount::new, Amount::value);
    private static final NexusCodec<Amount, Access.Plain> POSITIVE = NexusCodec.INT.flatXmap(Amount::new, TraversalReaderTest::positive);
    private static final NexusCodec<Amount, Access.Plain> REFUSING = NexusCodec.INT.xmap(Amount::new, TraversalReaderTest::refuse);
    private static final NexusCodec<Integer, Access.Plain> NON_NEGATIVE = NexusCodec.INT.validate(value -> value < 0 ? "negative" : null);
    private static final NexusCodec<Wrapper, Access.Plain> WRAPPED = TraversalReaderTest.LISTING.xmap(Wrapper::new, Wrapper::listing);
    
    private static final NexusCodec<Holder<Item>, Access.Registries> ITEM = NexusCodec.holder(Registries.ITEM);
    private static final NexusCodec<HolderSet<Item>, Access.Registries> ITEMS = NexusCodec.holderSet(Registries.ITEM);
    private static final NexusCodec<Holder<SoundEvent>, Access.Registries> SOUND = NexusCodec.holderOrInline(
            Registries.SOUND_EVENT,
            NexusCodec.struct(
                    "sound_event",
                    NexusCodec.IDENTIFIER.field("sound_id", SoundEvent::location),
                    NexusCodec.FLOAT.optionalField("range", SoundEvent::fixedRange),
                    SoundEvent::new
            )
    );
    
    private static final Holder<Item> STONE = BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE);
    private static final Holder<Item> DIRT = BuiltInRegistries.ITEM.wrapAsHolder(Items.DIRT);
    private static final Holder<Item> DIRECT_STONE = Holder.direct(Items.STONE);
    private static final Holder<Item> FOREIGN_STONE = Holder.Reference.createStandAlone(
            new HolderOwner<>() { },
            ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("stone"))
    );
    private static final Holder<SoundEvent> ANVIL = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ANVIL_LAND);
    private static final Holder<SoundEvent> BOOM = Holder.direct(new SoundEvent(Identifier.fromNamespaceAndPath("nexus", "boom"), Optional.of(8.0F)));
    
    private static Comparison.Literal literal(Object value) {
        return switch (value) {
            case Number number -> new Comparison.Literal.Numeric(number.doubleValue());
            case String text -> new Comparison.Literal.Text(text);
            case Boolean flag -> new Comparison.Literal.Flag(flag);
            default -> throw new IllegalArgumentException("Not a literal '" + value + "'");
        };
    }
    
    private static List<ComponentPath.Step> path(Object... parts) {
        return Arrays.stream(parts)
                .map(part -> switch (part) {
                    case String name -> new ComponentPath.Step.Key(name);
                    case Integer index -> new ComponentPath.Step.Index(index);
                    case ComponentPath.Step step -> step;
                    default -> throw new IllegalArgumentException("Not a step '" + part + "'");
                })
                .toList();
    }
    
    private static <T> PathReader<T> reader(NexusCodec<T, ?> codec, List<ComponentPath.Step> steps) {
        return ((Traversal<T, ?>) codec).reader(steps, 0);
    }
    
    private static <T> boolean passes(NexusCodec<T, ?> codec, List<ComponentPath.Step> steps, T value, Comparison.Operator operator, Object literal) {
        return TraversalReaderTest.reader(codec, steps).bind(new Comparison(operator, TraversalReaderTest.literal(literal))).test(value);
    }
    
    private static <T> void assertParity(NexusCodec<T, ?> codec, List<ComponentPath.Step> steps, T value, Object... literals) {
        PathReader<T> reader = TraversalReaderTest.reader(codec, steps);
        Tag encoded = Encoding.encode(codec, value);
        TagReader tags = new TagReader(steps);
        List<Comparison> comparisons = Stream.concat(Arrays.stream(literals), TraversalReaderTest.COMMON_LITERALS.stream())
                .map(TraversalReaderTest::literal)
                .flatMap(literal -> Arrays.stream(Comparison.Operator.values())
                        .filter(operator -> !operator.isOrdered() || !(literal instanceof Comparison.Literal.Flag))
                        .map(operator -> new Comparison(operator, literal))
                )
                .toList();
        
        for (Comparison comparison : comparisons) {
            boolean expected = encoded != null && tags.test(encoded, comparison);
            Assertions.assertThat(reader.bind(comparison).test(value))
                    .as("%s %s %s on %s", steps, comparison.operator().getSymbol(), comparison.literal(), value)
                    .isEqualTo(expected);
        }
    }
    
    private static void assertRefused(NexusCodec<?, ?> codec, Object... parts) {
        Assertions.assertThatThrownBy(() -> TraversalReaderTest.reader(codec, TraversalReaderTest.path(parts))).isInstanceOf(IllegalArgumentException.class);
    }
    
    private static Identifier typeOf(Shape shape) {
        return switch (shape) {
            case Circle _ -> TraversalReaderTest.CIRCLE_ID;
            case Square _ -> TraversalReaderTest.SQUARE_ID;
            case Triangle _ -> TraversalReaderTest.TRIANGLE_ID;
        };
    }
    
    private static int positive(Amount amount) {
        if (amount.value() <= 0) {
            throw new NexusCodecException("Expected a positive amount");
        }
        
        return amount.value();
    }
    
    private static int refuse(Amount amount) {
        throw new IllegalStateException("Refused '" + amount + "'");
    }
    
    private static int refuseRight(Pair pair) {
        throw new IllegalStateException("Refused '" + pair.right() + "'");
    }
    
    private static <T> long allocatedBy(NexusCodec<T, ?> codec, List<ComponentPath.Step> steps, T value, Object literal) {
        Predicate<T> test = TraversalReaderTest.reader(codec, steps).bind(new Comparison(Comparison.Operator.EQUAL, TraversalReaderTest.literal(literal)));
        ThreadMXBean threads = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        int passed = 0;
        for (int i = 0; i < 1_000; i++) {
            passed += test.test(value) ? 1 : 0;
        }
        
        long before = threads.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 100_000; i++) {
            passed += test.test(value) ? 1 : 0;
        }
        
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        Assertions.assertThat(passed).isEqualTo(101_000);
        return allocated;
    }
    
    @Nested
    public class ScalarCodecs {
        
        @Test
        public void numbersCompareAsNumbers() {
            TraversalReaderTest.assertParity(NexusCodec.SHORT, TraversalReaderTest.ROOT, (short) -3, -3, -2.5);
            TraversalReaderTest.assertParity(NexusCodec.INT, TraversalReaderTest.ROOT, 5, 5, 4.5, "5");
            TraversalReaderTest.assertParity(NexusCodec.VAR_INT, TraversalReaderTest.ROOT, Integer.MIN_VALUE, Integer.MIN_VALUE);
            TraversalReaderTest.assertParity(NexusCodec.LONG, TraversalReaderTest.ROOT, (1L << 53) + 1, 1L << 53);
            TraversalReaderTest.assertParity(NexusCodec.VAR_LONG, TraversalReaderTest.ROOT, Long.MAX_VALUE, Long.MAX_VALUE);
            TraversalReaderTest.assertParity(NexusCodec.FLOAT, TraversalReaderTest.ROOT, 0.1F, 0.1F, 0.1);
            TraversalReaderTest.assertParity(NexusCodec.FLOAT, TraversalReaderTest.ROOT, Float.NaN, Double.NaN);
            TraversalReaderTest.assertParity(NexusCodec.DOUBLE, TraversalReaderTest.ROOT, -0.0D, 0.0D);
            TraversalReaderTest.assertParity(NexusCodec.DOUBLE, TraversalReaderTest.ROOT, Double.NaN, Double.NaN);
            
            Assertions.assertThat(TraversalReaderTest.passes(NexusCodec.INT, TraversalReaderTest.ROOT, 5, Comparison.Operator.EQUAL, 5)).isTrue();
            Assertions.assertThat(TraversalReaderTest.passes(NexusCodec.DOUBLE, TraversalReaderTest.ROOT, -0.0D, Comparison.Operator.EQUAL, 0)).isTrue();
        }
        
        @Test
        public void bytesAndBooleansCompareAsNumbersAndAsFlags() {
            for (byte value : new byte[]{ -1, 0, 1, 2 }) {
                TraversalReaderTest.assertParity(NexusCodec.BYTE, TraversalReaderTest.ROOT, value, 2, "1");
            }
            
            TraversalReaderTest.assertParity(NexusCodec.BOOL, TraversalReaderTest.ROOT, true, "true");
            TraversalReaderTest.assertParity(NexusCodec.BOOL, TraversalReaderTest.ROOT, false, "false");
            
            Assertions.assertThat(TraversalReaderTest.passes(NexusCodec.BOOL, TraversalReaderTest.ROOT, true, Comparison.Operator.EQUAL, 1)).isTrue();
            Assertions.assertThat(TraversalReaderTest.passes(NexusCodec.BYTE, TraversalReaderTest.ROOT, (byte) 2, Comparison.Operator.EQUAL, true)).isTrue();
        }
        
        @Test
        public void stringsCompareAsText() {
            TraversalReaderTest.assertParity(NexusCodec.STRING, TraversalReaderTest.ROOT, "beta", "alpha", "beta", "gamma");
            TraversalReaderTest.assertParity(NexusCodec.STRING, TraversalReaderTest.ROOT, "", "a");
            TraversalReaderTest.assertParity(NexusCodec.STRING, TraversalReaderTest.ROOT, new String(Character.toChars(0x10000)), String.valueOf(Character.MAX_VALUE));
        }
        
        @Test
        public void aStringLongerThanItsLimitIsAbsent() {
            TraversalReaderTest.assertParity(NexusCodec.string(3), TraversalReaderTest.ROOT, "abc", "abc");
            TraversalReaderTest.assertParity(NexusCodec.string(3), TraversalReaderTest.ROOT, "abcd", "abcd");
            
            Assertions.assertThat(TraversalReaderTest.passes(NexusCodec.string(3), TraversalReaderTest.ROOT, "abcd", Comparison.Operator.NOT_EQUAL, "x")).isFalse();
        }
        
        @Test
        public void identifiersAndEnumsCompareAsText() {
            Identifier stone = Identifier.withDefaultNamespace("stone");
            TraversalReaderTest.assertParity(
                    NexusCodec.IDENTIFIER,
                    TraversalReaderTest.ROOT,
                    stone,
                    "minecraft:stone",
                    "minecraft:stonf",
                    "minecraft",
                    "stone"
            );
            TraversalReaderTest.assertParity(NexusCodec.enumOf(Size.class), TraversalReaderTest.ROOT, Size.LARGE, "large", "LARGE", "small");
            
            Assertions.assertThat(TraversalReaderTest.passes(NexusCodec.IDENTIFIER, TraversalReaderTest.ROOT, stone, Comparison.Operator.EQUAL, "minecraft:stone")).isTrue();
            Assertions.assertThat(TraversalReaderTest.passes(NexusCodec.enumOf(Size.class), TraversalReaderTest.ROOT, Size.LARGE, Comparison.Operator.EQUAL, "large")).isTrue();
        }
        
        @Test
        public void aStepPastAScalarThrows() {
            List<NexusCodec<?, ?>> scalars = List.of(
                    NexusCodec.BOOL,
                    NexusCodec.BYTE,
                    NexusCodec.INT,
                    NexusCodec.DOUBLE,
                    NexusCodec.STRING,
                    NexusCodec.IDENTIFIER,
                    NexusCodec.enumOf(Size.class)
            );
            
            for (NexusCodec<?, ?> codec : scalars) {
                for (List<ComponentPath.Step> steps : List.of(TraversalReaderTest.path("value"), TraversalReaderTest.path(0), TraversalReaderTest.path(TraversalReaderTest.ANY))) {
                    Assertions.assertThatThrownBy(() -> TraversalReaderTest.reader(codec, steps)).isInstanceOf(IllegalArgumentException.class);
                }
            }
        }
    }
    
    @Nested
    public class HolderCodecs {
        
        @Test
        public void aHolderReadsItsIdentifierAsText() {
            TraversalReaderTest.assertParity(TraversalReaderTest.ITEM, TraversalReaderTest.ROOT, TraversalReaderTest.STONE, "minecraft:stone", "minecraft:dirt");
            TraversalReaderTest.assertParity(TraversalReaderTest.ITEM, TraversalReaderTest.ROOT, TraversalReaderTest.DIRT, "minecraft:stone", "minecraft:dirt");
            
            Assertions.assertThat(TraversalReaderTest.passes(
                    TraversalReaderTest.ITEM,
                    TraversalReaderTest.ROOT,
                    TraversalReaderTest.STONE,
                    Comparison.Operator.EQUAL,
                    "minecraft:stone"
            )).isTrue();
        }
        
        @Test
        public void aHolderThatCannotBeSerializedIsAbsent() {
            TraversalReaderTest.assertParity(TraversalReaderTest.ITEM, TraversalReaderTest.ROOT, TraversalReaderTest.DIRECT_STONE, "minecraft:stone");
            TraversalReaderTest.assertParity(TraversalReaderTest.ITEM, TraversalReaderTest.ROOT, TraversalReaderTest.FOREIGN_STONE, "minecraft:stone");
            
            Assertions.assertThat(TraversalReaderTest.passes(
                    TraversalReaderTest.ITEM,
                    TraversalReaderTest.ROOT,
                    TraversalReaderTest.FOREIGN_STONE,
                    Comparison.Operator.NOT_EQUAL,
                    "x"
            )).isFalse();
        }
        
        @Test
        public void anInlineValueReadsThroughTheInlineCodec() {
            for (Holder<SoundEvent> sound : List.of(TraversalReaderTest.ANVIL, TraversalReaderTest.BOOM)) {
                TraversalReaderTest.assertParity(TraversalReaderTest.SOUND, TraversalReaderTest.ROOT, sound, "minecraft:block.anvil.land");
                TraversalReaderTest.assertParity(TraversalReaderTest.SOUND, TraversalReaderTest.path("sound_id"), sound, "nexus:boom");
                TraversalReaderTest.assertParity(TraversalReaderTest.SOUND, TraversalReaderTest.path("range"), sound, 8);
            }
            
            Assertions.assertThat(TraversalReaderTest.passes(
                    TraversalReaderTest.SOUND,
                    TraversalReaderTest.path("range"),
                    TraversalReaderTest.BOOM,
                    Comparison.Operator.EQUAL,
                    8
            )).isTrue();
        }
        
        @Test
        public void aStepNeitherTheIdentifierNorTheInlineValueHoldsThrows() {
            TraversalReaderTest.assertRefused(TraversalReaderTest.ITEM, "id");
            TraversalReaderTest.assertRefused(TraversalReaderTest.SOUND, "volume");
        }
        
        @Test
        public void aHolderSetReadsByEncodingIt() {
            TraversalReaderTest.assertParity(TraversalReaderTest.ITEMS, TraversalReaderTest.ROOT, HolderSet.direct(TraversalReaderTest.STONE), "minecraft:stone");
            TraversalReaderTest.assertParity(
                    TraversalReaderTest.ITEMS,
                    TraversalReaderTest.path(1),
                    HolderSet.direct(TraversalReaderTest.STONE, TraversalReaderTest.DIRT),
                    "minecraft:dirt"
            );
            
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.ITEMS, TraversalReaderTest.ROOT)).isInstanceOf(Traversal.EncodedReader.class);
        }
    }
    
    @Nested
    public class ListAndSetCodecs {
        
        @Test
        public void anIndexReadsTheElementInIterationOrder() {
            List<Integer> numbers = List.of(3, 1, 2);
            Set<String> words = new LinkedHashSet<>(List.of("beta", "alpha"));
            for (int index = 0; index < 4; index++) {
                TraversalReaderTest.assertParity(TraversalReaderTest.NUMBERS, TraversalReaderTest.path(index), numbers, 2, 3);
                TraversalReaderTest.assertParity(TraversalReaderTest.WORDS, TraversalReaderTest.path(index), words, "alpha", "beta");
            }
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.WORDS, TraversalReaderTest.path(1), words, Comparison.Operator.EQUAL, "alpha")).isTrue();
        }
        
        @Test
        public void aWildcardReadsAnyElement() {
            TraversalReaderTest.assertParity(TraversalReaderTest.NUMBERS, TraversalReaderTest.path(TraversalReaderTest.ANY), List.of(3, 1, 2), 2, 4);
            TraversalReaderTest.assertParity(TraversalReaderTest.NUMBERS, TraversalReaderTest.path(TraversalReaderTest.ANY), List.of(), 2);
            TraversalReaderTest.assertParity(
                    NexusCodec.INT.list().list(),
                    TraversalReaderTest.path(TraversalReaderTest.ANY, 1),
                    List.of(List.of(1), List.of(1, 2), List.of()),
                    2
            );
        }
        
        @Test
        public void aCollectionWithMoreElementsThanItsLimitIsAbsent() {
            NexusCodec<List<Integer>, Access.Plain> pair = NexusCodec.INT.list(2);
            TraversalReaderTest.assertParity(pair, TraversalReaderTest.path(0), List.of(1, 2, 3), 1);
            TraversalReaderTest.assertParity(pair, TraversalReaderTest.path(TraversalReaderTest.ANY), List.of(1, 2, 3), 1);
            
            Assertions.assertThat(TraversalReaderTest.passes(pair, TraversalReaderTest.path(0), List.of(1, 2, 3), Comparison.Operator.EQUAL, 1)).isFalse();
        }
        
        @Test
        public void aWholeCollectionPassesNothing() {
            TraversalReaderTest.assertParity(TraversalReaderTest.NUMBERS, TraversalReaderTest.ROOT, List.of(1), 1);
        }
        
        @Test
        public void aKeyInACollectionThrows() {
            TraversalReaderTest.assertRefused(TraversalReaderTest.NUMBERS, "first");
            TraversalReaderTest.assertRefused(TraversalReaderTest.WORDS, "first");
            TraversalReaderTest.assertRefused(TraversalReaderTest.NUMBERS, 0, 0);
        }
    }
    
    @Nested
    public class MapCodecs {
        
        @Test
        public void aKeyReadsItsValueInEveryMapClass() {
            Map<String, Integer> entries = Map.of("a", 1, "b", 2);
            List<Map<String, Integer>> maps = List.of(
                    new HashMap<>(entries),
                    new LinkedHashMap<>(entries),
                    entries,
                    Map.of("a", 1),
                    Map.of(),
                    ImmutableMap.copyOf(entries),
                    ImmutableSortedMap.copyOf(entries),
                    new Object2ObjectOpenHashMap<>(entries),
                    new Object2ObjectLinkedOpenHashMap<>(entries),
                    new Object2ObjectArrayMap<>(entries),
                    new TreeMap<>(entries),
                    Collections.unmodifiableMap(new HashMap<>(entries))
            );
            
            for (Map<String, Integer> map : maps) {
                for (String key : List.of("a", "b", "c")) {
                    TraversalReaderTest.assertParity(TraversalReaderTest.COUNTS, TraversalReaderTest.path(key), map, 2);
                }
            }
            
            Assertions.assertThat(TraversalReaderTest.passes(
                    TraversalReaderTest.COUNTS,
                    TraversalReaderTest.path("b"),
                    new TreeMap<>(entries),
                    Comparison.Operator.EQUAL,
                    2
            )).isTrue();
        }
        
        @Test
        public void aKeyReadsTheEncodedKeyInMapsThatDoNotLookUpByEquals() {
            TreeMap<String, Integer> caseless = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            caseless.put("A", 1);
            Map<String, Integer> identity = new IdentityHashMap<>();
            identity.put(String.valueOf(new char[]{ 'a' }), 1);
            ImmutableSortedMap<String, Integer> sorted = ImmutableSortedMap.<String, Integer>orderedBy(String.CASE_INSENSITIVE_ORDER).put("A", 1).build();
            
            for (Map<String, Integer> map : List.of(caseless, identity, sorted)) {
                TraversalReaderTest.assertParity(TraversalReaderTest.COUNTS, TraversalReaderTest.path("a"), map, 1);
                TraversalReaderTest.assertParity(TraversalReaderTest.COUNTS, TraversalReaderTest.path("A"), map, 1);
            }
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.COUNTS, TraversalReaderTest.path("a"), caseless, Comparison.Operator.EQUAL, 1)).isFalse();
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.COUNTS, TraversalReaderTest.path("a"), identity, Comparison.Operator.EQUAL, 1)).isTrue();
        }
        
        @Test
        public void aNameThatDoesNotEncodeBackToItselfIsAbsent() {
            Map<Integer, String> names = new HashMap<>(Map.of(1, "one", -2, "minus two"));
            for (String key : List.of("1", "01", "+1", "-2", "x", "")) {
                TraversalReaderTest.assertParity(TraversalReaderTest.NAMES, TraversalReaderTest.path(key), names, "one", "minus two");
                TraversalReaderTest.assertParity(TraversalReaderTest.NAMES, TraversalReaderTest.path(key), new TreeMap<>(names), "one");
            }
            
            UUID id = new UUID(0xABCDL, 0xEF01L);
            NexusCodec<Map<UUID, Integer>, Access.Plain> byId = NexusCodec.mapOf(MapKey.UUID, NexusCodec.INT);
            TraversalReaderTest.assertParity(byId, TraversalReaderTest.path(id.toString()), Map.of(id, 7), 7);
            TraversalReaderTest.assertParity(byId, TraversalReaderTest.path(id.toString().toUpperCase(Locale.ROOT)), Map.of(id, 7), 7);
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.NAMES, TraversalReaderTest.path("01"), names, Comparison.Operator.EQUAL, "one")).isFalse();
        }
        
        @Test
        public void aWildcardReadsAnyValue() {
            TraversalReaderTest.assertParity(TraversalReaderTest.COUNTS, TraversalReaderTest.path(TraversalReaderTest.ANY), Map.of("a", 1, "b", 2), 2, 3);
            TraversalReaderTest.assertParity(TraversalReaderTest.COUNTS, TraversalReaderTest.path(TraversalReaderTest.ANY), Map.of(), 2);
        }
        
        @Test
        public void aMapWithMoreEntriesThanItsLimitIsAbsent() {
            NexusCodec<Map<String, Integer>, Access.Plain> single = NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT, 1);
            TraversalReaderTest.assertParity(single, TraversalReaderTest.path("a"), Map.of("a", 1, "b", 2), 1);
            TraversalReaderTest.assertParity(single, TraversalReaderTest.path(TraversalReaderTest.ANY), Map.of("a", 1, "b", 2), 1);
        }
        
        @Test
        public void anIndexInAMapThrows() {
            TraversalReaderTest.assertRefused(TraversalReaderTest.COUNTS, 0);
            TraversalReaderTest.assertRefused(TraversalReaderTest.COUNTS, "a", "b");
        }
    }
    
    @Nested
    public class StructCodecs {
        
        @Test
        public void aKeyReadsItsField() {
            Listing listing = new Listing("stone", 5, Optional.of("Stone"));
            TraversalReaderTest.assertParity(TraversalReaderTest.LISTING, TraversalReaderTest.path("id"), listing, "stone");
            TraversalReaderTest.assertParity(TraversalReaderTest.LISTING, TraversalReaderTest.path("count"), listing, 5);
            TraversalReaderTest.assertParity(TraversalReaderTest.LISTING, TraversalReaderTest.path("label"), listing, "Stone");
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.LISTING, TraversalReaderTest.path("count"), listing, Comparison.Operator.GREATER, 4)).isTrue();
        }
        
        @Test
        public void aFieldAtItsFallbackIsAbsent() {
            Listing listing = new Listing("stone", 1, Optional.empty());
            TraversalReaderTest.assertParity(TraversalReaderTest.LISTING, TraversalReaderTest.path("count"), listing, 1);
            TraversalReaderTest.assertParity(TraversalReaderTest.LISTING, TraversalReaderTest.path("label"), listing, "Stone");
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.LISTING, TraversalReaderTest.path("count"), listing, Comparison.Operator.EQUAL, 1)).isFalse();
            Assertions.assertThat(TraversalReaderTest.passes(
                    TraversalReaderTest.LISTING,
                    TraversalReaderTest.path("label"),
                    listing,
                    Comparison.Operator.NOT_EQUAL,
                    "x"
            )).isFalse();
        }
        
        @Test
        public void aKeyOfAnInlineStructReadsItsField() {
            Tagged tagged = new Tagged("first", new Listing("stone", 5, Optional.empty()));
            for (String key : List.of("name", "id", "count", "label")) {
                TraversalReaderTest.assertParity(TraversalReaderTest.TAGGED, TraversalReaderTest.path(key), tagged, "first", "stone", 5);
            }
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.TAGGED, TraversalReaderTest.path("id"), tagged, Comparison.Operator.EQUAL, "stone")).isTrue();
        }
        
        @Test
        public void aWildcardReadsAnyField() {
            Listing listing = new Listing("stone", 5, Optional.of("5"));
            TraversalReaderTest.assertParity(TraversalReaderTest.LISTING, TraversalReaderTest.path(TraversalReaderTest.ANY), listing, "5", 5, "stone");
        }
        
        @Test
        public void aWholeStructPassesNothingAndHasNoOrder() {
            Listing listing = new Listing("stone", 5, Optional.empty());
            TraversalReaderTest.assertParity(TraversalReaderTest.LISTING, TraversalReaderTest.ROOT, listing, "stone");
            TraversalReaderTest.assertParity(TraversalReaderTest.SHAPE, TraversalReaderTest.ROOT, new Circle("red", 2), "red");
            
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.LISTING, TraversalReaderTest.ROOT).isOrdered()).isFalse();
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.SHAPE, TraversalReaderTest.ROOT).isOrdered()).isFalse();
        }
        
        @Test
        public void aSiblingThatFailsToEncodeIsNotSeen() {
            Labeled labeled = new Labeled(5, "too long");
            Pair pair = new Pair(5, 6);
            Assertions.assertThat(Encoding.encode(TraversalReaderTest.LABELED, labeled)).isNull();
            Assertions.assertThat(Encoding.encode(TraversalReaderTest.BROKEN_PAIR, pair)).isNull();
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.LABELED, TraversalReaderTest.path("count"), labeled, Comparison.Operator.EQUAL, 5)).isTrue();
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.BROKEN_PAIR, TraversalReaderTest.path("left"), pair, Comparison.Operator.EQUAL, 5)).isTrue();
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.LABELED, TraversalReaderTest.path("name"), labeled, Comparison.Operator.NOT_EQUAL, "x")).isFalse();
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.BROKEN_PAIR, TraversalReaderTest.path("right"), pair, Comparison.Operator.NOT_EQUAL, 0)).isFalse();
        }
        
        @Test
        public void aKeyTheStructLacksThrows() {
            TraversalReaderTest.assertRefused(TraversalReaderTest.LISTING, "price");
            TraversalReaderTest.assertRefused(TraversalReaderTest.LISTING, 0);
            TraversalReaderTest.assertRefused(TraversalReaderTest.LISTING, "id", "first");
            TraversalReaderTest.assertRefused(TraversalReaderTest.TAGGED, "price");
        }
    }
    
    @Nested
    public class BranchCodecs {
        
        @Test
        public void eitherReadsTheSideTheValueHolds() {
            Either<Integer, Listing> number = Either.left(5);
            Either<Integer, Listing> listing = Either.right(new Listing("stone", 5, Optional.empty()));
            for (Either<Integer, Listing> value : List.of(number, listing)) {
                TraversalReaderTest.assertParity(TraversalReaderTest.NUMBER_OR_LISTING, TraversalReaderTest.ROOT, value, 5);
                TraversalReaderTest.assertParity(TraversalReaderTest.NUMBER_OR_LISTING, TraversalReaderTest.path("count"), value, 5);
            }
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.NUMBER_OR_LISTING, TraversalReaderTest.ROOT, number, Comparison.Operator.EQUAL, 5)).isTrue();
            Assertions.assertThat(TraversalReaderTest.passes(
                    TraversalReaderTest.NUMBER_OR_LISTING,
                    TraversalReaderTest.path("count"),
                    listing,
                    Comparison.Operator.EQUAL,
                    5
            )).isTrue();
        }
        
        @Test
        public void aDispatchReadsTheSubtypeTheValuePicks() {
            List<Shape> shapes = List.of(new Circle("red", 2), new Square("blue", 3, true), new Square("blue", 3, false), new Triangle("green"));
            for (Shape shape : shapes) {
                for (String key : List.of("type", "color", "radius", "side", "filled")) {
                    TraversalReaderTest.assertParity(
                            TraversalReaderTest.SHAPE,
                            TraversalReaderTest.path(key),
                            shape,
                            "nexus:circle",
                            "red",
                            2,
                            3
                    );
                }
            }
            
            Assertions.assertThat(TraversalReaderTest.passes(
                    TraversalReaderTest.SHAPE,
                    TraversalReaderTest.path("type"),
                    new Circle("red", 2),
                    Comparison.Operator.EQUAL,
                    "nexus:circle"
            )).isTrue();
            Assertions.assertThat(TraversalReaderTest.passes(
                    TraversalReaderTest.SHAPE,
                    TraversalReaderTest.path("side"),
                    new Square("blue", 3, false),
                    Comparison.Operator.EQUAL,
                    3
            )).isTrue();
            Assertions.assertThat(TraversalReaderTest.passes(
                    TraversalReaderTest.SHAPE,
                    TraversalReaderTest.path("color"),
                    new Triangle("green"),
                    Comparison.Operator.NOT_EQUAL,
                    "x"
            )).isFalse();
        }
        
        @Test
        public void anInlineDispatchReadsTheSubtypeTheValuePicks() {
            for (Shape shape : List.of(new Circle("red", 2), new Square("blue", 3, true))) {
                Named named = new Named("first", shape);
                for (String key : List.of("name", "type", "color", "radius", "side", "filled")) {
                    TraversalReaderTest.assertParity(TraversalReaderTest.INLINE_NAMED, TraversalReaderTest.path(key), named, "nexus:square", 2);
                }
            }
        }
        
        @Test
        public void theShortFormReadsAsItsCodecAndTheStructAsAStruct() {
            Listing shortForm = new Listing("stone", 1, Optional.empty());
            Listing full = new Listing("stone", 5, Optional.empty());
            for (Listing listing : List.of(shortForm, full)) {
                TraversalReaderTest.assertParity(TraversalReaderTest.SHORT_LISTING, TraversalReaderTest.ROOT, listing, "stone");
                TraversalReaderTest.assertParity(TraversalReaderTest.SHORT_LISTING, TraversalReaderTest.path("id"), listing, "stone");
                TraversalReaderTest.assertParity(TraversalReaderTest.SHORT_LISTING, TraversalReaderTest.path("count"), listing, 5);
            }
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.SHORT_LISTING, TraversalReaderTest.ROOT, shortForm, Comparison.Operator.EQUAL, "stone")).isTrue();
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.SHORT_LISTING, TraversalReaderTest.path("id"), full, Comparison.Operator.EQUAL, "stone")).isTrue();
        }
        
        @Test
        public void aStepNoBranchHoldsThrows() {
            TraversalReaderTest.assertRefused(TraversalReaderTest.NUMBER_OR_LISTING, 0);
            TraversalReaderTest.assertRefused(TraversalReaderTest.SHAPE, "height");
            TraversalReaderTest.assertRefused(TraversalReaderTest.SHAPE, "type", "path");
            TraversalReaderTest.assertRefused(TraversalReaderTest.SHAPE, "radius", 0);
            TraversalReaderTest.assertRefused(TraversalReaderTest.INLINE_NAMED, "height");
        }
    }
    
    @Nested
    public class MappedCodecs {
        
        @Test
        public void aMappingReadsTheValueMappedBack() {
            TraversalReaderTest.assertParity(TraversalReaderTest.AMOUNT, TraversalReaderTest.ROOT, new Amount(4), 4);
            TraversalReaderTest.assertParity(TraversalReaderTest.WRAPPED, TraversalReaderTest.path("count"), new Wrapper(new Listing("stone", 5, Optional.empty())), 5);
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.AMOUNT, TraversalReaderTest.ROOT, new Amount(4), Comparison.Operator.EQUAL, 4)).isTrue();
        }
        
        @Test
        public void aValueTheMappingRefusesIsAbsent() {
            for (Amount amount : List.of(new Amount(4), new Amount(-4))) {
                TraversalReaderTest.assertParity(TraversalReaderTest.POSITIVE, TraversalReaderTest.ROOT, amount, 4, -4);
                TraversalReaderTest.assertParity(TraversalReaderTest.REFUSING, TraversalReaderTest.ROOT, amount, 4, -4);
            }
            
            for (int value : new int[]{ 4, -4 }) {
                TraversalReaderTest.assertParity(TraversalReaderTest.NON_NEGATIVE, TraversalReaderTest.ROOT, value, 4, -4);
            }
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.POSITIVE, TraversalReaderTest.ROOT, new Amount(-4), Comparison.Operator.NOT_EQUAL, 0)).isFalse();
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.REFUSING, TraversalReaderTest.ROOT, new Amount(4), Comparison.Operator.NOT_EQUAL, 0)).isFalse();
        }
        
        @Test
        public void aRecursiveCodecReadsThroughItsDefinition() {
            Node tree = new Node(1, List.of(new Node(2, List.of()), new Node(3, List.of(new Node(4, List.of())))));
            for (List<ComponentPath.Step> steps : List.of(
                    TraversalReaderTest.path("value"),
                    TraversalReaderTest.path("children", 1, "value"),
                    TraversalReaderTest.path("children", 1, "children", 0, "value"),
                    TraversalReaderTest.path("children", TraversalReaderTest.ANY, "value"),
                    TraversalReaderTest.path("children", 2, "value")
            )) {
                TraversalReaderTest.assertParity(TraversalReaderTest.TREE, steps, tree, 2, 3, 4);
            }
            
            Assertions.assertThat(TraversalReaderTest.passes(
                    TraversalReaderTest.TREE,
                    TraversalReaderTest.path("children", 1, "children", 0, "value"),
                    tree,
                    Comparison.Operator.EQUAL,
                    4
            )).isTrue();
            TraversalReaderTest.assertRefused(TraversalReaderTest.TREE, "children", "value");
        }
        
        @Test
        public void aRecursiveCodecThatReachesItselfWithoutAStepReadsByEncoding() {
            Chain chain = new Chain(Either.right(new Chain(Either.left(5))));
            TraversalReaderTest.assertParity(TraversalReaderTest.CHAIN, TraversalReaderTest.ROOT, chain, 5, 4);
            TraversalReaderTest.assertParity(TraversalReaderTest.CHAIN, TraversalReaderTest.path("link"), chain, 5);
            TraversalReaderTest.assertParity(TraversalReaderTest.CHAIN, TraversalReaderTest.path(0), new Chain(Either.left(5)), 5);
            
            Assertions.assertThat(TraversalReaderTest.passes(TraversalReaderTest.CHAIN, TraversalReaderTest.ROOT, chain, Comparison.Operator.EQUAL, 5)).isTrue();
        }
    }
    
    @Nested
    public class EncodedReaders {
        
        @Test
        public void aCodecWithoutAReaderOfItsOwnReadsByEncoding() {
            UUID id = new UUID(0x0000_0001_0000_0002L, 0x0000_0003_0000_0004L);
            TraversalReaderTest.assertParity(NexusCodec.UUID, TraversalReaderTest.ROOT, id, 1);
            TraversalReaderTest.assertParity(NexusCodec.UUID, TraversalReaderTest.path(2), id, 3);
            TraversalReaderTest.assertParity(NexusCodec.UUID, TraversalReaderTest.path(TraversalReaderTest.ANY), id, 4);
            TraversalReaderTest.assertParity(NexusCodec.unit("unit"), TraversalReaderTest.ROOT, "unit", "unit");
            
            Assertions.assertThat(TraversalReaderTest.passes(NexusCodec.UUID, TraversalReaderTest.path(2), id, Comparison.Operator.EQUAL, 3)).isTrue();
            Assertions.assertThat(TraversalReaderTest.reader(NexusCodec.UUID, TraversalReaderTest.path(2))).isInstanceOf(Traversal.EncodedReader.class);
        }
        
        @Test
        public void oneOrManyReadsByEncoding() {
            NexusCodec<List<Integer>, Access.Plain> numbers = NexusCodec.INT.oneOrMany();
            for (List<Integer> value : List.of(List.of(5), List.of(5, 6), List.<Integer>of())) {
                TraversalReaderTest.assertParity(numbers, TraversalReaderTest.ROOT, value, 5);
                TraversalReaderTest.assertParity(numbers, TraversalReaderTest.path(1), value, 6);
                TraversalReaderTest.assertParity(numbers, TraversalReaderTest.path(TraversalReaderTest.ANY), value, 5);
            }
        }
    }
    
    @Nested
    public class CostsAndOrder {
        
        @Test
        public void aReaderCostsWhatItsMostExpensivePartCosts() {
            List<ComponentPath.Step> any = TraversalReaderTest.path(TraversalReaderTest.ANY);
            Assertions.assertThat(TraversalReaderTest.reader(NexusCodec.INT, TraversalReaderTest.ROOT).getCost()).isEqualTo(PathReader.Cost.DIRECT);
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.ITEM, TraversalReaderTest.ROOT).getCost()).isEqualTo(PathReader.Cost.DIRECT);
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.LISTING, TraversalReaderTest.path("count")).getCost()).isEqualTo(PathReader.Cost.DIRECT);
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.SHAPE, TraversalReaderTest.path("color")).getCost()).isEqualTo(PathReader.Cost.DIRECT);
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.NUMBERS, TraversalReaderTest.path(0)).getCost()).isEqualTo(PathReader.Cost.DIRECT);
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.WORDS, TraversalReaderTest.path(0)).getCost()).isEqualTo(PathReader.Cost.SCAN);
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.NUMBERS, any).getCost()).isEqualTo(PathReader.Cost.SCAN);
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.COUNTS, TraversalReaderTest.path("a")).getCost()).isEqualTo(PathReader.Cost.SCAN);
            Assertions.assertThat(TraversalReaderTest.reader(NexusCodec.UUID, TraversalReaderTest.ROOT).getCost()).isEqualTo(PathReader.Cost.ENCODE);
            Assertions.assertThat(TraversalReaderTest.reader(NexusCodec.UUID.list(), TraversalReaderTest.path(0)).getCost()).isEqualTo(PathReader.Cost.ENCODE);
            Assertions.assertThat(TraversalReaderTest.reader(NexusCodec.UUID.list(), any).getCost()).isEqualTo(PathReader.Cost.ENCODE);
        }
        
        @Test
        public void flagsHoldersAndStructsHaveNoOrder() {
            Assertions.assertThat(TraversalReaderTest.reader(NexusCodec.INT, TraversalReaderTest.ROOT).isOrdered()).isTrue();
            Assertions.assertThat(TraversalReaderTest.reader(NexusCodec.BYTE, TraversalReaderTest.ROOT).isOrdered()).isTrue();
            Assertions.assertThat(TraversalReaderTest.reader(NexusCodec.STRING, TraversalReaderTest.ROOT).isOrdered()).isTrue();
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.LISTING, TraversalReaderTest.path("count")).isOrdered()).isTrue();
            Assertions.assertThat(TraversalReaderTest.reader(NexusCodec.BOOL, TraversalReaderTest.ROOT).isOrdered()).isFalse();
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.ITEM, TraversalReaderTest.ROOT).isOrdered()).isFalse();
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.SHAPE, TraversalReaderTest.path("filled")).isOrdered()).isFalse();
            Assertions.assertThat(TraversalReaderTest.reader(TraversalReaderTest.NUMBER_OR_LISTING, TraversalReaderTest.ROOT).isOrdered()).isTrue();
        }
        
        @Test
        public void aDirectReadAllocatesNothing() {
            Listing listing = new Listing("stone", 5, Optional.empty());
            Circle circle = new Circle("red", 5);
            
            Assertions.assertThat(TraversalReaderTest.allocatedBy(NexusCodec.BOOL, TraversalReaderTest.ROOT, true, true)).isLessThan(1_024);
            Assertions.assertThat(TraversalReaderTest.allocatedBy(
                    NexusCodec.IDENTIFIER,
                    TraversalReaderTest.ROOT,
                    Identifier.withDefaultNamespace("stone"),
                    "minecraft:stone"
            )).isLessThan(1_024);
            Assertions.assertThat(TraversalReaderTest.allocatedBy(TraversalReaderTest.LISTING, TraversalReaderTest.path("count"), listing, 5)).isLessThan(1_024);
            Assertions.assertThat(TraversalReaderTest.allocatedBy(TraversalReaderTest.NUMBERS, TraversalReaderTest.path(1), List.of(1, 5), 5)).isLessThan(1_024);
            Assertions.assertThat(TraversalReaderTest.allocatedBy(
                    TraversalReaderTest.NUMBER_OR_LISTING,
                    TraversalReaderTest.path("count"),
                    Either.right(listing),
                    5
            )).isLessThan(1_024);
            Assertions.assertThat(TraversalReaderTest.allocatedBy(
                    TraversalReaderTest.INLINE_NAMED,
                    TraversalReaderTest.path("radius"),
                    new Named("first", circle),
                    5
            )).isLessThan(1_024);
            Assertions.assertThat(TraversalReaderTest.allocatedBy(TraversalReaderTest.SHAPE, TraversalReaderTest.path("type"), circle, "nexus:circle")).isLessThan(1_024);
        }
    }
    
    public enum Size {
        SMALL,
        LARGE
    }
    
    public sealed interface Shape permits Circle, Square, Triangle { }
    
    public record Listing(String id, int count, Optional<String> label) { }
    
    public record Tagged(String name, Listing listing) { }
    
    public record Labeled(int count, String name) { }
    
    public record Pair(int left, int right) { }
    
    public record Circle(String color, int radius) implements Shape { }
    
    public record Square(String color, int side, boolean filled) implements Shape { }
    
    public record Triangle(String color) implements Shape { }
    
    public record Named(String name, Shape shape) { }
    
    public record Node(int value, List<Node> children) { }
    
    public record Chain(Either<Integer, Chain> link) { }
    
    public record Amount(int value) { }
    
    public record Wrapper(Listing listing) { }
}
