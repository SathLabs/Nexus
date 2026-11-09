package dev.satherov.nexus.test.unit.api.component;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.api.event.component.RegisterPathReadersEvent;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.component.DataComponentEntry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;

import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.component.predicates.DamagePredicate;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Unit;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

///
/// Checks what each filter passes, how filters combine, the order filters are tested in, and the data and network form of every case.
///
public class ComponentFilterTest {
    
    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final CodecFormat<JsonElement, Access.Registries> JSON = CodecFormat.JSON.withRegistries(ComponentFilterTest.REGISTRIES);
    private static final CodecFormat<Tag, Access.Registries> NBT = CodecFormat.NBT.withRegistries(ComponentFilterTest.REGISTRIES);
    
    private static final List<String> READS = new ArrayList<>();
    private static final Map<String, PathReader<Integer>> READERS = Map.of(
            "direct", new RecordingReader("direct", PathReader.Cost.DIRECT, true),
            "late", new RecordingReader("late", PathReader.Cost.DIRECT, true),
            "scan", new RecordingReader("scan", PathReader.Cost.SCAN, true),
            "encode", new RecordingReader("encode", PathReader.Cost.ENCODE, true),
            "unordered", new RecordingReader("unordered", PathReader.Cost.DIRECT, false)
    );
    
    private static final Comparison AT_LEAST_ZERO = new Comparison(Comparison.Operator.GREATER_OR_EQUAL, new Comparison.Literal.Numeric(0.0D));
    private static final Comparison EQUAL_TO_ZERO = new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(0.0D));
    private static final DataComponentType<Integer> UNREGISTERED = DataComponentType.<Integer>builder().persistent(Codec.INT).build();
    
    private static @Nullable DataComponentEntry<Integer> level;
    
    @Test
    public void hasPassesAHolderWithTheComponentWithAnyValue() {
        ComponentFilter filter = ComponentFilter.has(DataComponents.MAX_STACK_SIZE);
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).build())).isTrue();
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build())).isTrue();
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.UNBREAKABLE, Unit.INSTANCE).build())).isFalse();
    }
    
    @Test
    public void hasCountsADefaultComponentUntilItIsRemoved() {
        PatchedDataComponentMap holder = new PatchedDataComponentMap(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 16).build());
        Assertions.assertThat(ComponentFilter.has(DataComponents.MAX_STACK_SIZE).test(holder)).isTrue();
        
        holder.remove(DataComponents.MAX_STACK_SIZE);
        Assertions.assertThat(ComponentFilter.has(DataComponents.MAX_STACK_SIZE).test(holder)).isFalse();
    }
    
    @Test
    public void hasAcceptsATransientComponent() {
        ComponentFilter filter = ComponentFilter.has(DataComponents.CREATIVE_SLOT_LOCK);
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE).build())).isTrue();
    }
    
    @Test
    public void exactPassesOnlyAnEqualValue() {
        ComponentFilter filter = ComponentFilter.exact(DataComponents.MAX_STACK_SIZE, 16);
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 16).build())).isTrue();
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 64).build())).isFalse();
        Assertions.assertThat(filter.test(DataComponentMap.EMPTY)).isFalse();
        Assertions.assertThat(filter.test(new PatchedDataComponentMap(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 16).build()))).isTrue();
    }
    
    @Test
    public void exactRefusesATransientComponent() {
        Assertions.assertThatThrownBy(() -> ComponentFilter.exact(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE)).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void compareTestsThePartAtThePath() {
        ComponentFilter filter = ComponentFilter.compare(ComponentFilterTest.path("direct"), new Comparison(Comparison.Operator.GREATER, new Comparison.Literal.Numeric(2.0D)));
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(3))).isTrue();
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(2))).isFalse();
    }
    
    @Test
    public void compareFailsEveryOperatorOnAnAbsentComponent() {
        for (Comparison.Operator operator : Comparison.Operator.values()) {
            ComponentFilter filter = ComponentFilter.compare(ComponentFilterTest.path("direct"), new Comparison(operator, new Comparison.Literal.Numeric(5.0D)));
            Assertions.assertThat(filter.test(DataComponentMap.EMPTY)).as(operator.getSymbol()).isFalse();
        }
    }
    
    @Test
    public void compareRefusesAnOrderedOperatorOnAPathWithoutOrder() {
        ComponentPath unordered = ComponentFilterTest.path("unordered");
        Assertions.assertThatThrownBy(() -> ComponentFilter.compare(unordered, new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Numeric(1.0D))))
                .isInstanceOf(IllegalArgumentException.class);
        
        Assertions.assertThat(ComponentFilter.compare(unordered, new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(1.0D))).test(ComponentFilterTest.holder(1)))
                .isTrue();
    }
    
    @Test
    public void matchingPassesWhatTheVanillaPredicateMatches() {
        ComponentFilter filter = ComponentFilter.matching(DataComponentPredicates.DAMAGE, DamagePredicate.durability(MinMaxBounds.Ints.atLeast(5)));
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.MAX_DAMAGE, 10).set(DataComponents.DAMAGE, 2).build())).isTrue();
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.MAX_DAMAGE, 10).set(DataComponents.DAMAGE, 8).build())).isFalse();
    }
    
    @Test
    public void allOfPassesOnlyIfEveryFilterPasses() {
        ComponentFilter filter = ComponentFilter.has(DataComponents.MAX_STACK_SIZE).and(ComponentFilter.has(DataComponents.UNBREAKABLE));
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).set(DataComponents.UNBREAKABLE, Unit.INSTANCE).build())).isTrue();
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE, 1).build())).isFalse();
    }
    
    @Test
    public void anyOfPassesIfOneFilterPasses() {
        ComponentFilter filter = ComponentFilter.has(DataComponents.MAX_STACK_SIZE).or(ComponentFilter.has(DataComponents.UNBREAKABLE));
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.UNBREAKABLE, Unit.INSTANCE).build())).isTrue();
        Assertions.assertThat(filter.test(DataComponentMap.EMPTY)).isFalse();
    }
    
    @Test
    public void notPassesWhatItsInnerFilterFails() {
        ComponentFilter filter = ComponentFilter.has(DataComponents.UNBREAKABLE).negate();
        Assertions.assertThat(filter.test(DataComponentMap.EMPTY)).isTrue();
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.UNBREAKABLE, Unit.INSTANCE).build())).isFalse();
    }
    
    @Test
    public void negatingANotReturnsItsInnerFilter() {
        ComponentFilter inner = ComponentFilter.has(DataComponents.UNBREAKABLE);
        ComponentFilter negated = inner.negate();
        Assertions.assertThat(negated).isInstanceOfSatisfying(ComponentFilter.Not.class, not -> Assertions.assertThat(not.getFilter()).isSameAs(inner));
        Assertions.assertThat(negated.negate()).isSameAs(inner);
    }
    
    @Test
    public void allOfAddsTheFiltersOfANestedAllOf() {
        ComponentFilter first = ComponentFilter.has(DataComponents.UNBREAKABLE);
        ComponentFilter second = ComponentFilter.has(DataComponents.DAMAGE);
        ComponentFilter third = ComponentFilter.has(DataComponents.MAX_DAMAGE);
        ComponentFilter filter = ComponentFilter.allOf(List.of(first, ComponentFilter.allOf(List.of(second, third)), ComponentFilter.ANY));
        Assertions.assertThat(filter).isInstanceOfSatisfying(ComponentFilter.AllOf.class, all -> Assertions.assertThat(all.getAllFilters()).containsExactly(first, second, third));
        Assertions.assertThat(first.and(second)).isEqualTo(ComponentFilter.allOf(List.of(first, second)));
    }
    
    @Test
    public void anyOfAddsTheFiltersOfANestedAnyOfOnly() {
        ComponentFilter first = ComponentFilter.has(DataComponents.UNBREAKABLE);
        ComponentFilter second = ComponentFilter.has(DataComponents.DAMAGE);
        ComponentFilter third = ComponentFilter.has(DataComponents.MAX_DAMAGE);
        ComponentFilter both = first.and(second);
        Assertions.assertThat(ComponentFilter.anyOf(List.of(first, ComponentFilter.anyOf(List.of(second, third)))))
                .isInstanceOfSatisfying(ComponentFilter.AnyOf.class, any -> Assertions.assertThat(any.getAllFilters()).containsExactly(first, second, third));
        Assertions.assertThat(ComponentFilter.anyOf(List.of(both, third)))
                .isInstanceOfSatisfying(ComponentFilter.AnyOf.class, any -> Assertions.assertThat(any.getAllFilters()).containsExactly(both, third));
        Assertions.assertThat(first.or(second)).isEqualTo(ComponentFilter.anyOf(List.of(first, second)));
    }
    
    @Test
    public void allOfOfNothingIsAnyAndPassesEverything() {
        Assertions.assertThat(ComponentFilter.allOf(List.of())).isSameAs(ComponentFilter.ANY);
        Assertions.assertThat(ComponentFilter.ANY.test(DataComponentMap.EMPTY)).isTrue();
    }
    
    @Test
    public void anyOfOfNothingPassesNothing() {
        ComponentFilter filter = ComponentFilter.anyOf(List.of());
        Assertions.assertThat(filter).isInstanceOfSatisfying(ComponentFilter.AnyOf.class, any -> Assertions.assertThat(any.getAllFilters()).isEmpty());
        Assertions.assertThat(filter.test(DataComponentMap.EMPTY)).isFalse();
        Assertions.assertThat(filter.test(DataComponentMap.builder().set(DataComponents.UNBREAKABLE, Unit.INSTANCE).build())).isFalse();
    }
    
    @Test
    public void combiningOneFilterReturnsIt() {
        ComponentFilter filter = ComponentFilter.has(DataComponents.UNBREAKABLE);
        Assertions.assertThat(ComponentFilter.allOf(List.of(filter))).isSameAs(filter);
        Assertions.assertThat(ComponentFilter.anyOf(List.of(filter))).isSameAs(filter);
    }
    
    @Test
    public void testsTheCheapestFiltersFirstAndKeepsTheGivenOrder() {
        List<ComponentFilter> filters = List.of(
                ComponentFilterTest.compare("encode"),
                ComponentFilterTest.compare("late"),
                ComponentFilterTest.compare("scan"),
                ComponentFilterTest.compare("direct")
        );
        
        ComponentFilter filter = ComponentFilter.allOf(filters);
        ComponentFilterTest.READS.clear();
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(3))).isTrue();
        Assertions.assertThat(ComponentFilterTest.READS).containsExactly("late", "direct", "scan", "encode");
        Assertions.assertThat(filter).isInstanceOfSatisfying(ComponentFilter.AllOf.class, all -> Assertions.assertThat(all.getAllFilters()).isEqualTo(filters));
    }
    
    @Test
    public void stopsAtTheFirstFilterThatDecides() {
        ComponentFilter failing = ComponentFilter.compare(ComponentFilterTest.path("direct"), new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Numeric(0.0D)));
        ComponentFilterTest.READS.clear();
        Assertions.assertThat(ComponentFilter.allOf(List.of(ComponentFilterTest.compare("encode"), failing)).test(ComponentFilterTest.holder(3))).isFalse();
        Assertions.assertThat(ComponentFilterTest.READS).containsExactly("direct");
        
        ComponentFilterTest.READS.clear();
        Assertions.assertThat(ComponentFilter.anyOf(List.of(ComponentFilterTest.compare("encode"), ComponentFilterTest.compare("scan"))).test(ComponentFilterTest.holder(3))).isTrue();
        Assertions.assertThat(ComponentFilterTest.READS).containsExactly("scan");
    }
    
    @Test
    public void equalsAFilterOfTheSameCaseAndParts() {
        ComponentFilter compare = ComponentFilterTest.compare("direct");
        Assertions.assertThat(compare).isEqualTo(ComponentFilterTest.compare("direct")).hasSameHashCodeAs(ComponentFilterTest.compare("direct"));
        Assertions.assertThat(compare).hasToString(ComponentFilterTest.compare("direct").toString());
        Assertions.assertThat(compare).isNotEqualTo(ComponentFilterTest.compare("late"));
        
        ComponentFilter all = ComponentFilter.allOf(List.of(ComponentFilterTest.compare("encode"), compare));
        Assertions.assertThat(all).isEqualTo(ComponentFilter.allOf(List.of(ComponentFilterTest.compare("encode"), ComponentFilterTest.compare("direct"))));
        Assertions.assertThat(all).isNotEqualTo(ComponentFilter.allOf(List.of(compare, ComponentFilterTest.compare("encode"))));
        Assertions.assertThat(all).isNotEqualTo(ComponentFilter.anyOf(List.of(ComponentFilterTest.compare("encode"), compare)));
        
        Assertions.assertThat(ComponentFilter.exact(DataComponents.MAX_STACK_SIZE, 16)).isEqualTo(ComponentFilter.exact(DataComponents.MAX_STACK_SIZE, 16));
        Assertions.assertThat(ComponentFilter.exact(DataComponents.MAX_STACK_SIZE, 16)).isNotEqualTo(ComponentFilter.exact(DataComponents.MAX_STACK_SIZE, 64));
        Assertions.assertThat(ComponentFilter.has(DataComponents.DAMAGE)).isNotEqualTo(ComponentFilter.has(DataComponents.MAX_DAMAGE));
        Assertions.assertThat(ComponentFilter.has(DataComponents.DAMAGE).negate()).isEqualTo(ComponentFilter.has(DataComponents.DAMAGE).negate());
        Assertions.assertThat(ComponentFilterTest.damage(5)).isEqualTo(ComponentFilterTest.damage(5)).isNotEqualTo(ComponentFilterTest.damage(6));
    }
    
    @Test
    public void roundTripsEveryCaseInJsonNbtAndOnTheNetwork() {
        for (ComponentFilter filter : ComponentFilterTest.everyCase()) {
            CodecFormat<JsonElement, Access.Registries> json = ComponentFilterTest.JSON.strict();
            CodecFormat<Tag, Access.Registries> nbt = ComponentFilterTest.NBT.strict();
            Assertions.assertThat(ComponentFilter.CODEC.decode(json, ComponentFilter.CODEC.encode(json, filter))).as("JSON").isEqualTo(filter);
            Assertions.assertThat(ComponentFilter.CODEC.decode(nbt, ComponentFilter.CODEC.encode(nbt, filter))).as("NBT").isEqualTo(filter);
            
            RegistryFriendlyByteBuf buffer = ComponentFilterTest.buffer();
            ComponentFilter.CODEC.encode(CodecFormat.netty(buffer), filter);
            Assertions.assertThat(ComponentFilter.CODEC.decode(CodecFormat.netty(buffer))).as("network").isEqualTo(filter);
            Assertions.assertThat(buffer.readableBytes()).isZero();
        }
    }
    
    @Test
    public void writesEveryCaseUnderItsKeys() {
        ComponentFilter unbreakable = ComponentFilter.has(DataComponents.UNBREAKABLE);
        ComponentFilter damage = ComponentFilter.has(DataComponents.DAMAGE);
        ComponentFilterTest.assertJson(unbreakable, "{\"has\": \"minecraft:unbreakable\"}");
        ComponentFilterTest.assertJson(ComponentFilter.exact(ComponentFilterTest.level(), 7), "{\"exact\": \"nexus:component_filter_test_level\", \"value\": 7}");
        ComponentFilterTest.assertJson(ComponentFilter.exact(DataComponents.UNBREAKABLE, Unit.INSTANCE), "{\"exact\": \"minecraft:unbreakable\", \"value\": {}}");
        ComponentFilterTest.assertJson(
                ComponentFilter.compare(ComponentFilterTest.path("direct"), new Comparison(Comparison.Operator.GREATER, new Comparison.Literal.Numeric(2.5D))),
                "{\"path\": \"nexus:component_filter_test_level.direct\", \"is\": \">\", \"value\": 2.5}"
        );
        ComponentFilterTest.assertJson(ComponentFilterTest.damage(5), "{\"matches\": \"minecraft:damage\", \"value\": {\"durability\": {\"min\": 5}}}");
        ComponentFilterTest.assertJson(unbreakable.and(damage), "{\"all\": [{\"has\": \"minecraft:unbreakable\"}, {\"has\": \"minecraft:damage\"}]}");
        ComponentFilterTest.assertJson(unbreakable.or(damage), "{\"any\": [{\"has\": \"minecraft:unbreakable\"}, {\"has\": \"minecraft:damage\"}]}");
        ComponentFilterTest.assertJson(unbreakable.negate(), "{\"not\": {\"has\": \"minecraft:unbreakable\"}}");
        ComponentFilterTest.assertJson(ComponentFilter.ANY, "{\"all\": []}");
        ComponentFilterTest.assertJson(ComponentFilter.anyOf(List.of()), "{\"any\": []}");
    }
    
    @Test
    public void writesAWholeNumberThatFitsAnIntAsAnInt() {
        Assertions.assertThat(ComponentFilterTest.literalTag(new Comparison.Literal.Numeric(3.0D))).isEqualTo(IntTag.valueOf(3));
        Assertions.assertThat(ComponentFilterTest.literalTag(new Comparison.Literal.Numeric(-7.0D))).isEqualTo(IntTag.valueOf(-7));
        Assertions.assertThat(ComponentFilterTest.literalTag(new Comparison.Literal.Numeric(2.5D))).isEqualTo(DoubleTag.valueOf(2.5D));
        Assertions.assertThat(ComponentFilterTest.literalTag(new Comparison.Literal.Numeric(1.0E10D))).isEqualTo(DoubleTag.valueOf(1.0E10D));
        Assertions.assertThat(ComponentFilterTest.literalTag(new Comparison.Literal.Text("x"))).isEqualTo(StringTag.valueOf("x"));
        Assertions.assertThat(ComponentFilterTest.literalTag(new Comparison.Literal.Flag(true))).isEqualTo(ByteTag.valueOf(true));
    }
    
    @Test
    public void keepsNegativeZeroInJson() {
        ComponentFilter filter = ComponentFilter.compare(ComponentFilterTest.path("direct"), new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(-0.0D)));
        Assertions.assertThat(ComponentFilter.CODEC.decode(ComponentFilterTest.JSON, ComponentFilter.CODEC.encode(ComponentFilterTest.JSON, filter))).isEqualTo(filter);
    }
    
    @Test
    public void readsABooleanThenANumberThenAString() {
        Assertions.assertThat(ComponentFilterTest.jsonLiteral("3")).isEqualTo(new Comparison.Literal.Numeric(3.0D));
        Assertions.assertThat(ComponentFilterTest.jsonLiteral("2.5")).isEqualTo(new Comparison.Literal.Numeric(2.5D));
        Assertions.assertThat(ComponentFilterTest.jsonLiteral("true")).isEqualTo(new Comparison.Literal.Flag(true));
        Assertions.assertThat(ComponentFilterTest.jsonLiteral("false")).isEqualTo(new Comparison.Literal.Flag(false));
        Assertions.assertThat(ComponentFilterTest.jsonLiteral("\"true\"")).isEqualTo(new Comparison.Literal.Text("true"));
        Assertions.assertThat(ComponentFilterTest.jsonLiteral("\"3\"")).isEqualTo(new Comparison.Literal.Text("3"));
        
        Assertions.assertThat(ComponentFilterTest.nbtLiteral(ByteTag.valueOf((byte) 1))).isEqualTo(new Comparison.Literal.Flag(true));
        Assertions.assertThat(ComponentFilterTest.nbtLiteral(ByteTag.valueOf((byte) 0))).isEqualTo(new Comparison.Literal.Flag(false));
        Assertions.assertThat(ComponentFilterTest.nbtLiteral(ByteTag.valueOf((byte) 5))).isEqualTo(new Comparison.Literal.Numeric(5.0D));
        Assertions.assertThat(ComponentFilterTest.nbtLiteral(IntTag.valueOf(1))).isEqualTo(new Comparison.Literal.Numeric(1.0D));
        Assertions.assertThat(ComponentFilterTest.nbtLiteral(StringTag.valueOf("a"))).isEqualTo(new Comparison.Literal.Text("a"));
    }
    
    @Test
    public void picksTheFirstCaseWhoseFirstKeyIsPresent() {
        Assertions.assertThat(ComponentFilterTest.decode("{\"not\": {\"has\": \"minecraft:unbreakable\"}, \"has\": \"minecraft:damage\"}"))
                .isEqualTo(ComponentFilter.has(DataComponents.DAMAGE));
        Assertions.assertThat(ComponentFilterTest.decode("{\"path\": \"nexus:component_filter_test_level.direct\", \"exact\": \"minecraft:max_stack_size\", \"value\": 16}"))
                .isEqualTo(ComponentFilter.exact(DataComponents.MAX_STACK_SIZE, 16));
        Assertions.assertThat(ComponentFilterTest.decode("{\"any\": [], \"all\": []}")).isSameAs(ComponentFilter.ANY);
    }
    
    @Test
    public void refusesTheKeysOfOtherCasesOnAStrictFormat() {
        String input = "{\"has\": \"minecraft:unbreakable\", \"not\": {\"has\": \"minecraft:damage\"}}";
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.decode(ComponentFilterTest.JSON.strict(), JsonParser.parseString(input))))
                .containsExactly(new CodecError("not", "Unknown key"));
    }
    
    @Test
    public void failsAtTheKeyOfEachFaultyPart() {
        String path = "\"nexus:component_filter_test_level.direct\"";
        ComponentFilterTest.assertError(
                "{\"has\": \"minecraft:nope\"}",
                "has",
                "Expected an element of the registry 'minecraft:data_component_type', found \"minecraft:nope\""
        );
        ComponentFilterTest.assertError(
                "{\"exact\": \"minecraft:creative_slot_lock\", \"value\": {}}",
                "exact",
                "Expected a persistent component type, found \"minecraft:creative_slot_lock\""
        );
        ComponentFilterTest.assertError(
                "{\"matches\": \"minecraft:nope\", \"value\": {}}",
                "matches",
                "Expected an element of the registry 'minecraft:data_component_predicate_type', found \"minecraft:nope\""
        );
        ComponentFilterTest.assertError(
                "{\"path\": " + path + ", \"is\": \"~\", \"value\": 1}",
                "is",
                "Expected one of [==, !=, <, <=, >, >=], found \"~\""
        );
        ComponentFilterTest.assertError("{\"path\": " + path + ", \"is\": \"<\", \"value\": true}", "is", "Flags have no order, found the operator '<'");
        ComponentFilterTest.assertError(
                "{\"path\": \"nexus:component_filter_test_level.unordered\", \"is\": \"<\", \"value\": 1}",
                "is",
                "The values at the path 'nexus:component_filter_test_level.unordered' have no order, found the operator '<'"
        );
        ComponentFilterTest.assertError("{\"path\": " + path + ", \"is\": \"==\", \"value\": [1]}", "value", "Expected a boolean, a number, or a string, found a list");
        ComponentFilterTest.assertError("{\"exact\": \"minecraft:max_stack_size\"}", "value", "Missing");
        ComponentFilterTest.assertError(
                "{\"all\": [{\"not\": {\"has\": \"minecraft:nope\"}}]}",
                "all[0].not.has",
                "Expected an element of the registry 'minecraft:data_component_type', found \"minecraft:nope\""
        );
        ComponentFilterTest.assertError("{\"junk\": 1}", "", "Expected an object with one of the keys [has, exact, path, matches, all, any, not], found an object");
    }
    
    @Test
    public void writesTheCaseIndexFirstOnTheNetwork() {
        ComponentFilter unbreakable = ComponentFilter.has(DataComponents.UNBREAKABLE);
        List<ComponentFilter> cases = List.of(
                unbreakable,
                ComponentFilter.exact(DataComponents.MAX_STACK_SIZE, 16),
                ComponentFilterTest.compare("direct"),
                ComponentFilterTest.damage(5),
                ComponentFilter.ANY,
                ComponentFilter.anyOf(List.of()),
                unbreakable.negate()
        );
        
        for (int index = 0; index < cases.size(); index++) {
            RegistryFriendlyByteBuf buffer = ComponentFilterTest.buffer();
            ComponentFilter.CODEC.encode(CodecFormat.netty(buffer), cases.get(index));
            Assertions.assertThat(buffer.readVarInt()).as(cases.get(index).toString()).isEqualTo(index);
        }
    }
    
    @Test
    public void writesTheKindOfALiteralAndEveryNumberAsADoubleOnTheNetwork() {
        RegistryFriendlyByteBuf buffer = ComponentFilterTest.buffer();
        ComponentFilter.CODEC.encode(
                CodecFormat.netty(buffer),
                ComponentFilter.compare(ComponentFilterTest.path("direct"), new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Numeric(3.0D)))
        );
        
        Assertions.assertThat(buffer.readVarInt()).isEqualTo(2);
        Assertions.assertThat(buffer.readUtf()).isEqualTo("nexus:component_filter_test_level.direct");
        Assertions.assertThat(buffer.readVarInt()).isEqualTo(Comparison.Operator.LESS.ordinal());
        Assertions.assertThat(buffer.readVarInt()).isEqualTo(0);
        Assertions.assertThat(buffer.readDouble()).isEqualTo(3.0D);
        Assertions.assertThat(buffer.readableBytes()).isZero();
        
        ComponentFilter.CODEC.encode(
                CodecFormat.netty(buffer),
                ComponentFilter.compare(ComponentFilterTest.path("unordered"), new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true)))
        );
        
        Assertions.assertThat(buffer.readVarInt()).isEqualTo(2);
        Assertions.assertThat(buffer.readUtf()).isEqualTo("nexus:component_filter_test_level.unordered");
        Assertions.assertThat(buffer.readVarInt()).isEqualTo(Comparison.Operator.EQUAL.ordinal());
        Assertions.assertThat(buffer.readVarInt()).isEqualTo(2);
        Assertions.assertThat(buffer.readBoolean()).isTrue();
    }
    
    @Test
    public void refusesAnIndexOutOfRangeOnTheNetwork() {
        RegistryFriendlyByteBuf cases = ComponentFilterTest.buffer();
        cases.writeVarInt(7);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.decode(CodecFormat.netty(cases))))
                .containsExactly(new CodecError("", "Expected an integer in [0, 6], found 7"));
        
        RegistryFriendlyByteBuf kinds = ComponentFilterTest.buffer();
        kinds.writeVarInt(2);
        kinds.writeUtf("nexus:component_filter_test_level.direct");
        kinds.writeVarInt(0);
        kinds.writeVarInt(3);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.decode(CodecFormat.netty(kinds))))
                .containsExactly(new CodecError("value", "Expected an integer in [0, 2], found 3"));
    }
    
    @Test
    public void failsToEncodeAnUnregisteredComponentOrPredicateType() {
        String unregistered = "Expected an element of the registry 'minecraft:data_component_type', found an unregistered value";
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.encode(ComponentFilterTest.JSON, ComponentFilter.has(ComponentFilterTest.UNREGISTERED))))
                .containsExactly(new CodecError("has", unregistered));
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.encode(CodecFormat.netty(ComponentFilterTest.buffer()), ComponentFilter.exact(ComponentFilterTest.UNREGISTERED, 1))))
                .containsExactly(new CodecError("exact", unregistered));
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.encode(ComponentFilterTest.NBT, ComponentFilter.compare(ComponentFilterTest.unregisteredPath(), ComponentFilterTest.EQUAL_TO_ZERO))))
                .containsExactly(new CodecError("path", unregistered));
        
        DataComponentPredicate.Type<DamagePredicate> type = new DataComponentPredicate.ConcreteType<>(DamagePredicate.CODEC);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.encode(ComponentFilterTest.JSON, ComponentFilter.matching(type, DamagePredicate.durability(MinMaxBounds.Ints.ANY)))))
                .containsExactly(new CodecError("matches", "Expected an element of the registry 'minecraft:data_component_predicate_type', found an unregistered value"));
    }
    
    private static List<ComponentFilter> everyCase() {
        ComponentFilter unbreakable = ComponentFilter.has(DataComponents.UNBREAKABLE);
        return List.of(
                unbreakable,
                ComponentFilter.has(DataComponents.CREATIVE_SLOT_LOCK),
                ComponentFilter.exact(DataComponents.MAX_STACK_SIZE, 16),
                ComponentFilter.exact(DataComponents.UNBREAKABLE, Unit.INSTANCE),
                ComponentFilter.exact(ComponentFilterTest.level(), 7),
                ComponentFilter.compare(ComponentFilterTest.path("direct"), new Comparison(Comparison.Operator.GREATER, new Comparison.Literal.Numeric(2.5D))),
                ComponentFilter.compare(ComponentFilterTest.path("direct"), new Comparison(Comparison.Operator.LESS_OR_EQUAL, new Comparison.Literal.Numeric(3.0D))),
                ComponentFilter.compare(ComponentFilterTest.path("direct"), new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Numeric(1.0E10D))),
                ComponentFilter.compare(ComponentFilterTest.path("direct"), new Comparison(Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Text("x"))),
                ComponentFilter.compare(ComponentFilterTest.path("unordered"), new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true))),
                ComponentFilterTest.damage(5),
                unbreakable.and(ComponentFilterTest.compare("encode")),
                unbreakable.or(ComponentFilterTest.damage(3)),
                unbreakable.negate(),
                ComponentFilter.ANY,
                ComponentFilter.anyOf(List.of()),
                ComponentFilter.allOf(List.of(unbreakable.or(ComponentFilter.has(DataComponents.DAMAGE)).negate(), ComponentFilterTest.compare("scan")))
        );
    }
    
    private static void assertJson(ComponentFilter filter, String json) {
        Assertions.assertThat(ComponentFilter.CODEC.encode(ComponentFilterTest.JSON, filter)).isEqualTo(JsonParser.parseString(json));
    }
    
    private static void assertError(String input, String path, String message) {
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilterTest.decode(input))).as(input).containsExactly(new CodecError(path, message));
    }
    
    private static ComponentFilter decode(String json) {
        return ComponentFilter.CODEC.decode(ComponentFilterTest.JSON, JsonParser.parseString(json));
    }
    
    private static Tag literalTag(Comparison.Literal literal) {
        ComponentFilter filter = ComponentFilter.compare(ComponentFilterTest.path("unordered"), new Comparison(Comparison.Operator.EQUAL, literal));
        CompoundTag encoded = (CompoundTag) ComponentFilter.CODEC.encode(ComponentFilterTest.NBT, filter);
        return Objects.requireNonNull(encoded.get("value"));
    }
    
    private static Comparison.Literal jsonLiteral(String json) {
        ComponentFilter filter = ComponentFilterTest.decode("{\"path\": \"nexus:component_filter_test_level.unordered\", \"is\": \"==\", \"value\": " + json + "}");
        return ((ComponentFilter.Compare) filter).getComparison().literal();
    }
    
    private static Comparison.Literal nbtLiteral(Tag tag) {
        CompoundTag input = new CompoundTag();
        input.putString("path", "nexus:component_filter_test_level.unordered");
        input.putString("is", "==");
        input.put("value", tag);
        return ((ComponentFilter.Compare) ComponentFilter.CODEC.decode(ComponentFilterTest.NBT, input)).getComparison().literal();
    }
    
    private static List<CodecError> errors(ThrowableAssert.ThrowingCallable call) {
        return Assertions.catchThrowableOfType(NexusCodecException.class, call).errors();
    }
    
    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), ComponentFilterTest.REGISTRIES, ConnectionType.OTHER);
    }
    
    private static DataComponentMap holder(int value) {
        return DataComponentMap.builder().set(ComponentFilterTest.level(), value).build();
    }
    
    private static ComponentPath path(String reader) {
        return ComponentPath.of(ComponentFilterTest.level(), reader);
    }
    
    private static ComponentPath unregisteredPath() {
        return ComponentPath.of(ComponentFilterTest.UNREGISTERED, "");
    }
    
    private static ComponentFilter compare(String reader) {
        return ComponentFilter.compare(ComponentFilterTest.path(reader), ComponentFilterTest.AT_LEAST_ZERO);
    }
    
    private static ComponentFilter damage(int durability) {
        return ComponentFilter.matching(DataComponentPredicates.DAMAGE, DamagePredicate.durability(MinMaxBounds.Ints.atLeast(durability)));
    }
    
    private static DataComponentType<Integer> level() {
        return Objects.requireNonNull(ComponentFilterTest.level).value();
    }
    
    private record RecordingReader(String name, PathReader.Cost cost, boolean ordered) implements PathReader<Integer> {
        
        @Override
        public PathReader.Cost getCost() {
            return this.cost;
        }
        
        @Override
        public boolean isOrdered() {
            return this.ordered;
        }
        
        @Override
        public Predicate<Integer> bind(Comparison comparison) {
            return value -> {
                ComponentFilterTest.READS.add(this.name);
                return comparison.test(value.doubleValue());
            };
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            ComponentFilterTest.level = registrations.component("component_filter_test_level", NexusCodec.INT).register();
        }
        
        @SubscribeEvent
        public static void onRegisterPathReaders(RegisterPathReadersEvent event) {
            event.register(
                    ComponentFilterTest.level(),
                    steps -> steps.size() == 1 && steps.getFirst() instanceof ComponentPath.Step.Key(String name) ? ComponentFilterTest.READERS.get(name) : null
            );
        }
    }
}
