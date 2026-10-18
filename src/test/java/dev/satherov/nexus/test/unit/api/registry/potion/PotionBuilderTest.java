package dev.satherov.nexus.test.unit.api.registry.potion;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.text.Translation;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

///
/// Checks the names that potion builders define for each form of their potion.
///
public class PotionBuilderTest {
    
    @Test
    public void definesTheFourNamesWithTheirEnglish() {
        Map<String, String> englishByKey = Nexus.getMod().getTranslations().getAll().stream()
                .collect(Collectors.toMap(Translation::getKey, Translation::getEnglish));
        
        Assertions.assertThat(englishByKey)
                .containsEntry("item.minecraft.potion.effect.nexus.potion_builder_test_sample", "Potion of Sampling")
                .containsEntry("item.minecraft.splash_potion.effect.nexus.potion_builder_test_sample", "Splash Potion of Sampling")
                .containsEntry("item.minecraft.lingering_potion.effect.nexus.potion_builder_test_sample", "Lingering Potion of Sampling")
                .containsEntry("item.minecraft.tipped_arrow.effect.nexus.potion_builder_test_sample", "Arrow of Sampling");
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Nexus.getMod().getRegistrations()
                    .potion("potion_builder_test_sample", "Sampling", () -> new MobEffectInstance(MobEffects.SPEED, 3600))
                    .register();
        }
    }
}
