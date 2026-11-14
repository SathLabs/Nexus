package dev.satherov.nexus.test.game.api.event.keybind;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.event.keybind.KeyActivatedEvent;
import dev.satherov.nexus.api.event.keybind.KeyReceivedEvent;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.client.Keyboard;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

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
/// The client tests that check the events posted when a key fires and when its firing arrives.
///
public class KeyEventSamples {

    private static final int SETTLE_TICKS = 5;
    private static final AtomicInteger CLIENT_RUNS = new AtomicInteger();
    private static final AtomicInteger SERVER_RUNS = new AtomicInteger();
    private static final AtomicInteger CANCELLED_ACTIVATIONS = new AtomicInteger();
    private static final List<Keybind> ACTIVATED = new CopyOnWriteArrayList<>();
    private static final List<Keybind> RECEIVED = new CopyOnWriteArrayList<>();
    private static final List<ServerPlayer> RECEIVERS = new CopyOnWriteArrayList<>();

    private static @Nullable Keybind cancelledActivation;
    private static @Nullable Keybind passed;
    private static @Nullable Keybind cancelledArrival;

    @ClientTest
    public static void cancelledActivationSkipsTheClientActionAndTheSend(Client client) {
        KeyEventSamples.enterLevel(client);
        KeyEventSamples.reset();
        client.keyboard().press(GLFW.GLFW_KEY_KP_ADD);
        client.until("the activation to be cancelled", () -> KeyEventSamples.CANCELLED_ACTIVATIONS.get() > 0);
        client.ticks(KeyEventSamples.SETTLE_TICKS);
        Assertions.assertThat(KeyEventSamples.CLIENT_RUNS.get()).as("runs of the client action").isZero();
        Assertions.assertThat(KeyEventSamples.SERVER_RUNS.get()).as("runs of the server action").isZero();
        Assertions.assertThat(KeyEventSamples.RECEIVED).as("keys whose firing arrived").isEmpty();
        client.leaveWorld();
    }

    @ClientTest
    public static void activatedEventCarriesTheKeyThatFired(Client client) {
        Keybind passed = Objects.requireNonNull(KeyEventSamples.passed);
        KeyEventSamples.enterLevel(client);
        KeyEventSamples.reset();
        client.keyboard().press(GLFW.GLFW_KEY_KP_SUBTRACT);
        client.until("the server action to run", () -> KeyEventSamples.SERVER_RUNS.get() > 0);
        client.ticks(KeyEventSamples.SETTLE_TICKS);
        Assertions.assertThat(KeyEventSamples.ACTIVATED).as("keys of the activated events").containsExactly(passed);
        Assertions.assertThat(KeyEventSamples.CLIENT_RUNS.get()).as("runs of the client action").isEqualTo(1);
        client.leaveWorld();
    }

    @ClientTest
    public static void receivedEventCarriesTheJoinedPlayer(Client client) {
        Keybind passed = Objects.requireNonNull(KeyEventSamples.passed);
        KeyEventSamples.enterLevel(client);
        ServerPlayer player = KeyEventSamples.getPlayer(client);
        KeyEventSamples.reset();
        client.keyboard().press(GLFW.GLFW_KEY_KP_SUBTRACT);
        client.until("the server action to run", () -> KeyEventSamples.SERVER_RUNS.get() > 0);
        client.ticks(KeyEventSamples.SETTLE_TICKS);
        Assertions.assertThat(KeyEventSamples.RECEIVED).as("keys of the received events").containsExactly(passed);
        Assertions.assertThat(KeyEventSamples.RECEIVERS).as("players of the received events").containsExactly(player);
        client.leaveWorld();
    }

    @ClientTest
    public static void cancelledArrivalSkipsTheServerActionAndKeepsTheHeldState(Client client) {
        Keybind cancelledArrival = Objects.requireNonNull(KeyEventSamples.cancelledArrival);
        Keyboard keyboard = client.keyboard();
        KeyEventSamples.enterLevel(client);
        ServerPlayer player = KeyEventSamples.getPlayer(client);
        KeyEventSamples.reset();
        keyboard.hold(GLFW.GLFW_KEY_KP_MULTIPLY);
        client.until("the firing to arrive", () -> KeyEventSamples.RECEIVED.contains(cancelledArrival));
        client.ticks(KeyEventSamples.SETTLE_TICKS);
        Assertions.assertThat(KeyEventSamples.SERVER_RUNS.get()).as("runs of the server action").isZero();
        Assertions.assertThat(cancelledArrival.isDown(player)).as("the key on the server while it is held").isTrue();

        keyboard.release(GLFW.GLFW_KEY_KP_MULTIPLY);
        client.until("the server to read the key up", () -> !cancelledArrival.isDown(player));
        client.leaveWorld();
    }

    private static void enterLevel(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
    }

    private static ServerPlayer getPlayer(Client client) {
        return client.serverLevel().players().getFirst();
    }

    private static void reset() {
        KeyEventSamples.CLIENT_RUNS.set(0);
        KeyEventSamples.SERVER_RUNS.set(0);
        KeyEventSamples.CANCELLED_ACTIVATIONS.set(0);
        KeyEventSamples.ACTIVATED.clear();
        KeyEventSamples.RECEIVED.clear();
        KeyEventSamples.RECEIVERS.clear();
    }

    private static boolean isSampleKey(Keybind keybind) {
        return keybind == KeyEventSamples.cancelledActivation || keybind == KeyEventSamples.passed || keybind == KeyEventSamples.cancelledArrival;
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Keybinds keybinds = Nexus.getMod().getKeybinds();
            Identifier category = keybinds.category("key_event_samples", "Key Event Samples");
            KeyEventSamples.cancelledActivation = keybinds.key("key_event_samples_cancelled_activation", "Cancelled Activation")
                    .defaultKey(GLFW.GLFW_KEY_KP_ADD)
                    .onClient(() -> KeyEventSamples.CLIENT_RUNS::incrementAndGet)
                    .onServer(_ -> KeyEventSamples.SERVER_RUNS.incrementAndGet())
                    .category(category)
                    .register();
            KeyEventSamples.passed = keybinds.key("key_event_samples_passed", "Passed")
                    .defaultKey(GLFW.GLFW_KEY_KP_SUBTRACT)
                    .onClient(() -> KeyEventSamples.CLIENT_RUNS::incrementAndGet)
                    .onServer(_ -> KeyEventSamples.SERVER_RUNS.incrementAndGet())
                    .category(category)
                    .register();
            KeyEventSamples.cancelledArrival = keybinds.key("key_event_samples_cancelled_arrival", "Cancelled Arrival")
                    .defaultKey(GLFW.GLFW_KEY_KP_MULTIPLY)
                    .syncHeld()
                    .onServer(_ -> KeyEventSamples.SERVER_RUNS.incrementAndGet())
                    .category(category)
                    .register();
        }

        @SubscribeEvent
        public static void onKeyActivated(KeyActivatedEvent event) {
            Keybind keybind = event.getKeybind();
            if (!KeyEventSamples.isSampleKey(keybind)) {
                return;
            }

            if (keybind == KeyEventSamples.cancelledActivation) {
                event.setCanceled(true);
                KeyEventSamples.CANCELLED_ACTIVATIONS.incrementAndGet();
                return;
            }

            KeyEventSamples.ACTIVATED.add(keybind);
        }

        @SubscribeEvent
        public static void onKeyReceived(KeyReceivedEvent event) {
            Keybind keybind = event.getKeybind();
            if (!KeyEventSamples.isSampleKey(keybind)) {
                return;
            }

            KeyEventSamples.RECEIVED.add(keybind);
            KeyEventSamples.RECEIVERS.add(event.getPlayer());
            if (keybind == KeyEventSamples.cancelledArrival) {
                event.setCanceled(true);
            }
        }
    }
}
