package dev.satherov.nexus.test.unit.api.registry.attribute;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.attribute.AttributeBuilder;
import dev.satherov.nexus.api.registry.attribute.AttributeEntry;
import dev.satherov.nexus.api.text.Translation;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.BooleanAttribute;
import net.neoforged.neoforge.common.PercentageAttribute;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.TooltipFlag;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;

///
/// Checks the attributes that attribute builders register, with their kinds, values, names, syncing, and sentiments.
///
public class AttributeBuilderTest {
    
    private static @Nullable AttributeBuilder<RangedAttribute, Registrations> plainBuilder;
    private static @Nullable AttributeEntry<RangedAttribute> plain;
    private static @Nullable AttributeEntry<RangedAttribute> tuned;
    private static @Nullable AttributeEntry<BooleanAttribute> flag;
    private static @Nullable AttributeEntry<PercentageAttribute> percentage;
    private static @Nullable AttributeEntry<PercentageAttribute> scaled;
    private static @Nullable AttributeEntry<Attribute> custom;
    
    @Test
    public void registersTheAttributeUnderItsName() {
        AttributeEntry<RangedAttribute> plain = AttributeBuilderTest.plain();
        Assertions.assertThat(plain.getId()).isEqualTo(Nexus.getMod().id("attribute_builder_test_plain"));
        Assertions.assertThat(plain.value()).isSameAs(BuiltInRegistries.ATTRIBUTE.getValue(plain.getId()));
    }
    
    @Test
    public void holdsTheDefaultMinimumAndMaximum() {
        RangedAttribute attribute = AttributeBuilderTest.plain().value();
        Assertions.assertThat(attribute.getDefaultValue()).isEqualTo(4.0D);
        Assertions.assertThat(attribute.getMinValue()).isEqualTo(1.0D);
        Assertions.assertThat(attribute.getMaxValue()).isEqualTo(10.0D);
        Assertions.assertThat(attribute.sanitizeValue(20.0D)).isEqualTo(10.0D);
        Assertions.assertThat(attribute.sanitizeValue(0.0D)).isEqualTo(1.0D);
    }
    
    @Test
    public void registersABooleanAttribute() {
        BooleanAttribute attribute = AttributeBuilderTest.flag().value();
        Assertions.assertThat(attribute.getDefaultValue()).isEqualTo(1.0D);
        Assertions.assertThat(attribute.sanitizeValue(5.0D)).isEqualTo(1.0D);
        Assertions.assertThat(attribute.sanitizeValue(-1.0D)).isEqualTo(0.0D);
    }
    
    @Test
    public void registersAPercentageAttributeWithItsScale() {
        PercentageAttribute percentage = AttributeBuilderTest.percentage().value();
        Assertions.assertThat(percentage.getMinValue()).isEqualTo(0.0D);
        Assertions.assertThat(percentage.getMaxValue()).isEqualTo(2.0D);
        Assertions.assertThat(AttributeBuilderTest.shownForHalf(percentage)).isEqualTo("50");
        Assertions.assertThat(AttributeBuilderTest.shownForHalf(AttributeBuilderTest.scaled().value())).isEqualTo("500");
    }
    
    @Test
    public void registersTheAttributeOfTheFactory() {
        Attribute attribute = AttributeBuilderTest.custom().value();
        Assertions.assertThat(attribute.getDefaultValue()).isEqualTo(3.0D);
        Assertions.assertThat(attribute.getDescriptionId()).isEqualTo("attribute.name.nexus.attribute_builder_test_custom");
    }
    
    @Test
    public void translatesTheNameUnderItsKey() {
        AttributeEntry<RangedAttribute> plain = AttributeBuilderTest.plain();
        Translation translation = plain.getTranslation();
        Assertions.assertThat(translation.getKey()).isEqualTo("attribute.name.nexus.attribute_builder_test_plain");
        Assertions.assertThat(translation.getEnglish()).isEqualTo("Plain Sample Attribute");
        Assertions.assertThat(plain.value().getDescriptionId()).isEqualTo(translation.getKey());
    }
    
    @Test
    public void isUnsyncedAndPositiveByDefault() {
        RangedAttribute attribute = AttributeBuilderTest.plain().value();
        Assertions.assertThat(attribute.isClientSyncable()).isFalse();
        Assertions.assertThat(attribute.getStyle(true)).isEqualTo(ChatFormatting.BLUE);
    }
    
    @Test
    public void appliesTheSyncAndTheSentiment() {
        RangedAttribute attribute = AttributeBuilderTest.tuned().value();
        Assertions.assertThat(attribute.isClientSyncable()).isTrue();
        Assertions.assertThat(attribute.getStyle(true)).isEqualTo(ChatFormatting.RED);
    }
    
    @Test
    public void refusesChangesAfterRegister() {
        AttributeBuilder<RangedAttribute, Registrations> builder = Objects.requireNonNull(AttributeBuilderTest.plainBuilder);
        Assertions.assertThatThrownBy(builder::synced).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> builder.sentiment(Attribute.Sentiment.NEGATIVE)).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> builder.on(() -> EntityType.ZOMBIE)).isInstanceOf(IllegalStateException.class);
        Assertions.assertThat(AttributeBuilderTest.plain().value().isClientSyncable()).isFalse();
    }
    
    private static String shownForHalf(PercentageAttribute attribute) {
        TranslatableContents contents = (TranslatableContents) attribute.toValueComponent(AttributeModifier.Operation.ADD_VALUE, 0.5D, TooltipFlag.NORMAL).getContents();
        return (String) contents.getArgs()[0];
    }
    
    private static AttributeEntry<RangedAttribute> plain() {
        return Objects.requireNonNull(AttributeBuilderTest.plain);
    }
    
    private static AttributeEntry<RangedAttribute> tuned() {
        return Objects.requireNonNull(AttributeBuilderTest.tuned);
    }
    
    private static AttributeEntry<BooleanAttribute> flag() {
        return Objects.requireNonNull(AttributeBuilderTest.flag);
    }
    
    private static AttributeEntry<PercentageAttribute> percentage() {
        return Objects.requireNonNull(AttributeBuilderTest.percentage);
    }
    
    private static AttributeEntry<PercentageAttribute> scaled() {
        return Objects.requireNonNull(AttributeBuilderTest.scaled);
    }
    
    private static AttributeEntry<Attribute> custom() {
        return Objects.requireNonNull(AttributeBuilderTest.custom);
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            AttributeBuilder<RangedAttribute, Registrations> builder = registrations.attribute("attribute_builder_test_plain", "Plain Sample Attribute", 4.0D, 1.0D, 10.0D);
            AttributeBuilderTest.plainBuilder = builder;
            AttributeBuilderTest.plain = builder.register();
            AttributeBuilderTest.tuned = registrations.attribute("attribute_builder_test_tuned", "Tuned Sample Attribute", 0.0D, -1.0D, 1.0D)
                    .synced()
                    .sentiment(Attribute.Sentiment.NEGATIVE)
                    .register();
            AttributeBuilderTest.flag = registrations.booleanAttribute("attribute_builder_test_flag", "Flag Sample Attribute", true).register();
            AttributeBuilderTest.percentage = registrations.percentageAttribute("attribute_builder_test_percentage", "Percentage Sample Attribute", 1.0D, 0.0D, 2.0D).register();
            AttributeBuilderTest.scaled = registrations.percentageAttribute("attribute_builder_test_scaled", "Scaled Sample Attribute", 1.0D, 0.0D, 2.0D, 1000.0D).register();
            AttributeBuilderTest.custom = registrations.<Attribute>attribute("attribute_builder_test_custom", "Custom Sample Attribute", id -> new Attribute(id, 3.0D) { }).register();
        }
    }
}
