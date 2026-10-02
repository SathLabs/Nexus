package dev.satherov.nexus.test.unit.api.registry.recipe;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.registry.recipe.RecipeTypeEntry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;

///
/// Checks the recipe types that recipe type builders register, with the serializers of their recipes.
///
public class RecipeTypeBuilderTest {
    
    private static final StructCodec<Probe, Access.Plain> CODEC = NexusCodec.struct("probe", NexusCodec.STRING.field("group", Probe::group), group -> new Probe(group, RecipeTypeBuilderTest.probe().value()));
    
    private static @Nullable RecipeTypeEntry<Probe> probe;
    
    @Test
    public void bindsTheEntryToTheTypeInTheRegistry() {
        RecipeTypeEntry<Probe> probe = RecipeTypeBuilderTest.probe();
        Assertions.assertThat(probe.getId()).isEqualTo(Nexus.getMod().id("recipe_type_builder_test_probe"));
        Assertions.assertThat(probe.value()).isSameAs(BuiltInRegistries.RECIPE_TYPE.getValue(probe.getId()));
    }
    
    @Test
    public void registersTheSerializerUnderTheSameName() {
        RecipeTypeEntry<Probe> probe = RecipeTypeBuilderTest.probe();
        RegistryEntry<RecipeSerializer<?>, RecipeSerializer<Probe>> serializer = probe.getSerializer();
        Assertions.assertThat(serializer.getId()).isEqualTo(probe.getId());
        Assertions.assertThat(serializer.value()).isSameAs(BuiltInRegistries.RECIPE_SERIALIZER.getValue(probe.getId()));
    }
    
    @Test
    public void serializesTheRecipesWithTheCodec() {
        RecipeTypeEntry<Probe> probe = RecipeTypeBuilderTest.probe();
        Codec<Probe> codec = probe.getSerializer().value().codec().codec();
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, new Probe("planks", probe.value())).getOrThrow();
        Assertions.assertThat(json).isEqualTo(JsonParser.parseString("{\"group\":\"planks\"}"));
        Assertions.assertThat(codec.parse(JsonOps.INSTANCE, json).getOrThrow()).isEqualTo(new Probe("planks", probe.value()));
    }
    
    @Test
    public void isTheTypeOfItsOwnRecipes() {
        RecipeTypeEntry<Probe> probe = RecipeTypeBuilderTest.probe();
        Assertions.assertThat(probe.is(RecipeTypeBuilderTest.holder(new Probe("", probe.value())))).isTrue();
    }
    
    @Test
    public void isNotTheTypeOfOtherRecipes() {
        RecipeType<Probe> other = RecipeType.simple(Nexus.getMod().id("recipe_type_builder_test_other"));
        Assertions.assertThat(RecipeTypeBuilderTest.probe().is(RecipeTypeBuilderTest.holder(new Probe("", other)))).isFalse();
    }
    
    private static RecipeHolder<Probe> holder(Probe recipe) {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Nexus.getMod().id("recipe_type_builder_test_recipe")), recipe);
    }
    
    private static RecipeTypeEntry<Probe> probe() {
        return Objects.requireNonNull(RecipeTypeBuilderTest.probe);
    }
    
    private record Probe(String group, RecipeType<Probe> type) implements Recipe<RecipeInput> {
        
        @Override
        public boolean matches(RecipeInput input, Level level) {
            return false;
        }
        
        @Override
        public ItemStack assemble(RecipeInput input) {
            return ItemStack.EMPTY;
        }
        
        @Override
        public boolean showNotification() {
            return false;
        }
        
        @Override
        public RecipeSerializer<Probe> getSerializer() {
            return RecipeTypeBuilderTest.probe().getSerializer().value();
        }
        
        @Override
        public RecipeType<Probe> getType() {
            return this.type;
        }
        
        @Override
        public PlacementInfo placementInfo() {
            return PlacementInfo.NOT_PLACEABLE;
        }
        
        @Override
        public RecipeBookCategory recipeBookCategory() {
            return RecipeBookCategories.CRAFTING_MISC;
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            RecipeTypeBuilderTest.probe = Nexus.getMod().getRegistrations().recipe("recipe_type_builder_test_probe", RecipeTypeBuilderTest.CODEC).register();
        }
    }
}
