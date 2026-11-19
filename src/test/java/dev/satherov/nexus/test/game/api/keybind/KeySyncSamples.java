package dev.satherov.nexus.test.game.api.keybind;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.api.persistence.PlayerData;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.client.Keyboard;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import com.mojang.blaze3d.platform.InputConstants;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

///
/// The client tests that check keys reach the server with their firings and their held state.
///
public class KeySyncSamples {

    private static final int SETTLE_TICKS = 5;
    private static final int GROWN_TICKS = 5;
    private static final int CHARGE_TICKS = 4;
    private static final String CLIENT_SIDE = "client";
    private static final String READER_SIDE = "reader";
    private static final String SERVER_SIDE = "server";
    private static final String FOREIGN_MAPPING = "key.key_sync_samples_foreign";
    private static final List<String> SIDES = new CopyOnWriteArrayList<>();
    private static final List<ServerPlayer> PRESSERS = new CopyOnWriteArrayList<>();
    private static final List<Integer> HELD_TICKS_ON_ARRIVAL = new CopyOnWriteArrayList<>();
    private static final List<Integer> HELD_TICKS_ON_FIRST_TICK = new CopyOnWriteArrayList<>();
    private static final List<Integer> HELD_TICKS_ON_RELEASE = new CopyOnWriteArrayList<>();
    private static final AtomicInteger JUMPS = new AtomicInteger();
    private static final AtomicInteger MISSING_RUNS = new AtomicInteger();
    private static final AtomicInteger DEPARTED_RUNS = new AtomicInteger();
    private static final AtomicInteger FOREIGN_RUNS = new AtomicInteger();
    private static final AtomicInteger INVENTORY_RUNS = new AtomicInteger();

    private static @Nullable Keybind held;
    private static @Nullable Keybind everywhere;
    private static @Nullable Keybind charged;
    private static @Nullable Keybind missing;
    private static @Nullable Keybind departed;
    private static @Nullable Keybind released;
    private static volatile boolean watchingFirstTick;

    @ClientTest
    public static void pressRunsTheServerActionWithTheJoinedPlayerAfterTheClientAction(Client client) {
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        KeySyncSamples.SIDES.clear();
        KeySyncSamples.PRESSERS.clear();
        client.keyboard().press(GLFW.GLFW_KEY_F19);
        client.until("the server action to run", () -> !KeySyncSamples.PRESSERS.isEmpty());
        client.ticks(KeySyncSamples.SETTLE_TICKS);
        Assertions.assertThat(KeySyncSamples.PRESSERS).as("players of the server action").containsExactly(player);
        Assertions.assertThat(KeySyncSamples.SIDES)
                .as("sides in the order they ran")
                .containsExactly(KeySyncSamples.CLIENT_SIDE, KeySyncSamples.READER_SIDE, KeySyncSamples.SERVER_SIDE);
        client.leaveWorld();
    }

    @ClientTest
    public static void heldKeyReadsDownOnTheServerUntilItsRelease(Client client) {
        Keybind held = Objects.requireNonNull(KeySyncSamples.held);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        keyboard.hold(GLFW.GLFW_KEY_F20);
        client.until("the server to read the key down", () -> KeySyncSamples.readOnServer(client, () -> held.isDown(player)));
        keyboard.release(GLFW.GLFW_KEY_F20);
        client.until("the server to read the key up", () -> !KeySyncSamples.readOnServer(client, () -> held.isDown(player)));
        client.leaveWorld();
    }

    @ClientTest
    public static void heldKeyReadsUpOnTheServerWhenAScreenOpensOverIt(Client client) {
        Keybind held = Objects.requireNonNull(KeySyncSamples.held);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        keyboard.hold(GLFW.GLFW_KEY_F20);
        client.until("the server to read the key down", () -> KeySyncSamples.readOnServer(client, () -> held.isDown(player)));
        client.minecraft().setScreen(new KeySyncSamples.PlainScreen());
        client.until("the server to read the key up once the screen opened", () -> !KeySyncSamples.readOnServer(client, () -> held.isDown(player)));
        Assertions.assertThat(keyboard.isHeld(GLFW.GLFW_KEY_F20)).as("the key is still held").isTrue();
        keyboard.release(GLFW.GLFW_KEY_F20);
        client.minecraft().setScreen(null);
        client.leaveWorld();
    }

    @ClientTest
    public static void heldKeyReadsUpOnTheServerAfterARelog(Client client) {
        Keybind everywhere = Objects.requireNonNull(KeySyncSamples.everywhere);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        keyboard.hold(GLFW.GLFW_KEY_F21);
        client.until("the server to read the key down", () -> KeySyncSamples.readOnServer(client, () -> everywhere.isDown(player)));
        client.leaveWorld();
        Assertions.assertThat(everywhere.isDown(player)).as("the key of the player who left").isFalse();

        keyboard.release(GLFW.GLFW_KEY_F21);
        KeySyncSamples.enterLevel(client);
        ServerPlayer rejoined = KeySyncSamples.getPlayer(client);
        Assertions.assertThat(KeySyncSamples.readOnServer(client, () -> everywhere.isDown(rejoined))).as("the key after the relog").isFalse();
        client.leaveWorld();
    }

    @ClientTest
    public static void heldEverywhereKeyReadsDownOnTheServerAfterARelog(Client client) {
        Keybind everywhere = Objects.requireNonNull(KeySyncSamples.everywhere);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer first = KeySyncSamples.getPlayer(client);
        keyboard.hold(GLFW.GLFW_KEY_F21);
        client.until("the server to read the key down", () -> KeySyncSamples.readOnServer(client, () -> everywhere.isDown(first)));
        client.leaveWorld();

        KeySyncSamples.enterLevel(client);
        ServerPlayer second = KeySyncSamples.getPlayer(client);
        client.until("the server to read the key down after the relog", () -> KeySyncSamples.readOnServer(client, () -> everywhere.isDown(second)));
        keyboard.release(GLFW.GLFW_KEY_F21);
        client.until("the server to read the key up", () -> !KeySyncSamples.readOnServer(client, () -> everywhere.isDown(second)));
        client.leaveWorld();
    }

    @ClientTest
    public static void heldEverywhereKeyStaysDownThroughAReplace(Client client) {
        Keybind everywhere = Objects.requireNonNull(KeySyncSamples.everywhere);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        keyboard.hold(GLFW.GLFW_KEY_F21);
        client.until("the server to read the key down", () -> KeySyncSamples.readOnServer(client, () -> everywhere.isDown(player)));

        LocalPlayer before = client.minecraft().player;
        MinecraftServer server = player.level().getServer();
        PlayerData data = Nexus.getMod().getPersistence().getPlayerData();
        CompletableFuture<ServerPlayer> replacing = server.submit(() -> {
            data.replace(server, player.getUUID(), data.snapshot(player));
            return Objects.requireNonNull(server.getPlayerList().getPlayer(player.getUUID()));
        });
        client.until("the server to replace the player", replacing::isDone);
        ServerPlayer replaced = replacing.join();
        client.until("the client to respawn", () -> client.minecraft().player != before);
        client.ticks(KeySyncSamples.SETTLE_TICKS);
        Assertions.assertThat(KeySyncSamples.readOnServer(client, () -> everywhere.isDown(replaced))).as("the key after the replace").isTrue();

        keyboard.release(GLFW.GLFW_KEY_F21);
        client.until("the server to read the key up", () -> !KeySyncSamples.readOnServer(client, () -> everywhere.isDown(replaced)));
        client.leaveWorld();
    }

    @ClientTest
    public static void heldKeyReadsUpOnTheServerOnceThePlayerLogsOut(Client client) {
        Keybind held = Objects.requireNonNull(KeySyncSamples.held);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        keyboard.hold(GLFW.GLFW_KEY_F20);
        client.until("the server to read the key down", () -> KeySyncSamples.readOnServer(client, () -> held.isDown(player)));
        player.level().getServer().execute(() -> {
            player.disconnect();
            NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        });
        client.until("the server to read the key up once the player logged out", () -> !KeySyncSamples.readOnServer(client, () -> held.isDown(player)));
        Assertions.assertThat(keyboard.isHeld(GLFW.GLFW_KEY_F20)).as("the key is still held").isTrue();
        keyboard.release(GLFW.GLFW_KEY_F20);
        client.leaveWorld();
    }

    @ClientTest
    public static void keyOfADisconnectedPlayerDoesNothingOnTheServer(Client client) {
        Keybind departed = Objects.requireNonNull(KeySyncSamples.departed);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        KeySyncSamples.DEPARTED_RUNS.set(0);
        player.level().getServer().execute(player::disconnect);
        client.until("the server to mark the player as disconnected", player::hasDisconnected);
        keyboard.hold(GLFW.GLFW_KEY_F7);
        client.until("the key to read down on the client", departed::isDown);
        int settled = player.level().getServer().getTickCount() + KeySyncSamples.SETTLE_TICKS;
        client.until("the server to handle what the client sent", () -> player.level().getServer().getTickCount() >= settled);
        Assertions.assertThat(KeySyncSamples.DEPARTED_RUNS.get()).as("runs of the server action").isZero();
        Assertions.assertThat(KeySyncSamples.readOnServer(client, () -> departed.isDown(player))).as("the key on the server").isFalse();
        keyboard.release(GLFW.GLFW_KEY_F7);
        client.leaveWorld();
    }

    @ClientTest
    public static void serverHeldTicksAreOneOnTheFirstServerTickTheKeyIsDown(Client client) {
        Keybind held = Objects.requireNonNull(KeySyncSamples.held);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        KeySyncSamples.HELD_TICKS_ON_FIRST_TICK.clear();
        KeySyncSamples.watchingFirstTick = true;
        keyboard.hold(GLFW.GLFW_KEY_F20);
        client.until("a server tick to read the key down", () -> !KeySyncSamples.HELD_TICKS_ON_FIRST_TICK.isEmpty());
        Assertions.assertThat(KeySyncSamples.HELD_TICKS_ON_FIRST_TICK).as("server held ticks on the first server tick the key is down").containsExactly(1);
        keyboard.release(GLFW.GLFW_KEY_F20);
        client.until("the server to read the key up", () -> !KeySyncSamples.readOnServer(client, () -> held.isDown(player)));
        client.leaveWorld();
    }

    @ClientTest
    public static void releaseFiringReadsTheHeldTicksOfTheHoldOnTheServer(Client client) {
        Keybind released = Objects.requireNonNull(KeySyncSamples.released);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        KeySyncSamples.HELD_TICKS_ON_RELEASE.clear();
        keyboard.hold(GLFW.GLFW_KEY_F8);
        client.until("the server held ticks to grow", () -> KeySyncSamples.readOnServer(client, () -> released.getHeldTicks(player)) >= KeySyncSamples.GROWN_TICKS);
        keyboard.release(GLFW.GLFW_KEY_F8);
        client.until("the server action to run", () -> !KeySyncSamples.HELD_TICKS_ON_RELEASE.isEmpty());
        Assertions.assertThat(KeySyncSamples.HELD_TICKS_ON_RELEASE).as("runs of the server action").hasSize(1);
        Assertions.assertThat(KeySyncSamples.HELD_TICKS_ON_RELEASE.getFirst())
                .as("server held ticks when the release arrived")
                .isGreaterThanOrEqualTo(KeySyncSamples.GROWN_TICKS);
        client.until("the server to read the key up", () -> !KeySyncSamples.readOnServer(client, () -> released.isDown(player)));
        client.leaveWorld();
    }

    @ClientTest
    public static void serverHeldTicksStartAtOneAndGrow(Client client) {
        Keybind held = Objects.requireNonNull(KeySyncSamples.held);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        KeySyncSamples.HELD_TICKS_ON_ARRIVAL.clear();
        keyboard.hold(GLFW.GLFW_KEY_F20);
        client.until("the server action to run", () -> !KeySyncSamples.HELD_TICKS_ON_ARRIVAL.isEmpty());
        Assertions.assertThat(KeySyncSamples.HELD_TICKS_ON_ARRIVAL).as("server held ticks when the key arrived").containsExactly(1);
        client.until("the server held ticks to grow", () -> KeySyncSamples.readOnServer(client, () -> held.getHeldTicks(player)) >= KeySyncSamples.GROWN_TICKS);

        keyboard.release(GLFW.GLFW_KEY_F20);
        client.until("the server to read the key up", () -> !KeySyncSamples.readOnServer(client, () -> held.isDown(player)));
        Assertions.assertThat(KeySyncSamples.readOnServer(client, () -> held.getHeldTicks(player))).as("server held ticks after the release").isZero();
        client.leaveWorld();
    }

    @ClientTest
    public static void serverProgressOfAHeldForKeyReachesOne(Client client) {
        Keybind charged = Objects.requireNonNull(KeySyncSamples.charged);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        keyboard.hold(GLFW.GLFW_KEY_F22);
        client.until("the server progress to reach one", () -> KeySyncSamples.readOnServer(client, () -> charged.getProgress(player)) >= 1.0F);
        Assertions.assertThat(KeySyncSamples.readOnServer(client, () -> charged.getProgress(player))).as("the server progress of the charged key").isEqualTo(1.0F);
        keyboard.release(GLFW.GLFW_KEY_F22);
        client.until("the server to read the key up", () -> !KeySyncSamples.readOnServer(client, () -> charged.isDown(player)));
        client.leaveWorld();
    }

    @ClientTest
    public static void existingJumpRunsTheServerActionWhileThePlayerJumps(Client client) {
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        KeySyncSamples.JUMPS.set(0);
        keyboard.hold(GLFW.GLFW_KEY_SPACE);
        client.until("the server action to run", () -> KeySyncSamples.JUMPS.get() > 0);
        LocalPlayer player = Objects.requireNonNull(client.minecraft().player);
        Assertions.assertThat(player.input.keyPresses.jump()).as("the jump input while the key is held").isTrue();
        keyboard.release(GLFW.GLFW_KEY_SPACE);
        client.ticks(KeySyncSamples.SETTLE_TICKS);
        Assertions.assertThat(KeySyncSamples.JUMPS.get()).as("runs of the server action").isEqualTo(1);
        client.leaveWorld();
    }

    @ClientTest
    public static void existingScreenMappingRunsTheServerActionInAScreen(Client client) {
        KeySyncSamples.enterLevel(client);
        KeySyncSamples.FOREIGN_RUNS.set(0);
        client.minecraft().setScreen(new KeySyncSamples.PlainScreen());
        client.keyboard().press(GLFW.GLFW_KEY_F6);
        client.until("the server action to run", () -> KeySyncSamples.FOREIGN_RUNS.get() > 0);
        client.ticks(KeySyncSamples.SETTLE_TICKS);
        Assertions.assertThat(KeySyncSamples.FOREIGN_RUNS.get()).as("runs of the server action").isEqualTo(1);
        client.minecraft().setScreen(null);
        client.leaveWorld();
    }

    @ClientTest
    public static void existingInventoryKeyRunsTheServerActionAsTheInventoryOpens(Client client) {
        KeySyncSamples.enterLevel(client);
        KeySyncSamples.INVENTORY_RUNS.set(0);
        client.keyboard().press(GLFW.GLFW_KEY_E);
        client.until("the inventory to open", () -> client.screen() != null);
        client.until("the server action to run", () -> KeySyncSamples.INVENTORY_RUNS.get() > 0);
        client.ticks(KeySyncSamples.SETTLE_TICKS);
        Assertions.assertThat(KeySyncSamples.INVENTORY_RUNS.get()).as("runs of the server action").isEqualTo(1);
        client.minecraft().setScreen(null);
        client.leaveWorld();
    }

    @ClientTest
    public static void existingNameWithoutAMappingStaysInert(Client client) {
        Keybind missing = Objects.requireNonNull(KeySyncSamples.missing);
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        KeySyncSamples.MISSING_RUNS.set(0);
        client.keyboard().press(GLFW.GLFW_KEY_F19);
        client.ticks(KeySyncSamples.SETTLE_TICKS);
        Assertions.assertThat(missing.isDown()).as("the key on the client").isFalse();
        Assertions.assertThat(KeySyncSamples.readOnServer(client, () -> missing.isDown(player))).as("the key on the server").isFalse();
        Assertions.assertThat(KeySyncSamples.MISSING_RUNS.get()).as("runs of either action").isZero();
        client.leaveWorld();
    }

    private static void enterLevel(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
    }

    private static ServerPlayer getPlayer(Client client) {
        ServerLevel level = client.serverLevel();
        return KeySyncSamples.readOnServer(client, () -> level.players().getFirst());
    }

    private static <VALUE> VALUE readOnServer(Client client, Supplier<VALUE> read) {
        AtomicReference<VALUE> value = new AtomicReference<>();
        client.onServer(_ -> value.set(read.get()));
        return value.get();
    }

    private static void onPressed(ServerPlayer player) {
        KeySyncSamples.SIDES.add(KeySyncSamples.SERVER_SIDE);
        KeySyncSamples.PRESSERS.add(player);
    }

    private static Integer readSide() {
        KeySyncSamples.SIDES.add(KeySyncSamples.READER_SIDE);
        return 1;
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Keybinds keybinds = Nexus.getMod().getKeybinds();
            Identifier category = keybinds.category("key_sync_samples", "Key Sync Samples");
            keybinds.key("key_sync_samples_pressed", "Pressed")
                    .defaultKey(GLFW.GLFW_KEY_F19)
                    .onClient(() -> () -> KeySyncSamples.SIDES.add(KeySyncSamples.CLIENT_SIDE))
                    .carry(NexusCodec.INT, () -> KeySyncSamples::readSide)
                    .onServer((player, _) -> KeySyncSamples.onPressed(player))
                    .category(category)
                    .register();
            KeySyncSamples.held = keybinds.key("key_sync_samples_held", "Held")
                    .defaultKey(GLFW.GLFW_KEY_F20)
                    .syncHeld()
                    .onServer(player -> KeySyncSamples.HELD_TICKS_ON_ARRIVAL.add(Objects.requireNonNull(KeySyncSamples.held).getHeldTicks(player)))
                    .category(category)
                    .register();
            KeySyncSamples.everywhere = keybinds.key("key_sync_samples_everywhere", "Everywhere")
                    .defaultKey(GLFW.GLFW_KEY_F21)
                    .context(Keybind.Context.EVERYWHERE)
                    .syncHeld()
                    .category(category)
                    .register();
            KeySyncSamples.charged = keybinds.key("key_sync_samples_charged", "Charged")
                    .defaultKey(GLFW.GLFW_KEY_F22)
                    .activation(Activation.heldFor(KeySyncSamples.CHARGE_TICKS))
                    .syncHeld()
                    .category(category)
                    .register();
            keybinds.existing("key.jump")
                    .onServer(_ -> KeySyncSamples.JUMPS.incrementAndGet())
                    .register();
            KeySyncSamples.missing = keybinds.existing("key.key_sync_samples_missing")
                    .syncHeld()
                    .onClient(() -> KeySyncSamples.MISSING_RUNS::incrementAndGet)
                    .onServer(_ -> KeySyncSamples.MISSING_RUNS.incrementAndGet())
                    .register();
            KeySyncSamples.departed = keybinds.key("key_sync_samples_departed", "Departed")
                    .defaultKey(GLFW.GLFW_KEY_F7)
                    .syncHeld()
                    .onServer(_ -> KeySyncSamples.DEPARTED_RUNS.incrementAndGet())
                    .category(category)
                    .register();
            KeySyncSamples.released = keybinds.key("key_sync_samples_released", "Released")
                    .defaultKey(GLFW.GLFW_KEY_F8)
                    .activation(Activation.release())
                    .syncHeld()
                    .onServer(player -> KeySyncSamples.HELD_TICKS_ON_RELEASE.add(Objects.requireNonNull(KeySyncSamples.released).getHeldTicks(player)))
                    .category(category)
                    .register();
            keybinds.existing(KeySyncSamples.FOREIGN_MAPPING)
                    .onServer(_ -> KeySyncSamples.FOREIGN_RUNS.incrementAndGet())
                    .register();
            keybinds.existing("key.inventory")
                    .onServer(_ -> KeySyncSamples.INVENTORY_RUNS.incrementAndGet())
                    .register();
        }

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            Keybind held = KeySyncSamples.held;
            if (!KeySyncSamples.watchingFirstTick || held == null) {
                return;
            }

            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (held.isDown(player)) {
                    KeySyncSamples.watchingFirstTick = false;
                    KeySyncSamples.HELD_TICKS_ON_FIRST_TICK.add(held.getHeldTicks(player));
                }
            }
        }
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID, value = Dist.CLIENT)
    public static final class Mappings {

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(new KeyMapping(KeySyncSamples.FOREIGN_MAPPING, KeyConflictContext.GUI, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, KeyMapping.Category.MISC));
        }
    }

    private static final class PlainScreen extends Screen {

        private PlainScreen() {
            super(Component.empty());
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
