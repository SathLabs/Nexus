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
import dev.satherov.nexus.internal.component.LookupGeneration;
import dev.satherov.nexus.internal.component.vanilla.FilterPredicate;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;

import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.advancements.criterion.NbtPredicate;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.component.predicates.CustomDataPredicate;
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
import net.minecraft.world.item.component.CustomData;

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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Predicate;

///
/// Checks what each filter passes, how filters combine, the order filters are tested in, the result that a patched map stores, and the data and network form of every case.
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
    private static final String TOO_DEEP = "Expected at most " + ComponentFilter.MAX_DEPTH + " nested filters, found more";
    
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
    
    @Test
    public void storesTheResultOfAMemoizedFilterOnAPatchedMapUntilItChanges() {
        PatchedDataComponentMap map = new PatchedDataComponentMap(DataComponentMap.EMPTY);
        map.set(ComponentFilterTest.level(), 3);
        ComponentFilter filter = ComponentFilterTest.compare("scan");
        ComponentFilterTest.assertThreeTests(filter, map, true, 2);
        
        map.set(ComponentFilterTest.level(), -1);
        ComponentFilterTest.assertThreeTests(filter, map, false, 1);
        
        map.applyPatch(DataComponentPatch.builder().set(ComponentFilterTest.level(), 5).build());
        ComponentFilterTest.assertThreeTests(filter, map, true, 1);
        
        map.remove(ComponentFilterTest.level());
        ComponentFilterTest.assertThreeTests(filter, map, false, 0);
        
        map.set(ComponentFilterTest.level(), 3);
        ComponentFilterTest.assertThreeTests(filter, map, true, 1);
        
        //noinspection UnstableApiUsage The event of the client stands in for any update of the tags.
        LookupGeneration.onTagsUpdated(new TagsUpdatedEvent.ClientPacketReceived(ComponentFilterTest.REGISTRIES, false));
        ComponentFilterTest.assertThreeTests(filter, map, true, 1);
    }
    
    @Test
    public void storesTheResultOfAFilterThatTestsAnotherFilterOnTheSameMap() {
        PatchedDataComponentMap map = new PatchedDataComponentMap(DataComponentMap.EMPTY);
        map.set(ComponentFilterTest.level(), 3);
        ComponentFilter outer = ComponentFilter.matching(FilterPredicate.TYPE, new FilterPredicate(ComponentFilterTest.compare("scan")));
        ComponentFilterTest.assertThreeTests(outer, map, true, 2);
    }
    
    @Test
    public void refusesMoreNestedFiltersThanTheLimitOnTheNetwork() {
        int unbreakable = BuiltInRegistries.DATA_COMPONENT_TYPE.getId(DataComponents.UNBREAKABLE);
        RegistryFriendlyByteBuf nots = ComponentFilterTest.buffer();
        for (int level = 0; level < 10_000; level++) {
            nots.writeVarInt(6);
        }
        
        nots.writeVarInt(0);
        nots.writeVarInt(unbreakable);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.decode(CodecFormat.netty(nots))))
                .extracting(CodecError::message)
                .containsExactly(ComponentFilterTest.TOO_DEEP);
        
        RegistryFriendlyByteBuf combined = ComponentFilterTest.buffer();
        for (int level = 0; level < 100; level++) {
            combined.writeVarInt(level % 2 == 0 ? 4 : 5);
            combined.writeVarInt(2);
            combined.writeVarInt(0);
            combined.writeVarInt(unbreakable);
        }
        
        combined.writeVarInt(0);
        combined.writeVarInt(unbreakable);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.decode(CodecFormat.netty(combined))))
                .extracting(CodecError::message)
                .containsExactly(ComponentFilterTest.TOO_DEEP);
    }
    
    @Test
    public void refusesMoreNestedFiltersThanTheLimitInJsonAndNbt() {
        String json = "{\"not\": ".repeat(ComponentFilter.MAX_DEPTH) + "{\"has\": \"minecraft:unbreakable\"}" + "}".repeat(ComponentFilter.MAX_DEPTH);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilterTest.decode(json))).extracting(CodecError::message).containsExactly(ComponentFilterTest.TOO_DEEP);
        
        Tag nbt = ComponentFilterTest.nbtNots(ComponentFilter.MAX_DEPTH);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.decode(ComponentFilterTest.NBT, nbt)))
                .extracting(CodecError::message)
                .containsExactly(ComponentFilterTest.TOO_DEEP);
    }
    
    @Test
    public void roundTripsAFilterAtTheLimitAndRefusesToEncodeADeeperOne() {
        ComponentFilter deepest = ComponentFilterTest.nested(ComponentFilter.MAX_DEPTH);
        Assertions.assertThat(ComponentFilter.CODEC.decode(ComponentFilterTest.JSON, ComponentFilter.CODEC.encode(ComponentFilterTest.JSON, deepest))).as("JSON").isEqualTo(deepest);
        Assertions.assertThat(ComponentFilter.CODEC.decode(ComponentFilterTest.NBT, ComponentFilter.CODEC.encode(ComponentFilterTest.NBT, deepest))).as("NBT").isEqualTo(deepest);
        
        RegistryFriendlyByteBuf buffer = ComponentFilterTest.buffer();
        ComponentFilter.CODEC.encode(CodecFormat.netty(buffer), deepest);
        Assertions.assertThat(ComponentFilter.CODEC.decode(CodecFormat.netty(buffer))).as("network").isEqualTo(deepest);
        
        // Both filters of the innermost list sit one level past the limit, and a list reports every element that fails.
        ComponentFilter deeper = ComponentFilterTest.nested(ComponentFilter.MAX_DEPTH + 1);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.encode(ComponentFilterTest.JSON, deeper)))
                .extracting(CodecError::message)
                .containsExactly(ComponentFilterTest.TOO_DEEP, ComponentFilterTest.TOO_DEEP);
    }
    
    @Test
    public void writesTheIdentifierOfAPredicateTypeOnTheNetwork() {
        RegistryFriendlyByteBuf buffer = ComponentFilterTest.buffer();
        ComponentFilter.CODEC.encode(CodecFormat.netty(buffer), ComponentFilterTest.damage(5));
        Assertions.assertThat(buffer.readVarInt()).isEqualTo(3);
        Assertions.assertThat(buffer.readUtf()).isEqualTo("minecraft:damage");
    }
    
    @Test
    public void refusesAnExactValueFromTheNetworkThatItsComponentCannotSave() {
        RegistryFriendlyByteBuf damage = ComponentFilterTest.buffer();
        damage.writeVarInt(1);
        damage.writeVarInt(BuiltInRegistries.DATA_COMPONENT_TYPE.getId(DataComponents.DAMAGE));
        damage.writeVarInt(-1);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.decode(CodecFormat.netty(damage)))).extracting(CodecError::path).containsExactly("value");
        
        RegistryFriendlyByteBuf stackSize = ComponentFilterTest.buffer();
        stackSize.writeVarInt(1);
        stackSize.writeVarInt(BuiltInRegistries.DATA_COMPONENT_TYPE.getId(DataComponents.MAX_STACK_SIZE));
        stackSize.writeVarInt(1000);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.decode(CodecFormat.netty(stackSize)))).extracting(CodecError::path).containsExactly("value");
    }
    
    @Test
    public void comparesAByteOfZeroOrOneInNbtWithANumber() {
        DataComponentMap undamaged = DataComponentMap.builder().set(DataComponents.DAMAGE, 0).build();
        Assertions.assertThat(ComponentFilterTest.nbtCompare("minecraft:damage", "==", ByteTag.valueOf((byte) 0)).test(undamaged)).isTrue();
        Assertions.assertThat(ComponentFilterTest.nbtCompare("minecraft:damage", "!=", ByteTag.valueOf((byte) 1)).test(undamaged)).isTrue();
        Assertions.assertThat(ComponentFilterTest.nbtCompare("minecraft:damage", "<", ByteTag.valueOf((byte) 1)).test(undamaged)).isTrue();
        Assertions.assertThat(ComponentFilterTest.nbtCompare("minecraft:damage", ">=", ByteTag.valueOf((byte) 1)).test(undamaged)).isFalse();
    }
    
    @Test
    public void refusesAnExactOrMatchingValueFromTheNetworkThatTakesTooManyLevelsOfNbt() {
        RegistryFriendlyByteBuf exact = ComponentFilterTest.customDataOnTheNetwork(0, 500);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilterTest.decodeOnALargeStack(exact)))
                .containsExactly(new CodecError("value", ComponentFilterTest.tooManyLevels(502)));
        
        CompoundTag filter = new CompoundTag();
        filter.putString("exact", "minecraft:custom_data");
        filter.put("value", ComponentFilterTest.compound(500));
        RegistryFriendlyByteBuf matching = ComponentFilterTest.matchingOnTheNetwork("nexus:filter", filter);
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilterTest.decodeOnALargeStack(matching)))
                .containsExactly(new CodecError("value", ComponentFilterTest.tooManyLevels(503)));
    }
    
    @Test
    public void refusesACustomDataPredicateFromTheNetworkWhoseSnbtNestsTooDeeply() {
        RegistryFriendlyByteBuf buffer = ComponentFilterTest.matchingOnTheNetwork("minecraft:custom_data", ComponentFilterTest.compound(300));
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilter.CODEC.decode(CodecFormat.netty(buffer))))
                .containsExactly(new CodecError("value", ComponentFilterTest.tooManyLevels(302)));
    }
    
    @Test
    public void readsACustomDataPredicateFromTheNetworkWhoseSnbtQuotesItsBrackets() {
        CompoundTag tag = new CompoundTag();
        tag.putString("single", "{[\"'\\".repeat(300));
        tag.putString("double", "'{[\"\\".repeat(300));
        ComponentFilter filter = ComponentFilter.CODEC.decode(CodecFormat.netty(ComponentFilterTest.matchingOnTheNetwork("minecraft:custom_data", tag)));
        Assertions.assertThat(filter).isEqualTo(ComponentFilter.matching(DataComponentPredicates.CUSTOM_DATA, new CustomDataPredicate(new NbtPredicate(tag))));
    }
    
    @Test
    public void readsAFilterFromTheNetworkUpToTheBoundOnLevelsOfNbt() throws InterruptedException {
        int bound = ComponentFilter.MAX_NETWORK_NBT_DEPTH;
        Assertions.assertThat(ComponentFilterTest.decodeOnALargeStack(ComponentFilterTest.customDataOnTheNetwork(0, bound - 2)))
                .isEqualTo(ComponentFilterTest.exactCustomData(bound - 2));
        Assertions.assertThat(ComponentFilterTest.decodeOnALargeStack(ComponentFilterTest.customDataOnTheNetwork(1, bound - 4)))
                .isEqualTo(ComponentFilterTest.exactCustomData(bound - 4).negate());
        
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilterTest.decodeOnALargeStack(ComponentFilterTest.customDataOnTheNetwork(0, bound - 1))))
                .containsExactly(new CodecError("value", ComponentFilterTest.tooManyLevels(bound + 1)));
        Assertions.assertThat(ComponentFilterTest.errors(() -> ComponentFilterTest.decodeOnALargeStack(ComponentFilterTest.customDataOnTheNetwork(1, bound - 3))))
                .containsExactly(new CodecError("not.value", ComponentFilterTest.tooManyLevels(bound + 1)));
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
    
    private static ComponentFilter nbtCompare(String path, String operator, Tag value) {
        CompoundTag input = new CompoundTag();
        input.putString("path", path);
        input.putString("is", operator);
        input.put("value", value);
        return ComponentFilter.CODEC.decode(ComponentFilterTest.NBT, input);
    }
    
    private static Tag nbtNots(int count) {
        Tag filter = ComponentFilter.CODEC.encode(ComponentFilterTest.NBT, ComponentFilter.has(DataComponents.UNBREAKABLE));
        for (int level = 0; level < count; level++) {
            CompoundTag not = new CompoundTag();
            not.put("not", filter);
            filter = not;
        }
        
        return filter;
    }
    
    private static ComponentFilter nested(int depth) {
        ComponentFilter filter = ComponentFilter.has(DataComponents.DAMAGE);
        for (int level = 1; level < depth; level++) {
            List<ComponentFilter> filters = List.of(ComponentFilter.has(DataComponents.UNBREAKABLE), filter);
            filter = level % 2 == 0 ? ComponentFilter.allOf(filters) : ComponentFilter.anyOf(filters);
        }
        
        return filter;
    }
    
    private static RegistryFriendlyByteBuf customDataOnTheNetwork(int nots, int depth) {
        RegistryFriendlyByteBuf buffer = ComponentFilterTest.buffer();
        for (int level = 0; level < nots; level++) {
            buffer.writeVarInt(6);
        }
        
        buffer.writeVarInt(1);
        buffer.writeVarInt(BuiltInRegistries.DATA_COMPONENT_TYPE.getId(DataComponents.CUSTOM_DATA));
        buffer.writeNbt(ComponentFilterTest.compound(depth));
        return buffer;
    }
    
    private static RegistryFriendlyByteBuf matchingOnTheNetwork(String type, Tag predicate) {
        RegistryFriendlyByteBuf buffer = ComponentFilterTest.buffer();
        buffer.writeVarInt(3);
        buffer.writeUtf(type);
        buffer.writeNbt(predicate);
        return buffer;
    }
    
    private static ComponentFilter exactCustomData(int depth) {
        return ComponentFilter.exact(DataComponents.CUSTOM_DATA, CustomData.of(ComponentFilterTest.compound(depth)));
    }
    
    private static CompoundTag compound(int depth) {
        CompoundTag tag = new CompoundTag();
        for (int level = 1; level < depth; level++) {
            CompoundTag outer = new CompoundTag();
            outer.put("a", tag);
            tag = outer;
        }
        
        return tag;
    }
    
    private static String tooManyLevels(int levels) {
        return "Expected at most " + ComponentFilter.MAX_NETWORK_NBT_DEPTH + " levels of NBT in a filter from the network, found " + levels;
    }
    
    private static ComponentFilter decodeOnALargeStack(RegistryFriendlyByteBuf buffer) throws InterruptedException {
        FutureTask<ComponentFilter> task = new FutureTask<>(() -> ComponentFilter.CODEC.decode(CodecFormat.netty(buffer)));
        // Converting NBT between two ops takes several frames for every level, which a thread of 1 MB may not hold for hundreds of levels before the JIT compiles it.
        new Thread(null, task, "component-filter-test", 16 * 1024 * 1024).start();
        try {
            return task.get();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            
            throw new IllegalStateException(failure);
        }
    }
    
    private static void assertThreeTests(ComponentFilter filter, DataComponentGetter holder, boolean expected, int reads) {
        ComponentFilterTest.READS.clear();
        Assertions.assertThat(List.of(filter.test(holder), filter.test(holder), filter.test(holder))).containsExactly(expected, expected, expected);
        Assertions.assertThat(ComponentFilterTest.READS).hasSize(reads);
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
