package dev.satherov.nexus.api.persistence.slot;

import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.key.MapKey;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.vanilla.VanillaCodecs;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.internal.codec.CodecErrors;
import dev.satherov.nexus.internal.codec.CollectionCodecs;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.format.Operations;

import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

///
/// A copy of the item stacks in the groups of slots of one holder, made by a [SlotLayout].
///
/// The NBT form inside the versioned form of [#FORMAT] is `{groups: {<name>: {size: <int>, items: [<ItemStackWithSlot>]}}}`:
/// - `<name>` is the name of a group in the layout.
/// - `size` is the number of slots of the group.
/// - `items` holds the item stack of every slot that isn't empty, in the form of [ItemStackWithSlot#CODEC].
///
/// The entry of an item stack of more than {@value Item#ABSOLUTE_MAX_STACK_SIZE} items holds that many under `count`,
/// and its number of items under `total`.
///
/// Keeps every group it was made with through encoding and decoding, even one that no source has anymore.
///
@Slf4j
public final class SlotSnapshot {

    ///
    /// The codec of the item stacks of one group, in slot order with an empty item stack for every empty slot.
    ///
    private static final NexusCodec<List<ItemStack>, Access.Registries> GROUP = NexusCodec.struct(
            "slot_group",
            NexusCodec.INT.field("size", Group::size),
            new SlotSnapshot.GroupItems().field("items", Group::items),
            Group::new
    ).flatXmap(Group::stacks, Group::of);

    ///
    /// The format of a snapshot, at version `1`.
    ///
    /// An item stack in a slot above `255` can't be encoded.
    /// If an entry of `items` can't be decoded from JSON or NBT, such as one of an item that is gone, it will be logged and left out.
    ///
    public static final Format<SlotSnapshot> FORMAT = Format.of(NexusCodec.struct(
            "slot_snapshot",
            NexusCodec.mapOf(MapKey.STRING, SlotSnapshot.GROUP).field("groups", snapshot -> snapshot.stacksByGroup),
            SlotSnapshot::new
    ));

    ///
    /// The snapshot without groups.
    ///
    public static final SlotSnapshot EMPTY = new SlotSnapshot(Map.of());

    ///
    /// The item stacks of every group in slot order, with an empty item stack for every empty slot.
    ///
    private final @UnmodifiableView Map<String, List<ItemStack>> stacksByGroup;

    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param stacksByGroup The item stacks of every group in slot order, with an empty item stack for every empty slot.
    ///
    @ApiStatus.Internal
    public SlotSnapshot(Map<String, List<ItemStack>> stacksByGroup) {
        Map<String, List<ItemStack>> copied = new LinkedHashMap<>();
        stacksByGroup.forEach((name, stacks) -> copied.put(name, List.copyOf(stacks)));
        this.stacksByGroup = Collections.unmodifiableMap(copied);
    }

    ///
    /// The name of every group.
    ///
    /// @return The name of every group.
    ///
    public @Unmodifiable List<String> groups() {
        return List.copyOf(this.stacksByGroup.keySet());
    }

    ///
    /// The number of slots of the given group.
    ///
    /// @param group The name of the group.
    ///
    /// @return The number of slots, or `0` if the snapshot doesn't have the group.
    ///
    public int size(String group) {
        return this.stacksByGroup.getOrDefault(group, List.of()).size();
    }

    ///
    /// Copies the item stacks of the given group, in slot order.
    ///
    /// @param group The name of the group.
    ///
    /// @return One item stack for every slot including the empty ones, or an empty list if the snapshot doesn't have the group.
    ///
    public @Unmodifiable List<ItemStack> stacks(String group) {
        return this.stacksByGroup.getOrDefault(group, List.of())
                .stream()
                .map(ItemStack::copy)
                .toList();
    }

    ///
    /// Copies every item stack that isn't empty from every group.
    ///
    /// @return The item stacks.
    ///
    public @Unmodifiable List<ItemStack> gather() {
        return this.stacksByGroup.values()
                .stream()
                .flatMap(List::stream)
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    ///
    /// @return `true` if no group holds an item stack.
    ///
    public boolean isEmpty() {
        return this.stacksByGroup.values()
                .stream()
                .flatMap(List::stream)
                .allMatch(ItemStack::isEmpty);
    }

    ///
    /// The NBT form of one group.
    ///
    /// @param size  The number of slots of the group.
    /// @param items The item stack of every slot that isn't empty.
    ///
    private record Group(int size, List<ItemStackWithSlot> items) {

        ///
        /// Creates the NBT form of the given item stacks.
        ///
        private static Group of(List<ItemStack> stacks) {
            List<ItemStackWithSlot> items = new ArrayList<>();
            for (int slot = 0; slot < stacks.size(); slot++) {
                if (!stacks.get(slot).isEmpty()) {
                    items.add(new ItemStackWithSlot(slot, stacks.get(slot)));
                }
            }

            return new Group(stacks.size(), items);
        }

        ///
        /// Places every item stack at its slot.
        ///
        /// Fails if the size is out of range, a slot is outside the group, or a slot holds two item stacks.
        ///
        private List<ItemStack> stacks() {
            if (this.size < 0 || this.size > CollectionCodecs.LIMIT) {
                throw CodecErrors.outOfRange(0, CollectionCodecs.LIMIT, this.size);
            }
            
            List<ItemStack> stacks = new ArrayList<>(Collections.nCopies(this.size, ItemStack.EMPTY));
            for (ItemStackWithSlot item : this.items) {
                if (!item.isValidInContainer(this.size)) {
                    throw CodecErrors.outOfRange(0, this.size - 1L, item.slot());
                }
                
                if (!stacks.get(item.slot()).isEmpty()) {
                    throw CodecErrors.mismatch("a slot without a stack", item.slot());
                }
                
                stacks.set(item.slot(), item.stack());
            }

            return stacks;
        }
    }

    ///
    /// The codec of the entries of one group, which leaves out every entry it can't decode from JSON or NBT.
    ///
    private static final class GroupItems extends Traversal<List<ItemStackWithSlot>, Access.Registries> {

        ///
        /// The codec of one entry.
        ///
        private final Traversal<ItemStackWithSlot, Access.Registries> entry = new SlotSnapshot.GroupEntry();

        ///
        /// The codec of every entry, which fails if one of them fails.
        ///
        private final Traversal<List<ItemStackWithSlot>, Access.Registries> entries = CollectionCodecs.list(this.entry, CollectionCodecs.LIMIT);

        ///
        /// Creates the codec of the entries of one group.
        ///
        private GroupItems() {
            super("slot_group_items");
        }

        ///
        /// Writes every entry, and fails if one of them fails.
        ///
        @Override
        public <V> V write(Operations<V> operations, List<ItemStackWithSlot> value) {
            return this.entries.write(operations, value);
        }

        ///
        /// Reads every entry on the network, and logs and leaves out every entry that can't be decoded otherwise.
        ///
        @Override
        public <V> List<ItemStackWithSlot> read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                return this.entries.read(operations, input);
            }

            List<V> elements = operations.asList(input);
            List<ItemStackWithSlot> items = new ArrayList<>(elements.size());
            for (int i = 0; i < elements.size(); i++) {
                try {
                    items.add(this.entry.read(operations, elements.get(i)));
                } catch (NexusCodecException failure) {
                    SlotSnapshot.log.warn("Could not decode the item stack at index '{}' of a slot group, it will be left out of the snapshot", i, failure);
                }
            }

            return items;
        }
    }

    ///
    /// The codec of one entry of a group, which writes the number of items of a large item stack under `total` in JSON and NBT.
    ///
    private static final class GroupEntry extends Traversal<ItemStackWithSlot, Access.Registries> {

        ///
        /// The key of the number of items of an item stack of more than {@value Item#ABSOLUTE_MAX_STACK_SIZE} items.
        ///
        private static final String TOTAL = "total";

        ///
        /// The codec of an entry of at most {@value Item#ABSOLUTE_MAX_STACK_SIZE} items.
        ///
        private final Traversal<ItemStackWithSlot, Access.Registries> vanilla = (Traversal<ItemStackWithSlot, Access.Registries>) VanillaCodecs.ITEM_STACK_WITH_SLOT;

        ///
        /// Creates the codec of one entry of a group.
        ///
        private GroupEntry() {
            super("slot_group_entry");
        }

        ///
        /// Writes the entry the same as [VanillaCodecs#ITEM_STACK_WITH_SLOT],
        /// with {@value Item#ABSOLUTE_MAX_STACK_SIZE} under `count` and the number of items under `total` for a large item stack in JSON and NBT.
        ///
        @Override
        public <V> V write(Operations<V> operations, ItemStackWithSlot value) {
            int count = value.stack().getCount();
            if (operations.isPositional() || count <= Item.ABSOLUTE_MAX_STACK_SIZE) {
                return this.vanilla.write(operations, value);
            }

            V entry = this.vanilla.write(operations, new ItemStackWithSlot(value.slot(), value.stack().copyWithCount(Item.ABSOLUTE_MAX_STACK_SIZE)));
            return operations.put(entry, SlotSnapshot.GroupEntry.TOTAL, operations.ofInt(count));
        }

        ///
        /// Reads the entry the same as [VanillaCodecs#ITEM_STACK_WITH_SLOT], and sets the number of items to `total` if the entry has it.
        ///
        /// Fails if `total` isn't an integer in `[100, 2147483647]`.
        ///
        @Override
        public <V> ItemStackWithSlot read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                return this.vanilla.read(operations, input);
            }

            V total = operations.get(input, SlotSnapshot.GroupEntry.TOTAL);
            if (total == null) {
                return this.vanilla.read(operations, input);
            }

            V rest = operations.emptyObject();
            for (String key : operations.keys(input)) {
                if (!key.equals(SlotSnapshot.GroupEntry.TOTAL)) {
                    operations.put(rest, key, Objects.requireNonNull(operations.get(input, key)));
                }
            }

            ItemStackWithSlot item = this.vanilla.read(operations, rest);
            return new ItemStackWithSlot(item.slot(), item.stack().copyWithCount(SlotSnapshot.GroupEntry.readTotal(operations, total)));
        }

        ///
        /// Reads the number of items under `total`.
        ///
        private static <V> int readTotal(Operations<V> operations, V total) {
            int count;
            try {
                count = operations.asInt(total);
            } catch (NexusCodecException failure) {
                throw CodecErrors.prefixKey(failure, SlotSnapshot.GroupEntry.TOTAL);
            }

            if (count <= Item.ABSOLUTE_MAX_STACK_SIZE) {
                throw CodecErrors.prefixKey(CodecErrors.outOfRange(Item.ABSOLUTE_MAX_STACK_SIZE + 1L, Integer.MAX_VALUE, count), SlotSnapshot.GroupEntry.TOTAL);
            }

            return count;
        }
    }
}
