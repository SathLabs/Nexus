package dev.satherov.nexus.test.game.api.registry.component;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.registry.component.DataComponentEntry;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

///
/// Checks the values that data component entries read from and write to item stacks, and that the stacks save and sync them.
///
public class DataComponentBuilderSamples {
    
    private static @Nullable DataComponentEntry<Integer> count;
    
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
    public static void syncsTheValueWithTheStack(GameTestHelper helper) {
        DataComponentEntry<Integer> count = Objects.requireNonNull(DataComponentBuilderSamples.count);
        ItemStack stack = new ItemStack(Items.STICK);
        count.set(stack, 3);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ItemStack.STREAM_CODEC.encode(buffer, stack);
        helper.assertValueEqual(count.get(ItemStack.STREAM_CODEC.decode(buffer)), 3, "value of the received stack");
        helper.succeed();
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            DataComponentBuilderSamples.count = Nexus.getMod().getRegistrations().component("data_component_builder_samples_count", NexusCodec.INT).register();
        }
    }
}
