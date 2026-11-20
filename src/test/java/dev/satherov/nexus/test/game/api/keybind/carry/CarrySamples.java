package dev.satherov.nexus.test.game.api.keybind.carry;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.vanilla.VanillaCodecs;
import dev.satherov.nexus.api.keybind.Keybind;
import dev.satherov.nexus.api.keybind.KeybindBuilder;
import dev.satherov.nexus.api.keybind.Keybinds;
import dev.satherov.nexus.api.keybind.carry.Carry1;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

///
/// The client tests that check keys carry typed values to the server.
///
public class CarrySamples {

    private static final int SETTLE_TICKS = 5;
    private static final BlockPos POS = new BlockPos(6, 6, 6);
    private static final Identifier ID = Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "carry_samples");
    private static final List<List<Object>> ARRIVED = new CopyOnWriteArrayList<>();
    private static final AtomicInteger CLIENT_RUNS = new AtomicInteger();
    private static final AtomicInteger SERVER_RUNS = new AtomicInteger();
    private static final AtomicInteger OFFLINE_RUNS = new AtomicInteger();
    private static final AtomicInteger OFFLINE_READS = new AtomicInteger();

    private static @Nullable Keybind unfinished;

    @ClientTest
    public static void oneValueArrivesTyped(Client client) {
        CarrySamples.pressAndSettle(client, GLFW.GLFW_KEY_F23);
        Assertions.assertThat(CarrySamples.ARRIVED).as("values of each firing").containsExactly(List.of(1));
        client.leaveWorld();
    }

    @ClientTest
    public static void twoValuesArriveTypedAndInOrder(Client client) {
        CarrySamples.pressAndSettle(client, GLFW.GLFW_KEY_F24);
        Assertions.assertThat(CarrySamples.ARRIVED).as("values of each firing").containsExactly(List.of(1, "two"));
        client.leaveWorld();
    }

    @ClientTest
    public static void eightValuesArriveTypedAndInOrder(Client client) {
        CarrySamples.pressAndSettle(client, GLFW.GLFW_KEY_F25);
        Assertions.assertThat(CarrySamples.ARRIVED)
                .as("values of each firing")
                .containsExactly(List.of(1, "two", true, 4L, 5.0D, CarrySamples.POS, Direction.SOUTH, CarrySamples.ID));
        client.leaveWorld();
    }

    @ClientTest
    public static void registryReadingCodecCarriesItsValue(Client client) {
        CarrySamples.pressAndSettle(client, GLFW.GLFW_KEY_KP_8);
        Assertions.assertThat(CarrySamples.ARRIVED).as("values of each firing").containsExactly(List.of(Items.DIAMOND.builtInRegistryHolder()));
        client.leaveWorld();
    }

    @ClientTest
    public static void nullReaderRunsTheClientActionAndSendsNothing(Client client) {
        CarrySamples.enterLevel(client);
        CarrySamples.CLIENT_RUNS.set(0);
        CarrySamples.SERVER_RUNS.set(0);
        client.keyboard().press(GLFW.GLFW_KEY_KP_9);
        client.until("the client action to run", () -> CarrySamples.CLIENT_RUNS.get() > 0);
        client.ticks(CarrySamples.SETTLE_TICKS);
        Assertions.assertThat(CarrySamples.CLIENT_RUNS.get()).as("runs of the client action").isEqualTo(1);
        Assertions.assertThat(CarrySamples.SERVER_RUNS.get()).as("runs of the server action").isZero();
        client.leaveWorld();
    }

    @ClientTest
    public static void readerDoesNotRunWithoutAServerToSendTo(Client client) {
        client.leaveWorld();
        CarrySamples.OFFLINE_RUNS.set(0);
        CarrySamples.OFFLINE_READS.set(0);
        client.keyboard().press(GLFW.GLFW_KEY_KP_EQUAL);
        client.until("the client action to run", () -> CarrySamples.OFFLINE_RUNS.get() > 0);
        client.ticks(CarrySamples.SETTLE_TICKS);
        Assertions.assertThat(CarrySamples.OFFLINE_RUNS.get()).as("runs of the client action").isEqualTo(1);
        Assertions.assertThat(CarrySamples.OFFLINE_READS.get()).as("runs of the reader without a server").isZero();
    }

    @ClientTest
    public static void carryChainWithoutAServerActionLeavesTheKeyWithoutAServerHalf(Client client) {
        Keybind unfinished = Objects.requireNonNull(CarrySamples.unfinished);
        Assertions.assertThat(unfinished.hasServerAction()).as("the key has a server action").isFalse();
        Assertions.assertThat(unfinished.getPayload()).as("the payload of the key").isNull();
        Assertions.assertThat(unfinished.getAllReaders()).as("the readers of the key").isEmpty();
    }

    private static void enterLevel(Client client) {
        client.joinWorld();
        client.until("the level to show", () -> client.screen() == null);
    }

    private static void pressAndSettle(Client client, int key) {
        CarrySamples.enterLevel(client);
        CarrySamples.ARRIVED.clear();
        client.keyboard().press(key);
        client.until("the server action to run", () -> !CarrySamples.ARRIVED.isEmpty());
        client.ticks(CarrySamples.SETTLE_TICKS);
    }

    private static void onOne(ServerPlayer player, Integer first) {
        CarrySamples.ARRIVED.add(List.of(first));
    }

    private static void onTwo(ServerPlayer player, Integer first, String second) {
        CarrySamples.ARRIVED.add(List.of(first, second));
    }

    private static void onEight(ServerPlayer player, Integer first, String second, Boolean third, Long fourth, Double fifth, BlockPos sixth, Direction seventh, Identifier eighth) {
        CarrySamples.ARRIVED.add(List.of(first, second, third, fourth, fifth, sixth, seventh, eighth));
    }

    private static void onItem(ServerPlayer player, Holder<Item> item) {
        CarrySamples.ARRIVED.add(List.of(item));
    }

    private static Integer readOffline() {
        CarrySamples.OFFLINE_READS.incrementAndGet();
        return 1;
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Keybinds keybinds = Nexus.getMod().getKeybinds();
            Identifier category = keybinds.category("carry_samples", "Carry Samples");
            keybinds.key("carry_samples_one", "One")
                    .defaultKey(GLFW.GLFW_KEY_F23)
                    .carry(NexusCodec.INT, () -> () -> 1)
                    .onServer(CarrySamples::onOne)
                    .category(category)
                    .register();
            keybinds.key("carry_samples_two", "Two")
                    .defaultKey(GLFW.GLFW_KEY_F24)
                    .carry(NexusCodec.INT, () -> () -> 1)
                    .carry(NexusCodec.STRING, () -> () -> "two")
                    .onServer(CarrySamples::onTwo)
                    .category(category)
                    .register();
            keybinds.key("carry_samples_eight", "Eight")
                    .defaultKey(GLFW.GLFW_KEY_F25)
                    .carry(NexusCodec.INT, () -> () -> 1)
                    .carry(NexusCodec.STRING, () -> () -> "two")
                    .carry(NexusCodec.BOOL, () -> () -> true)
                    .carry(NexusCodec.LONG, () -> () -> 4L)
                    .carry(NexusCodec.DOUBLE, () -> () -> 5.0D)
                    .carry(VanillaCodecs.BLOCK_POS, () -> () -> CarrySamples.POS)
                    .carry(VanillaCodecs.DIRECTION, () -> () -> Direction.SOUTH)
                    .carry(NexusCodec.IDENTIFIER, () -> () -> CarrySamples.ID)
                    .onServer(CarrySamples::onEight)
                    .category(category)
                    .register();
            keybinds.key("carry_samples_item", "Item")
                    .defaultKey(GLFW.GLFW_KEY_KP_8)
                    .carry(NexusCodec.holder(Registries.ITEM), () -> Items.DIAMOND::builtInRegistryHolder)
                    .onServer(CarrySamples::onItem)
                    .category(category)
                    .register();
            keybinds.key("carry_samples_null", "Null")
                    .defaultKey(GLFW.GLFW_KEY_KP_9)
                    .onClient(() -> CarrySamples.CLIENT_RUNS::incrementAndGet)
                    .carry(NexusCodec.INT, () -> () -> null)
                    .onServer((_, _) -> CarrySamples.SERVER_RUNS.incrementAndGet())
                    .category(category)
                    .register();
            keybinds.key("carry_samples_offline", "Offline")
                    .defaultKey(GLFW.GLFW_KEY_KP_EQUAL)
                    .context(Keybind.Context.EVERYWHERE)
                    .onClient(() -> CarrySamples.OFFLINE_RUNS::incrementAndGet)
                    .carry(NexusCodec.INT, () -> CarrySamples::readOffline)
                    .onServer((_, _) -> { })
                    .category(category)
                    .register();

            KeybindBuilder builder = keybinds.key("carry_samples_unfinished", "Unfinished")
                    .defaultKey(GLFW.GLFW_KEY_KP_0)
                    .category(category);
            Carry1<KeybindBuilder, Integer> _ = builder.carry(NexusCodec.INT, () -> () -> 1);
            CarrySamples.unfinished = builder.register();
        }
    }
}
