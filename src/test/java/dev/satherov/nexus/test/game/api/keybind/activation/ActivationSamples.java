package dev.satherov.nexus.test.game.api.keybind.activation;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.client.Keyboard;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

///
/// The client tests that check keys fire through their activation and read back their held ticks and progress.
///
public class ActivationSamples {

    private static final int SETTLE_TICKS = 10;
    private static final int HELD_FOR_TICKS = 5;
    private static final int REPEAT_INTERVAL_TICKS = 3;
    private static final int PROGRESS_TICKS = 4;
    private static final int DOUBLE_TAP_TICKS = 10;
    private static final AtomicInteger TICKS = new AtomicInteger();
    private static final AtomicInteger RELEASES = new AtomicInteger();
    private static final AtomicInteger TAPS = new AtomicInteger();
    private static final AtomicInteger DOUBLE_TAPS = new AtomicInteger();
    private static final List<Integer> HELD_FOR_FIRINGS = new ArrayList<>();
    private static final List<Integer> REPEAT_FIRINGS = new ArrayList<>();
    private static final List<Integer> TAP_FIRINGS = new ArrayList<>();

    private static @Nullable Keybind release;
    private static @Nullable Keybind heldFor;
    private static @Nullable Keybind repeat;
    private static @Nullable Keybind tap;
    private static @Nullable Keybind progress;
    private static @Nullable List<ActivationSamples.Reading> readings;

    @ClientTest
    public static void releaseFiresOnKeyUp(Client client) {
        Keybind release = Objects.requireNonNull(ActivationSamples.release);
        Keyboard keyboard = client.keyboard();
        ActivationSamples.enterLevel(client);
        ActivationSamples.RELEASES.set(0);
        keyboard.hold(GLFW.GLFW_KEY_F13);
        client.until("the key to read down", release::isDown);
        ActivationSamples.waitTicks(client, ActivationSamples.SETTLE_TICKS);
        Assertions.assertThat(ActivationSamples.RELEASES.get()).as("runs while the key is held").isZero();

        keyboard.release(GLFW.GLFW_KEY_F13);
        client.until("the client action to run on key up", () -> ActivationSamples.RELEASES.get() > 0);
        ActivationSamples.waitTicks(client, ActivationSamples.SETTLE_TICKS);
        Assertions.assertThat(ActivationSamples.RELEASES.get()).as("runs after key up").isEqualTo(1);
        client.leaveWorld();
    }

    @ClientTest
    public static void releaseDoesNotFireWhenAScreenOpensOverTheHeldKey(Client client) {
        Keybind release = Objects.requireNonNull(ActivationSamples.release);
        Keyboard keyboard = client.keyboard();
        ActivationSamples.enterLevel(client);
        ActivationSamples.RELEASES.set(0);
        keyboard.hold(GLFW.GLFW_KEY_F13);
        client.until("the key to read down", release::isDown);
        client.minecraft().setScreen(new ActivationSamples.PlainScreen());
        client.until("the key to go up once the screen opened", () -> !release.isDown());
        ActivationSamples.waitTicks(client, ActivationSamples.SETTLE_TICKS);
        keyboard.release(GLFW.GLFW_KEY_F13);
        client.minecraft().setScreen(null);
        ActivationSamples.waitTicks(client, ActivationSamples.SETTLE_TICKS);
        Assertions.assertThat(ActivationSamples.RELEASES.get()).as("runs after the screen opened over the key").isZero();
        client.leaveWorld();
    }

    @ClientTest
    public static void heldForFiresOnceOnTheFifthTick(Client client) {
        Keyboard keyboard = client.keyboard();
        ActivationSamples.enterLevel(client);
        ActivationSamples.HELD_FOR_FIRINGS.clear();
        keyboard.hold(GLFW.GLFW_KEY_F14);
        client.until("the client action to run while the key is held", () -> !ActivationSamples.HELD_FOR_FIRINGS.isEmpty());
        ActivationSamples.waitTicks(client, ActivationSamples.SETTLE_TICKS);
        keyboard.release(GLFW.GLFW_KEY_F14);
        ActivationSamples.waitTicks(client, ActivationSamples.SETTLE_TICKS);
        Assertions.assertThat(ActivationSamples.HELD_FOR_FIRINGS).as("held ticks at each run").containsExactly(ActivationSamples.HELD_FOR_TICKS);
        client.leaveWorld();
    }

    @ClientTest
    public static void heldForDoesNotFireOnAnEarlierRelease(Client client) {
        Keybind heldFor = Objects.requireNonNull(ActivationSamples.heldFor);
        Keyboard keyboard = client.keyboard();
        ActivationSamples.enterLevel(client);
        ActivationSamples.HELD_FOR_FIRINGS.clear();
        keyboard.hold(GLFW.GLFW_KEY_F14);
        client.until("the key to read down", heldFor::isDown);
        Assertions.assertThat(heldFor.getHeldTicks()).as("held ticks before the release").isLessThan(ActivationSamples.HELD_FOR_TICKS);
        keyboard.release(GLFW.GLFW_KEY_F14);
        ActivationSamples.waitTicks(client, ActivationSamples.SETTLE_TICKS);
        Assertions.assertThat(ActivationSamples.HELD_FOR_FIRINGS).as("held ticks at each run").isEmpty();
        client.leaveWorld();
    }

    @ClientTest
    public static void repeatFiresOnHeldTicksOneFourAndSeven(Client client) {
        Keyboard keyboard = client.keyboard();
        ActivationSamples.enterLevel(client);
        ActivationSamples.REPEAT_FIRINGS.clear();
        keyboard.hold(GLFW.GLFW_KEY_F15);
        client.until("the client action to run three times", () -> ActivationSamples.REPEAT_FIRINGS.size() >= 3);
        keyboard.release(GLFW.GLFW_KEY_F15);
        ActivationSamples.waitTicks(client, ActivationSamples.SETTLE_TICKS);
        Assertions.assertThat(ActivationSamples.REPEAT_FIRINGS).as("held ticks at each run").containsExactly(1, 4, 7);
        client.leaveWorld();
    }

    @ClientTest
    public static void tapInsideOneTickFiresPressOnce(Client client) {
        ActivationSamples.enterLevel(client);
        ActivationSamples.TAP_FIRINGS.clear();
        client.keyboard().press(GLFW.GLFW_KEY_F16);
        client.until("the client action to run", () -> !ActivationSamples.TAP_FIRINGS.isEmpty());
        ActivationSamples.waitTicks(client, ActivationSamples.SETTLE_TICKS);
        Assertions.assertThat(ActivationSamples.TAP_FIRINGS).as("held ticks at each run").containsExactly(1);
        Assertions.assertThat(Objects.requireNonNull(ActivationSamples.tap).isDown()).as("the key after the tap").isFalse();
        client.leaveWorld();
    }

    @ClientTest
    public static void doubleTapOverIdleTicksFires(Client client) {
        Keyboard keyboard = client.keyboard();
        ActivationSamples.enterLevel(client);
        ActivationSamples.waitTicks(client, ActivationSamples.DOUBLE_TAP_TICKS + 1);
        ActivationSamples.DOUBLE_TAPS.set(0);
        keyboard.press(GLFW.GLFW_KEY_F17);
        ActivationSamples.waitTicks(client, 3);
        Assertions.assertThat(ActivationSamples.DOUBLE_TAPS.get()).as("runs after the first tap").isZero();

        keyboard.press(GLFW.GLFW_KEY_F17);
        client.until("the client action to run on the second tap", () -> ActivationSamples.DOUBLE_TAPS.get() > 0);
        ActivationSamples.waitTicks(client, ActivationSamples.SETTLE_TICKS);
        Assertions.assertThat(ActivationSamples.DOUBLE_TAPS.get()).as("runs after the second tap").isEqualTo(1);
        client.leaveWorld();
    }

    @ClientTest
    public static void heldTicksAndProgressReadBackPerTick(Client client) {
        Keybind progress = Objects.requireNonNull(ActivationSamples.progress);
        Keyboard keyboard = client.keyboard();
        ActivationSamples.enterLevel(client);
        List<ActivationSamples.Reading> readings = new ArrayList<>();
        ActivationSamples.readings = readings;
        try {
            keyboard.hold(GLFW.GLFW_KEY_F18);
            client.until("the key to be held for six ticks", () -> progress.getHeldTicks() >= 6);
            keyboard.release(GLFW.GLFW_KEY_F18);
            client.until("the key to go up", () -> !progress.isDown());
            ActivationSamples.waitTicks(client, 2);
        } finally {
            ActivationSamples.readings = null;
        }

        Assertions.assertThat(readings)
                .containsSequence(
                        new ActivationSamples.Reading(1, 0.25F),
                        new ActivationSamples.Reading(2, 0.5F),
                        new ActivationSamples.Reading(3, 0.75F),
                        new ActivationSamples.Reading(4, 1.0F),
                        new ActivationSamples.Reading(5, 1.0F)
                )
                .last()
                .isEqualTo(new ActivationSamples.Reading(0, 0.0F));
        client.leaveWorld();
    }

    private static void enterLevel(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
    }

    private static void waitTicks(Client client, int count) {
        int target = ActivationSamples.TICKS.get() + count;
        client.until(count + " client ticks", () -> ActivationSamples.TICKS.get() >= target);
    }

    private static void record(List<Integer> firings, @Nullable Keybind keybind) {
        firings.add(Objects.requireNonNull(keybind).getHeldTicks());
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Keybinds keybinds = Nexus.getMod().getKeybinds();
            Identifier category = keybinds.category("activation_samples", "Activation Samples");
            ActivationSamples.release = keybinds.key("activation_samples_release", "Release")
                    .defaultKey(GLFW.GLFW_KEY_F13)
                    .activation(Activation.release())
                    .onClient(() -> ActivationSamples.RELEASES::incrementAndGet)
                    .category(category)
                    .register();
            ActivationSamples.heldFor = keybinds.key("activation_samples_held_for", "Held For")
                    .defaultKey(GLFW.GLFW_KEY_F14)
                    .activation(Activation.heldFor(ActivationSamples.HELD_FOR_TICKS))
                    .onClient(() -> () -> ActivationSamples.record(ActivationSamples.HELD_FOR_FIRINGS, ActivationSamples.heldFor))
                    .category(category)
                    .register();
            ActivationSamples.repeat = keybinds.key("activation_samples_repeat", "Repeat")
                    .defaultKey(GLFW.GLFW_KEY_F15)
                    .activation(Activation.repeat(ActivationSamples.REPEAT_INTERVAL_TICKS))
                    .onClient(() -> () -> ActivationSamples.record(ActivationSamples.REPEAT_FIRINGS, ActivationSamples.repeat))
                    .category(category)
                    .register();
            ActivationSamples.tap = keybinds.key("activation_samples_tap", "Tap")
                    .defaultKey(GLFW.GLFW_KEY_F16)
                    .activation(Activation.press())
                    .onClient(() -> () -> ActivationSamples.record(ActivationSamples.TAP_FIRINGS, ActivationSamples.tap))
                    .category(category)
                    .register();
            keybinds.key("activation_samples_double_tap", "Double Tap")
                    .defaultKey(GLFW.GLFW_KEY_F17)
                    .activation(tick -> tick.down() && !tick.wasDown() && tick.idleTicks() <= ActivationSamples.DOUBLE_TAP_TICKS)
                    .onClient(() -> ActivationSamples.DOUBLE_TAPS::incrementAndGet)
                    .category(category)
                    .register();
            ActivationSamples.progress = keybinds.key("activation_samples_progress", "Progress")
                    .defaultKey(GLFW.GLFW_KEY_F18)
                    .activation(Activation.heldFor(ActivationSamples.PROGRESS_TICKS))
                    .category(category)
                    .register();
        }
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID, value = Dist.CLIENT)
    public static final class Ticks {

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onClientTick(ClientTickEvent.Post event) {
            ActivationSamples.TICKS.incrementAndGet();
            List<ActivationSamples.Reading> readings = ActivationSamples.readings;
            if (readings != null) {
                Keybind progress = Objects.requireNonNull(ActivationSamples.progress);
                readings.add(new ActivationSamples.Reading(progress.getHeldTicks(), progress.getProgress()));
            }
        }
    }

    private record Reading(int heldTicks, float progress) { }

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
