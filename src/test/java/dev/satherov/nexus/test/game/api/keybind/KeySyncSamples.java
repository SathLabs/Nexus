package dev.satherov.nexus.test.game.api.keybind;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.client.Keyboard;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

///
/// The client tests that check keys reach the server with their firings and their held state.
///
public class KeySyncSamples {

    private static final int SETTLE_TICKS = 5;
    private static final int GROWN_TICKS = 5;
    private static final int CHARGE_TICKS = 4;
    private static final String CLIENT_SIDE = "client";
    private static final String SERVER_SIDE = "server";
    private static final List<String> SIDES = new CopyOnWriteArrayList<>();
    private static final List<ServerPlayer> PRESSERS = new CopyOnWriteArrayList<>();
    private static final List<Integer> HELD_TICKS_ON_ARRIVAL = new CopyOnWriteArrayList<>();
    private static final AtomicInteger JUMPS = new AtomicInteger();
    private static final AtomicInteger MISSING_RUNS = new AtomicInteger();

    private static @Nullable Keybind held;
    private static @Nullable Keybind everywhere;
    private static @Nullable Keybind charged;
    private static @Nullable Keybind missing;

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
        Assertions.assertThat(KeySyncSamples.SIDES).as("sides in the order they ran").containsExactly(KeySyncSamples.CLIENT_SIDE, KeySyncSamples.SERVER_SIDE);
        client.leaveWorld();
    }

    @ClientTest
    public static void heldKeyReadsDownOnTheServerUntilItsRelease(Client client) {
        Keybind held = Objects.requireNonNull(KeySyncSamples.held);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        keyboard.hold(GLFW.GLFW_KEY_F20);
        client.until("the server to read the key down", () -> held.isDown(player));
        keyboard.release(GLFW.GLFW_KEY_F20);
        client.until("the server to read the key up", () -> !held.isDown(player));
        client.leaveWorld();
    }

    @ClientTest
    public static void heldKeyReadsUpOnTheServerWhenAScreenOpensOverIt(Client client) {
        Keybind held = Objects.requireNonNull(KeySyncSamples.held);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        keyboard.hold(GLFW.GLFW_KEY_F20);
        client.until("the server to read the key down", () -> held.isDown(player));
        client.minecraft().setScreen(new KeySyncSamples.PlainScreen());
        client.until("the server to read the key up once the screen opened", () -> !held.isDown(player));
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
        client.until("the server to read the key down", () -> everywhere.isDown(player));
        client.leaveWorld();
        Assertions.assertThat(everywhere.isDown(player)).as("the key of the player who left").isFalse();

        keyboard.release(GLFW.GLFW_KEY_F21);
        KeySyncSamples.enterLevel(client);
        Assertions.assertThat(everywhere.isDown(KeySyncSamples.getPlayer(client))).as("the key after the relog").isFalse();
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
        client.until("the server held ticks to grow", () -> held.getHeldTicks(player) >= KeySyncSamples.GROWN_TICKS);

        keyboard.release(GLFW.GLFW_KEY_F20);
        client.until("the server to read the key up", () -> !held.isDown(player));
        Assertions.assertThat(held.getHeldTicks(player)).as("server held ticks after the release").isZero();
        client.leaveWorld();
    }

    @ClientTest
    public static void serverProgressOfAHeldForKeyReachesOne(Client client) {
        Keybind charged = Objects.requireNonNull(KeySyncSamples.charged);
        Keyboard keyboard = client.keyboard();
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        keyboard.hold(GLFW.GLFW_KEY_F22);
        client.until("the server progress to reach one", () -> charged.getProgress(player) >= 1.0F);
        Assertions.assertThat(charged.getProgress(player)).as("the server progress of the charged key").isEqualTo(1.0F);
        keyboard.release(GLFW.GLFW_KEY_F22);
        client.until("the server to read the key up", () -> !charged.isDown(player));
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
    public static void existingNameWithoutAMappingStaysInert(Client client) {
        Keybind missing = Objects.requireNonNull(KeySyncSamples.missing);
        KeySyncSamples.enterLevel(client);
        ServerPlayer player = KeySyncSamples.getPlayer(client);
        KeySyncSamples.MISSING_RUNS.set(0);
        client.keyboard().press(GLFW.GLFW_KEY_F19);
        client.ticks(KeySyncSamples.SETTLE_TICKS);
        Assertions.assertThat(missing.isDown()).as("the key on the client").isFalse();
        Assertions.assertThat(missing.isDown(player)).as("the key on the server").isFalse();
        Assertions.assertThat(KeySyncSamples.MISSING_RUNS.get()).as("runs of either action").isZero();
        client.leaveWorld();
    }

    private static void enterLevel(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
    }

    private static ServerPlayer getPlayer(Client client) {
        return client.serverLevel().players().getFirst();
    }

    private static void onPressed(ServerPlayer player) {
        KeySyncSamples.SIDES.add(KeySyncSamples.SERVER_SIDE);
        KeySyncSamples.PRESSERS.add(player);
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
                    .onServer(KeySyncSamples::onPressed)
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
