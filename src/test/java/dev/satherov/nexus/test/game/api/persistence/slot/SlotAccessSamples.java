package dev.satherov.nexus.test.game.api.persistence.slot;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.persistence.slot.SlotAccess;
import dev.satherov.nexus.api.persistence.slot.SlotLayout;
import dev.satherov.nexus.api.persistence.slot.SlotSnapshot;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemUtil;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.google.gson.JsonElement;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

///
/// Checks slot layouts over a resource handler, and the snapshot of a slot that holds more items than an item stack can encode.
///
public class SlotAccessSamples {

    private static final String BAG = "bag";
    private static final String NAME = "Named sword";
    private static final int SIZE = 4;
    private static final int CAPACITY = 16;
    private static final int LARGE_CAPACITY = Integer.MAX_VALUE;
    private static final int LARGE_COUNT = 4_000_000;
    private static final int REFUSED_SLOT = 1;

    private static SlotLayout<Bag> layout() {
        return Nexus.getMod()
                .getPersistence()
                .slots(Bag.class)
                .group(SlotAccessSamples.BAG, bag -> SlotAccess.of(bag, bag::set))
                .fallback(SlotAccessSamples.BAG)
                .build();
    }

    private static void put(Bag bag, int slot, ItemStack stack) {
        bag.set(slot, ItemResource.of(stack), stack.getCount());
    }

    private static List<String> describe(List<ItemStack> stacks) {
        return stacks.stream()
                .map(ItemStack::toString)
                .toList();
    }

    private static void assertSlot(GameTestHelper helper, Bag bag, int slot, ItemStack expected, String what) {
        ItemStack actual = ItemUtil.getStack(bag, slot);
        helper.assertTrue(ItemStack.matches(actual, expected), "The " + what + " holds " + actual + " instead of " + expected);
    }

    private static List<List<Integer>> entries(Tag encoded) {
        return ((CompoundTag) encoded).getCompoundOrEmpty("data")
                .getCompoundOrEmpty("groups")
                .getCompoundOrEmpty(SlotAccessSamples.BAG)
                .getListOrEmpty("items")
                .compoundStream()
                .map(item -> List.of(Byte.toUnsignedInt(item.getByteOr("Slot", (byte) 0)), item.getIntOr("count", 0), item.getIntOr("total", 0)))
                .toList();
    }

    @ServerTest
    public static void captureAndTakeReadTheSlotsOfAResourceHandler(GameTestHelper helper) {
        Bag bag = new Bag(SlotAccessSamples.CAPACITY);
        SlotAccessSamples.put(bag, 0, new ItemStack(Items.DIAMOND, 5));
        SlotAccessSamples.put(bag, 2, new ItemStack(Items.GOLD_INGOT, 3));
        List<String> expected = SlotAccessSamples.describe(List.of(new ItemStack(Items.DIAMOND, 5), ItemStack.EMPTY, new ItemStack(Items.GOLD_INGOT, 3), ItemStack.EMPTY));
        SlotLayout<Bag> layout = SlotAccessSamples.layout();
        helper.assertValueEqual(SlotAccessSamples.describe(layout.capture(bag).stacks(SlotAccessSamples.BAG)), expected, "captured stacks");
        SlotAccessSamples.assertSlot(helper, bag, 0, new ItemStack(Items.DIAMOND, 5), "first slot after the capture");
        SlotAccessSamples.assertSlot(helper, bag, 2, new ItemStack(Items.GOLD_INGOT, 3), "third slot after the capture");

        helper.assertValueEqual(SlotAccessSamples.describe(layout.take(bag).stacks(SlotAccessSamples.BAG)), expected, "taken stacks");
        helper.assertTrue(IntStream.range(0, SlotAccessSamples.SIZE).allMatch(slot -> bag.getResource(slot).isEmpty()), "The take left a stack in the resource handler");
        helper.succeed();
    }

    @ServerTest
    public static void restoreKeepsTheComponents(GameTestHelper helper) {
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal(SlotAccessSamples.NAME));
        Bag before = new Bag(SlotAccessSamples.CAPACITY);
        SlotAccessSamples.put(before, 3, named);
        SlotLayout<Bag> layout = SlotAccessSamples.layout();
        SlotSnapshot snapshot = layout.take(before);

        Bag after = new Bag(SlotAccessSamples.CAPACITY);
        helper.assertTrue(layout.restore(after, snapshot).isEmpty(), "The restore returned leftovers");
        SlotAccessSamples.assertSlot(helper, after, 3, named, "restored slot");
        helper.succeed();
    }

    @ServerTest
    public static void restoreTopsUpAPartlyFilledSlotToItsCapacity(GameTestHelper helper) {
        Bag before = new Bag(SlotAccessSamples.CAPACITY);
        SlotAccessSamples.put(before, 0, new ItemStack(Items.DIAMOND, 10));
        SlotLayout<Bag> layout = SlotAccessSamples.layout();
        SlotSnapshot snapshot = layout.capture(before);

        Bag after = new Bag(SlotAccessSamples.CAPACITY);
        SlotAccessSamples.put(after, 0, new ItemStack(Items.DIAMOND, 12));
        for (int slot = 1; slot < SlotAccessSamples.SIZE; slot++) {
            SlotAccessSamples.put(after, slot, new ItemStack(Items.DIRT, SlotAccessSamples.CAPACITY));
        }

        List<ItemStack> left = layout.restore(after, snapshot);
        helper.assertValueEqual(SlotAccessSamples.describe(left), SlotAccessSamples.describe(List.of(new ItemStack(Items.DIAMOND, 6))), "leftovers");
        SlotAccessSamples.assertSlot(helper, after, 0, new ItemStack(Items.DIAMOND, SlotAccessSamples.CAPACITY), "topped up slot");
        helper.succeed();
    }

    @ServerTest
    public static void restorePassesASlotTheHandlerRefusesToTheFallback(GameTestHelper helper) {
        Bag before = new Bag(SlotAccessSamples.CAPACITY);
        SlotAccessSamples.put(before, SlotAccessSamples.REFUSED_SLOT, new ItemStack(Items.DIRT, 4));
        SlotLayout<Bag> layout = SlotAccessSamples.layout();
        SlotSnapshot snapshot = layout.capture(before);

        Bag after = new Bag(SlotAccessSamples.CAPACITY, slot -> slot == SlotAccessSamples.REFUSED_SLOT);
        helper.assertTrue(layout.restore(after, snapshot).isEmpty(), "The restore returned leftovers");
        SlotAccessSamples.assertSlot(helper, after, SlotAccessSamples.REFUSED_SLOT, ItemStack.EMPTY, "refusing slot");
        SlotAccessSamples.assertSlot(helper, after, 0, new ItemStack(Items.DIRT, 4), "first free slot");
        helper.succeed();
    }

    @ServerTest
    public static void oversizedStackSurvivesTheFormat(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        Bag before = new Bag(SlotAccessSamples.LARGE_CAPACITY);
        SlotAccessSamples.put(before, 0, new ItemStack(Items.COBBLESTONE, SlotAccessSamples.LARGE_COUNT));
        SlotAccessSamples.put(before, 1, new ItemStack(Items.DIRT, 5));
        SlotLayout<Bag> layout = SlotAccessSamples.layout();
        SlotSnapshot snapshot = layout.take(before);
        Tag encoded = SlotSnapshot.FORMAT.write(snapshot, registries);
        helper.assertValueEqual(
                SlotAccessSamples.entries(encoded),
                List.of(List.of(0, 99, SlotAccessSamples.LARGE_COUNT), List.of(1, 5, 0)),
                "slot, count and total of every entry"
        );

        CodecFormat<JsonElement, Access.Registries> json = CodecFormat.JSON.withRegistries(registries).strict();
        SlotSnapshot fromJson = SlotSnapshot.FORMAT.codec().decode(json, SlotSnapshot.FORMAT.codec().encode(json, snapshot));
        helper.assertValueEqual(
                SlotAccessSamples.describe(fromJson.stacks(SlotAccessSamples.BAG)),
                SlotAccessSamples.describe(snapshot.stacks(SlotAccessSamples.BAG)),
                "stacks read back from strict JSON"
        );

        Bag after = new Bag(SlotAccessSamples.LARGE_CAPACITY);
        helper.assertTrue(layout.restore(after, SlotSnapshot.FORMAT.read(encoded, registries)).isEmpty(), "The restore returned leftovers");
        SlotAccessSamples.assertSlot(helper, after, 0, new ItemStack(Items.COBBLESTONE, SlotAccessSamples.LARGE_COUNT), "restored large slot");
        SlotAccessSamples.assertSlot(helper, after, 1, new ItemStack(Items.DIRT, 5), "restored small slot");
        helper.succeed();
    }

    private static final class Bag extends ItemStacksResourceHandler {

        private final int capacity;
        private final IntPredicate refuses;

        private Bag(int capacity, IntPredicate refuses) {
            super(SlotAccessSamples.SIZE);
            this.capacity = capacity;
            this.refuses = refuses;
        }

        private Bag(int capacity) {
            this(capacity, _ -> false);
        }

        @Override
        protected int getCapacity(int index, ItemResource resource) {
            return this.capacity;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return !this.refuses.test(index);
        }
    }
}
