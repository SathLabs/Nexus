package dev.satherov.nexus.test.game.api.keybind;

import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.client.Keyboard;
import dev.satherov.nexus.gametest.api.measurement.Measured;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

///
/// Measures the time the listeners of the client tick take with a few hundred declared keys, idle and with a few held.
///
@Slf4j
public class KeyTickSpeedSamples {

    private static final int UNBOUND_KEYS = 300;
    private static final int[] BOUND_KEYS = { GLFW.GLFW_KEY_F13, GLFW.GLFW_KEY_F14, GLFW.GLFW_KEY_F15, GLFW.GLFW_KEY_F16 };
    private static final int WARMUP_TICKS = 200;
    private static final int TICKS = 400;
    private static final int MEASURED_FRAMES = 400;
    private static final List<Keybind> BOUND = new ArrayList<>();

    private static long @Nullable [] nanos;
    private static int recorded;
    private static long started;

    @ClientTest(required = false, maxFrames = 3000)
    @Measured(KeyTickSpeedSamples.MEASURED_FRAMES)
    public static void timesTheKeyPass(Client client) {
        Keyboard keyboard = client.keyboard();
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
        client.ticks(KeyTickSpeedSamples.WARMUP_TICKS);
        long[] idle = KeyTickSpeedSamples.record(client);

        for (int key : KeyTickSpeedSamples.BOUND_KEYS) {
            keyboard.hold(key);
        }

        client.until("the bound keys to read down", () -> KeyTickSpeedSamples.BOUND.stream().allMatch(Keybind::isDown));
        long[] held = KeyTickSpeedSamples.record(client);
        for (int key : KeyTickSpeedSamples.BOUND_KEYS) {
            keyboard.release(key);
        }

        client.until("the bound keys to read up", () -> KeyTickSpeedSamples.BOUND.stream().noneMatch(Keybind::isDown));
        KeyTickSpeedSamples.log.info(
                "The client tick listeners over '{}' ticks with '{}' keys of the samples:{}{}",
                KeyTickSpeedSamples.TICKS,
                KeyTickSpeedSamples.UNBOUND_KEYS + KeyTickSpeedSamples.BOUND_KEYS.length,
                KeyTickSpeedSamples.summary("idle", idle),
                KeyTickSpeedSamples.summary("held", held)
        );
    }

    private static long[] record(Client client) {
        KeyTickSpeedSamples.nanos = new long[KeyTickSpeedSamples.TICKS];
        KeyTickSpeedSamples.recorded = 0;
        client.until("the ticks to be recorded", () -> KeyTickSpeedSamples.recorded == KeyTickSpeedSamples.TICKS);
        long[] series = Objects.requireNonNull(KeyTickSpeedSamples.nanos);
        KeyTickSpeedSamples.nanos = null;
        return series;
    }

    private static String summary(String name, long[] nanos) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        return String.format(
                Locale.ROOT,
                "%n%-5s median '%d' ns, mean '%d' ns, p90 '%d' ns, max '%d' ns",
                name,
                sorted[sorted.length / 2],
                (long) Arrays.stream(sorted).average().orElseThrow(),
                sorted[sorted.length * 9 / 10],
                sorted[sorted.length - 1]
        );
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Keybinds keybinds = Nexus.getMod().getKeybinds();
            Identifier category = keybinds.category("key_tick_speed_samples", "Key Tick Speed Samples");
            for (int index = 0; index < KeyTickSpeedSamples.UNBOUND_KEYS; index++) {
                keybinds.key("key_tick_speed_samples_unbound_" + index, "Unbound " + index)
                        .category(category)
                        .register();
            }

            for (int key : KeyTickSpeedSamples.BOUND_KEYS) {
                KeyTickSpeedSamples.BOUND.add(keybinds.key("key_tick_speed_samples_bound_" + key, "Bound " + key)
                        .defaultKey(key)
                        .category(category)
                        .register());
            }
        }
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID, value = Dist.CLIENT)
    public static final class Pass {

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void onTickStart(ClientTickEvent.Post event) {
            KeyTickSpeedSamples.started = System.nanoTime();
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onTickEnd(ClientTickEvent.Post event) {
            long[] nanos = KeyTickSpeedSamples.nanos;
            if (nanos != null && KeyTickSpeedSamples.recorded < nanos.length) {
                nanos[KeyTickSpeedSamples.recorded++] = System.nanoTime() - KeyTickSpeedSamples.started;
            }
        }
    }
}
