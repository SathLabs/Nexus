package dev.satherov.nexus.test.game.api.registry.effect;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.effect.EffectEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// Checks the effects that effect entries add to mobs and the mobs they find the effects on.
///
public class EffectEntrySamples {
    
    private static @Nullable EffectEntry<SampleEffect> plain;
    private static @Nullable EffectEntry<SampleEffect> refused;
    
    @ServerTest
    public static void appliesTheEffectToAMob(GameTestHelper helper) {
        EffectEntry<SampleEffect> plain = EffectEntrySamples.plain();
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 1));
        helper.assertFalse(plain.has(pig), "The pig had the effect before it was applied");
        helper.assertTrue(plain.apply(pig, 200, 1), "The pig refused the effect");
        helper.assertTrue(plain.has(pig), "The pig doesn't have the applied effect");
        
        MobEffectInstance active = Objects.requireNonNull(pig.getEffect(plain));
        helper.assertValueEqual(active.getDuration(), 200, "duration of the effect");
        helper.assertValueEqual(active.getAmplifier(), 1, "amplifier of the effect");
        helper.succeed();
    }
    
    @ServerTest
    public static void applyIsFalseIfTheMobRefusesTheEffect(GameTestHelper helper) {
        EffectEntry<SampleEffect> refused = EffectEntrySamples.refused();
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 1));
        helper.assertFalse(refused.apply(pig, 200, 0), "The pig took an effect it refused");
        helper.assertFalse(refused.has(pig), "The pig has an effect it refused");
        helper.succeed();
    }
    
    private static EffectEntry<SampleEffect> plain() {
        return Objects.requireNonNull(EffectEntrySamples.plain);
    }
    
    private static EffectEntry<SampleEffect> refused() {
        return Objects.requireNonNull(EffectEntrySamples.refused);
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
            Registrations registrations = Nexus.getMod().getRegistrations();
            EffectEntrySamples.plain = registrations.effect("effect_entry_samples_plain", "Plain Sample Effect", SampleEffect::new).register();
            EffectEntrySamples.refused = registrations.effect("effect_entry_samples_refused", "Refused Sample Effect", SampleEffect::new).register();
        }
        
        @SubscribeEvent
        public static void onApplicable(MobEffectEvent.Applicable event) {
            if (event.getEffectInstance().getEffect().is(EffectEntrySamples.refused().getKey())) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        }
    }
}
