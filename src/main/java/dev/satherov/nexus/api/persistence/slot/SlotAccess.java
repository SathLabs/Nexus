package dev.satherov.nexus.api.persistence.slot;

import net.neoforged.neoforge.transfer.IndexModifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.resource.Resource;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;
import java.util.function.Supplier;

///
/// A row of slots that a [SlotLayout] reads and fills by slot number.
///
/// May be implemented by a source to change which item stacks a slot accepts and how much of one it takes.
///
public interface SlotAccess {

    ///
    /// Creates a new window on the given container.
    ///
    /// A slot of the window accepts what [Container#canPlaceItem(int, ItemStack)] accepts
    /// and takes at most [Container#getMaxStackSize(ItemStack)] items of an item stack.
    ///
    /// @param container The container the slots belong to.
    /// @param offset    The slot of the container that slot `0` of the window reads.
    /// @param size      The number of slots of the window.
    ///
    /// @return The window.
    ///
    static SlotAccess of(Container container, int offset, int size) {
        return new SlotAccess() {

            ///
            /// Will always return the given size.
            ///
            @Override
            public int size() {
                return size;
            }

            ///
            /// Gets the item stack of the container at the given slot plus the offset.
            ///
            @Override
            public ItemStack get(int slot) {
                return container.getItem(offset + slot);
            }

            ///
            /// Puts the given item stack into the container at the given slot plus the offset.
            ///
            @Override
            public void set(int slot, ItemStack stack) {
                container.setItem(offset + slot, stack);
            }

            ///
            /// If the container can place the given item stack at the given slot plus the offset.
            ///
            @Override
            public boolean accepts(int slot, ItemStack stack) {
                return container.canPlaceItem(offset + slot, stack);
            }

            ///
            /// Inserts the given item stack up to the maximum stack size of the container.
            ///
            @Override
            public ItemStack insert(int slot, ItemStack stack) {
                return SlotAccess.fill(this, slot, stack, container.getMaxStackSize(stack));
            }
        };
    }

    ///
    /// Creates a new row over every slot of the given resource handler.
    ///
    /// A slot is read through [ItemUtil#getStack(ResourceHandler, int)] and written through the given index modifier.
    /// A slot accepts what [ResourceHandler#isValid(int, Resource)] accepts and
    /// takes at most [ResourceHandler#getCapacityAsInt(int, Resource)] items of an item stack.
    ///
    /// @param handler  The resource handler the slots belong to.
    /// @param modifier The index modifier that writes into the slots of the resource handler.
    ///
    /// @return The row.
    ///
    static SlotAccess of(ResourceHandler<ItemResource> handler, IndexModifier<ItemResource> modifier) {
        return new SlotAccess() {

            ///
            /// The number of slots of the resource handler.
            ///
            @Override
            public int size() {
                return handler.size();
            }

            ///
            /// Reads the item stack in the given slot from the resource handler.
            ///
            @Override
            public ItemStack get(int slot) {
                return ItemUtil.getStack(handler, slot);
            }

            ///
            /// Writes the given item stack into the given slot through the index modifier.
            ///
            @Override
            public void set(int slot, ItemStack stack) {
                modifier.set(slot, ItemResource.of(stack), stack.getCount());
            }

            ///
            /// If the resource handler accepts the given item stack in the given slot.
            ///
            @Override
            public boolean accepts(int slot, ItemStack stack) {
                return handler.isValid(slot, ItemResource.of(stack));
            }

            ///
            /// Inserts the given item stack up to the capacity of the given slot.
            ///
            @Override
            public ItemStack insert(int slot, ItemStack stack) {
                return SlotAccess.fill(this, slot, stack, handler.getCapacityAsInt(slot, ItemResource.of(stack)));
            }
        };
    }

    ///
    /// Creates a new row of one slot that is read through the given getter and written through the given setter.
    ///
    /// The slot number is ignored.
    ///
    /// @param getter The getter of the item stack in the slot.
    /// @param setter The setter of the item stack in the slot.
    ///
    /// @return The row.
    ///
    static SlotAccess single(Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        return new SlotAccess() {

            ///
            /// Will always return `1`.
            ///
            @Override
            public int size() {
                return 1;
            }

            ///
            /// Gets the item stack from the getter.
            ///
            @Override
            public ItemStack get(int slot) {
                return getter.get();
            }

            ///
            /// Passes the given item stack to the setter.
            ///
            @Override
            public void set(int slot, ItemStack stack) {
                setter.accept(stack);
            }
        };
    }

    ///
    /// Inserts as much of the given item stack into the given slot as the slot takes up to the given limit and returns the rest.
    ///
    private static ItemStack fill(SlotAccess access, int slot, ItemStack stack, int limit) {
        if (stack.isEmpty() || !access.accepts(slot, stack)) {
            return stack;
        }

        ItemStack existing = access.get(slot);
        if (!existing.isEmpty() &&!ItemStack.isSameItemSameComponents(existing, stack)) {
            return stack;
        }

        int moved = Math.min(limit - existing.getCount(), stack.getCount());
        if (moved <= 0) {
            return stack;
        }

        access.set(slot, stack.copyWithCount(existing.getCount() + moved));
        return moved == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    ///
    /// The number of slots in the row.
    ///
    /// @return The number of slots.
    ///
    int size();

    ///
    /// Gets the item stack in the given slot.
    ///
    /// @param slot The slot number.
    ///
    /// @return The item stack.
    ///
    ItemStack get(int slot);

    ///
    /// Puts the given item stack into the given slot.
    ///
    /// @param slot  The slot number.
    /// @param stack The item stack.
    ///
    void set(int slot, ItemStack stack);

    ///
    /// If the given slot accepts the given item stack.
    ///
    /// Accepts every item stack by default.
    ///
    /// @param slot  The slot number.
    /// @param stack The item stack.
    ///
    /// @return `true` if the slot accepts the item stack.
    ///
    default boolean accepts(int slot, ItemStack stack) {
        return true;
    }

    ///
    /// Inserts as much of the given item stack into the given slot as the slot takes and returns the rest.
    ///
    /// An empty slot takes the item stack up to the maximum stack size of its item.
    /// If the slot already holds the same item with the same components, it will be topped up to that size.
    /// If the slot doesn't accept the item stack, it will take nothing.
    ///
    /// The given item stack will be left as it was.
    ///
    /// @param slot  The slot number.
    /// @param stack The item stack to insert.
    ///
    /// @return The part of the item stack that the slot didn't take.
    ///
    default ItemStack insert(int slot, ItemStack stack) {
        return SlotAccess.fill(this, slot, stack, stack.getMaxStackSize());
    }
}
