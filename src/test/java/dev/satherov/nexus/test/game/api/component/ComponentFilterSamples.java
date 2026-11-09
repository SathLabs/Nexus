package dev.satherov.nexus.test.game.api.component;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.internal.component.vanilla.FilterFluidIngredient;
import dev.satherov.nexus.internal.component.vanilla.FilterIngredient;
import dev.satherov.nexus.internal.component.vanilla.FilterPredicate;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import net.minecraft.advancements.criterion.DataComponentMatchers;
import net.minecraft.advancements.criterion.EnchantmentPredicate;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.advancements.criterion.MinMaxBounds;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentExactPredicate;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.predicates.DamagePredicate;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.component.predicates.EnchantmentsPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;

import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// Checks what component filters pass on item stacks, fluid stacks, and resources, and the vanilla types that hold a filter.
///
public class ComponentFilterSamples {

    private static ItemStack damagedSword(int damage) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(damage);
        return sword;
    }

    private static FluidStack epicFluid(Fluid fluid) {
        return new FluidStack(fluid, 1_000, DataComponentPatch.builder().set(DataComponents.RARITY, Rarity.EPIC).build());
    }

    private static ComponentFilter mixed() {
        return ComponentFilter.allOf(List.of(
                ComponentFilter.has(DataComponents.MAX_STACK_SIZE),
                ComponentFilter.exact(DataComponents.DAMAGE, 3).negate(),
                ComponentFilter.anyOf(List.of(
                        ComponentFilter.compare(ComponentPath.of(DataComponents.DAMAGE, ""), new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Numeric(5))),
                        ComponentFilter.matching(DataComponentPredicates.DAMAGE, new DamagePredicate(MinMaxBounds.Ints.ANY, MinMaxBounds.Ints.atLeast(10)))
                ))
        ));
    }

    private static RegistryOps<JsonElement> ops(GameTestHelper helper) {
        return helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }

    private static RegistryFriendlyByteBuf buffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
    }

    @ServerTest
    public static void hasReadsTheDefaultComponentsOfTheItem(GameTestHelper helper) {
        ComponentFilter food = ComponentFilter.has(DataComponents.FOOD);
        ItemStack inedible = new ItemStack(Items.APPLE);
        inedible.remove(DataComponents.FOOD);
        Assertions.assertThat(food.test(new ItemStack(Items.APPLE))).isTrue();
        Assertions.assertThat(food.test(new ItemStack(Items.STICK))).isFalse();
        Assertions.assertThat(food.test(inedible)).isFalse();
        helper.succeed();
    }

    @ServerTest
    public static void exactPassesAnEqualValue(GameTestHelper helper) {
        ComponentFilter maxStackSize = ComponentFilter.exact(DataComponents.MAX_STACK_SIZE, 16);
        ComponentFilter damage = ComponentFilter.exact(DataComponents.DAMAGE, 3);
        Assertions.assertThat(maxStackSize.test(new ItemStack(Items.ENDER_PEARL))).isTrue();
        Assertions.assertThat(maxStackSize.test(new ItemStack(Items.STICK))).isFalse();
        Assertions.assertThat(damage.test(ComponentFilterSamples.damagedSword(3))).isTrue();
        Assertions.assertThat(damage.test(ComponentFilterSamples.damagedSword(4))).isFalse();
        Assertions.assertThat(damage.test(new ItemStack(Items.STICK))).isFalse();
        helper.succeed();
    }

    @ServerTest
    public static void compareTestsThePartAtThePath(GameTestHelper helper) {
        ComponentFilter nutrition = ComponentFilter.compare(ComponentPath.of(DataComponents.FOOD, "nutrition"), new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(4)));
        ComponentFilter damage = ComponentFilter.compare(ComponentPath.of(DataComponents.DAMAGE, ""), new Comparison(Comparison.Operator.GREATER, new Comparison.Literal.Numeric(2)));
        Assertions.assertThat(nutrition.test(new ItemStack(Items.APPLE))).isTrue();
        Assertions.assertThat(nutrition.test(new ItemStack(Items.BREAD))).isFalse();
        Assertions.assertThat(damage.test(ComponentFilterSamples.damagedSword(3))).isTrue();
        Assertions.assertThat(damage.test(ComponentFilterSamples.damagedSword(2))).isFalse();
        helper.succeed();
    }

    @ServerTest
    public static void compareFailsEveryOperatorOnAnAbsentPart(GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("other", 1);
        ItemStack tagged = new ItemStack(Items.STICK);
        tagged.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        for (Comparison.Operator operator : Comparison.Operator.values()) {
            Comparison zero = new Comparison(operator, new Comparison.Literal.Numeric(0));
            Assertions.assertThat(ComponentFilter.compare(ComponentPath.of(DataComponents.DAMAGE, ""), zero).test(new ItemStack(Items.STICK))).as(operator.name()).isFalse();
            Assertions.assertThat(ComponentFilter.compare(ComponentPath.of(DataComponents.CUSTOM_DATA, "level"), zero).test(tagged)).as(operator.name()).isFalse();
        }

        helper.succeed();
    }

    @ServerTest
    public static void matchingTestsTheVanillaPredicate(GameTestHelper helper) {
        Holder<Enchantment> sharpness = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        ComponentFilter sharp = ComponentFilter.matching(DataComponentPredicates.ENCHANTMENTS, EnchantmentsPredicate.enchantments(List.of(new EnchantmentPredicate(sharpness, MinMaxBounds.Ints.atLeast(2)))));
        ItemStack sharper = new ItemStack(Items.DIAMOND_SWORD);
        sharper.enchant(sharpness, 3);
        ItemStack blunter = new ItemStack(Items.DIAMOND_SWORD);
        blunter.enchant(sharpness, 1);
        Assertions.assertThat(sharp.test(sharper)).isTrue();
        Assertions.assertThat(sharp.test(blunter)).isFalse();
        Assertions.assertThat(sharp.test(new ItemStack(Items.DIAMOND_SWORD))).isFalse();
        helper.succeed();
    }

    @ServerTest
    public static void readsAnItemResourceThroughItsStack(GameTestHelper helper) {
        ComponentFilter damage = ComponentFilter.exact(DataComponents.DAMAGE, 3);
        Assertions.assertThat(ComponentFilter.has(DataComponents.FOOD).test(ItemResource.of(Items.APPLE))).isTrue();
        Assertions.assertThat(ComponentFilter.has(DataComponents.FOOD).test(ItemResource.of(Items.STICK))).isFalse();
        Assertions.assertThat(damage.test(ItemResource.of(ComponentFilterSamples.damagedSword(3)))).isTrue();
        Assertions.assertThat(damage.test(ItemResource.of(Items.DIAMOND_SWORD))).isFalse();
        helper.succeed();
    }

    @ServerTest
    public static void readsAFluidResourceThroughItsStack(GameTestHelper helper) {
        ComponentFilter epic = ComponentFilter.exact(DataComponents.RARITY, Rarity.EPIC);
        Assertions.assertThat(epic.test(FluidResource.of(ComponentFilterSamples.epicFluid(Fluids.WATER)))).isTrue();
        Assertions.assertThat(epic.test(FluidResource.of(Fluids.WATER))).isFalse();
        helper.succeed();
    }

    @ServerTest
    public static void testsAFluidStack(GameTestHelper helper) {
        ComponentFilter epic = ComponentFilter.exact(DataComponents.RARITY, Rarity.EPIC);
        ComponentFilter rarity = ComponentFilter.has(DataComponents.RARITY);
        Assertions.assertThat(epic.test(ComponentFilterSamples.epicFluid(Fluids.WATER))).isTrue();
        Assertions.assertThat(rarity.test(ComponentFilterSamples.epicFluid(Fluids.WATER))).isTrue();
        Assertions.assertThat(rarity.test(new FluidStack(Fluids.WATER, 1_000))).isFalse();
        helper.succeed();
    }

    @ServerTest
    public static void matchersHoldTheFilterAsTheirOnePredicate(GameTestHelper helper) {
        ComponentFilter damage = ComponentFilter.exact(DataComponents.DAMAGE, 3);
        DataComponentMatchers matchers = damage.asMatchers();
        Assertions.assertThat(matchers.exact()).isEqualTo(DataComponentExactPredicate.EMPTY);
        Assertions.assertThat(matchers.partial()).isEqualTo(Map.of(FilterPredicate.TYPE, new FilterPredicate(damage)));
        Assertions.assertThat(matchers.test(ComponentFilterSamples.damagedSword(3))).isTrue();
        Assertions.assertThat(matchers.test(ComponentFilterSamples.damagedSword(4))).isFalse();
        helper.succeed();
    }

    @ServerTest
    public static void itemPredicateFromTheMatchersPassesWhatTheFilterPasses(GameTestHelper helper) {
        ComponentFilter filter = ComponentFilterSamples.mixed();
        ItemPredicate predicate = new ItemPredicate(Optional.empty(), MinMaxBounds.Ints.ANY, filter.asMatchers());
        List<ItemStack> stacks = List.of(
                new ItemStack(Items.STICK),
                new ItemStack(Items.APPLE),
                ComponentFilterSamples.damagedSword(0),
                ComponentFilterSamples.damagedSword(3),
                ComponentFilterSamples.damagedSword(4),
                ComponentFilterSamples.damagedSword(12)
        );

        for (ItemStack stack : stacks) {
            Assertions.assertThat(predicate.test(stack)).as(stack.toString()).isEqualTo(filter.test(stack));
        }

        Assertions.assertThat(stacks).anyMatch(filter::test).anyMatch(stack -> !filter.test(stack));
        helper.succeed();
    }

    @ServerTest
    public static void ingredientPassesStacksOfTheSetThatPassTheFilter(GameTestHelper helper) {
        Ingredient ingredient = ComponentFilter.exact(DataComponents.DAMAGE, 3).asIngredient(HolderSet.direct(Items.DIAMOND_SWORD.builtInRegistryHolder()));
        ItemStack iron = new ItemStack(Items.IRON_SWORD);
        iron.setDamageValue(3);
        Assertions.assertThat(ingredient.test(ComponentFilterSamples.damagedSword(3))).isTrue();
        Assertions.assertThat(ingredient.test(ComponentFilterSamples.damagedSword(4))).isFalse();
        Assertions.assertThat(ingredient.test(iron)).isFalse();
        Assertions.assertThat(ingredient.items().toList()).containsExactly(Items.DIAMOND_SWORD.builtInRegistryHolder());
        Assertions.assertThat(ingredient.isSimple()).isFalse();
        helper.succeed();
    }

    @ServerTest
    public static void fluidIngredientPassesStacksOfTheSetThatPassTheFilter(GameTestHelper helper) {
        FluidIngredient ingredient = ComponentFilter.exact(DataComponents.RARITY, Rarity.EPIC).asFluidIngredient(HolderSet.direct(Fluids.WATER.builtInRegistryHolder()));
        Assertions.assertThat(ingredient.test(ComponentFilterSamples.epicFluid(Fluids.WATER))).isTrue();
        Assertions.assertThat(ingredient.test(new FluidStack(Fluids.WATER, 1_000))).isFalse();
        Assertions.assertThat(ingredient.test(ComponentFilterSamples.epicFluid(Fluids.LAVA))).isFalse();
        Assertions.assertThat(ingredient.fluids()).containsExactly(Fluids.WATER.builtInRegistryHolder());
        Assertions.assertThat(ingredient.isSimple()).isFalse();
        helper.succeed();
    }

    @ServerTest
    public static void ingredientsAreEqualBySetAndFilter(GameTestHelper helper) {
        HolderSet<Item> swords = HolderSet.direct(Items.DIAMOND_SWORD.builtInRegistryHolder());
        HolderSet<Fluid> water = HolderSet.direct(Fluids.WATER.builtInRegistryHolder());
        ComponentFilter damage = ComponentFilter.exact(DataComponents.DAMAGE, 3);
        FilterIngredient ingredient = new FilterIngredient(swords, damage);
        FilterFluidIngredient fluidIngredient = new FilterFluidIngredient(water, damage);
        Assertions.assertThat(new FilterIngredient(HolderSet.direct(Items.DIAMOND_SWORD.builtInRegistryHolder()), ComponentFilter.exact(DataComponents.DAMAGE, 3))).isEqualTo(ingredient).hasSameHashCodeAs(ingredient);
        Assertions.assertThat(new FilterIngredient(HolderSet.direct(Items.IRON_SWORD.builtInRegistryHolder()), damage)).isNotEqualTo(ingredient);
        Assertions.assertThat(new FilterIngredient(swords, ComponentFilter.exact(DataComponents.DAMAGE, 4))).isNotEqualTo(ingredient);
        Assertions.assertThat(new FilterFluidIngredient(HolderSet.direct(Fluids.WATER.builtInRegistryHolder()), ComponentFilter.exact(DataComponents.DAMAGE, 3))).isEqualTo(fluidIngredient).hasSameHashCodeAs(fluidIngredient);
        Assertions.assertThat(new FilterFluidIngredient(HolderSet.direct(Fluids.LAVA.builtInRegistryHolder()), damage)).isNotEqualTo(fluidIngredient);
        Assertions.assertThat(new FilterFluidIngredient(water, ComponentFilter.exact(DataComponents.DAMAGE, 4))).isNotEqualTo(fluidIngredient);
        helper.succeed();
    }

    @ServerTest
    public static void predicateRoundTripsInJson(GameTestHelper helper) {
        DataComponentMatchers matchers = ComponentFilterSamples.mixed().asMatchers();
        RegistryOps<JsonElement> ops = ComponentFilterSamples.ops(helper);
        JsonElement json = DataComponentMatchers.CODEC.codec().encodeStart(ops, matchers).getOrThrow();
        Assertions.assertThat(json.getAsJsonObject().getAsJsonObject("predicates").keySet()).containsExactly("nexus:filter");
        Assertions.assertThat(DataComponentMatchers.CODEC.codec().parse(ops, json).getOrThrow()).isEqualTo(matchers);
        helper.succeed();
    }

    @ServerTest
    public static void predicateRoundTripsOverTheNetwork(GameTestHelper helper) {
        DataComponentPredicate.Single<FilterPredicate> single = new DataComponentPredicate.Single<>(FilterPredicate.TYPE, new FilterPredicate(ComponentFilterSamples.mixed()));
        RegistryFriendlyByteBuf buffer = ComponentFilterSamples.buffer(helper);
        FilterPredicate.TYPE.singleStreamCodec().encode(buffer, single);
        Assertions.assertThat(FilterPredicate.TYPE.singleStreamCodec().decode(buffer)).isEqualTo(single);
        helper.succeed();
    }

    @ServerTest
    public static void ingredientRoundTripsInJson(GameTestHelper helper) {
        Ingredient ingredient = ComponentFilterSamples.mixed().asIngredient(HolderSet.direct(Items.DIAMOND_SWORD.builtInRegistryHolder()));
        RegistryOps<JsonElement> ops = ComponentFilterSamples.ops(helper);
        JsonElement json = Ingredient.CODEC.encodeStart(ops, ingredient).getOrThrow();
        JsonObject object = json.getAsJsonObject();
        Assertions.assertThat(object.keySet()).containsExactlyInAnyOrder("neoforge:ingredient_type", "items", "filter");
        Assertions.assertThat(object.get("neoforge:ingredient_type").getAsString()).isEqualTo("nexus:filter");
        Assertions.assertThat(Ingredient.CODEC.parse(ops, json).getOrThrow()).isEqualTo(ingredient);
        helper.succeed();
    }

    @ServerTest
    public static void ingredientRoundTripsOverTheNetwork(GameTestHelper helper) {
        Ingredient ingredient = ComponentFilterSamples.mixed().asIngredient(HolderSet.direct(Items.DIAMOND_SWORD.builtInRegistryHolder()));
        RegistryFriendlyByteBuf buffer = ComponentFilterSamples.buffer(helper);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
        Assertions.assertThat(Ingredient.CONTENTS_STREAM_CODEC.decode(buffer)).isEqualTo(ingredient);
        helper.succeed();
    }

    @ServerTest
    public static void fluidIngredientRoundTripsInJson(GameTestHelper helper) {
        FluidIngredient ingredient = ComponentFilterSamples.mixed().asFluidIngredient(HolderSet.direct(Fluids.WATER.builtInRegistryHolder()));
        RegistryOps<JsonElement> ops = ComponentFilterSamples.ops(helper);
        JsonElement json = FluidIngredient.CODEC.encodeStart(ops, ingredient).getOrThrow();
        JsonObject object = json.getAsJsonObject();
        Assertions.assertThat(object.keySet()).containsExactlyInAnyOrder("neoforge:ingredient_type", "fluids", "filter");
        Assertions.assertThat(object.get("neoforge:ingredient_type").getAsString()).isEqualTo("nexus:filter");
        Assertions.assertThat(FluidIngredient.CODEC.parse(ops, json).getOrThrow()).isEqualTo(ingredient);
        helper.succeed();
    }

    @ServerTest
    public static void fluidIngredientRoundTripsOverTheNetwork(GameTestHelper helper) {
        FluidIngredient ingredient = ComponentFilterSamples.mixed().asFluidIngredient(HolderSet.direct(Fluids.WATER.builtInRegistryHolder()));
        RegistryFriendlyByteBuf buffer = ComponentFilterSamples.buffer(helper);
        FluidIngredient.STREAM_CODEC.encode(buffer, ingredient);
        Assertions.assertThat(FluidIngredient.STREAM_CODEC.decode(buffer)).isEqualTo(ingredient);
        helper.succeed();
    }
}
