package dev.satherov.nexus.test.unit.api.registry.effect;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.effect.EffectEntry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;

///
/// Checks the instances that effect entries create.
///
public class EffectEntryTest {
    
    private static @Nullable EffectEntry<SampleEffect> sample;
    
    @Test
    public void createsAnInstanceOfTheEffectWithTheDurationAndTheAmplifier() {
        EffectEntry<SampleEffect> sample = Objects.requireNonNull(EffectEntryTest.sample);
        MobEffectInstance instance = sample.instance(200, 2);
        Assertions.assertThat(instance.getEffect().value()).isSameAs(sample.value());
        Assertions.assertThat(instance.getDuration()).isEqualTo(200);
        Assertions.assertThat(instance.getAmplifier()).isEqualTo(2);
    }
    
    private static final class SampleEffect extends MobEffect {
        
        private SampleEffect() {
            super(MobEffectCategory.NEUTRAL, 0x00FF00);
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            EffectEntryTest.sample = Nexus.getMod().getRegistrations()
                    .effect("effect_entry_test_sample", "Sample Effect", SampleEffect::new)
                    .register();
        }
    }
}
