package dev.satherov.nexus.test.unit.api.component;

import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.Operator;
import dev.satherov.nexus.internal.component.EncodedValues;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;

import com.google.gson.JsonPrimitive;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

///
/// Checks how component paths parse and resolve, what they compare, and the cache of encoded values.
///
public class ComponentPathTest {
    
    @Test
    public void rendersKeysIndicesAndMapKeys() {
        ComponentPath path = ComponentPath.of(DataComponents.CUSTOM_DATA, "entries[2].levels['minecraft:mending']['it\\'s']");
        Assertions.assertThat(path).hasToString("minecraft:custom_data.entries[2].levels['minecraft:mending']['it\\'s']");
    }
    
    @Test
    public void parsesAQuotedPlainKeyAsADottedKey() {
        ComponentPath quoted = ComponentPath.of(DataComponents.CUSTOM_DATA, "['entries'][0]");
        Assertions.assertThat(quoted).isEqualTo(ComponentPath.of(DataComponents.CUSTOM_DATA, "entries[0]"));
        Assertions.assertThat(quoted).hasToString("minecraft:custom_data.entries[0]");
    }
    
    @Test
    public void decodesAndEncodesTheStringForm() {
        JsonPrimitive form = new JsonPrimitive("nexus:test_tier.level");
        ComponentPath path = ComponentPath.CODEC.decode(CodecFormat.JSON, form);
        Assertions.assertThat(path).isEqualTo(TestComponents.tier().path("level"));
        Assertions.assertThat(ComponentPath.CODEC.encode(CodecFormat.JSON, path)).isEqualTo(form);
        Assertions.assertThat(ComponentPath.CODEC.decode(CodecFormat.JSON, new JsonPrimitive("nexus:test_tier"))).isEqualTo(TestComponents.tier().path());
    }
    
    @Test
    public void refusesAMalformedPath() {
        for (String path : List.of("", ".entries", "entries.", "entries..levels", "entries[", "entries[x]", "entries[-1]", "entries['open]", "entries levels", "[0]entries")) {
            Assertions.assertThatIllegalArgumentException()
                    .as(path)
                    .isThrownBy(() -> ComponentPath.of(DataComponents.CUSTOM_DATA, path));
        }
    }
    
    @Test
    public void refusesATransientComponent() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> ComponentPath.of(DataComponents.CREATIVE_SLOT_LOCK));
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> ComponentPath.of(DataComponents.CREATIVE_SLOT_LOCK, "value"));
    }
    
    @Test
    public void refusesAKeyTheStructLacks() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> TestComponents.tier().path("power"));
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> TestComponents.tier().path("[0]"));
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> TestComponents.tier().path("level.value"));
    }
    
    @Test
    public void readsAKeyCustomDataLacksAsAbsent() {
        DataComponentMap holder = ComponentPathTest.customData();
        ComponentPath power = ComponentPath.of(DataComponents.CUSTOM_DATA, "power");
        Assertions.assertThat(power.compare(Operator.EQUAL, 3).test(holder)).isFalse();
        Assertions.assertThat(power.compare(Operator.NOT_EQUAL, 3).test(holder)).isFalse();
    }
    
    @Test
    public void comparesNumbersNumerically() {
        for (ComponentPath level : List.of(TestComponents.tier().path("level"), ComponentPath.of(DataComponents.CUSTOM_DATA, "level"))) {
            DataComponentMap holder = level.type() == DataComponents.CUSTOM_DATA ? ComponentPathTest.customData() : ComponentPathTest.tier();
            Assertions.assertThat(level.compare(Operator.EQUAL, 3).test(holder)).as(level.toString()).isTrue();
            Assertions.assertThat(level.compare(Operator.EQUAL, 3.0D).test(holder)).as(level.toString()).isTrue();
            Assertions.assertThat(level.compare(Operator.GREATER, 2.5D).test(holder)).as(level.toString()).isTrue();
            Assertions.assertThat(level.compare(Operator.LESS, 3.5D).test(holder)).as(level.toString()).isTrue();
            Assertions.assertThat(level.compare(Operator.AT_LEAST, 4).test(holder)).as(level.toString()).isFalse();
            Assertions.assertThat(level.compare(Operator.NOT_EQUAL, 3L).test(holder)).as(level.toString()).isFalse();
        }
    }
    
    @Test
    public void comparesStringsLexically() {
        for (ComponentPath tag : List.of(TestComponents.tier().path("tag"), ComponentPath.of(DataComponents.CUSTOM_DATA, "tag"))) {
            DataComponentMap holder = tag.type() == DataComponents.CUSTOM_DATA ? ComponentPathTest.customData() : ComponentPathTest.tier();
            Assertions.assertThat(tag.compare(Operator.EQUAL, "gold").test(holder)).as(tag.toString()).isTrue();
            Assertions.assertThat(tag.compare(Operator.LESS, "silver").test(holder)).as(tag.toString()).isTrue();
            Assertions.assertThat(tag.compare(Operator.GREATER, "bronze").test(holder)).as(tag.toString()).isTrue();
            Assertions.assertThat(tag.compare(Operator.AT_MOST, "golden").test(holder)).as(tag.toString()).isTrue();
            Assertions.assertThat(tag.compare(Operator.GREATER, "gold").test(holder)).as(tag.toString()).isFalse();
        }
    }
    
    @Test
    public void refusesAnOrderedOperatorOnAFlag() {
        ComponentPath enabled = ComponentPath.of(DataComponents.CUSTOM_DATA, "enabled");
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> enabled.compare(Operator.LESS, true));
        Assertions.assertThat(enabled.compare(Operator.EQUAL, true).test(ComponentPathTest.customData())).isTrue();
    }
    
    @Test
    public void readsAStructWithoutEncodingIt() {
        ComponentFilter.Comparison comparison = TestComponents.tier().path("level").compare(Operator.EQUAL, 3);
        Assertions.assertThat(comparison.test(ComponentPathTest.tier())).isTrue();
        Assertions.assertThat(EncodedValues.holdsAny(TestComponents.tier().value())).isFalse();
    }
    
    @Test
    public void encodesAForeignValueOncePerObject() {
        TestComponents.Counted value = new TestComponents.Counted(5);
        ComponentFilter.Comparison comparison = ComponentPath.of(TestComponents.counted()).compare(Operator.EQUAL, 5);
        int before = TestComponents.encodes();
        Assertions.assertThat(comparison.test(ComponentPathTest.holder(TestComponents.counted(), value))).isTrue();
        Assertions.assertThat(comparison.test(ComponentPathTest.holder(TestComponents.counted(), value))).isTrue();
        Assertions.assertThat(TestComponents.encodes()).isEqualTo(before + 1);
        
        Assertions.assertThat(comparison.test(ComponentPathTest.holder(TestComponents.counted(), new TestComponents.Counted(5)))).isTrue();
        Assertions.assertThat(TestComponents.encodes()).isEqualTo(before + 2);
    }
    
    @Test
    public void comparesAForeignScalarWithoutEncodingIt() {
        DataComponentMap holder = ComponentPathTest.holder(DataComponents.DAMAGE, 3);
        Assertions.assertThat(ComponentPath.of(DataComponents.DAMAGE).compare(Operator.EQUAL, 3).test(holder)).isTrue();
        Assertions.assertThat(ComponentPath.of(DataComponents.DAMAGE).compare(Operator.GREATER, 2.5D).test(holder)).isTrue();
        Assertions.assertThat(ComponentPath.of(DataComponents.DAMAGE, "value").compare(Operator.EQUAL, 3).test(holder)).isFalse();
        Assertions.assertThat(EncodedValues.holdsAny(DataComponents.DAMAGE)).isFalse();
    }
    
    private static DataComponentMap tier() {
        return ComponentPathTest.holder(TestComponents.tier().value(), new TestComponents.Tier(3, "gold"));
    }
    
    private static DataComponentMap customData() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", 3);
        tag.putString("tag", "gold");
        tag.putBoolean("enabled", true);
        return ComponentPathTest.holder(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    
    private static <VALUE> DataComponentMap holder(DataComponentType<VALUE> type, VALUE value) {
        return DataComponentMap.builder()
                .set(type, value)
                .build();
    }
}
