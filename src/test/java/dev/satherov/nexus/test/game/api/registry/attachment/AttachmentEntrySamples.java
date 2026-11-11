package dev.satherov.nexus.test.game.api.registry.attachment;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.api.registry.attachment.AttachmentEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.attachment.AttachmentHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// Checks that a block entity keeps an attachment with a format through a save and load, and that setting an attachment marks its chunk unsaved.
///
public class AttachmentEntrySamples {

    private static final StructCodec<Counter, Access.Plain> CODEC = NexusCodec.struct("counter", NexusCodec.INT.field("count", Counter::count), Counter::new);
    private static final Format<Counter> FORMAT = Format.of(AttachmentEntrySamples.CODEC).upgradeFrom(0, AttachmentEntrySamples::renameStacks);

    private static @Nullable AttachmentEntry<Counter> counter;

    @ServerTest
    public static void blockEntityKeepsTheFormattedValueThroughASaveAndLoad(GameTestHelper helper) {
        AttachmentEntry<Counter> counter = AttachmentEntrySamples.counter();
        BarrelBlockEntity barrel = AttachmentEntrySamples.placeBarrel(helper);
        counter.set(barrel, new Counter(5));
        CompoundTag saved = barrel.saveWithFullMetadata(helper.getLevel().registryAccess());
        CompoundTag expected = AttachmentEntrySamples.tag("count", 5);
        expected.putInt("version", 1);
        helper.assertValueEqual(saved.getCompoundOrEmpty(AttachmentHolder.ATTACHMENTS_NBT_KEY).get(counter.getId().toString()), expected, "saved attachment");

        BlockEntity loaded = AttachmentEntrySamples.load(helper, barrel, saved);
        helper.assertTrue(counter.has(loaded), "The loaded block entity has no value of the attachment");
        helper.assertValueEqual(counter.get(loaded), new Counter(5), "loaded attachment");
        helper.succeed();
    }

    @ServerTest
    public static void blockEntityLoadsAMapWithoutAVersionThroughTheSteps(GameTestHelper helper) {
        AttachmentEntry<Counter> counter = AttachmentEntrySamples.counter();
        BarrelBlockEntity barrel = AttachmentEntrySamples.placeBarrel(helper);
        CompoundTag attachments = new CompoundTag();
        attachments.put(counter.getId().toString(), AttachmentEntrySamples.tag("stacks", 5));
        CompoundTag saved = barrel.saveWithFullMetadata(helper.getLevel().registryAccess());
        saved.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);

        BlockEntity loaded = AttachmentEntrySamples.load(helper, barrel, saved);
        helper.assertTrue(counter.has(loaded), "The loaded block entity has no value of the attachment");
        helper.assertValueEqual(counter.get(loaded), new Counter(5), "attachment loaded from version 0");
        helper.succeed();
    }

    @ServerTest
    public static void setOnABlockEntityMarksItsChunkUnsaved(GameTestHelper helper) {
        BarrelBlockEntity barrel = AttachmentEntrySamples.placeBarrel(helper);
        LevelChunk chunk = helper.getLevel().getChunkAt(barrel.getBlockPos());
        chunk.tryMarkSaved();
        helper.assertFalse(chunk.isUnsaved(), "The chunk is unsaved before the attachment was set");

        AttachmentEntrySamples.counter().set(barrel, new Counter(5));
        helper.assertTrue(chunk.isUnsaved(), "Setting the attachment on the block entity left its chunk saved");
        helper.succeed();
    }

    @ServerTest
    public static void setOnAChunkMarksItUnsaved(GameTestHelper helper) {
        LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(0, 1, 0)));
        chunk.tryMarkSaved();
        helper.assertFalse(chunk.isUnsaved(), "The chunk is unsaved before the attachment was set");

        AttachmentEntrySamples.counter().set(chunk, new Counter(5));
        helper.assertTrue(chunk.isUnsaved(), "Setting the attachment on the chunk left it saved");
        helper.succeed();
    }

    private static AttachmentEntry<Counter> counter() {
        return Objects.requireNonNull(AttachmentEntrySamples.counter);
    }

    private static BarrelBlockEntity placeBarrel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, Blocks.BARREL);
        return helper.getBlockEntity(pos, BarrelBlockEntity.class);
    }

    private static BlockEntity load(GameTestHelper helper, BlockEntity saved, CompoundTag tag) {
        BlockEntity loaded = BlockEntity.loadStatic(saved.getBlockPos(), saved.getBlockState(), tag, helper.getLevel().registryAccess());
        helper.assertTrue(loaded != null && loaded != saved, "The saved block entity did not load into a fresh one");
        return Objects.requireNonNull(loaded);
    }

    private static CompoundTag tag(String key, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(key, count);
        return tag;
    }

    private static Tag renameStacks(Tag tag) {
        CompoundTag renamed = ((CompoundTag) tag).copy();
        renamed.put("count", Objects.requireNonNull(renamed.get("stacks")));
        renamed.remove("stacks");
        return renamed;
    }

    private record Counter(int count) { }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            AttachmentEntrySamples.counter = Nexus.getMod().getRegistrations().attachment("attachment_entry_samples_counter", () -> new Counter(0))
                    .format(AttachmentEntrySamples.FORMAT)
                    .register();
        }
    }
}
