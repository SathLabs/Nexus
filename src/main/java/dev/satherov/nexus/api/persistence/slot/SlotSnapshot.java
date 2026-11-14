package dev.satherov.nexus.api.persistence.slot;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.key.MapKey;
import dev.satherov.nexus.api.codec.vanilla.VanillaCodecs;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.internal.codec.CodecErrors;
import dev.satherov.nexus.internal.codec.CollectionCodecs;

import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

///
/// A copy of the item stacks in the groups of slots of one holder, made by a [SlotLayout].
///
/// The NBT form inside the versioned form of [#FORMAT] is `{groups: {<name>: {size: <int>, items: [<ItemStackWithSlot>]}}}`:
/// - `<name>` is the name of a group in the layout.
/// - `size` is the number of slots of the group.
/// - `items` holds the item stack of every slot that isn't empty, in the form of [ItemStackWithSlot#CODEC].
///
/// Keeps every group it was made with through encoding and decoding, even one that no source has anymore.
///
public final class SlotSnapshot {

    ///
    /// The codec of the item stacks of one group, in slot order with an empty item stack for every empty slot.
    ///
    private static final NexusCodec<List<ItemStack>, Access.Registries> GROUP = NexusCodec.struct(
            "slot_group",
            NexusCodec.INT.field("size", Group::size),
            VanillaCodecs.ITEM_STACK_WITH_SLOT.list().field("items", Group::items),
            Group::new
    ).flatXmap(Group::stacks, Group::of);

    ///
    /// The format of a snapshot, at version `1`.
    ///
    /// An item stack in a slot above `255` can't be encoded.
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
}
