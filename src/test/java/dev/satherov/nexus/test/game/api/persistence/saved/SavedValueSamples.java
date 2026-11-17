package dev.satherov.nexus.test.game.api.persistence.saved;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.persistence.Persistence;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.api.persistence.saved.SavedValue;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.server.ServerTest;
import dev.satherov.nexus.internal.persistence.saved.SavedValueData;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import net.minecraft.SharedConstants;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.phys.Vec3;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.StreamSupport;

///
/// Checks that saved values survive a save and reach the client in every level it enters, that a late declaration and a value the format or the sync codec can't encode are refused, and that a newer or unreadable file is refused and kept without breaking a join.
///
public class SavedValueSamples {

    private static final Format<String> FORMAT = Format.of(NexusCodec.STRING);
    private static final String INITIAL = "initial";
    private static final Format<String> BOUNDED_FORMAT = Format.of(NexusCodec.string(SavedValueSamples.INITIAL.length()));
    private static final String BOUNDED_NAME = "saved_value_samples_bounded";
    private static final String CORRUPT_NAME = "saved_value_samples_corrupt";
    private static final String LEVEL_NAME = "saved_value_samples_level";
    private static final String NEWER_NAME = "saved_value_samples_newer";
    private static final String REFUSED_NAME = "saved_value_samples_refused";
    private static final String SERVER_NAME = "saved_value_samples_server";
    private static final String SYNCED_NAME = "saved_value_samples_synced";
    private static final String SET_ON_JOIN = "set when the player joined";
    private static final String SET_ON_RESPAWN = "set while the player respawned";
    private static final String SET_ON_START = "set when the server started";
    private static final String TOO_LONG = "longer than the initial value";
    private static final String UPDATE_SUFFIX = " and then updated";

    private static @Nullable SavedValue<MinecraftServer, String> bounded;
    private static @Nullable SavedValue<MinecraftServer, String> corrupt;
    private static @Nullable SavedValue<ServerLevel, String> levelSynced;
    private static @Nullable SavedValue<MinecraftServer, String> newer;
    private static @Nullable SavedValue<ServerLevel, String> perLevel;
    private static @Nullable SavedValue<MinecraftServer, String> perServer;
    private static @Nullable SavedValue<MinecraftServer, String> refused;
    private static @Nullable SavedValue<MinecraftServer, String> respawned;
    private static @Nullable SavedValue<MinecraftServer, String> syncBounded;
    private static @Nullable SavedValue<MinecraftServer, String> synced;
    private static @Nullable SavedValue<ServerLevel, String> visited;
    private static byte @Nullable [] refusedFile;

    @ServerTest
    public static void levelValueSurvivesAFreshStorage(GameTestHelper helper) throws IOException {
        SavedValue<ServerLevel, String> perLevel = Objects.requireNonNull(SavedValueSamples.perLevel);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertValueEqual(perLevel.get(level), SavedValueSamples.INITIAL, "value before the set");
        level.getDataStorage().saveAndJoin();

        String written = "set in the level";
        perLevel.set(level, written);
        helper.assertValueEqual(perLevel.get(level), written, "value after the set");

        level.getDataStorage().saveAndJoin();
        Path folder = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT)).resolve("data");
        helper.assertValueEqual(SavedValueSamples.readFile(server, folder, SavedValueSamples.LEVEL_NAME), written, "value in the file of the level");
        helper.assertValueEqual(SavedValueSamples.readFresh(server, folder, SavedValueSamples.LEVEL_NAME), written, "value in a fresh storage of the level");
        helper.succeed();
    }

    @ServerTest
    public static void serverValueSurvivesAFreshStorage(GameTestHelper helper) throws IOException {
        SavedValue<MinecraftServer, String> perServer = Objects.requireNonNull(SavedValueSamples.perServer);
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertValueEqual(perServer.get(server), SavedValueSamples.INITIAL, "value before the update");
        server.getDataStorage().saveAndJoin();

        String updated = perServer.update(server, current -> current + SavedValueSamples.UPDATE_SUFFIX);
        helper.assertValueEqual(perServer.get(server), updated, "value after the update");

        server.getDataStorage().saveAndJoin();
        Path folder = server.getWorldPath(LevelResource.DATA);
        helper.assertValueEqual(SavedValueSamples.readFile(server, folder, SavedValueSamples.SERVER_NAME), updated, "value in the file of the server");
        helper.assertValueEqual(SavedValueSamples.readFresh(server, folder, SavedValueSamples.SERVER_NAME), updated, "value in a fresh storage of the server");
        helper.succeed();
    }

    @ServerTest
    public static void refusesARegisterAfterTheServerStarted(GameTestHelper helper) {
        Persistence persistence = Nexus.getMod().getPersistence();
        Throwable refusal = Assertions.catchThrowable(() -> persistence.serverValue("saved_value_samples_late", SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL).register());

        Assertions.assertThat(refusal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Nexus.MOD_ID + ":saved_value_samples_late");
        helper.succeed();
    }

    @ServerTest
    public static void refusesASetItsFormatCannotEncode(GameTestHelper helper) {
        SavedValue<MinecraftServer, String> bounded = Objects.requireNonNull(SavedValueSamples.bounded);
        MinecraftServer server = helper.getLevel().getServer();

        Assertions.assertThat(Assertions.catchThrowable(() -> bounded.set(server, SavedValueSamples.TOO_LONG))).isInstanceOf(NexusCodecException.class);
        helper.assertValueEqual(bounded.get(server), SavedValueSamples.INITIAL, "value after the refused set");
        server.getDataStorage().saveAndJoin();
        helper.assertValueEqual(SavedValueSamples.readFresh(server, server.getWorldPath(LevelResource.DATA), SavedValueSamples.BOUNDED_NAME), SavedValueSamples.INITIAL, "value in a fresh storage of the server");
        helper.succeed();
    }

    @ServerTest
    public static void refusesASetItsSyncCodecCannotEncode(GameTestHelper helper) {
        SavedValue<MinecraftServer, String> syncBounded = Objects.requireNonNull(SavedValueSamples.syncBounded);
        MinecraftServer server = helper.getLevel().getServer();

        Assertions.assertThat(Assertions.catchThrowable(() -> syncBounded.set(server, SavedValueSamples.TOO_LONG))).isInstanceOf(NexusCodecException.class);
        helper.assertValueEqual(syncBounded.get(server), SavedValueSamples.INITIAL, "value after the refused set");
        helper.succeed();
    }

    @ServerTest
    public static void refusesAndKeepsAFileOfANewerVersion(GameTestHelper helper) throws IOException {
        SavedValue<MinecraftServer, String> newer = Objects.requireNonNull(SavedValueSamples.newer);
        MinecraftServer server = helper.getLevel().getServer();
        byte[] written = SavedValueSamples.writeNewer(server, SavedValueSamples.NEWER_NAME);

        helper.assertTrue(Assertions.catchThrowable(() -> newer.get(server)) instanceof NexusCodecException, "The value of a newer version was read");
        helper.assertTrue(Assertions.catchThrowable(() -> newer.update(server, current -> current + SavedValueSamples.UPDATE_SUFFIX)) instanceof NexusCodecException, "The value of a newer version was updated");
        server.getDataStorage().saveAndJoin();
        helper.assertTrue(Arrays.equals(Files.readAllBytes(SavedValueSamples.dataFile(server, SavedValueSamples.NEWER_NAME)), written), "The file of a newer version was overwritten");
        helper.succeed();
    }

    @ServerTest
    public static void refusesAndKeepsAnUnreadableFile(GameTestHelper helper) throws IOException {
        SavedValue<MinecraftServer, String> corrupt = Objects.requireNonNull(SavedValueSamples.corrupt);
        MinecraftServer server = helper.getLevel().getServer();
        Path file = SavedValueSamples.dataFile(server, SavedValueSamples.CORRUPT_NAME);
        byte[] written = "not nbt".getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(file.getParent());
        Files.write(file, written);

        helper.assertTrue(Assertions.catchThrowable(() -> corrupt.get(server)) instanceof NexusCodecException, "The value of an unreadable file was read");
        helper.assertTrue(Assertions.catchThrowable(() -> corrupt.update(server, current -> current + SavedValueSamples.UPDATE_SUFFIX)) instanceof NexusCodecException, "The value of an unreadable file was updated");
        server.getDataStorage().saveAndJoin();
        helper.assertTrue(Arrays.equals(Files.readAllBytes(file), written), "The unreadable file was overwritten");
        helper.succeed();
    }

    @ServerTest
    public static void joinSurvivesARefusedSyncedValue(GameTestHelper helper) throws IOException {
        SavedValue<MinecraftServer, String> refused = Objects.requireNonNull(SavedValueSamples.refused);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(Assertions.catchThrowable(() -> refused.get(server)) instanceof NexusCodecException, "The value of a newer version was read");

        PlayerList players = server.getPlayerList();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "saved_samples"), false);
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            players.placeNewPlayer(connection, player, cookie);
            channel.flushOutbound();
            helper.assertTrue(players.getPlayers().contains(player), "The player whose join held a refused value is not in the player list");

            List<Identifier> sent = channel.outboundMessages().stream()
                    .filter(ClientboundBundlePacket.class::isInstance)
                    .flatMap(bundle -> StreamSupport.stream(((ClientboundBundlePacket) bundle).subPackets().spliterator(), false))
                    .filter(ClientboundCustomPayloadPacket.class::isInstance)
                    .map(packet -> ((ClientboundCustomPayloadPacket) packet).payload().type().id())
                    .toList();
            Assertions.assertThat(sent)
                    .contains(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "saved/" + SavedValueSamples.SYNCED_NAME))
                    .doesNotContain(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "saved/" + SavedValueSamples.REFUSED_NAME));
        } finally {
            players.remove(player);
        }

        byte[] written = Objects.requireNonNull(SavedValueSamples.refusedFile);
        helper.assertTrue(Arrays.equals(Files.readAllBytes(SavedValueSamples.dataFile(server, SavedValueSamples.REFUSED_NAME)), written), "The file of the refused value was overwritten");
        helper.succeed();
    }

    @ClientTest
    public static void clientSeesTheSyncedValueOnJoinAndOnChange(Client client) {
        SavedValue<MinecraftServer, String> synced = Objects.requireNonNull(SavedValueSamples.synced);
        client.joinWorld();
        client.until("the value set when the server started to reach the client", () -> SavedValueSamples.SET_ON_START.equals(synced.getOnClient()));

        MinecraftServer server = client.serverLevel().getServer();
        String updated = SavedValueSamples.SET_ON_START + SavedValueSamples.UPDATE_SUFFIX;
        server.execute(() -> synced.update(server, current -> current + SavedValueSamples.UPDATE_SUFFIX));
        client.until("the updated value to reach the client", () -> updated.equals(synced.getOnClient()));
        client.leaveWorld();
        client.until("the value to be dropped once the client left", () -> SavedValueSamples.INITIAL.equals(synced.getOnClient()));
    }

    @ClientTest
    public static void clientSeesTheLevelValueOfEveryLevelItEnters(Client client) {
        SavedValue<ServerLevel, String> levelSynced = Objects.requireNonNull(SavedValueSamples.levelSynced);
        client.joinWorld();
        ServerLevel overworld = client.serverLevel();
        MinecraftServer server = overworld.getServer();
        ServerLevel nether = Objects.requireNonNull(server.getLevel(Level.NETHER));
        ServerPlayer player = overworld.players().getFirst();
        client.until("the value of the overworld to reach the client", () -> SavedValueSamples.setOnStart(overworld).equals(levelSynced.getOnClient()));

        server.execute(() -> player.teleport(new TeleportTransition(nether, new Vec3(0.5D, 100.0D, 0.5D), Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING)));
        client.until("the value of the nether to reach the client", () -> SavedValueSamples.setOnStart(nether).equals(levelSynced.getOnClient()));

        String updated = SavedValueSamples.setOnStart(nether) + SavedValueSamples.UPDATE_SUFFIX;
        server.execute(() -> levelSynced.update(nether, current -> current + SavedValueSamples.UPDATE_SUFFIX));
        client.until("the updated value of the nether to reach the client", () -> updated.equals(levelSynced.getOnClient()));
        client.leaveWorld();
        client.until("the value to be dropped once the client left", () -> SavedValueSamples.INITIAL.equals(levelSynced.getOnClient()));
    }

    @ClientTest
    public static void clientSeesALevelValueSetWhenThePlayerJoined(Client client) {
        SavedValue<ServerLevel, String> visited = Objects.requireNonNull(SavedValueSamples.visited);
        client.joinWorld();
        client.until("the value set when the player joined to reach the client", () -> SavedValueSamples.SET_ON_JOIN.equals(visited.getOnClient()));
        client.leaveWorld();
    }

    @ClientTest
    public static void clientSeesAServerValueSetDuringRespawn(Client client) {
        SavedValue<MinecraftServer, String> respawned = Objects.requireNonNull(SavedValueSamples.respawned);
        client.joinWorld();
        ServerLevel level = client.serverLevel();
        ServerPlayer player = level.players().getFirst();
        client.until("the server to see that the client loaded", player.connection::hasClientLoaded);
        level.getServer().execute(() -> player.kill(level));
        client.until("the player to die", player::isDeadOrDying);

        Objects.requireNonNull(client.minecraft().player).respawn();
        client.until("the value set while the player respawned to reach the client", () -> SavedValueSamples.SET_ON_RESPAWN.equals(respawned.getOnClient()));
        client.leaveWorld();
    }

    private static String setOnStart(ServerLevel level) {
        return SavedValueSamples.SET_ON_START + " in " + level.dimension().identifier();
    }

    private static Path dataFile(MinecraftServer server, String name) {
        return server.getWorldPath(LevelResource.DATA).resolve(Nexus.MOD_ID).resolve(name + ".dat");
    }

    private static byte[] writeNewer(MinecraftServer server, String name) throws IOException {
        CompoundTag versioned = new CompoundTag();
        versioned.putInt("version", SavedValueSamples.FORMAT.getVersion() + 1);
        versioned.putString("data", "written by a newer version");
        CompoundTag saved = new CompoundTag();
        saved.put("data", versioned);
        NbtUtils.addCurrentDataVersion(saved);
        Path file = SavedValueSamples.dataFile(server, name);
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(saved, file);
        return Files.readAllBytes(file);
    }

    private static String readFile(MinecraftServer server, Path folder, String name) throws IOException {
        try (SavedDataStorage fresh = new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess())) {
            Path file = folder.resolve(Nexus.MOD_ID).resolve(name + ".dat");
            CompoundTag saved = fresh.readTagFromDisk(file, null, SharedConstants.getCurrentVersion().dataVersion().version());
            return SavedValueSamples.FORMAT.read(Objects.requireNonNull(saved.get("data")), server.registryAccess());
        }
    }

    private static String readFresh(MinecraftServer server, Path folder, String name) {
        try (SavedDataStorage fresh = new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess())) {
            SavedValueData<String> data = fresh.get(SavedValueData.type(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, name), SavedValueSamples.FORMAT));
            return Objects.requireNonNull(data).getValue();
        }
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Persistence persistence = Nexus.getMod().getPersistence();
            SavedValueSamples.perLevel = persistence.levelValue(SavedValueSamples.LEVEL_NAME, SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL).register();
            SavedValueSamples.perServer = persistence.serverValue(SavedValueSamples.SERVER_NAME, SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL).register();
            SavedValueSamples.newer = persistence.serverValue(SavedValueSamples.NEWER_NAME, SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL).register();
            SavedValueSamples.corrupt = persistence.serverValue(SavedValueSamples.CORRUPT_NAME, SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL).register();
            SavedValueSamples.bounded = persistence.serverValue(SavedValueSamples.BOUNDED_NAME, SavedValueSamples.BOUNDED_FORMAT, () -> SavedValueSamples.INITIAL).register();
            SavedValueSamples.syncBounded = persistence.serverValue("saved_value_samples_sync_bounded", SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL)
                    .sync(NexusCodec.string(SavedValueSamples.INITIAL.length()))
                    .register();
            SavedValueSamples.synced = persistence.serverValue(SavedValueSamples.SYNCED_NAME, SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL)
                    .sync(NexusCodec.STRING)
                    .register();
            SavedValueSamples.refused = persistence.serverValue(SavedValueSamples.REFUSED_NAME, SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL)
                    .sync(NexusCodec.STRING)
                    .register();
            SavedValueSamples.respawned = persistence.serverValue("saved_value_samples_respawned", SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL)
                    .sync(NexusCodec.STRING)
                    .register();
            SavedValueSamples.levelSynced = persistence.levelValue("saved_value_samples_level_synced", SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL)
                    .sync(NexusCodec.STRING)
                    .register();
            SavedValueSamples.visited = persistence.levelValue("saved_value_samples_visited", SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL)
                    .sync(NexusCodec.STRING)
                    .register();
        }

        @SubscribeEvent
        public static void onServerAboutToStart(ServerAboutToStartEvent event) {
            try {
                SavedValueSamples.refusedFile = SavedValueSamples.writeNewer(event.getServer(), SavedValueSamples.REFUSED_NAME);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            MinecraftServer server = event.getServer();
            Objects.requireNonNull(SavedValueSamples.synced).set(server, SavedValueSamples.SET_ON_START);
            for (ServerLevel level : server.getAllLevels()) {
                Objects.requireNonNull(SavedValueSamples.levelSynced).set(level, SavedValueSamples.setOnStart(level));
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof ServerPlayer && event.getLevel() instanceof ServerLevel level) {
                Objects.requireNonNull(SavedValueSamples.visited).set(level, SavedValueSamples.SET_ON_JOIN);
            }
        }

        @SubscribeEvent
        public static void onClone(PlayerEvent.Clone event) {
            if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
                Objects.requireNonNull(SavedValueSamples.respawned).set(player.level().getServer(), SavedValueSamples.SET_ON_RESPAWN);
            }
        }
    }
}
