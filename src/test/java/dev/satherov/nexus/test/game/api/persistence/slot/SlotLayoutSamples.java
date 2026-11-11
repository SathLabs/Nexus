package dev.satherov.nexus.test.game.api.persistence.slot;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.event.persistence.slot.RegisterSlotSourcesEvent;
import dev.satherov.nexus.api.persistence.slot.SlotAccess;
import dev.satherov.nexus.api.persistence.slot.SlotLayout;
import dev.satherov.nexus.api.persistence.slot.SlotLayoutBuilder;
import dev.satherov.nexus.api.persistence.slot.SlotSnapshot;
import dev.satherov.nexus.api.persistence.slot.SlotSource;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import com.google.common.collect.Maps;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

///
/// Checks slot layouts over a holder of the test's own and over a mock player, and the NBT form of a slot snapshot.
///
public class SlotLayoutSamples {

    private static final Identifier POUCH = Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "slot_layout_samples_pouch");
    private static final Identifier POCKETS = Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "slot_layout_samples_pockets");
    private static final String LEFT = "left";
    private static final String RIGHT = "right";
    private static final String BAG = "bag";
    private static final String PICKY = "picky";
    private static final String POUCH_GROUP = SlotLayoutSamples.POUCH.toString();
    private static final String LEFT_GROUP = SlotLayoutSamples.POCKETS + "/" + SlotLayoutSamples.LEFT;
    private static final String RIGHT_GROUP = SlotLayoutSamples.POCKETS + "/" + SlotLayoutSamples.RIGHT;
    private static final String ABSENT_GROUP = Nexus.MOD_ID + ":slot_layout_samples_absent";
    private static final String INVENTORY_GROUP = "minecraft:inventory";
    private static final String ARMOR_GROUP = "minecraft:armor";
    private static final String OFFHAND_GROUP = "minecraft:offhand";
    private static final int POUCH_SIZE = 3;
    private static final int GROUP_SIZE = 2;
    private static final int ARMOR_SIZE = 4;
    private static final String DATA_TAG = "data";
    private static final String GROUPS_TAG = "groups";
    private static final String ITEMS_TAG = "items";
    private static final String SLOT_TAG = "Slot";
    private static final String ID_TAG = "id";

    private static @Nullable Throwable secondRegistration;

    private static SlotLayoutBuilder<Carrier> slots() {
        return Nexus.getMod().getPersistence().slots(Carrier.class);
    }

    private static SlotLayout<Carrier> layout() {
        return SlotLayoutSamples.slots()
                .registered()
                .group(SlotLayoutSamples.BAG, carrier -> SlotLayoutSamples.whole(carrier.bag))
                .fallback(SlotLayoutSamples.BAG)
                .build();
    }

    private static SlotAccess whole(SimpleContainer container) {
        return SlotAccess.of(container, 0, container.getContainerSize());
    }

    private static List<String> describe(List<ItemStack> stacks) {
        return stacks.stream()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::toString)
                .toList();
    }

    private static void assertStacks(GameTestHelper helper, List<ItemStack> actual, List<ItemStack> expected, String what) {
        helper.assertValueEqual(SlotLayoutSamples.describe(actual), SlotLayoutSamples.describe(expected), what);
    }

    private static void assertStacksInAnyOrder(GameTestHelper helper, List<ItemStack> actual, List<ItemStack> expected, String what) {
        helper.assertValueEqual(
                SlotLayoutSamples.describe(actual).stream().sorted().toList(),
                SlotLayoutSamples.describe(expected).stream().sorted().toList(),
                what
        );
    }

    private static void assertSlot(GameTestHelper helper, SimpleContainer container, int slot, ItemStack expected, String what) {
        helper.assertValueEqual(container.getItem(slot).toString(), expected.toString(), what);
    }

    private static CompoundTag documented(CompoundTag groups) {
        CompoundTag data = new CompoundTag();
        data.put(SlotLayoutSamples.GROUPS_TAG, groups);
        CompoundTag versioned = new CompoundTag();
        versioned.putInt("version", 1);
        versioned.put(SlotLayoutSamples.DATA_TAG, data);
        return versioned;
    }

    private static CompoundTag group(int size, CompoundTag... items) {
        ListTag list = new ListTag();
        list.addAll(Arrays.asList(items));
        CompoundTag group = new CompoundTag();
        group.putInt("size", size);
        group.put(SlotLayoutSamples.ITEMS_TAG, list);
        return group;
    }

    private static CompoundTag item(int slot, String id, int count) {
        CompoundTag item = new CompoundTag();
        item.putByte(SlotLayoutSamples.SLOT_TAG, (byte) slot);
        item.putString(SlotLayoutSamples.ID_TAG, id);
        item.putInt("count", count);
        return item;
    }

    private static Map<String, Integer> slotsByItem(Tag encoded, String group) {
        return ((CompoundTag) encoded).getCompoundOrEmpty(SlotLayoutSamples.DATA_TAG)
                .getCompoundOrEmpty(SlotLayoutSamples.GROUPS_TAG)
                .getCompoundOrEmpty(group)
                .getListOrEmpty(SlotLayoutSamples.ITEMS_TAG)
                .compoundStream()
                .collect(Collectors.toMap(
                        item -> item.getStringOr(SlotLayoutSamples.ID_TAG, ""),
                        item -> Byte.toUnsignedInt(item.getByteOr(SlotLayoutSamples.SLOT_TAG, (byte) 0))
                ));
    }

    @ServerTest
    public static void sourcesGiveTheirGroupsUnderTheSourceId(GameTestHelper helper) {
        SlotLayout<Carrier> layout = SlotLayoutSamples.slots().registered().build();
        SlotSnapshot both = layout.capture(new Carrier(SlotLayoutSamples.LEFT, SlotLayoutSamples.RIGHT));
        helper.assertValueEqual(
                Set.copyOf(both.groups()),
                Set.of(SlotLayoutSamples.POUCH_GROUP, SlotLayoutSamples.LEFT_GROUP, SlotLayoutSamples.RIGHT_GROUP),
                "groups of a carrier with two pockets"
        );
        helper.assertValueEqual(both.size(SlotLayoutSamples.POUCH_GROUP), SlotLayoutSamples.POUCH_SIZE, "size of the pouch group");
        helper.assertValueEqual(both.size(SlotLayoutSamples.RIGHT_GROUP), SlotLayoutSamples.GROUP_SIZE, "size of the right pocket group");

        SlotSnapshot one = layout.capture(new Carrier(SlotLayoutSamples.LEFT));
        helper.assertValueEqual(Set.copyOf(one.groups()), Set.of(SlotLayoutSamples.POUCH_GROUP, SlotLayoutSamples.LEFT_GROUP), "groups of a carrier with one pocket");
        helper.succeed();
    }

    @ServerTest
    public static void registeredLeavesOutTheExceptedSources(GameTestHelper helper) {
        Carrier carrier = new Carrier(SlotLayoutSamples.LEFT, SlotLayoutSamples.RIGHT);
        SlotSnapshot excepted = SlotLayoutSamples.slots().registered(SlotLayoutSamples.POCKETS).build().capture(carrier);
        helper.assertValueEqual(excepted.groups(), List.of(SlotLayoutSamples.POUCH_GROUP), "groups of every registered source except the pockets");

        SlotSnapshot pockets = SlotLayoutSamples.slots().source(SlotLayoutSamples.POCKETS).build().capture(carrier);
        helper.assertValueEqual(Set.copyOf(pockets.groups()), Set.of(SlotLayoutSamples.LEFT_GROUP, SlotLayoutSamples.RIGHT_GROUP), "groups of the pockets source alone");
        helper.succeed();
    }

    @ServerTest
    public static void secondSourceOfOneIdThrows(GameTestHelper helper) {
        helper.assertTrue(SlotLayoutSamples.secondRegistration instanceof IllegalStateException, "A second source of one id did not throw an IllegalStateException");
        helper.succeed();
    }

    @ServerTest
    public static void captureCopiesTheStacksAndLeavesTheHolderAlone(GameTestHelper helper) {
        Carrier carrier = new Carrier(SlotLayoutSamples.LEFT);
        carrier.pouch.setItem(0, new ItemStack(Items.DIAMOND, 4));
        carrier.getPocket(SlotLayoutSamples.LEFT).setItem(1, new ItemStack(Items.GOLD_INGOT, 2));
        SlotSnapshot snapshot = SlotLayoutSamples.layout().capture(carrier);
        SlotLayoutSamples.assertSlot(helper, carrier.pouch, 0, new ItemStack(Items.DIAMOND, 4), "pouch slot after the capture");
        SlotLayoutSamples.assertSlot(helper, carrier.getPocket(SlotLayoutSamples.LEFT), 1, new ItemStack(Items.GOLD_INGOT, 2), "left pocket slot after the capture");

        carrier.pouch.getItem(0).shrink(1);
        SlotLayoutSamples.assertStacks(helper, snapshot.stacks(SlotLayoutSamples.POUCH_GROUP), List.of(new ItemStack(Items.DIAMOND, 4)), "captured pouch stacks");
        SlotLayoutSamples.assertStacksInAnyOrder(
                helper,
                snapshot.gather(),
                List.of(new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.GOLD_INGOT, 2)),
                "every captured stack"
        );
        helper.succeed();
    }

    @ServerTest
    public static void takeWithAFilterEmptiesOnlyTheMatchingSlots(GameTestHelper helper) {
        Carrier carrier = new Carrier(SlotLayoutSamples.LEFT);
        carrier.pouch.setItem(0, new ItemStack(Items.DIAMOND, 4));
        carrier.pouch.setItem(1, new ItemStack(Items.DIRT, 3));
        carrier.getPocket(SlotLayoutSamples.LEFT).setItem(0, new ItemStack(Items.DIAMOND, 2));
        SlotSnapshot taken = SlotLayoutSamples.layout().take(carrier, stack -> stack.is(Items.DIAMOND));
        SlotLayoutSamples.assertStacksInAnyOrder(
                helper,
                taken.gather(),
                List.of(new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.DIAMOND, 2)),
                "taken stacks"
        );
        SlotLayoutSamples.assertSlot(helper, carrier.pouch, 0, ItemStack.EMPTY, "pouch slot of a taken stack");
        SlotLayoutSamples.assertSlot(helper, carrier.getPocket(SlotLayoutSamples.LEFT), 0, ItemStack.EMPTY, "left pocket slot of a taken stack");
        SlotLayoutSamples.assertSlot(helper, carrier.pouch, 1, new ItemStack(Items.DIRT, 3), "pouch slot of a stack the filter left out");
        helper.succeed();
    }

    @ServerTest
    public static void restoreTopsUpMatchingStacksBeforeTakingAFreeSlot(GameTestHelper helper) {
        SlotLayout<Carrier> layout = SlotLayoutSamples.layout();
        Carrier before = new Carrier();
        before.pouch.setItem(0, new ItemStack(Items.DIAMOND, 10));
        before.pouch.setItem(1, new ItemStack(Items.GOLD_INGOT, 5));
        SlotSnapshot snapshot = layout.capture(before);

        Carrier after = new Carrier();
        after.pouch.setItem(0, new ItemStack(Items.DIRT));
        after.bag.setItem(0, new ItemStack(Items.DIAMOND, 60));
        List<ItemStack> left = layout.restore(after, snapshot);
        SlotLayoutSamples.assertStacks(helper, left, List.of(), "leftovers");
        SlotLayoutSamples.assertSlot(helper, after.pouch, 0, new ItemStack(Items.DIRT), "occupied pouch slot");
        SlotLayoutSamples.assertSlot(helper, after.pouch, 1, new ItemStack(Items.GOLD_INGOT, 5), "empty pouch slot");
        SlotLayoutSamples.assertSlot(helper, after.bag, 0, new ItemStack(Items.DIAMOND, 64), "bag slot with matching stacks");
        SlotLayoutSamples.assertSlot(helper, after.bag, 1, new ItemStack(Items.DIAMOND, 6), "free bag slot");
        helper.succeed();
    }

    @ServerTest
    public static void restoreReturnsWhatNoSlotTakes(GameTestHelper helper) {
        SlotLayout<Carrier> layout = SlotLayoutSamples.layout();
        Carrier before = new Carrier();
        before.pouch.setItem(0, new ItemStack(Items.DIAMOND, 10));
        SlotSnapshot snapshot = layout.capture(before);

        Carrier after = new Carrier();
        after.pouch.setItem(0, new ItemStack(Items.DIRT));
        after.bag.setItem(0, new ItemStack(Items.DIAMOND, 60));
        after.bag.setItem(1, new ItemStack(Items.DIAMOND, 62));
        List<ItemStack> left = layout.restore(after, snapshot);
        SlotLayoutSamples.assertStacks(helper, left, List.of(new ItemStack(Items.DIAMOND, 4)), "leftovers");
        SlotLayoutSamples.assertSlot(helper, after.bag, 0, new ItemStack(Items.DIAMOND, 64), "first bag slot");
        SlotLayoutSamples.assertSlot(helper, after.bag, 1, new ItemStack(Items.DIAMOND, 64), "second bag slot");
        helper.succeed();
    }

    @ServerTest
    public static void refuseKeepsStacksOutOfTheOwnSlotAndTheFallback(GameTestHelper helper) {
        SlotLayout<Carrier> layout = SlotLayoutSamples.slots()
                .registered()
                .group(SlotLayoutSamples.BAG, carrier -> SlotLayoutSamples.whole(carrier.bag))
                .refuse(SlotLayoutSamples.POUCH_GROUP, stack -> stack.is(Items.ROTTEN_FLESH))
                .fallback(SlotLayoutSamples.POUCH_GROUP)
                .build();
        Carrier before = new Carrier();
        before.pouch.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 2));
        before.bag.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 1));
        before.bag.setItem(1, new ItemStack(Items.DIAMOND, 3));
        SlotSnapshot snapshot = layout.capture(before);

        Carrier after = new Carrier();
        after.bag.setItem(0, new ItemStack(Items.STONE));
        after.bag.setItem(1, new ItemStack(Items.STONE));
        List<ItemStack> left = layout.restore(after, snapshot);
        SlotLayoutSamples.assertStacksInAnyOrder(
                helper,
                left,
                List.of(new ItemStack(Items.ROTTEN_FLESH, 2), new ItemStack(Items.ROTTEN_FLESH, 1)),
                "leftovers"
        );
        SlotLayoutSamples.assertStacks(helper, after.pouch.getItems(), List.of(new ItemStack(Items.DIAMOND, 3)), "pouch stacks");
        helper.succeed();
    }

    @ServerTest
    public static void restorePassesOverASlotThatDoesNotAccept(GameTestHelper helper) {
        SlotLayout<Carrier> layout = SlotLayoutSamples.slots()
                .registered()
                .group(SlotLayoutSamples.BAG, carrier -> SlotLayoutSamples.whole(carrier.bag))
                .group(SlotLayoutSamples.PICKY, carrier -> new DiamondSlots(carrier.picky))
                .fallback(SlotLayoutSamples.BAG)
                .build();
        Carrier before = new Carrier();
        before.picky.setItem(0, new ItemStack(Items.DIRT, 4));
        before.picky.setItem(1, new ItemStack(Items.DIAMOND, 2));
        SlotSnapshot snapshot = layout.capture(before);

        Carrier after = new Carrier();
        List<ItemStack> left = layout.restore(after, snapshot);
        SlotLayoutSamples.assertStacks(helper, left, List.of(), "leftovers");
        SlotLayoutSamples.assertSlot(helper, after.picky, 0, ItemStack.EMPTY, "slot that refused the dirt");
        SlotLayoutSamples.assertSlot(helper, after.picky, 1, new ItemStack(Items.DIAMOND, 2), "slot that accepts the diamonds");
        SlotLayoutSamples.assertStacks(helper, after.bag.getItems(), List.of(new ItemStack(Items.DIRT, 4)), "bag stacks");
        helper.succeed();
    }

    @ServerTest
    public static void clearEmptiesEveryGroupOfTheLayout(GameTestHelper helper) {
        Carrier carrier = new Carrier(SlotLayoutSamples.LEFT, SlotLayoutSamples.RIGHT);
        carrier.pouch.setItem(2, new ItemStack(Items.DIAMOND));
        carrier.bag.setItem(0, new ItemStack(Items.DIRT));
        carrier.getPocket(SlotLayoutSamples.LEFT).setItem(0, new ItemStack(Items.GOLD_INGOT));
        carrier.getPocket(SlotLayoutSamples.RIGHT).setItem(1, new ItemStack(Items.EMERALD));
        carrier.picky.setItem(0, new ItemStack(Items.DIAMOND));
        SlotLayoutSamples.layout().clear(carrier);
        helper.assertTrue(
                Stream.of(carrier.pouch, carrier.bag, carrier.getPocket(SlotLayoutSamples.LEFT), carrier.getPocket(SlotLayoutSamples.RIGHT)).allMatch(SimpleContainer::isEmpty),
                "The clear left a stack in a group of the layout"
        );
        helper.assertFalse(carrier.picky.isEmpty(), "The clear emptied a container outside the layout");
        helper.succeed();
    }

    @ServerTest
    public static void decodesTheDocumentedForm(GameTestHelper helper) {
        CompoundTag groups = new CompoundTag();
        groups.put(SlotLayoutSamples.POUCH_GROUP, SlotLayoutSamples.group(SlotLayoutSamples.POUCH_SIZE, SlotLayoutSamples.item(2, "minecraft:diamond", 5)));
        groups.put(SlotLayoutSamples.LEFT_GROUP, SlotLayoutSamples.group(SlotLayoutSamples.GROUP_SIZE, SlotLayoutSamples.item(1, "minecraft:gold_ingot", 3)));
        SlotSnapshot snapshot = SlotSnapshot.FORMAT.read(SlotLayoutSamples.documented(groups), helper.getLevel().registryAccess());
        helper.assertValueEqual(Set.copyOf(snapshot.groups()), Set.of(SlotLayoutSamples.POUCH_GROUP, SlotLayoutSamples.LEFT_GROUP), "decoded groups");
        helper.assertValueEqual(snapshot.size(SlotLayoutSamples.POUCH_GROUP), SlotLayoutSamples.POUCH_SIZE, "decoded size of the pouch group");
        SlotLayoutSamples.assertStacks(helper, snapshot.stacks(SlotLayoutSamples.POUCH_GROUP), List.of(new ItemStack(Items.DIAMOND, 5)), "decoded pouch stacks");

        Carrier carrier = new Carrier(SlotLayoutSamples.LEFT);
        List<ItemStack> left = SlotLayoutSamples.layout().restore(carrier, snapshot);
        SlotLayoutSamples.assertStacks(helper, left, List.of(), "leftovers");
        SlotLayoutSamples.assertSlot(helper, carrier.pouch, 2, new ItemStack(Items.DIAMOND, 5), "restored pouch slot");
        SlotLayoutSamples.assertSlot(helper, carrier.getPocket(SlotLayoutSamples.LEFT), 1, new ItemStack(Items.GOLD_INGOT, 3), "restored left pocket slot");
        helper.succeed();
    }

    @ServerTest
    public static void encodesTheDocumentedForm(GameTestHelper helper) {
        Carrier carrier = new Carrier();
        carrier.pouch.setItem(1, new ItemStack(Items.DIAMOND, 5));
        SlotSnapshot snapshot = SlotLayoutSamples.slots().source(SlotLayoutSamples.POUCH).build().capture(carrier);
        CompoundTag groups = new CompoundTag();
        groups.put(SlotLayoutSamples.POUCH_GROUP, SlotLayoutSamples.group(SlotLayoutSamples.POUCH_SIZE, SlotLayoutSamples.item(1, "minecraft:diamond", 5)));
        helper.assertValueEqual(SlotSnapshot.FORMAT.write(snapshot, helper.getLevel().registryAccess()), SlotLayoutSamples.documented(groups), "encoded snapshot");
        helper.succeed();
    }

    @ServerTest
    public static void groupsOfAnAbsentSourceSurviveAndComeBackAsLeftovers(GameTestHelper helper) {
        CompoundTag groups = new CompoundTag();
        groups.put(SlotLayoutSamples.POUCH_GROUP, SlotLayoutSamples.group(SlotLayoutSamples.POUCH_SIZE, SlotLayoutSamples.item(0, "minecraft:diamond", 2)));
        groups.put(SlotLayoutSamples.ABSENT_GROUP, SlotLayoutSamples.group(4, SlotLayoutSamples.item(2, "minecraft:emerald", 7)));
        CompoundTag documented = SlotLayoutSamples.documented(groups);
        SlotSnapshot snapshot = SlotSnapshot.FORMAT.read(documented, helper.getLevel().registryAccess());
        helper.assertValueEqual(snapshot.size(SlotLayoutSamples.ABSENT_GROUP), 4, "decoded size of the absent group");
        helper.assertValueEqual(SlotSnapshot.FORMAT.write(snapshot, helper.getLevel().registryAccess()), documented, "snapshot encoded again");

        Carrier carrier = new Carrier();
        List<ItemStack> left = SlotLayoutSamples.layout().restore(carrier, snapshot);
        SlotLayoutSamples.assertStacks(helper, left, List.of(new ItemStack(Items.EMERALD, 7)), "leftovers");
        SlotLayoutSamples.assertSlot(helper, carrier.pouch, 0, new ItemStack(Items.DIAMOND, 2), "restored pouch slot");
        helper.assertTrue(carrier.bag.isEmpty(), "A stack of the absent group went into the fallback");
        helper.succeed();
    }

    @ServerTest
    public static void emptySnapshotHoldsNoGroups(GameTestHelper helper) {
        helper.assertTrue(SlotSnapshot.EMPTY.groups().isEmpty(), "The empty snapshot has groups");
        helper.assertTrue(SlotSnapshot.EMPTY.isEmpty(), "The empty snapshot is not empty");

        SlotLayout<Carrier> layout = SlotLayoutSamples.layout();
        Carrier carrier = new Carrier(SlotLayoutSamples.LEFT);
        SlotSnapshot nothing = layout.capture(carrier);
        helper.assertFalse(nothing.groups().isEmpty(), "The capture of an empty carrier has no groups");
        helper.assertTrue(nothing.isEmpty(), "The capture of an empty carrier is not empty");
        SlotLayoutSamples.assertStacks(helper, layout.restore(carrier, SlotSnapshot.EMPTY), List.of(), "leftovers of the empty snapshot");

        carrier.bag.setItem(1, new ItemStack(Items.DIRT));
        helper.assertFalse(layout.capture(carrier).isEmpty(), "The capture of a carrier with a stack is empty");
        helper.succeed();
    }

    @ServerTest
    public static void vanillaSourcesCarryTheSlotsOfAPlayer(GameTestHelper helper) {
        SlotLayout<Player> layout = Nexus.getMod().getPersistence().slots(Player.class).registered().build();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        player.getInventory().setItem(Inventory.SELECTION_SIZE, new ItemStack(Items.EMERALD));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        SlotSnapshot snapshot = layout.take(player);
        helper.assertTrue(
                snapshot.groups().containsAll(List.of(SlotLayoutSamples.INVENTORY_GROUP, SlotLayoutSamples.ARMOR_GROUP, SlotLayoutSamples.OFFHAND_GROUP)),
                "A vanilla source is missing from the groups " + snapshot.groups()
        );
        helper.assertValueEqual(snapshot.size(SlotLayoutSamples.INVENTORY_GROUP), Inventory.INVENTORY_SIZE, "size of the inventory group");
        helper.assertValueEqual(snapshot.size(SlotLayoutSamples.ARMOR_GROUP), SlotLayoutSamples.ARMOR_SIZE, "size of the armor group");
        helper.assertValueEqual(snapshot.size(SlotLayoutSamples.OFFHAND_GROUP), 1, "size of the offhand group");

        Tag encoded = SlotSnapshot.FORMAT.write(snapshot, helper.getLevel().registryAccess());
        helper.assertValueEqual(
                SlotLayoutSamples.slotsByItem(encoded, SlotLayoutSamples.INVENTORY_GROUP),
                Map.of("minecraft:diamond", 0, "minecraft:emerald", Inventory.SELECTION_SIZE),
                "slots of the inventory group"
        );
        helper.assertValueEqual(SlotLayoutSamples.slotsByItem(encoded, SlotLayoutSamples.ARMOR_GROUP), Map.of("minecraft:iron_boots", 0), "slots of the armor group");
        helper.assertValueEqual(SlotLayoutSamples.slotsByItem(encoded, SlotLayoutSamples.OFFHAND_GROUP), Map.of("minecraft:shield", 0), "slots of the offhand group");
        helper.assertTrue(
                player.getInventory().getItem(0).isEmpty() && player.getItemBySlot(EquipmentSlot.FEET).isEmpty() && player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(),
                "The take left a stack on the player"
        );

        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        SlotLayoutSamples.assertStacks(helper, layout.restore(other, snapshot), List.of(), "leftovers");
        helper.assertValueEqual(other.getInventory().getItem(Inventory.SELECTION_SIZE).toString(), new ItemStack(Items.EMERALD).toString(), "restored inventory slot");
        helper.assertValueEqual(other.getItemBySlot(EquipmentSlot.FEET).toString(), new ItemStack(Items.IRON_BOOTS).toString(), "restored feet slot");
        helper.assertValueEqual(other.getItemBySlot(EquipmentSlot.OFFHAND).toString(), new ItemStack(Items.SHIELD).toString(), "restored offhand slot");
        helper.succeed();
    }

    private record DiamondSlots(SimpleContainer container) implements SlotAccess {

        @Override
        public int size() {
            return this.container.getContainerSize();
        }

        @Override
        public ItemStack get(int slot) {
            return this.container.getItem(slot);
        }

        @Override
        public void set(int slot, ItemStack stack) {
            this.container.setItem(slot, stack);
        }

        @Override
        public boolean accepts(int slot, ItemStack stack) {
            return stack.is(Items.DIAMOND);
        }
    }

    private static final class PocketSlots implements SlotSource<Carrier> {

        @Override
        public Class<Carrier> holder() {
            return Carrier.class;
        }

        @Override
        public Map<String, SlotAccess> groups(Carrier holder) {
            return Maps.transformValues(holder.pocketsByName, SlotLayoutSamples::whole);
        }
    }

    private static final class Carrier {

        private final SimpleContainer pouch = new SimpleContainer(SlotLayoutSamples.POUCH_SIZE);
        private final SimpleContainer bag = new SimpleContainer(SlotLayoutSamples.GROUP_SIZE);
        private final SimpleContainer picky = new SimpleContainer(SlotLayoutSamples.GROUP_SIZE);
        private final Map<String, SimpleContainer> pocketsByName = new HashMap<>();

        private Carrier(String... pockets) {
            for (String pocket : pockets) {
                this.pocketsByName.put(pocket, new SimpleContainer(SlotLayoutSamples.GROUP_SIZE));
            }
        }

        private SimpleContainer getPocket(String name) {
            return Objects.requireNonNull(this.pocketsByName.get(name));
        }
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {

        @SubscribeEvent
        public static void onRegisterSlotSources(RegisterSlotSourcesEvent event) {
            event.register(SlotLayoutSamples.POUCH, SlotSource.of(Carrier.class, carrier -> SlotLayoutSamples.whole(carrier.pouch)));
            event.register(SlotLayoutSamples.POCKETS, new PocketSlots());
            SlotLayoutSamples.secondRegistration = Assertions.catchThrowable(() -> event.register(
                    SlotLayoutSamples.POUCH,
                    SlotSource.of(Carrier.class, carrier -> SlotLayoutSamples.whole(carrier.bag))
            ));
        }
    }
}
