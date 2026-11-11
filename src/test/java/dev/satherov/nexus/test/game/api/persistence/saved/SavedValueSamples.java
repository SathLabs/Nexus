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

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import net.minecraft.SharedConstants;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

///
/// Checks that saved values survive a save and reach the client, that a late declaration is refused, and that a file of a newer version is refused and kept.
///
public class SavedValueSamples {

    private static final Format<String> FORMAT = Format.of(NexusCodec.STRING);
    private static final String INITIAL = "initial";
    private static final String LEVEL_NAME = "saved_value_samples_level";
    private static final String NEWER_NAME = "saved_value_samples_newer";
    private static final String SERVER_NAME = "saved_value_samples_server";
    private static final String SET_ON_START = "set when the server started";
    private static final String UPDATE_SUFFIX = " and then updated";

    private static @Nullable SavedValue<MinecraftServer, String> newer;
    private static @Nullable SavedValue<ServerLevel, String> perLevel;
    private static @Nullable SavedValue<MinecraftServer, String> perServer;
    private static @Nullable SavedValue<MinecraftServer, String> synced;

    @ServerTest
    public static void levelValueSurvivesAFreshStorage(GameTestHelper helper) throws IOException {
        SavedValue<ServerLevel, String> perLevel = Objects.requireNonNull(SavedValueSamples.perLevel);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String written = "set in the level";
        perLevel.set(level, written);
        helper.assertValueEqual(perLevel.get(level), written, "value after the set");

        level.getDataStorage().saveAndJoin();
        Path folder = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT)).resolve("data");
        helper.assertValueEqual(SavedValueSamples.readFresh(server, folder, SavedValueSamples.LEVEL_NAME), written, "value in a fresh storage of the level");
        helper.succeed();
    }

    @ServerTest
    public static void serverValueSurvivesAFreshStorage(GameTestHelper helper) throws IOException {
        SavedValue<MinecraftServer, String> perServer = Objects.requireNonNull(SavedValueSamples.perServer);
        MinecraftServer server = helper.getLevel().getServer();
        String updated = perServer.update(server, current -> current + SavedValueSamples.UPDATE_SUFFIX);
        helper.assertValueEqual(perServer.get(server), updated, "value after the update");

        server.getDataStorage().saveAndJoin();
        helper.assertValueEqual(SavedValueSamples.readFresh(server, server.getWorldPath(LevelResource.DATA), SavedValueSamples.SERVER_NAME), updated, "value in a fresh storage of the server");
        helper.succeed();
    }

    @ServerTest
    public static void refusesARegisterAfterTheServerStarted(GameTestHelper helper) {
        Persistence persistence = Nexus.getMod().getPersistence();
        helper.assertTrue(
                Assertions.catchThrowable(() -> persistence.serverValue("saved_value_samples_late", SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL).register()) != null,
                "A value was registered after the server started"
        );
        helper.succeed();
    }

    @ServerTest
    public static void refusesAndKeepsAFileOfANewerVersion(GameTestHelper helper) throws IOException {
        SavedValue<MinecraftServer, String> newer = Objects.requireNonNull(SavedValueSamples.newer);
        MinecraftServer server = helper.getLevel().getServer();
        CompoundTag versioned = new CompoundTag();
        versioned.putInt("version", SavedValueSamples.FORMAT.getVersion() + 1);
        versioned.putString("data", "written by a newer version");
        CompoundTag saved = new CompoundTag();
        saved.put("data", versioned);
        NbtUtils.addCurrentDataVersion(saved);
        Path file = server.getWorldPath(LevelResource.DATA).resolve(Nexus.MOD_ID).resolve(SavedValueSamples.NEWER_NAME + ".dat");
        Files.createDirectories(file.getParent());
        NbtIo.writeCompressed(saved, file);
        byte[] written = Files.readAllBytes(file);

        helper.assertTrue(Assertions.catchThrowable(() -> newer.get(server)) instanceof NexusCodecException, "The value of a newer version was read");
        helper.assertTrue(Assertions.catchThrowable(() -> newer.update(server, current -> current + SavedValueSamples.UPDATE_SUFFIX)) instanceof NexusCodecException, "The value of a newer version was updated");
        server.getDataStorage().saveAndJoin();
        helper.assertTrue(Arrays.equals(Files.readAllBytes(file), written), "The file of a newer version was overwritten");
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
    }

    private static String readFresh(MinecraftServer server, Path folder, String name) throws IOException {
        try (SavedDataStorage fresh = new SavedDataStorage(folder, server.getFixerUpper(), server.registryAccess())) {
            Path file = folder.resolve(Nexus.MOD_ID).resolve(name + ".dat");
            CompoundTag saved = fresh.readTagFromDisk(file, null, SharedConstants.getCurrentVersion().dataVersion().version());
            return SavedValueSamples.FORMAT.read(Objects.requireNonNull(saved.get("data")), server.registryAccess());
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
            SavedValueSamples.synced = persistence.serverValue("saved_value_samples_synced", SavedValueSamples.FORMAT, () -> SavedValueSamples.INITIAL)
                    .sync(NexusCodec.STRING)
                    .register();
        }

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            Objects.requireNonNull(SavedValueSamples.synced).set(event.getServer(), SavedValueSamples.SET_ON_START);
        }
    }
}
