package dev.satherov.nexus.test.unit.api.registry.sound;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.sound.SoundBuilder;
import dev.satherov.nexus.api.registry.sound.SoundEntry;
import dev.satherov.nexus.api.text.Translation;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;

///
/// Checks the sound events that sound builders register, with their subtitles and ranges.
///
public class SoundBuilderTest {
    
    private static @Nullable SoundBuilder<Registrations> plainBuilder;
    private static @Nullable SoundEntry plain;
    private static @Nullable SoundEntry ranged;
    
    @Test
    public void registersTheSoundEventUnderItsName() {
        SoundEntry plain = SoundBuilderTest.plain();
        Assertions.assertThat(plain.getId()).isEqualTo(Nexus.getMod().id("sound_builder_test_plain"));
        Assertions.assertThat(plain.value()).isSameAs(BuiltInRegistries.SOUND_EVENT.getValue(plain.getId()));
        Assertions.assertThat(plain.value().location()).isEqualTo(plain.getId());
    }
    
    @Test
    public void translatesTheSubtitleUnderItsKey() {
        Translation translation = SoundBuilderTest.plain().getTranslation();
        Assertions.assertThat(translation.getKey()).isEqualTo("subtitles.nexus.sound_builder_test_plain");
        Assertions.assertThat(translation.getEnglish()).isEqualTo("Plain Sample Sound");
    }
    
    @Test
    public void growsTheDefaultRangeWithTheVolume() {
        SoundEvent sound = SoundBuilderTest.plain().value();
        Assertions.assertThat(sound.fixedRange()).isEmpty();
        Assertions.assertThat(sound.getRange(1.0F)).isEqualTo(16.0F);
        Assertions.assertThat(sound.getRange(2.0F)).isEqualTo(32.0F);
    }
    
    @Test
    public void keepsAFixedRangeAtEveryVolume() {
        SoundEvent sound = SoundBuilderTest.ranged().value();
        Assertions.assertThat(sound.getRange(1.0F)).isEqualTo(4.0F);
        Assertions.assertThat(sound.getRange(2.0F)).isEqualTo(4.0F);
    }
    
    @Test
    public void refusesARangeAfterRegister() {
        SoundBuilder<Registrations> builder = Objects.requireNonNull(SoundBuilderTest.plainBuilder);
        Assertions.assertThatThrownBy(() -> builder.range(8.0F)).isInstanceOf(IllegalStateException.class);
        Assertions.assertThat(SoundBuilderTest.plain().value().fixedRange()).isEmpty();
    }
    
    private static SoundEntry plain() {
        return Objects.requireNonNull(SoundBuilderTest.plain);
    }
    
    private static SoundEntry ranged() {
        return Objects.requireNonNull(SoundBuilderTest.ranged);
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            SoundBuilder<Registrations> builder = registrations.sound("sound_builder_test_plain", "Plain Sample Sound");
            SoundBuilderTest.plainBuilder = builder;
            SoundBuilderTest.plain = builder.register();
            SoundBuilderTest.ranged = registrations.sound("sound_builder_test_ranged", "Ranged Sample Sound").range(4.0F).register();
        }
    }
}
