package dev.satherov.nexus.test.unit.api.component;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.component.DataComponentEntry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.component.DataComponents;

import com.google.gson.JsonPrimitive;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

///
/// Checks how component paths are parsed, rendered, compared, and encoded.
///
public class ComponentPathTest {
    
    private static final StructCodec<Tier, Access.Plain> TIER_CODEC = NexusCodec.struct(
            "tier",
            NexusCodec.INT.field("level", Tier::level),
            NexusCodec.STRING.field("name", Tier::name),
            Tier::new
    );
    
    private static @Nullable DataComponentEntry<Tier> tier;
    private static @Nullable DataComponentEntry<Integer> dotted;
    
    @Test
    public void parsesPlainKeysIndicesAndTheWildcard() {
        ComponentPath path = ComponentPath.of(DataComponents.CUSTOM_DATA, "a.b_c-D[0][*].e[123456789]");
        Assertions.assertThat(path.getAllSteps()).containsExactly(
                new ComponentPath.Step.Key("a"),
                new ComponentPath.Step.Key("b_c-D"),
                new ComponentPath.Step.Index(0),
                new ComponentPath.Step.Wildcard(),
                new ComponentPath.Step.Key("e"),
                new ComponentPath.Step.Index(123_456_789)
        );
    }
    
    @Test
    public void parsesStepsThatStartWithABracket() {
        ComponentPath path = ComponentPath.of(DataComponents.CUSTOM_DATA, "[2].a");
        Assertions.assertThat(path.getAllSteps()).containsExactly(new ComponentPath.Step.Index(2), new ComponentPath.Step.Key("a"));
    }
    
    @Test
    public void parsesQuotedKeysWithEscapes() {
        ComponentPath path = ComponentPath.of(DataComponents.CUSTOM_DATA, "['minecraft:sharpness']['it\\'s']['back\\\\slash']['']['a.b[0]']");
        Assertions.assertThat(path.getAllSteps()).containsExactly(
                new ComponentPath.Step.Key("minecraft:sharpness"),
                new ComponentPath.Step.Key("it's"),
                new ComponentPath.Step.Key("back\\slash"),
                new ComponentPath.Step.Key(""),
                new ComponentPath.Step.Key("a.b[0]")
        );
    }
    
    @Test
    public void readsTheWholeValueWithEmptySteps() {
        ComponentPath path = ComponentPath.of(DataComponents.CUSTOM_DATA, "");
        Assertions.assertThat(path.getAllSteps()).isEmpty();
        Assertions.assertThat(path.getComponent()).isSameAs(DataComponents.CUSTOM_DATA);
        Assertions.assertThat(path).hasToString("minecraft:custom_data");
    }
    
    @ParameterizedTest
    @MethodSource("malformed")
    public void refusesMalformedStepsAtTheIndexWhereParsingStopped(String steps, int index) {
        Assertions.assertThatThrownBy(() -> ComponentPath.of(DataComponents.CUSTOM_DATA, steps))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("index '" + index + "'");
    }
    
    private static Stream<Arguments> malformed() {
        return Stream.of(
                Arguments.of(".a", 0),
                Arguments.of("a..b", 1),
                Arguments.of("a.", 1),
                Arguments.of("a b", 1),
                Arguments.of("a.[0]", 1),
                Arguments.of("a[0]b", 4),
                Arguments.of("[0", 0),
                Arguments.of("[]", 0),
                Arguments.of("[-1]", 0),
                Arguments.of("[1234567890]", 0),
                Arguments.of("a['b]", 1),
                Arguments.of("['a\\b']", 0),
                Arguments.of("['a''b']", 0),
                Arguments.of("[*", 0),
                Arguments.of("a.b#", 3)
        );
    }
    
    @Test
    public void rendersTheIdentifierFollowedByTheSteps() {
        Assertions.assertThat(ComponentPath.of(DataComponents.CUSTOM_DATA, "a[0].b[*]")).hasToString("minecraft:custom_data.a[0].b[*]");
        Assertions.assertThat(ComponentPath.of(DataComponents.CUSTOM_DATA, "[1].a")).hasToString("minecraft:custom_data[1].a");
    }
    
    @Test
    public void rendersEveryKeyThatIsNotPlainQuoted() {
        ComponentPath path = ComponentPath.of(DataComponents.CUSTOM_DATA, "['minecraft:sharpness'].level['it\\'s']['back\\\\slash']['']");
        Assertions.assertThat(path).hasToString("minecraft:custom_data['minecraft:sharpness'].level['it\\'s']['back\\\\slash']['']");
    }
    
    @Test
    public void rendersAQuotedPlainKeyAsADottedKey() {
        ComponentPath quoted = ComponentPath.of(DataComponents.CUSTOM_DATA, "['a']['b-1'][0]");
        ComponentPath dotted = ComponentPath.of(DataComponents.CUSTOM_DATA, "a.b-1[0]");
        Assertions.assertThat(quoted).isEqualTo(dotted);
        Assertions.assertThat(quoted).hasSameHashCodeAs(dotted);
        Assertions.assertThat(quoted).hasToString("minecraft:custom_data.a.b-1[0]");
    }
    
    @Test
    public void equalsAPathOnlyWithTheSameComponentAndSteps() {
        ComponentPath path = ComponentPath.of(DataComponents.CUSTOM_DATA, "a[0]");
        Assertions.assertThat(path).isEqualTo(ComponentPath.of(DataComponents.CUSTOM_DATA, "a[00]"));
        Assertions.assertThat(path).isNotEqualTo(ComponentPath.of(DataComponents.CUSTOM_DATA, "a[1]"));
        Assertions.assertThat(path).isNotEqualTo(ComponentPath.of(DataComponents.CUSTOM_DATA, "a"));
        Assertions.assertThat(ComponentPath.of(DataComponents.CUSTOM_DATA, "")).isNotEqualTo(ComponentPath.of(DataComponents.BLOCK_ENTITY_DATA, ""));
    }
    
    @Test
    public void refusesChangesToTheSteps() {
        Assertions.assertThatThrownBy(() -> ComponentPath.of(DataComponents.CUSTOM_DATA, "a").getAllSteps().add(new ComponentPath.Step.Key("b")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
    
    @Test
    public void refusesANegativeIndex() {
        Assertions.assertThatThrownBy(() -> new ComponentPath.Step.Index(-1)).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void refusesATransientComponent() {
        Assertions.assertThatThrownBy(() -> ComponentPath.of(DataComponents.CREATIVE_SLOT_LOCK, "")).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void refusesStepsThatNoValueOfTheComponentCanHold() {
        Assertions.assertThatThrownBy(() -> ComponentPath.of(ComponentPathTest.dotted().value(), "level")).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void createsThePathOfAnEntry() {
        Assertions.assertThat(ComponentPathTest.tier().path("level")).isEqualTo(ComponentPath.of(ComponentPathTest.tier().value(), "level"));
        Assertions.assertThat(ComponentPathTest.tier().path("level")).hasToString("nexus:component_path_test_tier.level");
    }
    
    @ParameterizedTest
    @MethodSource("texts")
    public void encodesTheTextFormAndDecodesItBack(String text) {
        ComponentPath path = ComponentPath.CODEC.decode(CodecFormat.JSON, new JsonPrimitive(text));
        Assertions.assertThat(path).hasToString(text);
        Assertions.assertThat(ComponentPath.CODEC.encode(CodecFormat.JSON, path)).isEqualTo(new JsonPrimitive(text));
    }
    
    private static Stream<String> texts() {
        return Stream.of(
                "minecraft:custom_data",
                "minecraft:custom_data.a.b",
                "minecraft:custom_data['minecraft:sharpness']",
                "minecraft:custom_data[0].item.id",
                "minecraft:custom_data[*].item.id",
                "nexus:component_path_test_tier.level",
                "nexus:component_path_test_tier"
        );
    }
    
    @Test
    public void decodesTheLongestRegisteredIdentifier() {
        ComponentPath whole = ComponentPath.CODEC.decode(CodecFormat.JSON, new JsonPrimitive("nexus:component_path_test_tier.level"));
        Assertions.assertThat(whole.getComponent()).isSameAs(ComponentPathTest.dotted().value());
        Assertions.assertThat(whole.getAllSteps()).isEmpty();
        
        ComponentPath name = ComponentPath.CODEC.decode(CodecFormat.JSON, new JsonPrimitive("nexus:component_path_test_tier.name"));
        Assertions.assertThat(name.getComponent()).isSameAs(ComponentPathTest.tier().value());
        Assertions.assertThat(name.getAllSteps()).containsExactly(new ComponentPath.Step.Key("name"));
    }
    
    @Test
    public void decodesTheSameIdentifierWithAnyKindOfStep() {
        ComponentPath quoted = ComponentPath.CODEC.decode(CodecFormat.JSON, new JsonPrimitive("nexus:component_path_test_tier['level']"));
        Assertions.assertThat(quoted.getComponent()).isSameAs(ComponentPathTest.tier().value());
        Assertions.assertThat(quoted).isEqualTo(ComponentPathTest.tier().path("level"));
    }
    
    @ParameterizedTest
    @MethodSource("undecodable")
    public void failsToDecodeATextWithoutARegisteredComponentOrWithMalformedSteps(String text) {
        Assertions.assertThatThrownBy(() -> ComponentPath.CODEC.decode(CodecFormat.JSON, new JsonPrimitive(text))).isInstanceOf(NexusCodecException.class);
    }
    
    private static Stream<String> undecodable() {
        return Stream.of(
                "",
                ".a",
                "nexus:missing",
                "nexus:missing.a",
                "minecraft:custom_data.",
                "minecraft:custom_data.[0]",
                "minecraft:custom_data a",
                "minecraft:custom_data[0"
        );
    }
    
    private static DataComponentEntry<Tier> tier() {
        return Objects.requireNonNull(ComponentPathTest.tier);
    }
    
    private static DataComponentEntry<Integer> dotted() {
        return Objects.requireNonNull(ComponentPathTest.dotted);
    }
    
    private record Tier(int level, String name) { }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            ComponentPathTest.tier = registrations.component("component_path_test_tier", ComponentPathTest.TIER_CODEC).register();
            ComponentPathTest.dotted = registrations.component("component_path_test_tier.level", NexusCodec.INT).register();
        }
    }
}
