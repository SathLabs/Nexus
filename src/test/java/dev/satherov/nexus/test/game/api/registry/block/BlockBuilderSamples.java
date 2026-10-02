package dev.satherov.nexus.test.game.api.registry.block;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.block.BlockBuilder;
import dev.satherov.nexus.api.registry.block.BlockEntry;
import dev.satherov.nexus.api.registry.block.PlainBlockBuilder;
import dev.satherov.nexus.api.registry.block.PlainBlockEntry;
import dev.satherov.nexus.api.registry.item.ItemBuilder;
import dev.satherov.nexus.api.text.Translation;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

///
/// Checks the blocks and block items that block builders register, with their states, stacks, and translations.
///
public class BlockBuilderSamples {
    
    private static final List<Block> CALLBACKS = new ArrayList<>();
    
    private static @Nullable PlainBlockEntry<Block> plain;
    private static @Nullable BlockEntry<Block, BlockItem> withItem;
    private static @Nullable BlockEntry<RotatedPillarBlock, BlockItem> pillar;
    private static @Nullable BlockEntry<Block, BlockItem> editedItem;
    private static @Nullable PlainBlockBuilder<Block, Registrations> takenOver;
    private static @Nullable Throwable itemRefusal;
    private static boolean tintsRequested;
    
    @ServerTest
    public static void defaultStatesAreTheBlocksOwn(GameTestHelper helper) {
        PlainBlockEntry<Block> plain = BlockBuilderSamples.plain();
        BlockEntry<Block, BlockItem> withItem = BlockBuilderSamples.withItem();
        BlockEntry<RotatedPillarBlock, BlockItem> pillar = BlockBuilderSamples.pillar();
        helper.assertValueEqual(plain.getDefaultState(), plain.value().defaultBlockState(), "default state of the plain block");
        helper.assertValueEqual(withItem.getDefaultState(), withItem.value().defaultBlockState(), "default state of the block with an item");
        helper.assertValueEqual(pillar.getDefaultState(), pillar.value().defaultBlockState(), "default state of the pillar");
        helper.succeed();
    }
    
    @ServerTest
    public static void isMatchesEveryStateOfTheBlockOnly(GameTestHelper helper) {
        BlockEntry<RotatedPillarBlock, BlockItem> pillar = BlockBuilderSamples.pillar();
        helper.assertTrue(pillar.is(pillar.getDefaultState()), "The pillar did not match its default state");
        helper.assertTrue(pillar.is(pillar.getDefaultState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X)), "The pillar did not match its state along x");
        helper.assertFalse(pillar.is(BlockBuilderSamples.plain().getDefaultState()), "The pillar matched the plain block");
        helper.assertFalse(pillar.is(Blocks.STONE.defaultBlockState()), "The pillar matched stone");
        helper.succeed();
    }
    
    @ServerTest
    public static void appliesThePropertiesInCallOrder(GameTestHelper helper) {
        Block plain = BlockBuilderSamples.plain().value();
        helper.assertValueEqual(plain.defaultDestroyTime(), 3.0F, "destroy time");
        helper.assertValueEqual(plain.getFriction(), 0.5F, "friction");
        helper.succeed();
    }
    
    @ServerTest
    public static void translatesTheBlockUnderItsKey(GameTestHelper helper) {
        PlainBlockEntry<Block> plain = BlockBuilderSamples.plain();
        helper.assertValueEqual(plain.getTranslation().getKey(), "block.nexus.block_builder_samples_plain", "translation key");
        helper.assertValueEqual(plain.getTranslation().getEnglish(), "Plain Sample Block", "English name");
        helper.assertValueEqual(plain.value().getDescriptionId(), plain.getTranslation().getKey(), "description id of the block");
        helper.succeed();
    }
    
    @ServerTest
    public static void registersNoItemForAPlainBlock(GameTestHelper helper) {
        helper.assertValueEqual(BlockBuilderSamples.plain().value().asItem(), Items.AIR, "item of the plain block");
        helper.succeed();
    }
    
    @ServerTest
    public static void registersTheBlockItemUnderTheBlocksName(GameTestHelper helper) {
        BlockEntry<Block, BlockItem> withItem = BlockBuilderSamples.withItem();
        BlockItem item = withItem.asItem();
        helper.assertValueEqual(withItem.getItem().getId(), withItem.getId(), "identifier of the item");
        helper.assertValueEqual(item, withItem.getItem().value(), "item of the entry");
        helper.assertValueEqual(item.getBlock(), withItem.value(), "block of the item");
        helper.assertValueEqual(withItem.value().asItem(), item, "item of the block");
        helper.succeed();
    }
    
    @ServerTest
    public static void stacksHoldTheBlockItem(GameTestHelper helper) {
        BlockEntry<Block, BlockItem> withItem = BlockBuilderSamples.withItem();
        ItemStack one = withItem.stack();
        ItemStack five = withItem.stack(5);
        helper.assertTrue(one.is(withItem.asItem()), "The stack of one does not hold the block item");
        helper.assertValueEqual(one.getCount(), 1, "count of the stack of one");
        helper.assertTrue(five.is(withItem.asItem()), "The stack of five does not hold the block item");
        helper.assertValueEqual(five.getCount(), 5, "count of the stack of five");
        helper.succeed();
    }
    
    @ServerTest
    public static void translatesTheBlockItemAsTheBlock(GameTestHelper helper) {
        BlockEntry<Block, BlockItem> withItem = BlockBuilderSamples.withItem();
        List<String> keys = Nexus.getMod().getTranslations().getAll().stream().map(Translation::getKey).toList();
        helper.assertValueEqual(withItem.getItem().getTranslation(), withItem.getTranslation(), "translation of the item");
        helper.assertValueEqual(withItem.asItem().getDescriptionId(), "block.nexus.block_builder_samples_with_item", "description id of the item");
        helper.assertFalse(keys.contains("item.nexus.block_builder_samples_with_item"), "The block item declared a key of its own");
        helper.succeed();
    }
    
    @ServerTest
    public static void givesTheItemFactoryTheBlockAndItsFeatures(GameTestHelper helper) {
        BlockEntry<RotatedPillarBlock, BlockItem> pillar = BlockBuilderSamples.pillar();
        helper.assertValueEqual(pillar.asItem().getBlock(), pillar.value(), "block of the item");
        helper.assertValueEqual(pillar.stack().getMaxStackSize(), 16, "maximum stack size");
        helper.assertTrue(pillar.asItem().requiredFeatures().contains(FeatureFlags.REDSTONE_EXPERIMENTS), "The item does not require the features of the block");
        helper.succeed();
    }
    
    @ServerTest
    public static void appliesTheItemEditsAfterTheBlocksFeatures(GameTestHelper helper) {
        helper.assertTrue(BlockBuilderSamples.editedItem().asItem().requiredFeatures().contains(FeatureFlags.MINECART_IMPROVEMENTS), "The edit of the item properties was replaced");
        helper.succeed();
    }
    
    @ServerTest
    public static void keepsWhatWasRecordedBeforeTheItem(GameTestHelper helper) {
        BlockEntry<Block, BlockItem> withItem = BlockBuilderSamples.withItem();
        helper.assertValueEqual(List.copyOf(withItem.getTags()), List.of(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.NEEDS_STONE_TOOL), "tags");
        helper.assertValueEqual(BlockBuilderSamples.CALLBACKS, List.of(withItem.value()), "blocks the callbacks ran with");
        helper.succeed();
    }
    
    @ServerTest
    public static void refusesChangesToTheBlockBuilderAfterTheItem(GameTestHelper helper) {
        PlainBlockBuilder<Block, Registrations> builder = BlockBuilderSamples.takenOver();
        helper.assertTrue(Assertions.catchThrowable(() -> builder.properties(BlockBehaviour.Properties.of())) instanceof IllegalStateException, "The properties were replaced");
        helper.assertTrue(Assertions.catchThrowable(() -> builder.properties(properties -> properties)) instanceof IllegalStateException, "The properties were edited");
        helper.assertTrue(Assertions.catchThrowable(() -> builder.tint(List::of)) instanceof IllegalStateException, "The tint sources were replaced");
        helper.assertTrue(Assertions.catchThrowable(builder::item) instanceof IllegalStateException, "A second item was opened");
        helper.assertTrue(Assertions.catchThrowable(builder::register) instanceof IllegalStateException, "The block was registered a second time");
        helper.succeed();
    }
    
    @ServerTest
    public static void refusesToRegisterTheItemOnItsOwn(GameTestHelper helper) {
        Throwable refusal = BlockBuilderSamples.itemRefusal;
        helper.assertTrue(refusal instanceof IllegalStateException, "The item builder registered on its own");
        helper.assertValueEqual(Objects.requireNonNull(refusal).getMessage(), "'nexus:block_builder_samples_with_item' is registered by its parent through end().register()", "message");
        helper.succeed();
    }
    
    @ServerTest
    public static void neverRequestsTheTintsOnADedicatedServer(GameTestHelper helper) {
        helper.assertFalse(BlockBuilderSamples.tintsRequested, "The tint sources were requested on a dedicated server");
        helper.succeed();
    }
    
    private static PlainBlockEntry<Block> plain() {
        return Objects.requireNonNull(BlockBuilderSamples.plain);
    }
    
    private static BlockEntry<Block, BlockItem> withItem() {
        return Objects.requireNonNull(BlockBuilderSamples.withItem);
    }
    
    private static BlockEntry<RotatedPillarBlock, BlockItem> pillar() {
        return Objects.requireNonNull(BlockBuilderSamples.pillar);
    }
    
    private static BlockEntry<Block, BlockItem> editedItem() {
        return Objects.requireNonNull(BlockBuilderSamples.editedItem);
    }
    
    private static PlainBlockBuilder<Block, Registrations> takenOver() {
        return Objects.requireNonNull(BlockBuilderSamples.takenOver);
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            BlockBuilderSamples.plain = registrations.block("block_builder_samples_plain", "Plain Sample Block")
                    .properties(properties -> properties.strength(9.0F))
                    .properties(BlockBehaviour.Properties.of().strength(3.0F))
                    .properties(properties -> properties.friction(0.5F))
                    .tint(() -> {
                        BlockBuilderSamples.tintsRequested = true;
                        return List.of();
                    })
                    .register();
            
            PlainBlockBuilder<Block, Registrations> builder = registrations
                    .block("block_builder_samples_with_item", "Sample Block With Item")
                    .tag(BlockTags.MINEABLE_WITH_PICKAXE)
                    .onRegister(BlockBuilderSamples.CALLBACKS::add);
            ItemBuilder<BlockItem, BlockBuilder<Block, BlockItem, Registrations>> item = builder.item();
            BlockBuilderSamples.takenOver = builder;
            BlockBuilderSamples.itemRefusal = Assertions.catchThrowable(item::register);
            BlockBuilderSamples.withItem = item.end()
                    .tag(BlockTags.NEEDS_STONE_TOOL)
                    .register();
            
            BlockBuilderSamples.pillar = registrations.block("block_builder_samples_pillar", "Sample Pillar", RotatedPillarBlock::new)
                    .properties(properties -> properties.requiredFeatures(FeatureFlags.REDSTONE_EXPERIMENTS))
                    .item((block, properties) -> new BlockItem(block, properties.stacksTo(16)))
                    .end()
                    .register();
            
            BlockBuilderSamples.editedItem = registrations.block("block_builder_samples_edited_item", "Sample Block With Edited Item")
                    .properties(properties -> properties.requiredFeatures(FeatureFlags.REDSTONE_EXPERIMENTS))
                    .item()
                    .properties(properties -> properties.requiredFeatures(FeatureFlags.MINECART_IMPROVEMENTS))
                    .end()
                    .register();
        }
    }
}
