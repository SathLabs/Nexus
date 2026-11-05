package dev.satherov.nexus.test.unit.api.component;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.Operator;

import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.predicates.DamagePredicate;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

///
/// Checks each case of a component filter, how all-ofs and any-ofs flatten, and the codec round trip with its errors.
///
public class ComponentFilterTest {
    
    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final CodecFormat<JsonElement, Access.Registries> JSON = CodecFormat.JSON.withRegistries(ComponentFilterTest.REGISTRIES);
    private static final CodecFormat<Tag, Access.Registries> NBT = CodecFormat.NBT.withRegistries(ComponentFilterTest.REGISTRIES);
    private static final DamagePredicate DAMAGED = new DamagePredicate(MinMaxBounds.Ints.ANY, MinMaxBounds.Ints.atLeast(2));
    
    @Test
    public void hasPassesAHolderWithTheComponent() {
        ComponentFilter filter = ComponentFilter.has(DataComponents.DAMAGE);
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(DataComponents.DAMAGE, 0))).isTrue();
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(DataComponents.MAX_DAMAGE, 0))).isFalse();
    }
    
    @Test
    public void exactPassesAHolderWithAnEqualValue() {
        ComponentFilter filter = ComponentFilter.exact(DataComponents.DAMAGE, 3);
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(DataComponents.DAMAGE, 3))).isTrue();
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(DataComponents.DAMAGE, 4))).isFalse();
    }
    
    @Test
    public void comparisonPassesAHolderWhoseValueCompares() {
        DataComponentType<TestComponents.Tier> tier = TestComponents.tier().get();
        ComponentFilter level = ComponentPath.of(tier, "level").compare(Operator.AT_LEAST, 3);
        ComponentFilter tag = ComponentPath.of(tier, "tag").compare(Operator.EQUAL, "gold");
        Assertions.assertThat(level.test(ComponentFilterTest.holder(tier, new TestComponents.Tier(3, "iron")))).isTrue();
        Assertions.assertThat(level.test(ComponentFilterTest.holder(tier, new TestComponents.Tier(2, "iron")))).isFalse();
        Assertions.assertThat(tag.test(ComponentFilterTest.holder(tier, new TestComponents.Tier(1, "gold")))).isTrue();
        Assertions.assertThat(tag.test(ComponentFilterTest.holder(tier, new TestComponents.Tier(1, "iron")))).isFalse();
    }
    
    @Test
    public void matchingPassesAHolderThatPassesThePredicate() {
        ComponentFilter filter = ComponentFilter.matching(DataComponentPredicates.DAMAGE, ComponentFilterTest.DAMAGED);
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(DataComponents.DAMAGE, 2))).isTrue();
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(DataComponents.DAMAGE, 1))).isFalse();
    }
    
    @Test
    public void allOfPassesAHolderThatPassesEveryFilter() {
        ComponentFilter filter = ComponentFilter.allOf(ComponentFilter.has(DataComponents.DAMAGE), ComponentFilter.has(DataComponents.MAX_DAMAGE));
        DataComponentMap both = DataComponentMap.builder()
                .set(DataComponents.DAMAGE, 0)
                .set(DataComponents.MAX_DAMAGE, 10)
                .build();
        
        Assertions.assertThat(filter.test(both)).isTrue();
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(DataComponents.DAMAGE, 0))).isFalse();
        Assertions.assertThat(ComponentFilter.ANY.test(DataComponentMap.EMPTY)).isTrue();
        Assertions.assertThat(ComponentFilter.allOf()).isEqualTo(ComponentFilter.ANY);
    }
    
    @Test
    public void anyOfPassesAHolderThatPassesOneFilter() {
        ComponentFilter filter = ComponentFilter.anyOf(ComponentFilter.exact(DataComponents.DAMAGE, 3), ComponentFilter.exact(DataComponents.DAMAGE, 4));
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(DataComponents.DAMAGE, 4))).isTrue();
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(DataComponents.DAMAGE, 5))).isFalse();
        Assertions.assertThat(ComponentFilter.anyOf().test(ComponentFilterTest.holder(DataComponents.DAMAGE, 4))).isFalse();
    }
    
    @Test
    public void notPassesAHolderThatFailsTheFilter() {
        ComponentFilter filter = ComponentFilter.has(DataComponents.DAMAGE).negate();
        Assertions.assertThat(filter.test(DataComponentMap.EMPTY)).isTrue();
        Assertions.assertThat(filter.test(ComponentFilterTest.holder(DataComponents.DAMAGE, 0))).isFalse();
    }
    
    @Test
    public void aMissingComponentFailsHasExactAndComparison() {
        DataComponentType<TestComponents.Tier> tier = TestComponents.tier().get();
        DataComponentMap other = ComponentFilterTest.holder(DataComponents.MAX_DAMAGE, 0);
        Assertions.assertThat(ComponentFilter.has(tier).test(other)).isFalse();
        Assertions.assertThat(ComponentFilter.exact(tier, new TestComponents.Tier(0, "")).test(other)).isFalse();
        Assertions.assertThat(ComponentPath.of(tier, "level").compare(Operator.AT_MOST, 0).test(other)).isFalse();
        Assertions.assertThat(ComponentPath.of(tier, "level").compare(Operator.NOT_EQUAL, 0).test(other)).isFalse();
    }
    
    @Test
    public void negatingANotGivesItsFilter() {
        ComponentFilter has = ComponentFilter.has(DataComponents.DAMAGE);
        Assertions.assertThat(new ComponentFilter.Not(has).negate()).isSameAs(has);
        Assertions.assertThat(has.negate().negate()).isSameAs(has);
    }
    
    @Test
    public void andOfTwoAllOfsIsOneAllOf() {
        ComponentFilter damage = ComponentFilter.has(DataComponents.DAMAGE);
        ComponentFilter maxDamage = ComponentFilter.has(DataComponents.MAX_DAMAGE);
        ComponentFilter exact = ComponentFilter.exact(DataComponents.DAMAGE, 3);
        ComponentFilter any = ComponentFilter.anyOf(damage, maxDamage);
        Assertions.assertThat(ComponentFilter.allOf(damage, maxDamage).and(ComponentFilter.allOf(exact, any)).filters()).containsExactly(damage, maxDamage, exact, any);
    }
    
    @Test
    public void roundTripsATreeWithEveryCase() {
        DataComponentType<TestComponents.Tier> tier = TestComponents.tier().get();
        ComponentPath level = ComponentPath.of(tier, "level");
        ComponentFilter tree = ComponentFilter.anyOf(
                ComponentFilter.has(DataComponents.DAMAGE),
                ComponentFilter.exact(DataComponents.DAMAGE, 3),
                ComponentFilter.exact(tier, new TestComponents.Tier(2, "iron")),
                ComponentFilter.allOf(
                        level.compare(Operator.AT_LEAST, 3),
                        level.compare(Operator.GREATER, 2.5),
                        ComponentPath.of(tier, "tag").compare(Operator.LESS, "gold"),
                        level.compare(Operator.NOT_EQUAL, true)
                ),
                ComponentFilter.matching(DataComponentPredicates.DAMAGE, ComponentFilterTest.DAMAGED),
                ComponentFilter.has(DataComponents.MAX_DAMAGE).negate()
        );
        
        JsonElement json = JsonParser.parseString("""
                {"any": [
                    {"has": "minecraft:damage"},
                    {"exact": "minecraft:damage", "value": 3},
                    {"exact": "nexus:test_tier", "value": {"level": 2, "tag": "iron"}},
                    {"all": [
                        {"path": "nexus:test_tier.level", "is": ">=", "value": 3},
                        {"path": "nexus:test_tier.level", "is": ">", "value": 2.5},
                        {"path": "nexus:test_tier.tag", "is": "<", "value": "gold"},
                        {"path": "nexus:test_tier.level", "is": "!=", "value": true}
                    ]},
                    {"matches": "minecraft:damage", "value": {"damage": {"min": 2}}},
                    {"not": {"has": "minecraft:max_damage"}}
                ]}
                """);
        Assertions.assertThat(ComponentFilter.CODEC.encode(ComponentFilterTest.JSON, tree)).hasToString(json.toString());
        Assertions.assertThat(ComponentFilter.CODEC.decode(ComponentFilterTest.JSON, json)).isEqualTo(tree);
        Assertions.assertThat(ComponentFilter.CODEC.decode(ComponentFilterTest.NBT, ComponentFilter.CODEC.encode(ComponentFilterTest.NBT, tree))).isEqualTo(tree);
        
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), ComponentFilterTest.REGISTRIES);
        ComponentFilter.CODEC.encode(CodecFormat.netty(buffer), tree);
        Assertions.assertThat(ComponentFilter.CODEC.decode(CodecFormat.netty(buffer))).isEqualTo(tree);
        Assertions.assertThat(buffer.readableBytes()).isZero();
    }
    
    @Test
    public void decodingFailsAtTheKeyOfAnUnknownComponent() {
        Assertions.assertThat(ComponentFilterTest.errors("{\"has\": \"nexus:unknown\"}")).extracting(CodecError::path).containsExactly("has");
        Assertions.assertThat(ComponentFilterTest.errors("{\"exact\": \"nexus:unknown\", \"value\": 3}")).extracting(CodecError::path).containsExactly("exact");
    }
    
    @Test
    public void decodingFailsAtTheKeyOfAnUnknownOperator() {
        Assertions.assertThat(ComponentFilterTest.errors("{\"path\": \"nexus:test_tier.level\", \"is\": \"=>\", \"value\": 3}")).extracting(CodecError::path).containsExactly("is");
    }
    
    @Test
    public void decodingFailsAtTheKeyOfALiteralOfAnUnknownKind() {
        Assertions.assertThat(ComponentFilterTest.errors("{\"path\": \"nexus:test_tier.level\", \"is\": \"==\", \"value\": [3]}")).extracting(CodecError::path).containsExactly("value");
        Assertions.assertThat(ComponentFilterTest.errors("{\"path\": \"nexus:test_tier.level\", \"is\": \"==\", \"value\": {}}")).extracting(CodecError::path).containsExactly("value");
    }
    
    private static <VALUE> DataComponentMap holder(DataComponentType<VALUE> type, VALUE value) {
        return DataComponentMap.builder()
                .set(type, value)
                .build();
    }
    
    private static List<CodecError> errors(String json) {
        return Assertions.catchThrowableOfType(NexusCodecException.class, () -> ComponentFilter.CODEC.decode(ComponentFilterTest.JSON, JsonParser.parseString(json))).errors();
    }
}
