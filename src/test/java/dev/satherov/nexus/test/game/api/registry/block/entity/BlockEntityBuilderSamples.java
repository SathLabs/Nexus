package dev.satherov.nexus.test.game.api.registry.block.entity;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.block.PlainBlockEntry;
import dev.satherov.nexus.api.registry.block.entity.BlockEntityEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.capabilities.BlockCapability;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// Checks the block entity types that block entity builders register, with their blocks, lookups, and capabilities.
///
public class BlockEntityBuilderSamples {
    
    private static final BlockCapability<SampleBlockEntity, @Nullable Void> CAPABILITY = BlockCapability.createVoid(
            Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "block_entity_builder_samples"),
            SampleBlockEntity.class
    );
    
    private static @Nullable PlainBlockEntry<SampleBlock> first;
    private static @Nullable PlainBlockEntry<SampleBlock> second;
    private static @Nullable BlockEntityEntry<SampleBlockEntity> sample;
    
    @ServerTest
    public static void isValidForEveryGivenBlockOnly(GameTestHelper helper) {
        BlockEntityType<SampleBlockEntity> type = BlockEntityBuilderSamples.sample().value();
        helper.assertTrue(type.isValid(BlockEntityBuilderSamples.first().getDefaultState()), "The type is not valid for the first block");
        helper.assertTrue(type.isValid(BlockEntityBuilderSamples.second().getDefaultState()), "The type is not valid for the second block");
        helper.assertFalse(type.isValid(Blocks.STONE.defaultBlockState()), "The type is valid for stone");
        helper.succeed();
    }
    
    @ServerTest
    public static void atFindsThePlacedBlockEntity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, BlockEntityBuilderSamples.first().value());
        SampleBlockEntity placed = helper.getBlockEntity(pos, SampleBlockEntity.class);
        helper.assertValueEqual(BlockEntityBuilderSamples.sample().at(helper.getLevel(), helper.absolutePos(pos)), placed, "block entity at the position");
        helper.succeed();
    }
    
    @ServerTest
    public static void atIsNullWithoutABlockEntityOfTheType(GameTestHelper helper) {
        BlockEntityEntry<SampleBlockEntity> sample = BlockEntityBuilderSamples.sample();
        BlockPos empty = new BlockPos(0, 1, 0);
        BlockPos chest = new BlockPos(1, 1, 0);
        helper.setBlock(empty, Blocks.AIR);
        helper.setBlock(chest, Blocks.CHEST);
        helper.assertTrue(sample.at(helper.getLevel(), helper.absolutePos(empty)) == null, "A block entity was found at an empty position");
        helper.assertTrue(sample.at(helper.getLevel(), helper.absolutePos(chest)) == null, "The chest was found as a block entity of the type");
        helper.succeed();
    }
    
    @ServerTest
    public static void isMatchesBlockEntitiesOfTheTypeOnly(GameTestHelper helper) {
        BlockEntityEntry<SampleBlockEntity> sample = BlockEntityBuilderSamples.sample();
        BlockPos pos = new BlockPos(0, 1, 0);
        BlockPos chest = new BlockPos(1, 1, 0);
        helper.setBlock(pos, BlockEntityBuilderSamples.second().value());
        helper.setBlock(chest, Blocks.CHEST);
        helper.assertTrue(sample.is(helper.getBlockEntity(pos, SampleBlockEntity.class)), "The type did not match its own block entity");
        helper.assertFalse(sample.is(helper.getBlockEntity(chest, ChestBlockEntity.class)), "The type matched the chest");
        helper.succeed();
    }
    
    @ServerTest
    public static void findsTheCapabilityOnEveryBlockEntityOfTheType(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = new BlockPos(0, 1, 0);
        BlockPos second = new BlockPos(1, 1, 0);
        helper.setBlock(first, BlockEntityBuilderSamples.first().value());
        helper.setBlock(second, BlockEntityBuilderSamples.second().value());
        helper.assertValueEqual(level.getCapability(BlockEntityBuilderSamples.CAPABILITY, helper.absolutePos(first)), helper.getBlockEntity(first, SampleBlockEntity.class), "capability on the first block");
        helper.assertValueEqual(level.getCapability(BlockEntityBuilderSamples.CAPABILITY, helper.absolutePos(second)), helper.getBlockEntity(second, SampleBlockEntity.class), "capability on the second block");
        helper.succeed();
    }
    
    private static PlainBlockEntry<SampleBlock> first() {
        return Objects.requireNonNull(BlockEntityBuilderSamples.first);
    }
    
    private static PlainBlockEntry<SampleBlock> second() {
        return Objects.requireNonNull(BlockEntityBuilderSamples.second);
    }
    
    private static BlockEntityEntry<SampleBlockEntity> sample() {
        return Objects.requireNonNull(BlockEntityBuilderSamples.sample);
    }
    
    public static final class SampleBlock extends Block implements EntityBlock {
        
        public SampleBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }
        
        @Override
        public SampleBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new SampleBlockEntity(pos, state);
        }
    }
    
    public static final class SampleBlockEntity extends BlockEntity {
        
        public SampleBlockEntity(BlockPos pos, BlockState state) {
            super(BlockEntityBuilderSamples.sample().value(), pos, state);
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            PlainBlockEntry<SampleBlock> first = registrations.block("block_entity_builder_samples_first", "First Sample Block Entity Block", SampleBlock::new).register();
            PlainBlockEntry<SampleBlock> second = registrations.block("block_entity_builder_samples_second", "Second Sample Block Entity Block", SampleBlock::new).register();
            BlockEntityBuilderSamples.first = first;
            BlockEntityBuilderSamples.second = second;
            BlockEntityBuilderSamples.sample = registrations.blockEntity("block_entity_builder_samples", SampleBlockEntity::new)
                    .blocks(first)
                    .blocks(second)
                    .capability(BlockEntityBuilderSamples.CAPABILITY, (entity, _) -> entity)
                    .register();
        }
    }
}
