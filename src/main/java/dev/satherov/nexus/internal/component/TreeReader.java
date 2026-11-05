package dev.satherov.nexus.internal.component;

import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.component.Literal;
import dev.satherov.nexus.api.component.Operator;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.List;

///
/// A path reader over any component that wasn't built by Nexus.
///
/// The tree of a value comes from [EncodedValues] and is walked along the steps.
///
@ApiStatus.Internal
@RequiredArgsConstructor
public final class TreeReader implements PathReader {
    
    ///
    /// The component the path starts at.
    ///
    private final DataComponentType<?> type;
    
    ///
    /// The keys and indices down to the value.
    ///
    private final List<Step> steps;
    
    ///
    /// Compares the given scalar against the given literal with the given operator.
    ///
    private static boolean compareScalar(Object value, Operator operator, Literal literal) {
        return switch (literal) {
            case Literal.Numeric(double expected) -> value instanceof Number number && PathReader.passes(operator, Double.compare(number.doubleValue(), expected));
            case Literal.Text(String expected) -> value instanceof String text && PathReader.passes(operator, text.compareTo(expected));
            case Literal.Flag(boolean expected) -> value instanceof Boolean flag && PathReader.passes(operator, flag == expected ? 0 : 1);
        };
    }
    
    ///
    /// Will always return [Kind#UNKNOWN].
    ///
    @Override
    public Kind kind() {
        return Kind.UNKNOWN;
    }
    
    ///
    /// Compares a number, a string, or a boolean directly and anything else through its tree.
    ///
    @Override
    public boolean compare(DataComponentGetter holder, Operator operator, Literal literal) {
        return this.compareValue(this.type, holder, operator, literal);
    }
    
    ///
    /// Compares the value of the given type on the given holder, reading a byte tag in the tree as a boolean.
    ///
    private <VALUE> boolean compareValue(DataComponentType<VALUE> type, DataComponentGetter holder, Operator operator, Literal literal) {
        VALUE value = holder.get(type);
        if (value instanceof Number || value instanceof String || value instanceof Boolean) {
            return this.steps.isEmpty() && TreeReader.compareScalar(value, operator, literal);
        }
        
        Tag tag = value != null ? this.find(EncodedValues.tree(type, value)) : null;
        return switch (literal) {
            case Literal.Numeric(double expected) -> tag instanceof NumericTag number && PathReader.passes(operator, Double.compare(number.doubleValue(), expected));
            case Literal.Text(String expected) -> tag instanceof StringTag(String text) && PathReader.passes(operator, text.compareTo(expected));
            case Literal.Flag(boolean expected) -> tag instanceof ByteTag(byte flag) && PathReader.passes(operator, (flag != 0) == expected ? 0 : 1);
        };
    }
    
    ///
    /// Gets the tag at the end of the steps below the given tree, or `null` if a key or index is missing.
    ///
    private @Nullable Tag find(Tag tree) {
        Tag tag = tree;
        for (int i = 0; i < this.steps.size(); i++) {
            tag = switch (this.steps.get(i)) {
                case Step.Key(String name) -> tag instanceof CompoundTag compound ? compound.get(name) : null;
                case Step.Index(int index) -> tag instanceof CollectionTag list && index < list.size() ? list.get(index) : null;
            };
        }
        
        return tag;
    }
}
