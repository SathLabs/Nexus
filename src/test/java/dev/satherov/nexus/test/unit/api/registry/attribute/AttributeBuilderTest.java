package dev.satherov.nexus.test.unit.api.registry.attribute;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.attribute.AttributeBuilder;
import dev.satherov.nexus.api.registry.attribute.AttributeEntry;
import dev.satherov.nexus.api.text.Translation;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;

///
/// Checks the attributes that attribute builders register, with their values, names, syncing, and sentiments.
///
public class AttributeBuilderTest {
    
    private static @Nullable AttributeBuilder<Registrations> plainBuilder;
    private static @Nullable AttributeEntry plain;
    private static @Nullable AttributeEntry tuned;
    
    @Test
    public void registersTheAttributeUnderItsName() {
        AttributeEntry plain = AttributeBuilderTest.plain();
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
    public void translatesTheNameUnderItsKey() {
        AttributeEntry plain = AttributeBuilderTest.plain();
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
        AttributeBuilder<Registrations> builder = Objects.requireNonNull(AttributeBuilderTest.plainBuilder);
        Assertions.assertThatThrownBy(builder::synced).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> builder.sentiment(Attribute.Sentiment.NEGATIVE)).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> builder.on(() -> EntityType.ZOMBIE)).isInstanceOf(IllegalStateException.class);
        Assertions.assertThat(AttributeBuilderTest.plain().value().isClientSyncable()).isFalse();
    }
    
    private static AttributeEntry plain() {
        return Objects.requireNonNull(AttributeBuilderTest.plain);
    }
    
    private static AttributeEntry tuned() {
        return Objects.requireNonNull(AttributeBuilderTest.tuned);
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            AttributeBuilder<Registrations> builder = registrations.attribute("attribute_builder_test_plain", "Plain Sample Attribute", 4.0D, 1.0D, 10.0D);
            AttributeBuilderTest.plainBuilder = builder;
            AttributeBuilderTest.plain = builder.register();
            AttributeBuilderTest.tuned = registrations.attribute("attribute_builder_test_tuned", "Tuned Sample Attribute", 0.0D, -1.0D, 1.0D)
                    .synced()
                    .sentiment(Attribute.Sentiment.NEGATIVE)
                    .register();
        }
    }
}
