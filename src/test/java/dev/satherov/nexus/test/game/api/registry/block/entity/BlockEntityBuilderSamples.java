package dev.satherov.nexus.test.game.api.registry.block.entity;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.block.PlainBlockEntry;
import dev.satherov.nexus.api.registry.block.entity.BlockEntityEntry;
import dev.satherov.nexus.api.tick.ticker.Next;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

///
/// Checks the block entity types that block entity builders register, with their blocks, lookups, capabilities, and server tickers.
///
public class BlockEntityBuilderSamples {

    private static final BlockCapability<SampleBlockEntity, @Nullable Void> CAPABILITY = BlockCapability.createVoid(
            Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "block_entity_builder_samples"),
            SampleBlockEntity.class
    );

    private static @Nullable PlainBlockEntry<SampleBlock> first;
    private static @Nullable PlainBlockEntry<SampleBlock> second;
    private static @Nullable BlockEntityEntry<SampleBlockEntity> sample;
    private static @Nullable PlainBlockEntry<TickingBlock> tickingBlock;
    private static @Nullable BlockEntityEntry<TickingBlockEntity> ticking;

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

    @ServerTest
    public static void tickerBindsOnLoadAndSleeps(GameTestHelper helper) {
        TickingBlockEntity entity = BlockEntityBuilderSamples.placeTicking(helper, new BlockPos(0, 1, 0));
        helper.runAfterDelay(1L, () -> helper.assertValueEqual(entity.elapsed, List.of(1), "elapsed passes of the ticks after one pass"));
        helper.runAfterDelay(4L, () -> {
            helper.assertValueEqual(entity.elapsed, List.of(1), "elapsed passes of the ticks after four passes");
            helper.succeed();
        });
    }

    @ServerTest
    public static void wakeTicksTheBoundTicker(GameTestHelper helper) {
        TickingBlockEntity entity = BlockEntityBuilderSamples.placeTicking(helper, new BlockPos(0, 1, 0));
        helper.runAfterDelay(3L, () -> {
            helper.assertValueEqual(entity.elapsed, List.of(1), "elapsed passes of the ticks after three passes asleep");
            Nexus.getMod().getScheduler().wake(entity);
        });
        helper.runAfterDelay(4L, () -> helper.assertValueEqual(entity.elapsed, List.of(1, 3), "elapsed passes of the ticks after the wake"));
        helper.runAfterDelay(6L, () -> {
            helper.assertValueEqual(entity.elapsed, List.of(1, 3), "elapsed passes of the ticks after it slept again");
            helper.succeed();
        });
    }

    @ServerTest
    public static void breakingTheBlockDropsTheTicker(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        TickingBlockEntity entity = BlockEntityBuilderSamples.placeTicking(helper, pos);
        entity.next = Next.TICK;
        helper.runAfterDelay(2L, () -> {
            helper.assertValueEqual(entity.elapsed, List.of(1, 1), "elapsed passes of the ticks after two passes");
            helper.destroyBlock(pos);
        });
        helper.runAfterDelay(5L, () -> {
            helper.assertValueEqual(entity.elapsed, List.of(1, 1), "elapsed passes of the ticks after the block was broken");
            helper.succeed();
        });
    }

    @ServerTest
    public static void secondTickerOnTheBlockEntityThrows(GameTestHelper helper) {
        TickingBlockEntity entity = BlockEntityBuilderSamples.placeTicking(helper, new BlockPos(0, 1, 0));
        helper.runAfterDelay(1L, () -> {
            helper.assertValueEqual(entity.elapsed, List.of(1), "elapsed passes of the ticks after one pass");

            boolean threw = false;
            try {
                Nexus.getMod().getScheduler().in(helper.getLevel()).ticker(entity, _ -> Next.SLEEP).cancel();
            } catch (IllegalStateException _) {
                threw = true;
            }

            helper.assertTrue(threw, "A second ticker was bound to the block entity");
            helper.succeed();
        });
    }

    private static TickingBlockEntity placeTicking(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BlockEntityBuilderSamples.tickingBlock().value());
        return helper.getBlockEntity(pos, TickingBlockEntity.class);
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

    private static PlainBlockEntry<TickingBlock> tickingBlock() {
        return Objects.requireNonNull(BlockEntityBuilderSamples.tickingBlock);
    }

    private static BlockEntityEntry<TickingBlockEntity> ticking() {
        return Objects.requireNonNull(BlockEntityBuilderSamples.ticking);
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

    public static final class TickingBlock extends Block implements EntityBlock {

        public TickingBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }

        @Override
        public TickingBlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TickingBlockEntity(pos, state);
        }
    }

    public static final class TickingBlockEntity extends BlockEntity {

        private final List<Integer> elapsed = new ArrayList<>();
        private Next next = Next.SLEEP;

        public TickingBlockEntity(BlockPos pos, BlockState state) {
            super(BlockEntityBuilderSamples.ticking().value(), pos, state);
        }

        private Next tick(int elapsed) {
            this.elapsed.add(elapsed);
            return this.next;
        }
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            PlainBlockEntry<SampleBlock> first = registrations.block("block_entity_builder_samples_first", "First Sample Block Entity Block", SampleBlock::new).register();
            PlainBlockEntry<SampleBlock> second = registrations.block("block_entity_builder_samples_second", "Second Sample Block Entity Block", SampleBlock::new).register();
            PlainBlockEntry<TickingBlock> tickingBlock = registrations.block("block_entity_builder_samples_ticking_block", "Ticking Sample Block Entity Block", TickingBlock::new).register();
            BlockEntityBuilderSamples.first = first;
            BlockEntityBuilderSamples.second = second;
            BlockEntityBuilderSamples.tickingBlock = tickingBlock;
            BlockEntityBuilderSamples.sample = registrations.blockEntity("block_entity_builder_samples", SampleBlockEntity::new)
                    .blocks(first)
                    .blocks(second)
                    .capability(BlockEntityBuilderSamples.CAPABILITY, (entity, _) -> entity)
                    .register();
            BlockEntityBuilderSamples.ticking = registrations.blockEntity("block_entity_builder_samples_ticking", TickingBlockEntity::new)
                    .blocks(tickingBlock)
                    .serverTicker(TickingBlockEntity::tick)
                    .register();
        }
    }
}
