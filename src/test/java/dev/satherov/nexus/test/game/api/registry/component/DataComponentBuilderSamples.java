package dev.satherov.nexus.test.game.api.registry.component;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.component.DataComponentEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.common.util.FakePlayer;

import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.HashedPatchMap;
import net.minecraft.network.HashedStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.HashOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import com.google.common.hash.HashCode;
import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.DecoderException;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

///
/// Checks the values that data component entries read from and write to item stacks, and that the stacks save, sync, and hash them.
///
public class DataComponentBuilderSamples {
    
    private static final GameProfile PLAYER = new GameProfile(UUID.fromString("3f1c9a7e-52d4-4b8a-9e6f-0c2d8b7a4e51"), "[Nexus Hasher]");
    
    private static @Nullable DataComponentEntry<Integer> count;
    private static @Nullable DataComponentEntry<List<Integer>> limited;
    private static @Nullable DataComponentEntry<Integer> natural;
    
    @ServerTest
    public static void readsNothingFromAStackWithoutTheComponent(GameTestHelper helper) {
        DataComponentEntry<Integer> count = Objects.requireNonNull(DataComponentBuilderSamples.count);
        ItemStack stack = new ItemStack(Items.STICK);
        helper.assertFalse(count.has(stack), "The stack has the component before it was set");
        helper.assertTrue(count.get(stack) == null, "The stack has a value before it was set");
        helper.assertValueEqual(count.getOrElse(stack, 7), 7, "value with a fallback");
        helper.succeed();
    }
    
    @ServerTest
    public static void setsTheValueOnAStack(GameTestHelper helper) {
        DataComponentEntry<Integer> count = Objects.requireNonNull(DataComponentBuilderSamples.count);
        ItemStack stack = new ItemStack(Items.STICK);
        helper.assertTrue(count.set(stack, 3) == null, "Setting the first value replaced another");
        helper.assertValueEqual(count.set(stack, 4), 3, "replaced value");
        helper.assertTrue(count.has(stack), "The stack doesn't have the component after it was set");
        helper.assertValueEqual(count.get(stack), 4, "value");
        helper.assertValueEqual(count.getOrElse(stack, 7), 4, "value with a fallback");
        helper.succeed();
    }
    
    @ServerTest
    public static void setRefusesAValueItsCodecCannotEncode(GameTestHelper helper) {
        DataComponentEntry<List<Integer>> limited = Objects.requireNonNull(DataComponentBuilderSamples.limited);
        DataComponentEntry<Integer> natural = Objects.requireNonNull(DataComponentBuilderSamples.natural);
        ItemStack stack = new ItemStack(Items.STICK);
        limited.set(stack, List.of(1));
        natural.set(stack, 1);
        
        Assertions.assertThatThrownBy(() -> limited.set(stack, List.of(1, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(limited.getId().toString());
        Assertions.assertThatThrownBy(() -> natural.set(stack, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(natural.getId().toString());
        helper.assertValueEqual(limited.get(stack), List.of(1), "list after the refused set");
        helper.assertValueEqual(natural.get(stack), 1, "number after the refused set");
        helper.succeed();
    }
    
    @ServerTest
    public static void updateRefusesAValueItsCodecCannotEncode(GameTestHelper helper) {
        DataComponentEntry<List<Integer>> limited = Objects.requireNonNull(DataComponentBuilderSamples.limited);
        ItemStack stack = new ItemStack(Items.STICK);
        limited.set(stack, List.of(1));
        
        Assertions.assertThatThrownBy(() -> limited.update(stack, _ -> List.of(1, 2))).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> limited.update(stack, () -> List.of(1, 2))).isInstanceOf(IllegalArgumentException.class);
        helper.assertValueEqual(limited.get(stack), List.of(1), "list after the refused updates");
        helper.succeed();
    }
    
    @ServerTest
    public static void savesTheValueWithTheStack(GameTestHelper helper) {
        DataComponentEntry<Integer> count = Objects.requireNonNull(DataComponentBuilderSamples.count);
        ItemStack stack = new ItemStack(Items.STICK);
        count.set(stack, 3);
        RegistryOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        ItemStack loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow();
        helper.assertValueEqual(count.get(loaded), 3, "value of the loaded stack");
        helper.succeed();
    }
    
    @ServerTest
    public static void savesTheValueWithTheStackInNbt(GameTestHelper helper) {
        DataComponentEntry<Integer> count = Objects.requireNonNull(DataComponentBuilderSamples.count);
        RegistryAccess registries = helper.getLevel().registryAccess();
        ItemStack stack = new ItemStack(Items.STICK);
        count.set(stack, 3);
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        output.store("item", ItemStack.CODEC, stack);
        ItemStack loaded = TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult()).read("item", ItemStack.CODEC).orElseThrow();
        helper.assertValueEqual(count.get(loaded), 3, "value of the loaded stack");
        helper.succeed();
    }
    
    @ServerTest
    public static void syncsTheValueWithTheStack(GameTestHelper helper) {
        DataComponentEntry<Integer> count = Objects.requireNonNull(DataComponentBuilderSamples.count);
        ItemStack stack = new ItemStack(Items.STICK);
        count.set(stack, 3);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ItemStack.STREAM_CODEC.encode(buffer, stack);
        helper.assertValueEqual(count.get(ItemStack.STREAM_CODEC.decode(buffer)), 3, "value of the received stack");
        helper.succeed();
    }
    
    @ServerTest
    public static void receivesTheValueThroughTheValidatedStreamCodec(GameTestHelper helper) {
        DataComponentEntry<Integer> count = Objects.requireNonNull(DataComponentBuilderSamples.count);
        StreamCodec<RegistryFriendlyByteBuf, ItemStack> validated = ItemStack.validatedStreamCodec(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC);
        ItemStack stack = new ItemStack(Items.STICK);
        count.set(stack, 3);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        validated.encode(buffer, stack);
        helper.assertValueEqual(count.get(validated.decode(buffer)), 3, "value of the validated stack");
        helper.succeed();
    }
    
    @ServerTest
    public static void refusesToReceiveAValueItsCodecCannotEncode(GameTestHelper helper) {
        DataComponentEntry<Integer> natural = Objects.requireNonNull(DataComponentBuilderSamples.natural);
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(natural.value(), -1);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
        
        Assertions.assertThatThrownBy(() -> ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)).isInstanceOf(DecoderException.class);
        helper.succeed();
    }
    
    @ServerTest
    public static void hashesTheValueTheSameOnACopyAndAReceivedStack(GameTestHelper helper) {
        DataComponentEntry<Integer> count = Objects.requireNonNull(DataComponentBuilderSamples.count);
        HashedPatchMap.HashGenerator hasher = DataComponentBuilderSamples.hasher(helper);
        ItemStack stack = new ItemStack(Items.STICK);
        count.set(stack, 3);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ItemStack.STREAM_CODEC.encode(buffer, stack);
        ItemStack received = ItemStack.STREAM_CODEC.decode(buffer);
        ItemStack other = stack.copy();
        count.set(other, 4);
        
        HashedStack hashed = HashedStack.create(stack, hasher);
        helper.assertTrue(hashed.matches(stack.copy(), hasher), "The hash of the stack doesn't match its copy");
        helper.assertTrue(hashed.matches(received, hasher), "The hash of the stack doesn't match the received stack");
        helper.assertFalse(hashed.matches(other, hasher), "The hash of the stack matches a stack with another value");
        helper.succeed();
    }
    
    @ServerTest
    public static void menuResendsOnlyASlotWhoseHashDiffers(GameTestHelper helper) {
        DataComponentEntry<Integer> count = Objects.requireNonNull(DataComponentBuilderSamples.count);
        ItemStack stack = new ItemStack(Items.STICK);
        count.set(stack, 3);
        DataComponentBuilderSamples.assertResendsOnlyADifferentHash(helper, stack);
        helper.succeed();
    }
    
    @ServerTest
    public static void menuHashesAValueItsCodecCannotEncode(GameTestHelper helper) {
        DataComponentEntry<List<Integer>> limited = Objects.requireNonNull(DataComponentBuilderSamples.limited);
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(limited.value(), List.of(1, 2));
        DataComponentBuilderSamples.assertResendsOnlyADifferentHash(helper, stack);
        helper.succeed();
    }
    
    private static void assertResendsOnlyADifferentHash(GameTestHelper helper, ItemStack stack) {
        List<Packet<?>> sent = new ArrayList<>();
        ServerPlayer player = DataComponentBuilderSamples.recordingPlayer(helper, sent);
        HashedPatchMap.HashGenerator hasher = DataComponentBuilderSamples.hasher(helper);
        InventoryMenu menu = player.inventoryMenu;
        int slot = InventoryMenu.INV_SLOT_START;
        menu.getSlot(slot).set(stack);
        menu.broadcastChanges();
        sent.clear();
        
        menu.setRemoteSlotUnsafe(slot, HashedStack.create(stack, hasher));
        menu.broadcastChanges();
        helper.assertTrue(sent.isEmpty(), "The slot was sent again although the player's hash is the server's");
        
        menu.setRemoteSlotUnsafe(slot, HashedStack.create(stack, component -> hasher.apply(component) + 1));
        menu.broadcastChanges();
        helper.assertFalse(sent.isEmpty(), "The slot was not sent again although the player's hash differs");
        
        sent.clear();
        menu.broadcastChanges();
        helper.assertTrue(sent.isEmpty(), "The slot was sent again although the player has it");
    }
    
    private static HashedPatchMap.HashGenerator hasher(GameTestHelper helper) {
        RegistryOps<HashCode> ops = helper.getLevel().registryAccess().createSerializationContext(HashOps.CRC32C_INSTANCE);
        return component -> component.encodeValue(ops).getOrThrow().asInt();
    }
    
    private static ServerPlayer recordingPlayer(GameTestHelper helper, List<Packet<?>> sent) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = new FakePlayer(level, DataComponentBuilderSamples.PLAYER);
        player.connection = new ServerGamePacketListenerImpl(
                level.getServer(),
                new Connection(PacketFlow.SERVERBOUND),
                player,
                CommonListenerCookie.createInitial(DataComponentBuilderSamples.PLAYER, false)
        ) {
            
            @Override
            public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
                sent.add(packet);
            }
        };
        player.initInventoryMenu();
        return player;
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            DataComponentBuilderSamples.count = registrations.component("data_component_builder_samples_count", NexusCodec.INT).register();
            DataComponentBuilderSamples.limited = registrations.component("data_component_builder_samples_limited", NexusCodec.INT.list(1)).register();
            DataComponentBuilderSamples.natural = registrations.component(
                    "data_component_builder_samples_natural",
                    NexusCodec.ofVanilla(ExtraCodecs.NON_NEGATIVE_INT, ByteBufCodecs.VAR_INT)
            ).register();
        }
    }
}
