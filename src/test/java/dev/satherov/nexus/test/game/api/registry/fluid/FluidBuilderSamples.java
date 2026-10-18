package dev.satherov.nexus.test.game.api.registry.fluid;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.block.BlockEntry;
import dev.satherov.nexus.api.registry.fluid.FluidEntry;
import dev.satherov.nexus.api.registry.fluid.PlainFluidBuilder;
import dev.satherov.nexus.api.registry.fluid.PlainFluidEntry;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

///
/// Checks the fluids, types, blocks, and buckets that fluid builders register, with their links, stacks, and translations.
///
public class FluidBuilderSamples {
    
    private static @Nullable PlainFluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing> plain;
    private static @Nullable FluidEntry<FluidType, SampleSource, SampleFlowing, LiquidBlock, SampleBucket> full;
    private static @Nullable FluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing, LiquidBlock, BucketItem> withItem;
    private static @Nullable PlainFluidBuilder<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing, Registrations> takenOver;
    private static boolean clientRequested;
    
    @ServerTest
    public static void registersTheTypeAndBothFluidsUnderTheirNames(GameTestHelper helper) {
        PlainFluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing> plain = FluidBuilderSamples.plain();
        helper.assertValueEqual(plain.getId(), Nexus.getMod().id("fluid_builder_samples_plain"), "identifier of the source");
        helper.assertValueEqual(plain.getType().getId(), plain.getId(), "identifier of the type");
        helper.assertValueEqual(plain.getFlowing().getId(), Nexus.getMod().id("flowing_fluid_builder_samples_plain"), "identifier of the flowing fluid");
        helper.assertValueEqual(BuiltInRegistries.FLUID.getValue(plain.getId()), plain.value(), "registered source");
        helper.assertValueEqual(BuiltInRegistries.FLUID.getValue(plain.getFlowing().getId()), plain.getFlowing().value(), "registered flowing fluid");
        helper.succeed();
    }
    
    @ServerTest
    public static void linksTheTypeAndBothFluids(GameTestHelper helper) {
        PlainFluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing> plain = FluidBuilderSamples.plain();
        BaseFlowingFluid.Source source = plain.value();
        BaseFlowingFluid.Flowing flowing = plain.getFlowing().value();
        helper.assertValueEqual(source.getFluidType(), plain.getType().value(), "type of the source");
        helper.assertValueEqual(flowing.getFluidType(), plain.getType().value(), "type of the flowing fluid");
        helper.assertValueEqual(source.getSource(), source, "source of the source");
        helper.assertValueEqual(source.getFlowing(), flowing, "flowing fluid of the source");
        helper.assertValueEqual(flowing.getSource(), source, "source of the flowing fluid");
        helper.assertValueEqual(flowing.getFlowing(), flowing, "flowing fluid of the flowing fluid");
        helper.succeed();
    }
    
    @ServerTest
    public static void registersNoBlockOrBucketForAPlainFluid(GameTestHelper helper) {
        PlainFluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing> plain = FluidBuilderSamples.plain();
        helper.assertFalse(BuiltInRegistries.BLOCK.containsKey(plain.getId()), "The plain fluid registered a block");
        helper.assertFalse(BuiltInRegistries.ITEM.containsKey(Nexus.getMod().id("fluid_builder_samples_plain_bucket")), "The plain fluid registered a bucket");
        helper.assertValueEqual(plain.value().getBucket(), Items.AIR, "bucket of the source");
        helper.assertTrue(plain.value().defaultFluidState().createLegacyBlock().isAir(), "The source placed a block");
        helper.succeed();
    }
    
    @ServerTest
    public static void appliesTheEditsInCallOrder(GameTestHelper helper) {
        PlainFluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing> plain = FluidBuilderSamples.plain();
        helper.assertValueEqual(plain.getType().value().getDensity(), 2000, "density of the type");
        helper.assertValueEqual(plain.value().getTickDelay(helper.getLevel()), 20, "tick delay of the source");
        helper.assertValueEqual(plain.getFlowing().value().getTickDelay(helper.getLevel()), 20, "tick delay of the flowing fluid");
        helper.succeed();
    }
    
    @ServerTest
    public static void translatesTheTypeUnderItsKey(GameTestHelper helper) {
        PlainFluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing> plain = FluidBuilderSamples.plain();
        List<String> keys = Nexus.getMod().getTranslations().getAll().stream().map(Translation::getKey).toList();
        helper.assertValueEqual(plain.getTranslation().getKey(), "fluid_type.nexus.fluid_builder_samples_plain", "translation key");
        helper.assertValueEqual(plain.getTranslation().getEnglish(), "Plain Sample Fluid", "English name");
        helper.assertValueEqual(plain.getType().value().getDescriptionId(), plain.getTranslation().getKey(), "description id of the type");
        helper.assertFalse(keys.contains("fluid_type.nexus.flowing_fluid_builder_samples_plain"), "The flowing fluid declared a key of its own");
        helper.succeed();
    }
    
    @ServerTest
    public static void stacksHoldTheSource(GameTestHelper helper) {
        PlainFluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing> plain = FluidBuilderSamples.plain();
        FluidStack stack = plain.stack(250);
        helper.assertValueEqual(stack.getFluid(), plain.value(), "fluid of the stack");
        helper.assertValueEqual(stack.getAmount(), 250, "amount of the stack");
        helper.succeed();
    }
    
    @ServerTest
    public static void isMatchesTheStatesOfBothFluidsOnly(GameTestHelper helper) {
        PlainFluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing> plain = FluidBuilderSamples.plain();
        helper.assertTrue(plain.is(plain.value().defaultFluidState()), "The plain fluid did not match its source");
        helper.assertTrue(plain.is(plain.getFlowing().value().getFlowing(3, false)), "The plain fluid did not match its flowing fluid");
        helper.assertFalse(plain.is(FluidBuilderSamples.full().value().defaultFluidState()), "The plain fluid matched the full fluid");
        helper.assertFalse(plain.is(Fluids.WATER.defaultFluidState()), "The plain fluid matched water");
        helper.succeed();
    }
    
    @ServerTest
    public static void replacesTheTypeAndBothFluids(GameTestHelper helper) {
        FluidEntry<FluidType, SampleSource, SampleFlowing, LiquidBlock, SampleBucket> full = FluidBuilderSamples.full();
        helper.assertValueEqual(full.value().getClass(), SampleSource.class, "class of the source");
        helper.assertValueEqual(full.getFlowing().value().getClass(), SampleFlowing.class, "class of the flowing fluid");
        helper.assertValueEqual(full.getType().value().getLightLevel(), 7, "light level of the type");
        helper.assertValueEqual(full.getType().value().getDescriptionId(), "fluid_type.nexus.fluid_builder_samples_full", "description id of the type");
        helper.assertValueEqual(full.getFlowing().value().getSource(), full.value(), "source of the flowing fluid");
        helper.succeed();
    }
    
    @ServerTest
    public static void linksTheBlockAndTheBucket(GameTestHelper helper) {
        FluidEntry<FluidType, SampleSource, SampleFlowing, LiquidBlock, SampleBucket> full = FluidBuilderSamples.full();
        LiquidBlock block = full.getBlock().value();
        SampleBucket bucket = full.getBucket().value();
        helper.assertValueEqual(full.getBlock().getId(), full.getId(), "identifier of the block");
        helper.assertValueEqual(full.getBucket().getId(), Nexus.getMod().id("fluid_builder_samples_full_bucket"), "identifier of the bucket");
        helper.assertValueEqual(full.value().defaultFluidState().createLegacyBlock().getBlock(), block, "block of the source");
        helper.assertValueEqual(block.defaultBlockState().getFluidState().getType(), full.value(), "fluid of the block");
        helper.assertValueEqual(full.value().getBucket(), bucket, "bucket of the source");
        helper.assertValueEqual(bucket.content, full.value(), "fluid of the bucket");
        helper.succeed();
    }
    
    @ServerTest
    public static void givesTheBlockTheLiquidPropertiesOfWater(GameTestHelper helper) {
        FluidEntry<FluidType, SampleSource, SampleFlowing, LiquidBlock, SampleBucket> full = FluidBuilderSamples.full();
        BlockState state = full.getBlock().getDefaultState();
        helper.assertTrue(state.liquid(), "The block is not a liquid");
        helper.assertTrue(state.canBeReplaced(), "The block cannot be replaced");
        helper.assertTrue(full.getBlock().value().getLootTable().isEmpty(), "The block drops loot");
        helper.assertValueEqual(full.getBlock().value().getDescriptionId(), "block.nexus.fluid_builder_samples_full", "description id of the block");
        helper.assertValueEqual(full.getBlock().getTranslation().getEnglish(), "Full Sample Fluid", "English name of the block");
        helper.succeed();
    }
    
    @ServerTest
    public static void givesTheBucketFactoryTheSourceAndTheBucketProperties(GameTestHelper helper) {
        FluidEntry<FluidType, SampleSource, SampleFlowing, LiquidBlock, SampleBucket> full = FluidBuilderSamples.full();
        SampleBucket bucket = full.getBucket().value();
        ItemStackTemplate remainder = Objects.requireNonNull(bucket.getCraftingRemainder());
        helper.assertValueEqual(bucket.content, full.value(), "fluid of the bucket");
        helper.assertValueEqual(full.getBucket().stack().getMaxStackSize(), 1, "maximum stack size of the bucket");
        helper.assertValueEqual(remainder.item().value(), Items.BUCKET, "crafting remainder of the bucket");
        helper.succeed();
    }
    
    @ServerTest
    public static void namesTheBucketAfterTheFluid(GameTestHelper helper) {
        FluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing, LiquidBlock, BucketItem> withItem = FluidBuilderSamples.withItem();
        FluidEntry<FluidType, SampleSource, SampleFlowing, LiquidBlock, SampleBucket> full = FluidBuilderSamples.full();
        helper.assertValueEqual(withItem.getBucket().getTranslation().getKey(), "item.nexus.fluid_builder_samples_with_item_bucket", "translation key of the default bucket");
        helper.assertValueEqual(withItem.getBucket().getTranslation().getEnglish(), "Sample Fluid With Item Bucket", "English name of the default bucket");
        helper.assertValueEqual(withItem.getBucket().value().getDescriptionId(), withItem.getBucket().getTranslation().getKey(), "description id of the default bucket");
        helper.assertValueEqual(full.getBucket().getTranslation().getEnglish(), "Full Sample Fluid Bucket", "English name of the custom bucket");
        helper.succeed();
    }
    
    @ServerTest
    public static void showsTheBucketsInTheFirstTabOfTheMod(GameTestHelper helper) {
        Set<ResourceKey<CreativeModeTab>> first = Set.of(Nexus.getMod().getRegistrations().getAll(Registries.CREATIVE_MODE_TAB).getFirst().getKey());
        helper.assertValueEqual(FluidBuilderSamples.withItem().getBucket().getTabs(), first, "tabs of the default bucket");
        helper.assertValueEqual(FluidBuilderSamples.full().getBucket().getTabs(), first, "tabs of the custom bucket");
        helper.succeed();
    }
    
    @ServerTest
    public static void registersTheBlockBuilderThatTookOverTheBlock(GameTestHelper helper) {
        FluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing, LiquidBlock, BucketItem> withItem = FluidBuilderSamples.withItem();
        helper.assertTrue(withItem.getBlock() instanceof BlockEntry<?, ?>, "The block is registered without its item");
        BlockEntry<?, ?> block = (BlockEntry<?, ?>) withItem.getBlock();
        helper.assertValueEqual(block.getItem().getId(), withItem.getId(), "identifier of the block item");
        helper.assertValueEqual(block.asItem(), BuiltInRegistries.ITEM.getValue(withItem.getId()), "registered block item");
        helper.assertValueEqual(block.value().asItem(), block.asItem(), "item of the block");
        helper.assertValueEqual(withItem.value().defaultFluidState().createLegacyBlock().getBlock(), block.value(), "block of the source");
        helper.succeed();
    }
    
    @ServerTest
    public static void refusesChangesToTheFluidBuilderAfterTheBlock(GameTestHelper helper) {
        PlainFluidBuilder<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing, Registrations> builder = FluidBuilderSamples.takenOver();
        helper.assertTrue(Assertions.catchThrowable(() -> builder.type(FluidType::new)) instanceof IllegalStateException, "The type was replaced");
        helper.assertTrue(Assertions.catchThrowable(() -> builder.typeProperties(properties -> properties)) instanceof IllegalStateException, "The type properties were edited");
        helper.assertTrue(Assertions.catchThrowable(() -> builder.fluids(BaseFlowingFluid.Source::new, BaseFlowingFluid.Flowing::new)) instanceof IllegalStateException, "The fluids were replaced");
        helper.assertTrue(Assertions.catchThrowable(() -> builder.properties(properties -> properties)) instanceof IllegalStateException, "The fluid properties were edited");
        helper.assertTrue(Assertions.catchThrowable(() -> builder.model(() -> SampleClient::model)) instanceof IllegalStateException, "The model was replaced");
        helper.assertTrue(Assertions.catchThrowable(() -> builder.extensions(() -> SampleClient::extensions)) instanceof IllegalStateException, "The client extensions were replaced");
        helper.assertTrue(Assertions.catchThrowable(builder::block) instanceof IllegalStateException, "A second block was opened");
        helper.assertTrue(Assertions.catchThrowable(builder::register) instanceof IllegalStateException, "The fluid was registered a second time");
        helper.succeed();
    }
    
    @ServerTest
    public static void neverRequestsTheClientHooksOnADedicatedServer(GameTestHelper helper) {
        helper.assertFalse(FluidBuilderSamples.clientRequested, "The model or the client extensions were requested on a dedicated server");
        helper.succeed();
    }
    
    private static PlainFluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing> plain() {
        return Objects.requireNonNull(FluidBuilderSamples.plain);
    }
    
    private static FluidEntry<FluidType, SampleSource, SampleFlowing, LiquidBlock, SampleBucket> full() {
        return Objects.requireNonNull(FluidBuilderSamples.full);
    }
    
    private static FluidEntry<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing, LiquidBlock, BucketItem> withItem() {
        return Objects.requireNonNull(FluidBuilderSamples.withItem);
    }
    
    private static PlainFluidBuilder<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing, Registrations> takenOver() {
        return Objects.requireNonNull(FluidBuilderSamples.takenOver);
    }
    
    private static final class SampleSource extends BaseFlowingFluid.Source {
        
        private SampleSource(BaseFlowingFluid.Properties properties) {
            super(properties);
        }
    }
    
    private static final class SampleFlowing extends BaseFlowingFluid.Flowing {
        
        private SampleFlowing(BaseFlowingFluid.Properties properties) {
            super(properties);
        }
    }
    
    private static final class SampleBucket extends BucketItem {
        
        private SampleBucket(Fluid content, Item.Properties properties) {
            super(content, properties);
        }
    }
    
    private static final class SampleClient {
        
        private static FluidModel.Unbaked model() {
            return new FluidModel.Unbaked(
                    new Material(Identifier.withDefaultNamespace("block/water_still")),
                    new Material(Identifier.withDefaultNamespace("block/water_flow")),
                    new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                    FluidTintSources.water()
            );
        }
        
        private static IClientFluidTypeExtensions extensions() {
            return IClientFluidTypeExtensions.DEFAULT;
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            FluidBuilderSamples.plain = registrations.fluid("fluid_builder_samples_plain", "Plain Sample Fluid")
                    .typeProperties(properties -> properties.density(500))
                    .typeProperties(properties -> properties.density(2000))
                    .properties(properties -> properties.tickRate(10))
                    .properties(properties -> properties.tickRate(20))
                    .register();
            
            FluidBuilderSamples.full = registrations.fluid("fluid_builder_samples_full", "Full Sample Fluid")
                    .type(properties -> new FluidType(properties.lightLevel(7)))
                    .fluids(SampleSource::new, SampleFlowing::new)
                    .model(() -> {
                        FluidBuilderSamples.clientRequested = true;
                        return SampleClient::model;
                    })
                    .extensions(() -> {
                        FluidBuilderSamples.clientRequested = true;
                        return SampleClient::extensions;
                    })
                    .block()
                    .end()
                    .bucket(SampleBucket::new)
                    .end()
                    .register();
            
            PlainFluidBuilder<FluidType, BaseFlowingFluid.Source, BaseFlowingFluid.Flowing, Registrations> builder = registrations.fluid("fluid_builder_samples_with_item", "Sample Fluid With Item");
            FluidBuilderSamples.withItem = builder.block()
                    .item()
                    .end()
                    .end()
                    .register();
            FluidBuilderSamples.takenOver = builder;
        }
    }
}
