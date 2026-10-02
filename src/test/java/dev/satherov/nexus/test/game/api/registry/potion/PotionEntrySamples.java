package dev.satherov.nexus.test.game.api.registry.potion;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.potion.PotionEntry;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

///
/// Checks the stacks that potion entries create in each container, the names of the stacks, the stacks the entries match, and the brewing recipes of the potions.
///
public class PotionEntrySamples {
    
    private static @Nullable PotionEntry sample;
    
    @ServerTest
    public static void stacksHoldThePotionInEachContainer(GameTestHelper helper) {
        PotionEntry sample = PotionEntrySamples.sample();
        for (Item container : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW)) {
            ItemStack stack = sample.stack(container);
            helper.assertValueEqual(stack.getItem(), container, "item of the stack");
            helper.assertValueEqual(stack.getCount(), 1, "count of the stack");
            helper.assertTrue(sample.is(stack), "The potion did not match its own stack of '" + container + "'");
        }
        
        helper.succeed();
    }
    
    @ServerTest
    public static void namesEachStackUnderADefinedKey(GameTestHelper helper) {
        PotionEntry sample = PotionEntrySamples.sample();
        Map<String, String> englishByKey = Nexus.getMod().getTranslations().getAll().stream()
                .collect(Collectors.toMap(Translation::getKey, Translation::getEnglish));
        
        helper.assertValueEqual(englishByKey.get(PotionEntrySamples.nameKey(sample.stack(Items.POTION))), "Potion of Sampling", "English name of the potion");
        helper.assertValueEqual(englishByKey.get(PotionEntrySamples.nameKey(sample.stack(Items.SPLASH_POTION))), "Splash Potion of Sampling", "English name of the splash potion");
        helper.assertValueEqual(englishByKey.get(PotionEntrySamples.nameKey(sample.stack(Items.LINGERING_POTION))), "Lingering Potion of Sampling", "English name of the lingering potion");
        helper.assertValueEqual(englishByKey.get(PotionEntrySamples.nameKey(sample.stack(Items.TIPPED_ARROW))), "Arrow of Sampling", "English name of the arrow");
        helper.succeed();
    }
    
    @ServerTest
    public static void isMatchesStacksOfThePotionOnly(GameTestHelper helper) {
        PotionEntry sample = PotionEntrySamples.sample();
        ItemStack withCustomEffect = new ItemStack(Items.POTION);
        withCustomEffect.set(DataComponents.POTION_CONTENTS, new PotionContents(sample).withEffectAdded(new MobEffectInstance(MobEffects.SPEED, 100)));
        helper.assertFalse(sample.is(PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS)), "The potion matched a stack of swiftness");
        helper.assertFalse(sample.is(new ItemStack(Items.GLASS_BOTTLE)), "The potion matched a stack without potion contents");
        helper.assertFalse(sample.is(withCustomEffect), "The potion matched a stack with a custom effect");
        helper.succeed();
    }
    
    @ServerTest
    public static void brewsThePotionFromTheBaseWithTheIngredient(GameTestHelper helper) {
        PotionEntry sample = PotionEntrySamples.sample();
        PotionBrewing brewing = helper.getLevel().potionBrewing();
        ItemStack base = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
        ItemStack ingredient = new ItemStack(Items.DIAMOND);
        helper.assertTrue(brewing.hasMix(base, ingredient), "The base potion has no mix with the ingredient");
        helper.assertTrue(sample.is(brewing.mix(ingredient, base)), "The mix of the base potion and the ingredient isn't the potion");
        helper.succeed();
    }
    
    private static PotionEntry sample() {
        return Objects.requireNonNull(PotionEntrySamples.sample);
    }
    
    private static String nameKey(ItemStack stack) {
        return ((TranslatableContents) stack.getItemName().getContents()).getKey();
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            PotionEntrySamples.sample = Nexus.getMod().getRegistrations()
                    .potion("potion_entry_samples_sample", "Sampling", () -> new MobEffectInstance(MobEffects.SPEED, 3600))
                    .brewing(Potions.AWKWARD, Items.DIAMOND)
                    .register();
        }
    }
}
