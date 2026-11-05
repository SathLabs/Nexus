package dev.satherov.nexus.test.game.api.component;

import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.Operator;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluids;

///
/// Checks a component filter, its matchers, and its ingredients on real item and fluid stacks and transfer resources.
///
public class ComponentFilterSamples {

    private static final ComponentFilter EPIC = ComponentFilter.exact(DataComponents.RARITY, Rarity.EPIC);

    @ServerTest
    public static void testsStacksAndResources(GameTestHelper helper) {
        ItemStack epicStick = ComponentFilterSamples.epic(new ItemStack(Items.STICK));
        FluidStack epicWater = ComponentFilterSamples.epic(new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        helper.assertTrue(ComponentFilterSamples.EPIC.test(epicStick), "The filter fails an epic item stack");
        helper.assertFalse(ComponentFilterSamples.EPIC.test(new ItemStack(Items.STICK)), "The filter passes a common item stack");
        helper.assertTrue(ComponentFilterSamples.EPIC.test(epicWater), "The filter fails an epic fluid stack");
        helper.assertFalse(ComponentFilterSamples.EPIC.test(new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME)), "The filter passes a fluid stack without a rarity");
        helper.assertTrue(ComponentFilterSamples.EPIC.test(ItemResource.of(epicStick)), "The filter fails an epic item resource");
        helper.assertFalse(ComponentFilterSamples.EPIC.test(ItemResource.of(Items.STICK)), "The filter passes a common item resource");
        helper.assertTrue(ComponentFilterSamples.EPIC.test(FluidResource.of(epicWater)), "The filter fails an epic fluid resource");
        helper.assertFalse(ComponentFilterSamples.EPIC.test(FluidResource.of(Fluids.WATER)), "The filter passes a fluid resource without a rarity");
        helper.succeed();
    }

    @ServerTest
    public static void comparesTheLevelOfAnEnchantment(GameTestHelper helper) {
        Holder<Enchantment> sharpness = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        ComponentFilter sharp = ComponentPath.of(DataComponents.ENCHANTMENTS, "['minecraft:sharpness']").compare(Operator.AT_LEAST, 3);
        ItemStack strong = new ItemStack(Items.DIAMOND_SWORD);
        strong.enchant(sharpness, 4);
        ItemStack weak = new ItemStack(Items.DIAMOND_SWORD);
        weak.enchant(sharpness, 2);
        helper.assertTrue(sharp.test(strong), "The filter fails a sword with Sharpness '4'");
        helper.assertFalse(sharp.test(weak), "The filter passes a sword with Sharpness '2'");
        helper.succeed();
    }

    @ServerTest
    public static void ingredientAcceptsByFilterAndRejectsByItemSet(GameTestHelper helper) {
        Ingredient ingredient = ComponentFilterSamples.EPIC.asIngredient(HolderSet.direct(BuiltInRegistries.ITEM.wrapAsHolder(Items.STICK)));
        helper.assertTrue(ingredient.test(ComponentFilterSamples.epic(new ItemStack(Items.STICK))), "The ingredient rejects an epic stick");
        helper.assertFalse(ingredient.test(new ItemStack(Items.STICK)), "The ingredient accepts a common stick");
        helper.assertFalse(ingredient.test(ComponentFilterSamples.epic(new ItemStack(Items.BLAZE_ROD))), "The ingredient accepts an epic blaze rod");
        helper.succeed();
    }

    @ServerTest
    public static void fluidIngredientAcceptsByFilterAndRejectsByFluidSet(GameTestHelper helper) {
        FluidIngredient ingredient = ComponentFilterSamples.EPIC.asFluidIngredient(HolderSet.direct(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER)));
        helper.assertTrue(ingredient.test(ComponentFilterSamples.epic(new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME))), "The ingredient rejects epic water");
        helper.assertFalse(ingredient.test(new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME)), "The ingredient accepts water without a rarity");
        helper.assertFalse(ingredient.test(ComponentFilterSamples.epic(new FluidStack(Fluids.LAVA, FluidType.BUCKET_VOLUME))), "The ingredient accepts epic lava");
        helper.succeed();
    }

    @ServerTest
    public static void itemPredicatePassesTheSameStacks(GameTestHelper helper) {
        ItemPredicate predicate = ItemPredicate.Builder.item().withComponents(ComponentFilterSamples.EPIC.asMatchers()).build();
        helper.assertTrue(predicate.test(ComponentFilterSamples.epic(new ItemStack(Items.STICK))), "The predicate fails an epic stick");
        helper.assertFalse(predicate.test(new ItemStack(Items.STICK)), "The predicate passes a common stick");
        helper.succeed();
    }

    @ServerTest
    public static void hasPassesADefaultComponent(GameTestHelper helper) {
        ComponentFilter food = ComponentFilter.has(DataComponents.FOOD);
        helper.assertTrue(food.test(new ItemStack(Items.APPLE)), "The filter fails an apple");
        helper.assertFalse(food.test(new ItemStack(Items.STICK)), "The filter passes a stick");
        helper.succeed();
    }

    private static ItemStack epic(ItemStack stack) {
        stack.set(DataComponents.RARITY, Rarity.EPIC);
        return stack;
    }

    private static FluidStack epic(FluidStack stack) {
        stack.set(DataComponents.RARITY, Rarity.EPIC);
        return stack;
    }
}
