package dev.satherov.nexus.api.persistence.slot;

import dev.satherov.nexus.api.event.persistence.slot.RegisterSlotSourcesEvent;
import dev.satherov.nexus.api.persistence.Persistence;

import net.minecraft.world.item.ItemStack;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

///
/// A layout that copies the item stacks in the groups of slots of a holder into a [SlotSnapshot] and puts them back.
///
/// Can be built through [Persistence#slots(Class)] and kept in a `static` field.
/// The registered sources will be looked up on the first use of the layout.
///
/// Works on a live holder only.
///
/// @param <HOLDER> The type of the holders of the slots.
///
public final class SlotLayout<HOLDER> {

    ///
    /// The function that gets all groups of a holder, by their name in the layout,
    /// which looks up the sources on its first call.
    ///
    private final Function<HOLDER, Map<String, SlotAccess>> groups;

    ///
    /// The predicates of the item stacks that a group refuses, by the name of the group.
    ///
    private final Map<String, Predicate<ItemStack>> refusals;

    ///
    /// The fallback groups, in the order they are tried.
    ///
    private final List<String> fallbacks;

    ///
    /// Should only ever be called from [SlotLayoutBuilder#build()].
    ///
    /// @param groups    The function that gets the groups of a holder by their name in the layout.
    /// @param refusals  The predicates of the item stacks that a group refuses, by the name of the group.
    /// @param fallbacks The fallback groups, in the order they are tried.
    ///
    @ApiStatus.Internal
    public SlotLayout(Function<HOLDER, Map<String, SlotAccess>> groups, Map<String, Predicate<ItemStack>> refusals, List<String> fallbacks) {
        this.groups = groups;
        this.refusals = refusals;
        this.fallbacks = fallbacks;
    }

    ///
    /// Copies the item stacks that match the given filter from the given groups into a new snapshot.
    ///
    private static SlotSnapshot capture(Map<String, SlotAccess> groups, Predicate<ItemStack> filter) {
        Map<String, List<ItemStack>> stacksByGroup = new LinkedHashMap<>();
        groups.forEach((name, access) -> {
            List<ItemStack> stacks = new ArrayList<>(access.size());
            for (int slot = 0; slot < access.size(); slot++) {
                ItemStack stack = access.get(slot);
                stacks.add(stack.isEmpty() || !filter.test(stack) ? ItemStack.EMPTY : stack.copy());
            }

            stacksByGroup.put(name, stacks);
        });

        return new SlotSnapshot(stacksByGroup);
    }

    ///
    /// Copies the item stacks of every group of the given holder into a new snapshot.
    ///
    /// The holder will be left as it was.
    ///
    /// @param holder The holder of the slots.
    ///
    /// @return The snapshot, with the size of every group and the slot of every item stack.
    ///
    /// @throws IllegalStateException - If [RegisterSlotSourcesEvent] didn't fire yet.
    /// - If a source added by its identifier isn't registered or is for another class of holders.
    /// - If a group given to a refusal or a fallback can't be a group of the layout.
    /// - If two groups of the given holder have the same name in the layout.
    ///
    public SlotSnapshot capture(HOLDER holder) {
        return this.capture(holder, _ -> true);
    }

    ///
    /// Copies the item stacks that match the given filter from every group of the given holder into a new snapshot.
    ///
    /// The holder will be left as it was.
    ///
    /// @param holder The holder of the slots.
    /// @param filter The predicate of the item stacks to copy.
    ///
    /// @return The snapshot, with the size of every group and the slot of every item stack.
    ///
    /// @throws IllegalStateException - If [RegisterSlotSourcesEvent] didn't fire yet.
    /// - If a source added by its identifier isn't registered or is for another class of holders.
    /// - If a group given to a refusal or a fallback can't be a group of the layout.
    /// - If two groups of the given holder have the same name in the layout.
    ///
    public SlotSnapshot capture(HOLDER holder, Predicate<ItemStack> filter) {
        return SlotLayout.capture(this.groups.apply(holder), filter);
    }

    ///
    /// Moves the item stacks of every group of the given holder into a new snapshot.
    ///
    /// Every slot of every group will be empty.
    ///
    /// @param holder The holder of the slots.
    ///
    /// @return The snapshot, with the size of every group and the slot of every item stack.
    ///
    /// @throws IllegalStateException - If [RegisterSlotSourcesEvent] didn't fire yet.
    /// - If a source added by its identifier isn't registered or is for another class of holders.
    /// - If a group given to a refusal or a fallback can't be a group of the layout.
    /// - If two groups of the given holder have the same name in the layout.
    ///
    @CanIgnoreReturnValue
    public SlotSnapshot take(HOLDER holder) {
        return this.take(holder, _ -> true);
    }

    ///
    /// Moves the item stacks that match the given filter from every group of the given holder into a new snapshot.
    ///
    /// Every slot that held a matching item stack will be empty.
    ///
    /// @param holder The holder of the slots.
    /// @param filter The predicate of the item stacks to move.
    ///
    /// @return The snapshot, with the size of every group and the slot of every item stack.
    ///
    /// @throws IllegalStateException - If [RegisterSlotSourcesEvent] didn't fire yet.
    /// - If a source added by its identifier isn't registered or is for another class of holders.
    /// - If a group given to a refusal or a fallback can't be a group of the layout.
    /// - If two groups of the given holder have the same name in the layout.
    ///
    @CanIgnoreReturnValue
    public SlotSnapshot take(HOLDER holder, Predicate<ItemStack> filter) {
        Map<String, SlotAccess> groups = this.groups.apply(holder);
        SlotSnapshot snapshot = SlotLayout.capture(groups, filter);
        groups.forEach((name, access) -> {
            List<ItemStack> taken = snapshot.stacks(name);
            for (int slot = 0; slot < taken.size(); slot++) {
                if (!taken.get(slot).isEmpty()) {
                    access.set(slot, ItemStack.EMPTY);
                }
            }
        });

        return snapshot;
    }

    ///
    /// Puts every item stack of the given snapshot back into the slots of the given holder.
    ///
    /// An item stack goes back into its own slot if all the following is true:
    /// - The group still has the slot and the slot is empty.
    /// - The slot accepts the item stack.
    /// - The group doesn't refuse the item stack.
    ///
    /// Every other item stack goes through the fallback groups in their order, skipping every group that refuses it.
    /// Each fallback group takes the item stack through [SlotAccess#insert(int, ItemStack)],
    /// topping up matching item stacks before it fills a free slot.
    ///
    /// @param holder   The holder of the slots.
    /// @param snapshot The snapshot to put back.
    ///
    /// @return Every item stack or the rest of one that no slot took,
    /// including every item stack of a group the holder doesn't have.
    ///
    /// @throws IllegalStateException - If [RegisterSlotSourcesEvent] didn't fire yet.
    /// - If a source added by its identifier isn't registered or is for another class of holders.
    /// - If a group given to a refusal or a fallback can't be a group of the layout.
    /// - If two groups of the given holder have the same name in the layout.
    ///
    @CanIgnoreReturnValue
    public @UnmodifiableView List<ItemStack> restore(HOLDER holder, SlotSnapshot snapshot) {
        Map<String, SlotAccess> groups = this.groups.apply(holder);
        List<ItemStack> unplaced = new ArrayList<>();
        List<ItemStack> rest = new ArrayList<>();
        for (String name : snapshot.groups()) {
            SlotAccess access = groups.get(name);
            List<ItemStack> stacks = snapshot.stacks(name);
            if (access == null) {
                stacks.stream()
                        .filter(stack -> !stack.isEmpty())
                        .forEach(rest::add);
                continue;
            }

            for (int slot = 0; slot < stacks.size(); slot++) {
                ItemStack stack = stacks.get(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                
                if (this.fits(name, access, slot, stack)) {
                    access.set(slot, stack);
                } else {
                    unplaced.add(stack);
                }
            }
        }

        for (ItemStack stack : unplaced) {
            ItemStack left = this.fallBack(groups, stack);
            if (!left.isEmpty()) {
                rest.add(left);
            }
        }

        return Collections.unmodifiableList(rest);
    }

    ///
    /// Empties every slot of every group of the given holder.
    ///
    /// @param holder The holder of the slots.
    ///
    /// @throws IllegalStateException - If [RegisterSlotSourcesEvent] didn't fire yet.
    /// - If a source added by its identifier isn't registered or is for another class of holders.
    /// - If a group given to a refusal or a fallback can't be a group of the layout.
    /// - If two groups of the given holder have the same name in the layout.
    ///
    public void clear(HOLDER holder) {
        this.groups.apply(holder).values().forEach(access -> {
            for (int slot = 0; slot < access.size(); slot++) {
                access.set(slot, ItemStack.EMPTY);
            }
        });
    }

    ///
    /// If the given item stack can go back into the given slot of the given group.
    ///
    private boolean fits(String name, SlotAccess access, int slot, ItemStack stack) {
        return slot < access.size() && access.get(slot).isEmpty() && access.accepts(slot, stack) && !this.refuses(name, stack);
    }

    ///
    /// Inserts the given item stack into the fallback groups and returns the rest.
    ///
    private ItemStack fallBack(Map<String, SlotAccess> groups, ItemStack stack) {
        ItemStack rest = stack;
        for (String name : this.fallbacks) {
            SlotAccess access = groups.get(name);
            if (access == null || this.refuses(name, rest)) {
                continue;
            }
            
            for (int slot = 0; slot < access.size() && !rest.isEmpty(); slot++) {
                if (ItemStack.isSameItemSameComponents(access.get(slot), rest)) {
                    rest = access.insert(slot, rest);
                }
            }

            for (int slot = 0; slot < access.size() && !rest.isEmpty(); slot++) {
                if (access.get(slot).isEmpty()) {
                    rest = access.insert(slot, rest);
                }
            }
            
            if (rest.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        return rest;
    }

    ///
    /// If the given group refuses the given item stack.
    ///
    private boolean refuses(String name, ItemStack stack) {
        Predicate<ItemStack> refusal = this.refusals.get(name);
        return refusal != null && refusal.test(stack);
    }
}
