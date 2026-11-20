package dev.satherov.nexus.test.game.api.persistence.archive;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.persistence.archive.Archive;
import dev.satherov.nexus.api.persistence.archive.NameKey;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

///
/// Checks that an archive opens before the other listeners of the server start, writes into the data folder of the world, and retires and deletes its entries.
///
public class ArchiveSamples {

    private static final Format<String> FORMAT = Format.of(NexusCodec.STRING);
    private static final String NAME = "archive_samples";

    private static @Nullable Archive<NameKey, String> archive;
    private static boolean listedOnStart;

    @ServerTest
    public static void opensBeforeOtherListenersOfTheStart(GameTestHelper helper) {
        helper.assertTrue(ArchiveSamples.listedOnStart, "The archive could not be listed by another listener of the server start");
        helper.succeed();
    }

    @ServerTest
    public static void writesNowIntoTheDataFolderOfTheWorld(GameTestHelper helper) throws IOException {
        Archive<NameKey, String> archive = Objects.requireNonNull(ArchiveSamples.archive);
        MinecraftServer server = helper.getLevel().getServer();
        NameKey key = new NameKey("written_now");
        archive.writeNow(server, key, "written now");

        Path file = ArchiveSamples.folder(server).resolve("written_now.dat");
        helper.assertValueEqual(ArchiveSamples.FORMAT.read(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()), server.registryAccess()), "written now", "value in the file");
        helper.assertValueEqual(archive.read(server, key), Optional.of("written now"), "value read back");
        helper.succeed();
    }

    @ServerTest
    public static void readsAQueuedWriteBeforeAndAfterItLands(GameTestHelper helper) {
        Archive<NameKey, String> archive = Objects.requireNonNull(ArchiveSamples.archive);
        MinecraftServer server = helper.getLevel().getServer();
        NameKey key = new NameKey("queued");
        archive.write(server, key, "queued");
        helper.assertValueEqual(archive.read(server, key), Optional.of("queued"), "value while the write is queued");
        helper.assertTrue(archive.list(server).contains(key), "The key of the queued write is not listed");

        Path file = ArchiveSamples.folder(server).resolve("queued.dat");
        helper.succeedWhen(() -> {
            helper.assertTrue(Files.isRegularFile(file), "The queued write was not written yet");
            helper.assertValueEqual(archive.read(server, key), Optional.of("queued"), "value once the write is done");
        });
    }

    @ServerTest
    public static void retiresAndDeletesAnEntry(GameTestHelper helper) {
        Archive<NameKey, String> archive = Objects.requireNonNull(ArchiveSamples.archive);
        MinecraftServer server = helper.getLevel().getServer();
        NameKey key = new NameKey("retired_then_deleted");
        archive.writeNow(server, key, "retired");
        archive.retire(server, key);
        helper.assertTrue(archive.isRetired(server, key), "The entry is not retired");
        helper.assertFalse(archive.list(server).contains(key), "The retired entry is listed as active");
        helper.assertTrue(archive.listRetired(server, key::equals).contains(key), "The retired entry is not listed as retired");
        helper.assertValueEqual(archive.read(server, key), Optional.of("retired"), "value of the retired entry");

        archive.delete(server, key);
        helper.assertFalse(archive.isRetired(server, key), "The deleted entry is still retired");
        helper.assertValueEqual(archive.read(server, key), Optional.empty(), "value of the deleted entry");

        Path folder = ArchiveSamples.folder(server);
        helper.succeedWhen(() -> {
            helper.assertFalse(Files.exists(folder.resolve("retired_then_deleted.dat")), "The active file of the deleted entry is still there");
            helper.assertFalse(Files.exists(folder.resolve("retired_then_deleted.retired")), "The retired file of the deleted entry is still there");
        });
    }

    private static Path folder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATA).resolve(Nexus.MOD_ID).resolve(ArchiveSamples.NAME);
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            ArchiveSamples.archive = Nexus.getMod().getPersistence().archive(ArchiveSamples.NAME, NameKey::parse, ArchiveSamples.FORMAT).register();
        }

        @SubscribeEvent
        public static void onServerAboutToStart(ServerAboutToStartEvent event) {
            Archive<NameKey, String> archive = Objects.requireNonNull(ArchiveSamples.archive);
            ArchiveSamples.listedOnStart = Assertions.catchThrowable(() -> archive.list(event.getServer())) == null;
        }
    }
}
