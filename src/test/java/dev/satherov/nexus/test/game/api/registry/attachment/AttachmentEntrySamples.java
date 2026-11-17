package dev.satherov.nexus.test.game.api.registry.attachment;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.attachment.AttachmentEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.attachment.AttachmentSync;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.payload.SyncAttachmentsPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import com.mojang.authlib.GameProfile;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

///
/// Checks that a block entity keeps an attachment with a format through a save and load, that setting or updating an attachment marks its chunk unsaved,
/// that a chunk loads and saves its other attachments beside a value the codec refuses, and that attachments are copied on death and synced as registered.
///
public class AttachmentEntrySamples {

    private static final StructCodec<Counter, Access.Plain> CODEC = NexusCodec.struct("counter", NexusCodec.INT.field("count", Counter::count), Counter::new);
    private static final StructCodec<Counter, Access.Plain> CHECKED = NexusCodec.struct(
            "checked",
            NexusCodec.INT.validate(count -> count < 0 ? "The count is negative" : null).field("count", Counter::count),
            Counter::new
    );
    
    private static final Format<Counter> FORMAT = Format.of(AttachmentEntrySamples.CODEC).upgradeFrom(0, AttachmentEntrySamples::renameStacks);
    private static final String BLOCK_ENTITIES_TAG = "block_entities";
    private static final GameProfile TRACKER = new GameProfile(UUID.fromString("5e6c1d2a-8b3f-4c7e-9a41-2f0d7b6c3e18"), "[Nexus Tracker]");

    private static @Nullable AttachmentEntry<Counter> counter;
    private static @Nullable AttachmentEntry<Counter> copied;
    private static @Nullable AttachmentEntry<Counter> checked;
    private static @Nullable AttachmentEntry<List<Integer>> list;
    private static @Nullable AttachmentEntry<Unit> marker;

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
    public static void blockEntityWritesTheFormattedValueWithItsChunk(GameTestHelper helper) {
        AttachmentEntry<Counter> counter = AttachmentEntrySamples.counter();
        BarrelBlockEntity barrel = AttachmentEntrySamples.placeBarrel(helper);
        counter.set(barrel, new Counter(5));
        ServerChunkCache chunks = helper.getLevel().getChunkSource();
        chunks.save(true);

        ChunkPos pos = ChunkPos.containing(barrel.getBlockPos());
        CompoundTag chunk = chunks.chunkMap.read(pos)
                .join()
                .orElseThrow(() -> helper.assertionException(Component.literal("The saved chunk has no tag in the region storage")));
        CompoundTag written = chunk.getListOrEmpty(AttachmentEntrySamples.BLOCK_ENTITIES_TAG)
                .compoundStream()
                .filter(tag -> BlockEntity.getPosFromTag(pos, tag).equals(barrel.getBlockPos()))
                .findFirst()
                .orElseThrow(() -> helper.assertionException(Component.literal("The saved chunk holds no tag of the barrel")));
        CompoundTag expected = AttachmentEntrySamples.tag("count", 5);
        expected.putInt("version", 1);
        helper.assertValueEqual(written.getCompoundOrEmpty(AttachmentHolder.ATTACHMENTS_NBT_KEY).get(counter.getId().toString()), expected, "attachment in the saved chunk");
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

    @ServerTest
    public static void updateInPlaceOnAChunkMarksItUnsaved(GameTestHelper helper) {
        AttachmentEntry<List<Integer>> list = AttachmentEntrySamples.list();
        LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(0, 1, 0)));
        List<Integer> current = list.get(chunk);
        chunk.tryMarkSaved();
        helper.assertFalse(chunk.isUnsaved(), "The chunk is unsaved before the attachment was updated");
        
        helper.assertTrue(list.update(chunk, AttachmentEntrySamples::addOne) == current, "Updating the attachment in place did not set it again");
        helper.assertTrue(chunk.isUnsaved(), "Updating the attachment in place left the chunk saved");
        helper.succeed();
    }
    
    @ServerTest
    public static void updateInPlaceOnABlockEntityMarksItsChunkUnsaved(GameTestHelper helper) {
        AttachmentEntry<List<Integer>> list = AttachmentEntrySamples.list();
        BarrelBlockEntity barrel = AttachmentEntrySamples.placeBarrel(helper);
        LevelChunk chunk = helper.getLevel().getChunkAt(barrel.getBlockPos());
        List<Integer> current = list.get(barrel);
        chunk.tryMarkSaved();
        helper.assertFalse(chunk.isUnsaved(), "The chunk is unsaved before the attachment was updated");
        
        helper.assertTrue(list.update(barrel, AttachmentEntrySamples::addOne) == current, "Updating the attachment in place did not set it again");
        helper.assertTrue(chunk.isUnsaved(), "Updating the attachment in place on the block entity left its chunk saved");
        helper.succeed();
    }
    
    @ServerTest
    public static void chunkLoadsTheOtherAttachmentsBesideOneTheCodecRefuses(GameTestHelper helper) {
        AttachmentEntry<Counter> checked = AttachmentEntrySamples.checked();
        AttachmentEntry<Counter> copied = AttachmentEntrySamples.copied();
        CompoundTag saved = new CompoundTag();
        saved.put(checked.getId().toString(), AttachmentEntrySamples.tag("count", -1));
        saved.put(copied.getId().toString(), AttachmentEntrySamples.tag("count", 5));
        LevelChunk chunk = AttachmentEntrySamples.detachedChunk(helper);
        chunk.readAttachmentsFromNBT(helper.getLevel().registryAccess(), saved);
        helper.assertFalse(checked.has(chunk), "The chunk loaded a value the codec refuses");
        helper.assertValueEqual(copied.get(chunk), new Counter(5), "attachment loaded beside the refused one");
        helper.succeed();
    }
    
    @ServerTest
    public static void chunkSavesTheOtherAttachmentsBesideOneTheCodecCannotWrite(GameTestHelper helper) {
        AttachmentEntry<Counter> checked = AttachmentEntrySamples.checked();
        AttachmentEntry<Counter> copied = AttachmentEntrySamples.copied();
        LevelChunk chunk = AttachmentEntrySamples.detachedChunk(helper);
        checked.set(chunk, new Counter(-1));
        copied.set(chunk, new Counter(5));
        CompoundTag saved = chunk.writeAttachmentsToNBT(helper.getLevel().registryAccess());
        helper.assertTrue(saved != null, "The chunk saved none of its attachments");
        CompoundTag written = Objects.requireNonNull(saved);
        helper.assertFalse(written.contains(checked.getId().toString()), "The chunk saved a value the codec cannot write");
        helper.assertValueEqual(written.get(copied.getId().toString()), AttachmentEntrySamples.tag("count", 5), "attachment saved beside the unwritable one");
        helper.succeed();
    }
    
    @ServerTest
    public static void deathCopyKeepsOnlyTheAttachmentsCopiedOnDeath(GameTestHelper helper) {
        AttachmentEntry<Counter> copied = AttachmentEntrySamples.copied();
        AttachmentEntry<Counter> counter = AttachmentEntrySamples.counter();
        ArmorStand from = AttachmentEntrySamples.armorStand(helper);
        copied.set(from, new Counter(5));
        counter.set(from, new Counter(5));
        ArmorStand to = AttachmentEntrySamples.armorStand(helper);
        to.copyAttachmentsFrom(from, true);
        helper.assertTrue(copied.has(to), "The death copy left out the attachment that is copied on death");
        helper.assertValueEqual(copied.get(to), new Counter(5), "attachment copied on death");
        helper.assertFalse(counter.has(to), "The death copy kept an attachment that is not copied on death");
        helper.succeed();
    }
    
    @ServerTest
    public static void initialSyncSendsAnAttachmentThatWritesNoBytes(GameTestHelper helper) {
        AttachmentEntry<Unit> marker = AttachmentEntrySamples.marker();
        ArmorStand tracked = AttachmentEntrySamples.armorStand(helper);
        marker.set(tracked, Unit.INSTANCE);
        List<Packet<?>> packets = new ArrayList<>();
        AttachmentSync.syncInitialEntityAttachments(tracked, AttachmentEntrySamples.tracker(helper), packets::add);
        if (packets.size() != 1 || !(packets.getFirst() instanceof ClientboundCustomPayloadPacket(SyncAttachmentsPayload payload))) {
            throw helper.assertionException(Component.literal("The initial sync sent no payload of attachments"));
        }
        
        helper.assertTrue(payload.types().contains(marker.value()), "The initial sync left out the attachment whose codec writes no bytes");
        ArmorStand received = AttachmentEntrySamples.armorStand(helper);
        AttachmentSync.receiveSyncedDataAttachments(received, helper.getLevel().registryAccess(), payload.types(), payload.syncPayload());
        helper.assertTrue(marker.has(received), "The entity that received the initial sync has no value of the attachment");
        helper.succeed();
    }
    
    private static AttachmentEntry<Counter> counter() {
        return Objects.requireNonNull(AttachmentEntrySamples.counter);
    }
    
    private static AttachmentEntry<Counter> copied() {
        return Objects.requireNonNull(AttachmentEntrySamples.copied);
    }
    
    private static AttachmentEntry<Counter> checked() {
        return Objects.requireNonNull(AttachmentEntrySamples.checked);
    }
    
    private static AttachmentEntry<List<Integer>> list() {
        return Objects.requireNonNull(AttachmentEntrySamples.list);
    }
    
    private static AttachmentEntry<Unit> marker() {
        return Objects.requireNonNull(AttachmentEntrySamples.marker);
    }
    
    private static LevelChunk detachedChunk(GameTestHelper helper) {
        return new LevelChunk(helper.getLevel(), ChunkPos.containing(helper.absolutePos(new BlockPos(0, 1, 0))));
    }
    
    private static ArmorStand armorStand(GameTestHelper helper) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        helper.assertTrue(stand != null, "The armor stand could not be created");
        return Objects.requireNonNull(stand);
    }
    
    private static ServerPlayer tracker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, AttachmentEntrySamples.TRACKER);
        player.connection = new ServerGamePacketListenerImpl(
                level.getServer(),
                new Connection(PacketFlow.SERVERBOUND),
                player,
                CommonListenerCookie.createInitial(AttachmentEntrySamples.TRACKER, false)
        ) {
            
            @Override
            public boolean hasChannel(Identifier payloadId) {
                return true;
            }
        };
        return player;
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

    private static List<Integer> addOne(List<Integer> values) {
        values.add(1);
        return values;
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
            Registrations registrations = Nexus.getMod().getRegistrations();
            AttachmentEntrySamples.counter = registrations.attachment("attachment_entry_samples_counter", () -> new Counter(0))
                    .format(AttachmentEntrySamples.FORMAT)
                    .register();
            AttachmentEntrySamples.copied = registrations.attachment("attachment_entry_samples_copied", () -> new Counter(0))
                    .codec(AttachmentEntrySamples.CODEC)
                    .copyOnDeath()
                    .register();
            AttachmentEntrySamples.checked = registrations.attachment("attachment_entry_samples_checked", () -> new Counter(0))
                    .codec(AttachmentEntrySamples.CHECKED)
                    .register();
            AttachmentEntrySamples.list = registrations.<List<Integer>>attachment("attachment_entry_samples_list", ArrayList::new).register();
            AttachmentEntrySamples.marker = registrations.attachment("attachment_entry_samples_marker", () -> Unit.INSTANCE)
                    .sync(NexusCodec.unit(Unit.INSTANCE))
                    .register();
        }
    }
}
