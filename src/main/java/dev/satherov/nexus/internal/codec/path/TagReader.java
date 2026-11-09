package dev.satherov.nexus.internal.codec.path;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentPath;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

///
/// A reader of the steps of a component path in an NBT tree.
///
/// @param steps The steps to read, starting at the root of the tree.
///
@ApiStatus.Internal
public record TagReader(List<ComponentPath.Step> steps) {
    
    ///
    /// Checks if the tag at the steps in the given tree passes the given comparison.
    ///
    /// A wildcard step passes if any element of a list tag or any value of a compound tag passes the steps after it.
    ///
    /// The elements of byte, int, and long array tags are read directly from the array, and a byte is compared as a flag if the literal of the comparison is a flag.
    /// An element of an array tag fails if more steps follow it.
    ///
    /// @param root      The root of the tree.
    /// @param comparison The comparison to test the tag at the steps with.
    ///
    /// @return `true` if the tag at the steps passes, and `false` if it fails or the tree doesn't have it.
    ///
    public boolean test(Tag root, Comparison comparison) {
        return this.test(root, 0, comparison);
    }
    
    ///
    /// Checks if the tag at the steps from the given index on passes the given comparison.
    ///
    private boolean test(Tag tag, int from, Comparison comparison) {
        if (from == this.steps.size()) {
            return comparison.test(tag);
        }
        
        return switch (this.steps.get(from)) {
            case ComponentPath.Step.Key(String name) -> tag instanceof CompoundTag compound && compound.get(name) instanceof Tag value && this.test(value, from + 1, comparison);
            case ComponentPath.Step.Index(int index) -> tag instanceof CollectionTag list && index < list.size() && this.testElement(list, index, from + 1, comparison);
            case ComponentPath.Step.Wildcard _ -> this.testAny(tag, from + 1, comparison);
        };
    }
    
    ///
    /// Checks if any element of the given list tag or any value of the given compound tag passes the steps from the given index on.
    ///
    private boolean testAny(Tag tag, int from, Comparison comparison) {
        if (tag instanceof CollectionTag list) {
            for (int i = 0; i < list.size(); i++) {
                if (this.testElement(list, i, from, comparison)) {
                    return true;
                }
            }
            
            return false;
        }
        
        if (tag instanceof CompoundTag compound) {
            for (Tag value : compound.values()) {
                if (this.test(value, from, comparison)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    ///
    /// Checks if the element at the given index of the given collection tag passes the steps from `from` on.
    ///
    private boolean testElement(CollectionTag list, int index, int from, Comparison comparison) {
        return switch (list) {
            case ListTag elements -> this.test(elements.get(index), from, comparison);
            // The element of an array tag is a number, so no step can follow it.
            case ByteArrayTag _, IntArrayTag _, LongArrayTag _ when from < this.steps.size() -> false;
            case ByteArrayTag bytes when comparison.literal() instanceof Comparison.Literal.Flag -> comparison.test(bytes.getAsByteArray()[index] != 0);
            case ByteArrayTag bytes -> comparison.test(bytes.getAsByteArray()[index]);
            case IntArrayTag ints -> comparison.test(ints.getAsIntArray()[index]);
            case LongArrayTag longs -> comparison.test(longs.getAsLongArray()[index]);
        };
    }
}
