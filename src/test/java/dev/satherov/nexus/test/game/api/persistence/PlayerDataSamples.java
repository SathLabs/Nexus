package dev.satherov.nexus.test.game.api.persistence;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.persistence.PlayerData;
import dev.satherov.nexus.api.registry.attachment.AttachmentEntry;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

///
/// Checks the edits, inspections and replacements of the player data, on a joined player and on the file of an offline player.
///
public class PlayerDataSamples {

    private static final StructCodec<Counter, Access.Plain> CODEC = NexusCodec.struct("counter", NexusCodec.INT.field("count", Counter::count), Counter::new);
    private static final Vec3 SAVED_POSITION = new Vec3(12.5D, 70.0D, -3.5D);
    private static final double POSITION_TOLERANCE = 0.01D;
    private static final String ROOT_VEHICLE_TAG = "RootVehicle";
    private static final String POSITION_TAG = "Pos";
    private static final float REPLACED_HEALTH = 7.0F;
    private static final int REPLACED_DIAMONDS = 3;
    private static final int FOOD_LEVEL_BELOW_REGENERATION = 17;
    private static final Set<UUID> LOADED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SAVED = ConcurrentHashMap.newKeySet();
    private static final List<Player> LOGGED_IN = new CopyOnWriteArrayList<>();
    private static final List<Player> LOGGED_OUT = new CopyOnWriteArrayList<>();
    private static final List<Player> CLONED = new CopyOnWriteArrayList<>();
    private static final List<Player> RESPAWNED = new CopyOnWriteArrayList<>();
    private static final String MISSING_DIMENSION = "nexus:player_data_samples_missing";
    private static final int VERSION_BEFORE_EQUIPMENT = 4300;
    private static final byte FEET_SLOT_BEFORE_EQUIPMENT = 100;
    private static final int LIVE_COUNT = 3;
    private static final int REPLACED_COUNT = 9;
    private static final Set<UUID> FAILING_SAVES = ConcurrentHashMap.newKeySet();
    private static final List<AdvancementEvent.AdvancementProgressEvent> PROGRESSED = new CopyOnWriteArrayList<>();

    private static @Nullable AttachmentEntry<Counter> counter;

    private static AttachmentEntry<Counter> counter() {
        return Objects.requireNonNull(PlayerDataSamples.counter);
    }

    private static PlayerData playerData() {
        return Nexus.getMod().getPersistence().getPlayerData();
    }

    private static CompoundTag counterTag(int count) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("count", count);
        return tag;
    }

    private static CompoundTag playerTag(int count) {
        ListTag position = new ListTag();
        position.add(DoubleTag.valueOf(PlayerDataSamples.SAVED_POSITION.x));
        position.add(DoubleTag.valueOf(PlayerDataSamples.SAVED_POSITION.y));
        position.add(DoubleTag.valueOf(PlayerDataSamples.SAVED_POSITION.z));
        String nether = Level.NETHER.identifier().toString();

        CompoundTag pearl = new CompoundTag();
        pearl.putString("id", "minecraft:ender_pearl");
        pearl.put(PlayerDataSamples.POSITION_TAG, position.copy());
        pearl.putString(ServerPlayer.ENDER_PEARL_DIMENSION_TAG, nether);
        ListTag pearls = new ListTag();
        pearls.add(pearl);

        IntArrayTag vehicleId = new IntArrayTag(UUIDUtil.uuidToIntArray(UUID.randomUUID()));
        CompoundTag boat = new CompoundTag();
        boat.putString("id", "minecraft:oak_boat");
        boat.put("UUID", vehicleId.copy());
        boat.put(PlayerDataSamples.POSITION_TAG, position.copy());
        CompoundTag vehicle = new CompoundTag();
        vehicle.put("Attach", vehicleId);
        vehicle.put("Entity", boat);

        CompoundTag attachments = new CompoundTag();
        attachments.put(PlayerDataSamples.counter().getId().toString(), PlayerDataSamples.counterTag(count));

        CompoundTag tag = new CompoundTag();
        tag.putString(ServerPlayer.TAG_DIMENSION, nether);
        tag.put(PlayerDataSamples.POSITION_TAG, position);
        tag.put(ServerPlayer.ENDER_PEARLS_TAG, pearls);
        tag.put(PlayerDataSamples.ROOT_VEHICLE_TAG, vehicle);
        tag.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
        return NbtUtils.addCurrentDataVersion(tag);
    }

    private static CompoundTag oldPlayerTag() {
        CompoundTag boots = new CompoundTag();
        boots.putByte("Slot", PlayerDataSamples.FEET_SLOT_BEFORE_EQUIPMENT);
        boots.putString("id", "minecraft:diamond_boots");
        boots.putInt("count", 1);
        ListTag inventory = new ListTag();
        inventory.add(boots);

        CompoundTag tag = new CompoundTag();
        tag.put("Inventory", inventory);
        return NbtUtils.addDataVersion(tag, PlayerDataSamples.VERSION_BEFORE_EQUIPMENT);
    }

    private static Path write(MinecraftServer server, UUID player, CompoundTag tag) throws IOException {
        Path file = server.getPlayerList().getPlayerIo().getPlayerDir().toPath().resolve(player + ".dat");
        NbtIo.writeCompressed(tag, file);
        return file;
    }

    private static CompoundTag read(Path file) throws IOException {
        return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
    }

    private static <RESULT> RESULT onServer(Client client, Supplier<RESULT> action) {
        CompletableFuture<RESULT> result = client.serverLevel().getServer().submit(action);
        client.until("the server to run the call", result::isDone);
        return result.join();
    }

    private static void runOnServer(Client client, Runnable action) {
        CompletableFuture<Void> result = client.serverLevel().getServer().submit(action);
        client.until("the server to run the call", result::isDone);
        result.join();
    }

    private static List<Entity> entitiesWithUuid(MinecraftServer server, UUID uuid) {
        return StreamSupport.stream(server.getAllLevels().spliterator(), false)
                .map(level -> level.getEntity(uuid))
                .filter(Objects::nonNull)
                .toList();
    }

    private static ChestBoat rideChestBoat(ServerPlayer player) {
        ChestBoat boat = Objects.requireNonNull(EntityType.OAK_CHEST_BOAT.create(player.level(), EntitySpawnReason.COMMAND));
        boat.snapTo(player.position());
        boat.setItem(0, new ItemStack(Items.DIAMOND));
        player.level().addFreshEntity(boat);
        Assertions.assertThat(player.startRiding(boat, true, true)).isTrue();
        return boat;
    }

    private static CompoundTag vehicleTag(Entity vehicle) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, vehicle.registryAccess());
        Assertions.assertThat(vehicle.save(output)).isTrue();
        CompoundTag tag = new CompoundTag();
        tag.put("Attach", new IntArrayTag(UUIDUtil.uuidToIntArray(vehicle.getUUID())));
        tag.put("Entity", output.buildResult());
        return tag;
    }

    private static AdvancementHolder advancement(MinecraftServer server) {
        return Objects.requireNonNull(server.getAdvancements().get(Identifier.withDefaultNamespace("story/root")));
    }

    private static String criterion(AdvancementHolder advancement) {
        return advancement.value().criteria().keySet().iterator().next();
    }

    private static List<Player> progressed(AdvancementHolder advancement, UUID player) {
        return PlayerDataSamples.PROGRESSED.stream()
                .filter(event -> event.getAdvancement().equals(advancement) && event.getEntity().getUUID().equals(player))
                .map(PlayerEvent::getEntity)
                .toList();
    }

    @ServerTest
    public static void editKeepsPearlsVehicleAndDimensionAndSavesTheAttachment(GameTestHelper helper) throws IOException {
        AttachmentEntry<Counter> counter = PlayerDataSamples.counter();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        UUID uuid = UUID.randomUUID();
        CompoundTag written = PlayerDataSamples.playerTag(1);
        Path file = PlayerDataSamples.write(server, uuid, written);
        AtomicReference<@Nullable ServerPlayer> edited = new AtomicReference<>();
        boolean known = data.edit(server, uuid, player -> {
            helper.assertFalse(player instanceof FakePlayer, "The detached copy is a fake player");
            helper.assertTrue(server.getPlayerList().getPlayer(uuid) == null, "The detached copy is in the player list");
            helper.assertTrue(
                    StreamSupport.stream(server.getAllLevels().spliterator(), false).allMatch(level -> level.getEntity(uuid) == null),
                    "The detached copy is in the entity list of a level"
            );
            helper.assertValueEqual(player.level().dimension(), Level.NETHER, "dimension of the detached copy");
            player.setServerLevel(server.overworld());
            player.snapTo(player.position().add(16.0, 0.0, 16.0));
            counter.set(player, new Counter(2));
            edited.set(player);
        });
        
        helper.assertTrue(known, "The edit of a player with a file returned false");

        CompoundTag saved = PlayerDataSamples.read(file);
        for (String key : List.of(ServerPlayer.ENDER_PEARLS_TAG, PlayerDataSamples.ROOT_VEHICLE_TAG, ServerPlayer.TAG_DIMENSION, PlayerDataSamples.POSITION_TAG)) {
            helper.assertValueEqual(saved.get(key), written.get(key), key + " in the saved file");
        }

        helper.assertValueEqual(saved.getCompoundOrEmpty(AttachmentHolder.ATTACHMENTS_NBT_KEY).get(counter.getId().toString()), PlayerDataSamples.counterTag(2), "attachment in the saved file");
        helper.assertTrue(PlayerDataSamples.LOADED.contains(uuid), "The edit did not fire the load from file event");
        helper.assertTrue(PlayerDataSamples.SAVED.contains(uuid), "The edit did not fire the save to file event");
        helper.assertFalse(server.getPlayerList().advancements.containsKey(uuid), "The player list kept the advancements of the detached copy");

        ServerPlayer copy = Objects.requireNonNull(edited.get());
        ServerPlayer later = data.inspect(server, saved, Function.identity());
        helper.assertTrue(later.getStats() != copy.getStats(), "The player list kept the stats of the detached copy");
        helper.succeed();
    }

    @ServerTest
    public static void editOfAnUnknownPlayerDoesNotRun(GameTestHelper helper) {
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        UUID unknown = UUID.randomUUID();
        AtomicBoolean ran = new AtomicBoolean();
        helper.assertFalse(data.edit(server, unknown, _ -> ran.set(true)), "The edit of a player without a file returned true");
        helper.assertFalse(ran.get(), "The action ran for a player without a file");
        helper.assertTrue(data.read(server, unknown).isEmpty(), "A tag was read for a player without a file");
        helper.succeed();
    }

    @ServerTest
    public static void throwingEditLeavesTheFile(GameTestHelper helper) throws IOException {
        AttachmentEntry<Counter> counter = PlayerDataSamples.counter();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        UUID uuid = UUID.randomUUID();
        Path file = PlayerDataSamples.write(server, uuid, PlayerDataSamples.playerTag(1));
        byte[] written = Files.readAllBytes(file);
        IllegalStateException failure = new IllegalStateException("thrown by the action of the edit");
        Throwable thrown = Assertions.catchThrowable(() -> data.edit(server, uuid, player -> {
            counter.set(player, new Counter(3));
            throw failure;
        }));
        
        helper.assertTrue(thrown == failure, "The edit did not rethrow the exception of the action");
        helper.assertTrue(Arrays.equals(Files.readAllBytes(file), written), "The edit changed the file although the action threw");
        helper.succeed();
    }

    @ServerTest
    public static void inspectLeavesTheFileAndReturnsTheResult(GameTestHelper helper) throws IOException {
        AttachmentEntry<Counter> counter = PlayerDataSamples.counter();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        UUID uuid = UUID.randomUUID();
        Path file = PlayerDataSamples.write(server, uuid, PlayerDataSamples.playerTag(1));
        byte[] written = Files.readAllBytes(file);
        CompoundTag tag = data.read(server, uuid).orElseThrow();
        int result = data.inspect(server, tag, player -> {
            counter.set(player, new Counter(counter.get(player).count() + 5));
            return counter.get(player).count();
        });
        
        helper.assertValueEqual(result, 6, "result of the inspection");
        helper.assertTrue(Arrays.equals(Files.readAllBytes(file), written), "The inspection changed the file");
        helper.succeed();
    }

    @ServerTest
    public static void replaceSwapsTheFileOfAnOfflinePlayer(GameTestHelper helper) throws IOException {
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        UUID uuid = UUID.randomUUID();
        Path file = PlayerDataSamples.write(server, uuid, PlayerDataSamples.playerTag(1));
        data.replace(server, uuid, PlayerDataSamples.playerTag(4));
        CompoundTag attachments = PlayerDataSamples.read(file).getCompoundOrEmpty(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        helper.assertValueEqual(attachments.get(PlayerDataSamples.counter().getId().toString()), PlayerDataSamples.counterTag(4), "attachment in the replaced file");
        helper.succeed();
    }

    @ServerTest
    public static void editKeepsTheDimensionOfALevelThatIsGone(GameTestHelper helper) throws IOException {
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        UUID uuid = UUID.randomUUID();
        CompoundTag written = PlayerDataSamples.playerTag(1);
        written.putString(ServerPlayer.TAG_DIMENSION, PlayerDataSamples.MISSING_DIMENSION);
        Path file = PlayerDataSamples.write(server, uuid, written);
        data.edit(server, uuid, player -> helper.assertValueEqual(player.level().dimension(), Level.OVERWORLD, "dimension of the detached copy"));
        helper.assertValueEqual(PlayerDataSamples.read(file).get(ServerPlayer.TAG_DIMENSION), written.get(ServerPlayer.TAG_DIMENSION), "dimension in the saved file");
        helper.succeed();
    }

    @ServerTest
    public static void nestedEditOfOneOfflinePlayerKeepsBothChanges(GameTestHelper helper) throws IOException {
        AttachmentEntry<Counter> counter = PlayerDataSamples.counter();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        UUID uuid = UUID.randomUUID();
        Path file = PlayerDataSamples.write(server, uuid, PlayerDataSamples.playerTag(0));

        // Every mod has player data of its own, so the inner edit goes through another one.
        PlayerData other = new PlayerData();
        data.edit(server, uuid, outer -> {
            counter.set(outer, new Counter(1));
            other.edit(server, uuid, inner -> counter.set(inner, new Counter(counter.get(inner).count() + 10)));
        });

        CompoundTag attachments = PlayerDataSamples.read(file).getCompoundOrEmpty(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        helper.assertValueEqual(attachments.get(counter.getId().toString()), PlayerDataSamples.counterTag(11), "attachment in the saved file");
        helper.succeed();
    }

    @ServerTest
    public static void editThrowsWhenTheFileCannotBeReplaced(GameTestHelper helper) throws IOException {
        AttachmentEntry<Counter> counter = PlayerDataSamples.counter();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        UUID uuid = UUID.randomUUID();
        Path file = PlayerDataSamples.write(server, uuid, PlayerDataSamples.playerTag(1));
        byte[] written = Files.readAllBytes(file);
        Files.createDirectories(file.resolveSibling(uuid + ".dat_old").resolve("blocker"));
        Throwable thrown = Assertions.catchThrowable(() -> data.edit(server, uuid, player -> counter.set(player, new Counter(2))));
        helper.assertTrue(thrown instanceof UncheckedIOException, "The edit did not throw although the file could not be replaced");
        helper.assertTrue(Arrays.equals(Files.readAllBytes(file), written), "The edit changed the file although it could not be replaced");
        try (Stream<Path> files = Files.list(file.getParent())) {
            helper.assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(uuid + "-")), "The edit left its temporary file behind");
        }

        helper.succeed();
    }

    @ServerTest
    public static void editStaysSavedWhenASaveListenerThrows(GameTestHelper helper) throws IOException {
        AttachmentEntry<Counter> counter = PlayerDataSamples.counter();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        UUID uuid = UUID.randomUUID();
        Path file = PlayerDataSamples.write(server, uuid, PlayerDataSamples.playerTag(1));
        PlayerDataSamples.FAILING_SAVES.add(uuid);
        try {
            data.edit(server, uuid, player -> counter.set(player, new Counter(2)));
        } finally {
            PlayerDataSamples.FAILING_SAVES.remove(uuid);
        }

        CompoundTag attachments = PlayerDataSamples.read(file).getCompoundOrEmpty(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        helper.assertValueEqual(attachments.get(counter.getId().toString()), PlayerDataSamples.counterTag(2), "attachment in the saved file");
        helper.succeed();
    }

    @ServerTest
    public static void inspectUpgradesAnOldTag(GameTestHelper helper) {
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        boolean booted = data.inspect(server, PlayerDataSamples.oldPlayerTag(), player -> player.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS));
        helper.assertTrue(booted, "The copy of an old tag lost the boots of its feet slot");
        helper.succeed();
    }

    @ServerTest
    public static void readOfAnOldFileKeepsItsEquipmentThroughAReplace(GameTestHelper helper) throws IOException {
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        UUID uuid = UUID.randomUUID();
        PlayerDataSamples.write(server, uuid, PlayerDataSamples.oldPlayerTag());
        CompoundTag tag = data.read(server, uuid).orElseThrow();
        helper.assertValueEqual(NbtUtils.getDataVersion(tag), SharedConstants.getCurrentVersion().dataVersion().version(), "data version of the read tag");

        data.replace(server, uuid, tag);
        CompoundTag loaded = server.getPlayerList().getPlayerIo().load(new NameAndId(uuid, uuid.toString())).orElseThrow();
        helper.assertValueEqual(loaded.getCompoundOrEmpty("equipment").getCompoundOrEmpty("feet").getStringOr("id", ""), "minecraft:diamond_boots", "item in the feet slot of the replaced file");
        helper.succeed();
    }

    @ServerTest
    public static void inspectInsideAnEditLeavesTheAdvancementsOfTheEditedCopy(GameTestHelper helper) throws IOException {
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = helper.getLevel().getServer();
        AdvancementHolder advancement = PlayerDataSamples.advancement(server);
        String criterion = PlayerDataSamples.criterion(advancement);
        UUID uuid = UUID.randomUUID();
        PlayerDataSamples.write(server, uuid, PlayerDataSamples.playerTag(1));
        CompoundTag tag = data.read(server, uuid).orElseThrow();
        AtomicReference<@Nullable ServerPlayer> edited = new AtomicReference<>();
        data.edit(server, uuid, outer -> {
            data.inspect(server, tag, Function.identity());
            helper.assertTrue(outer.getAdvancements().award(advancement, criterion), "The edited copy could not be awarded the criterion");
            edited.set(outer);
        });

        List<Player> progressed = PlayerDataSamples.progressed(advancement, uuid);
        helper.assertTrue(progressed.size() == 1 && progressed.getFirst() == edited.get(), "The advancements of the edited copy named another player after the inspection");
        helper.succeed();
    }

    @ClientTest
    public static void editRunsOnTheJoinedPlayer(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        AtomicReference<@Nullable ServerPlayer> edited = new AtomicReference<>();
        boolean known = PlayerDataSamples.onServer(client, () -> data.edit(server, live.getUUID(), edited::set));
        Assertions.assertThat(known).isTrue();
        Assertions.assertThat(edited.get()).isSameAs(live);
        client.leaveWorld();
    }

    @ClientTest
    public static void inspectLeavesTheJoinedPlayerAlone(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        AdvancementHolder advancement = PlayerDataSamples.advancement(server);
        String criterion = PlayerDataSamples.criterion(advancement);
        float health = live.getHealth();
        ServerPlayer copy = PlayerDataSamples.onServer(client, () -> data.inspect(server, data.snapshot(live), inspected -> {
            inspected.setHealth(1.0F);
            return inspected;
        }));

        PlayerDataSamples.PROGRESSED.clear();
        boolean awarded = PlayerDataSamples.onServer(client, () -> live.getAdvancements().award(advancement, criterion));
        PlayerDataSamples.runOnServer(client, () -> live.getAdvancements().revoke(advancement, criterion));
        Assertions.assertThat(copy).isNotSameAs(live);
        Assertions.assertThat(live.getHealth()).isEqualTo(health);
        Assertions.assertThat(awarded).isTrue();
        Assertions.assertThat(PlayerDataSamples.progressed(advancement, live.getUUID())).containsExactly(live);
        Assertions.assertThat(server.getPlayerList().advancements.get(live.getUUID())).isSameAs(live.getAdvancements());
        Assertions.assertThat(server.getPlayerList().getPlayer(live.getUUID())).isSameAs(live);
        client.leaveWorld();
    }

    @ClientTest
    public static void replaceSwapsTheJoinedPlayerForOneLoadedFromTheTag(Client client) {
        client.joinWorld();
        AttachmentEntry<Counter> counter = PlayerDataSamples.counter();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        Vec3 target = live.position().add(8.0D, 2.0D, 8.0D);
        PlayerDataSamples.runOnServer(client, () -> counter.set(live, new Counter(PlayerDataSamples.LIVE_COUNT)));
        CompoundTag tag = PlayerDataSamples.onServer(client, () -> data.inspect(server, data.snapshot(live), copy -> {
            counter.set(copy, new Counter(PlayerDataSamples.REPLACED_COUNT));
            copy.setServerLevel(Objects.requireNonNull(server.getLevel(Level.NETHER)));
            copy.snapTo(target);
            copy.setHealth(PlayerDataSamples.REPLACED_HEALTH);
            copy.getFoodData().setFoodLevel(PlayerDataSamples.FOOD_LEVEL_BELOW_REGENERATION);
            copy.getFoodData().setSaturation(0.0F);
            copy.getInventory().setItem(0, new ItemStack(Items.DIAMOND, PlayerDataSamples.REPLACED_DIAMONDS));
            return data.snapshot(copy);
        }));
        PlayerDataSamples.LOGGED_IN.clear();
        PlayerDataSamples.LOGGED_OUT.clear();
        PlayerDataSamples.CLONED.clear();
        PlayerDataSamples.RESPAWNED.clear();

        ServerPlayer replaced = PlayerDataSamples.onServer(client, () -> {
            data.replace(server, live.getUUID(), tag);
            return Objects.requireNonNull(server.getPlayerList().getPlayer(live.getUUID()));
        });
        
        Assertions.assertThat(replaced).isNotSameAs(live);
        Assertions.assertThat(replaced.connection).isSameAs(live.connection);
        Assertions.assertThat(replaced.level().dimension()).isEqualTo(Level.NETHER);
        Assertions.assertThat(replaced.position().closerThan(target, PlayerDataSamples.POSITION_TOLERANCE)).isTrue();
        Assertions.assertThat(replaced.getHealth()).isEqualTo(PlayerDataSamples.REPLACED_HEALTH);
        Assertions.assertThat(replaced.getInventory().getItem(0).is(Items.DIAMOND)).isTrue();
        Assertions.assertThat(PlayerDataSamples.LOGGED_OUT).containsExactly(live);
        Assertions.assertThat(PlayerDataSamples.LOGGED_IN).containsExactly(replaced);
        Assertions.assertThat(PlayerDataSamples.CLONED).isEmpty();
        Assertions.assertThat(PlayerDataSamples.RESPAWNED).isEmpty();

        client.until("the client to take the dimension, position, health, inventory and counter of the tag", () -> {
            LocalPlayer player = client.minecraft().player;
            if (player == null) {
                return false;
            }

            ItemStack first = player.getInventory().getItem(0);
            return (
                    player.level().dimension() == Level.NETHER &&
                    player.position().closerThan(target, PlayerDataSamples.POSITION_TOLERANCE) &&
                    player.getHealth() == PlayerDataSamples.REPLACED_HEALTH &&
                    first.is(Items.DIAMOND) &&
                    first.getCount() == PlayerDataSamples.REPLACED_DIAMONDS &&
                    counter.get(player).count() == PlayerDataSamples.REPLACED_COUNT
            );
        });
        client.leaveWorld();
    }

    @ClientTest
    public static void replaceWithAnUnloadableTagLeavesThePlayer(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        AdvancementHolder advancement = PlayerDataSamples.advancement(server);
        String criterion = PlayerDataSamples.criterion(advancement);
        PlayerDataSamples.runOnServer(client, () -> {
            ListTag position = new ListTag();
            position.add(DoubleTag.valueOf(Double.NaN));
            position.add(DoubleTag.valueOf(Double.NaN));
            position.add(DoubleTag.valueOf(Double.NaN));
            CompoundTag tag = data.snapshot(live);
            tag.put(PlayerDataSamples.POSITION_TAG, position);
            Assertions.assertThatThrownBy(() -> data.replace(server, live.getUUID(), tag)).isInstanceOf(ReportedException.class);
            Assertions.assertThat(server.getPlayerList().getPlayer(live.getUUID())).isSameAs(live);
            Assertions.assertThat(live.connection.player).isSameAs(live);
            Assertions.assertThat(live.isRemoved()).isFalse();
            Assertions.assertThat(live.level().getEntity(live.getUUID())).isSameAs(live);

            PlayerDataSamples.PROGRESSED.clear();
            Assertions.assertThat(live.getAdvancements().award(advancement, criterion)).isTrue();
            live.getAdvancements().revoke(advancement, criterion);
            Assertions.assertThat(PlayerDataSamples.progressed(advancement, live.getUUID())).containsExactly(live);
        });
        client.leaveWorld();
    }

    @ClientTest
    public static void replaceOfADeadPlayerLetsTheClientPlay(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        client.until("the server to count the client as loaded", live.connection::hasClientLoaded);
        CompoundTag alive = PlayerDataSamples.onServer(client, () -> data.snapshot(live));
        PlayerDataSamples.runOnServer(client, () -> live.kill(live.level()));
        client.until("the client to show the death screen", () -> client.screen() instanceof DeathScreen);

        ServerPlayer replaced = PlayerDataSamples.onServer(client, () -> {
            data.replace(server, live.getUUID(), alive);
            return Objects.requireNonNull(server.getPlayerList().getPlayer(live.getUUID()));
        });
        
        client.until("the server to count the client of the new player as loaded", replaced.connection::hasClientLoaded);
        float health = PlayerDataSamples.onServer(client, () -> {
            replaced.hurtServer(replaced.level(), replaced.damageSources().fellOutOfWorld(), 1.0F);
            return replaced.getHealth();
        });
        
        Assertions.assertThat(health).isLessThan(replaced.getMaxHealth());
        client.leaveWorld();
    }

    @ClientTest
    public static void replaceDropsNothingOfTheOldPlayer(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        PlayerDataSamples.runOnServer(client, () -> {
            CompoundTag tag = data.snapshot(live);
            Vec3 position = live.position();
            live.inventoryMenu.getCraftSlots().setItem(0, new ItemStack(Items.DIAMOND));
            live.openMenu(new SimpleMenuProvider((id, inventory, _) -> new BeaconMenu(id, inventory), Component.literal("beacon")));
            Assertions.assertThat(live.containerMenu).isInstanceOf(BeaconMenu.class);
            live.containerMenu.getSlot(0).set(new ItemStack(Items.IRON_INGOT));
            live.containerMenu.setCarried(new ItemStack(Items.DIAMOND));
            data.replace(server, live.getUUID(), tag);
            Assertions.assertThat(live.level().getEntitiesOfClass(ItemEntity.class, AABB.ofSize(position, 16.0D, 16.0D, 16.0D))).isEmpty();
        });
        client.leaveWorld();
    }

    @ClientTest
    public static void sleepingPlayerKeepsItsBedOnInspectAndLeavesItOnReplace(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        PlayerDataSamples.runOnServer(client, () -> {
            ServerLevel level = live.level();
            CompoundTag awake = data.snapshot(live);
            BlockPos head = live.blockPosition().relative(Direction.EAST, 2);
            BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
            level.setBlockAndUpdate(head.relative(Direction.SOUTH), bed.setValue(BedBlock.PART, BedPart.FOOT));
            level.setBlockAndUpdate(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
            live.startSleeping(head);
            Assertions.assertThat(level.getBlockState(head).getValue(BedBlock.OCCUPIED)).isTrue();

            data.inspect(server, data.snapshot(live), Function.identity());
            Assertions.assertThat(level.getBlockState(head).getValue(BedBlock.OCCUPIED)).isTrue();

            data.replace(server, live.getUUID(), awake);
            Assertions.assertThat(level.getBlockState(head).getValue(BedBlock.OCCUPIED)).isFalse();
        });
        client.leaveWorld();
    }

    @ClientTest
    public static void replaceKeepsTheChatSessionAndTheTabList(Client client) throws CryptException {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        ProfilePublicKey key = new ProfilePublicKey(new ProfilePublicKey.Data(Instant.now().plus(Duration.ofHours(1)), Crypt.generateKeyPair().getPublic(), new byte[0]));
        RemoteChatSession session = new RemoteChatSession(UUID.randomUUID(), key);
        Component header = Component.literal("header");
        Component footer = Component.literal("footer");
        ServerPlayer replaced = PlayerDataSamples.onServer(client, () -> {
            live.setChatSession(session);
            live.setTabListHeaderFooter(header, footer);
            data.replace(server, live.getUUID(), data.snapshot(live));
            return Objects.requireNonNull(server.getPlayerList().getPlayer(live.getUUID()));
        });

        Assertions.assertThat(replaced).isNotSameAs(live);
        Assertions.assertThat(replaced.getChatSession()).isSameAs(session);
        Assertions.assertThat(replaced.getTabListHeader()).isEqualTo(header);
        Assertions.assertThat(replaced.getTabListFooter()).isEqualTo(footer);
        client.leaveWorld();
    }

    @ClientTest
    public static void replaceOfARiderWithAnOnFootTagKeepsTheVehicle(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        PlayerDataSamples.runOnServer(client, () -> {
            CompoundTag onFoot = data.snapshot(live);
            ChestBoat boat = PlayerDataSamples.rideChestBoat(live);
            data.replace(server, live.getUUID(), onFoot);
            ServerPlayer replaced = Objects.requireNonNull(server.getPlayerList().getPlayer(live.getUUID()));
            Assertions.assertThat(PlayerDataSamples.entitiesWithUuid(server, boat.getUUID())).containsExactly(boat);
            Assertions.assertThat(boat.getItem(0).is(Items.DIAMOND)).isTrue();
            Assertions.assertThat(boat.getPassengers()).isEmpty();
            Assertions.assertThat(replaced.isPassenger()).isFalse();
        });
        client.leaveWorld();
    }

    @ClientTest
    public static void replaceOfARidingPlayerKeepsOneVehicle(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        PlayerDataSamples.runOnServer(client, () -> {
            ChestBoat boat = PlayerDataSamples.rideChestBoat(live);
            data.replace(server, live.getUUID(), data.snapshot(live));
            Assertions.assertThat(PlayerDataSamples.entitiesWithUuid(server, boat.getUUID())).containsExactly(boat);
            Assertions.assertThat(boat.getItem(0).is(Items.DIAMOND)).isTrue();
        });
        client.leaveWorld();
    }

    @ClientTest
    public static void replaceWithTheVehicleOfTheRiderPutsTheVehicleOfTheTagInItsPlace(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        PlayerDataSamples.runOnServer(client, () -> {
            ChestBoat boat = PlayerDataSamples.rideChestBoat(live);
            CompoundTag tag = data.snapshot(live);
            tag.put(PlayerDataSamples.ROOT_VEHICLE_TAG, PlayerDataSamples.vehicleTag(boat));
            boat.setItem(0, ItemStack.EMPTY);
            data.replace(server, live.getUUID(), tag);
            ServerPlayer replaced = Objects.requireNonNull(server.getPlayerList().getPlayer(live.getUUID()));
            List<Entity> vehicles = PlayerDataSamples.entitiesWithUuid(server, boat.getUUID());
            Assertions.assertThat(vehicles).hasSize(1);
            Assertions.assertThat(vehicles.getFirst()).isNotSameAs(boat).isInstanceOfSatisfying(ChestBoat.class, restored -> Assertions.assertThat(restored.getItem(0).is(Items.DIAMOND)).isTrue());
            Assertions.assertThat(replaced.getVehicle()).isSameAs(vehicles.getFirst());
        });
        client.leaveWorld();
    }

    @ClientTest
    public static void replaceWithASnapshotOfARiderSpawnsNoSecondVehicle(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        PlayerDataSamples.runOnServer(client, () -> {
            ChestBoat boat = PlayerDataSamples.rideChestBoat(live);
            ThrownEnderpearl pearl = new ThrownEnderpearl(live.level(), live, new ItemStack(Items.ENDER_PEARL));
            live.level().addFreshEntity(pearl);
            CompoundTag riding = data.snapshot(live);

            // The boat breaks and drops its diamond, and the pearl lands, before the snapshot is put back.
            live.stopRiding();
            boat.kill(live.level());
            pearl.discard();
            data.replace(server, live.getUUID(), riding);
            Assertions.assertThat(PlayerDataSamples.entitiesWithUuid(server, boat.getUUID())).isEmpty();
            Assertions.assertThat(PlayerDataSamples.entitiesWithUuid(server, pearl.getUUID())).isEmpty();
        });
        client.leaveWorld();
    }

    @ClientTest
    public static void replaceRemovesTheEnderPearlsOfTheOldPlayer(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        PlayerDataSamples.runOnServer(client, () -> {
            CompoundTag tag = data.snapshot(live);
            ThrownEnderpearl pearl = new ThrownEnderpearl(live.level(), live, new ItemStack(Items.ENDER_PEARL));
            live.level().addFreshEntity(pearl);
            Assertions.assertThat(live.getEnderPearls()).containsExactly(pearl);

            data.replace(server, live.getUUID(), tag);
            ServerPlayer replaced = Objects.requireNonNull(server.getPlayerList().getPlayer(live.getUUID()));
            Assertions.assertThat(pearl.isRemoved()).isTrue();
            Assertions.assertThat(PlayerDataSamples.entitiesWithUuid(server, pearl.getUUID())).isEmpty();
            Assertions.assertThat(replaced.getEnderPearls()).isEmpty();
        });
        client.leaveWorld();
    }

    private record Counter(int count) { }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            PlayerDataSamples.counter = Nexus.getMod().getRegistrations().attachment("player_data_samples_counter", () -> new Counter(0))
                    .codec(PlayerDataSamples.CODEC)
                    .sync(PlayerDataSamples.CODEC)
                    .register();
        }

        @SubscribeEvent
        public static void onLoadFromFile(PlayerEvent.LoadFromFile event) {
            PlayerDataSamples.LOADED.add(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void onSaveToFile(PlayerEvent.SaveToFile event) {
            UUID player = event.getEntity().getUUID();
            PlayerDataSamples.SAVED.add(player);
            if (PlayerDataSamples.FAILING_SAVES.contains(player)) {
                throw new IllegalStateException("Thrown by the save listener of '" + player + "'");
            }
        }

        @SubscribeEvent
        public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            PlayerDataSamples.LOGGED_IN.add(event.getEntity());
        }

        @SubscribeEvent
        public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            PlayerDataSamples.LOGGED_OUT.add(event.getEntity());
        }

        @SubscribeEvent
        public static void onClone(PlayerEvent.Clone event) {
            PlayerDataSamples.CLONED.add(event.getEntity());
        }

        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
            PlayerDataSamples.RESPAWNED.add(event.getEntity());
        }

        @SubscribeEvent
        public static void onAdvancementProgress(AdvancementEvent.AdvancementProgressEvent event) {
            if (event.getProgressType() == AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT) {
                PlayerDataSamples.PROGRESSED.add(event);
            }
        }
    }
}
