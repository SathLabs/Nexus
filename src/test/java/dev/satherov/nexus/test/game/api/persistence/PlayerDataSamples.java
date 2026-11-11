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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        ServerPlayer copy = Objects.requireNonNull(edited.get());
        ServerPlayer later = data.inspect(server, saved, Function.identity());
        helper.assertTrue(later.getAdvancements() != copy.getAdvancements(), "The player list kept the advancements of the detached copy");
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
        float health = live.getHealth();
        PlayerAdvancements advancements = live.getAdvancements();
        ServerPlayer copy = PlayerDataSamples.onServer(client, () -> data.inspect(server, data.snapshot(live), inspected -> {
            inspected.setHealth(1.0F);
            return inspected;
        }));
        
        Assertions.assertThat(copy).isNotSameAs(live);
        Assertions.assertThat(live.getHealth()).isEqualTo(health);
        Assertions.assertThat(live.getAdvancements()).isSameAs(advancements);
        Assertions.assertThat(server.getPlayerList().getPlayer(live.getUUID())).isSameAs(live);
        client.leaveWorld();
    }

    @ClientTest
    public static void replaceSwapsTheJoinedPlayerForOneLoadedFromTheTag(Client client) {
        client.joinWorld();
        PlayerData data = PlayerDataSamples.playerData();
        MinecraftServer server = client.serverLevel().getServer();
        ServerPlayer live = client.serverLevel().players().getFirst();
        Vec3 target = live.position().add(8.0D, 2.0D, 8.0D);
        CompoundTag tag = PlayerDataSamples.onServer(client, () -> data.inspect(server, data.snapshot(live), copy -> {
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
        Assertions.assertThat(replaced.position().closerThan(target, PlayerDataSamples.POSITION_TOLERANCE)).isTrue();
        Assertions.assertThat(replaced.getHealth()).isEqualTo(PlayerDataSamples.REPLACED_HEALTH);
        Assertions.assertThat(replaced.getInventory().getItem(0).is(Items.DIAMOND)).isTrue();
        Assertions.assertThat(PlayerDataSamples.LOGGED_OUT).containsExactly(live);
        Assertions.assertThat(PlayerDataSamples.LOGGED_IN).containsExactly(replaced);
        Assertions.assertThat(PlayerDataSamples.CLONED).isEmpty();
        Assertions.assertThat(PlayerDataSamples.RESPAWNED).isEmpty();

        client.until("the client to take the position, health and inventory of the tag", () -> {
            LocalPlayer player = client.minecraft().player;
            if (player == null) {
                return false;
            }

            ItemStack first = player.getInventory().getItem(0);
            return (
                    player.position().closerThan(target, PlayerDataSamples.POSITION_TOLERANCE) &&
                    player.getHealth() == PlayerDataSamples.REPLACED_HEALTH &&
                    first.is(Items.DIAMOND) &&
                    first.getCount() == PlayerDataSamples.REPLACED_DIAMONDS
            );
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
            PlayerDataSamples.SAVED.add(event.getEntity().getUUID());
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
    }
}
