package dev.satherov.nexus.api.component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

///
/// A comparison of the part at the end of a path against a literal.
///
/// @param operator The operator that compares the part against the literal.
/// @param literal  The literal on the right side of the operator.
///
public record Comparison(Operator operator, Literal literal) {
    
    ///
    /// Creates a new comparison.
    ///
    /// @throws IllegalArgumentException If the operator is ordered and the literal is a flag.
    ///
    public Comparison {
        if (operator.isOrdered() && literal instanceof Literal.Flag) {
            throw new IllegalArgumentException("Flags have no order, found the operator '" + operator.getSymbol() + "'");
        }
    }
    
    ///
    /// Compares the text form of the given identifier with the given text by UTF-16 code units, the way [String#compareTo(String)] does.
    ///
    private static int compare(Identifier value, String text) {
        String namespace = value.getNamespace();
        String path = value.getPath();
        int length = namespace.length() + 1 + path.length();
        for (int i = 0; i < Math.min(length, text.length()); i++) {
            char unit = i < namespace.length() ? namespace.charAt(i) : (i == namespace.length() ? Identifier.NAMESPACE_SEPARATOR : path.charAt(i - namespace.length() - 1));
            if (unit != text.charAt(i)) {
                return unit - text.charAt(i);
            }
        }
        
        return length - text.length();
    }
    
    ///
    /// Tests the given number against the literal.
    ///
    /// Fails if the literal is not a number.
    /// `-0` equals `0`, and `NaN` passes only [Operator#NOT_EQUAL].
    ///
    /// @param value The number on the left side of the operator.
    ///
    /// @return `true` if the number passes.
    ///
    public boolean test(double value) {
        if (!(this.literal instanceof Literal.Numeric(double number))) {
            return false;
        }
        
        return switch (this.operator) {
            case EQUAL -> value == number;
            case NOT_EQUAL -> value != number;
            case LESS -> value < number;
            case LESS_OR_EQUAL -> value <= number;
            case GREATER -> value > number;
            case GREATER_OR_EQUAL -> value >= number;
        };
    }
    
    ///
    /// Tests the given text against the literal.
    ///
    /// Fails if the literal is not text.
    /// The ordered operators compare the text by its UTF-16 code units, the way [String#compareTo(String)] does.
    ///
    /// @param value The text on the left side of the operator.
    ///
    /// @return `true` if the text passes.
    ///
    public boolean test(String value) {
        return this.literal instanceof Literal.Text(String text) && this.passes(value.compareTo(text));
    }
    
    ///
    /// Tests the given flag against the literal.
    ///
    /// Fails if the literal is text.
    /// If the literal is a number, the flag is compared as `1` or `0`, the way NBT writes a flag as a byte.
    ///
    /// @param value The flag on the left side of the operator.
    ///
    /// @return `true` if the flag passes.
    ///
    public boolean test(boolean value) {
        return switch (this.literal) {
            case Literal.Flag(boolean flag) -> this.passes(Boolean.compare(value, flag));
            case Literal.Numeric _ -> this.test(value ? 1.0D : 0.0D);
            case Literal.Text _ -> false;
        };
    }
    
    ///
    /// Tests the text form of the given identifier against the literal.
    ///
    /// Fails if the literal is not text.
    /// The text form is compared without building a string.
    ///
    /// @param value The identifier on the left side of the operator.
    ///
    /// @return `true` if the identifier passes.
    ///
    public boolean test(Identifier value) {
        return this.literal instanceof Literal.Text(String text) && this.passes(Comparison.compare(value, text));
    }
    
    ///
    /// Applies the operator to the given order of a value against the literal, as [Comparable#compareTo(Object)] returns it.
    ///
    private boolean passes(int order) {
        return switch (this.operator) {
            case EQUAL -> order == 0;
            case NOT_EQUAL -> order != 0;
            case LESS -> order < 0;
            case LESS_OR_EQUAL -> order <= 0;
            case GREATER -> order > 0;
            case GREATER_OR_EQUAL -> order >= 0;
        };
    }
    
    ///
    /// Tests the given tag against the literal.
    ///
    /// A number tag is compared as a `double` and a string tag as text.
    /// If the literal is a flag, a byte tag counts as `true` unless it's `0`.
    /// Compounds, lists, and the end tag fail.
    ///
    /// @param value The tag on the left side of the operator.
    ///
    /// @return `true` if the tag passes.
    ///
    public boolean test(Tag value) {
        return switch (value) {
            case ByteTag(byte number) when this.literal instanceof Literal.Flag -> this.test(number != 0);
            case NumericTag number -> this.test(number.doubleValue());
            case StringTag(String text) -> this.test(text);
            case CompoundTag _, CollectionTag _, EndTag _ -> false;
        };
    }
    
    ///
    /// The operator of a comparison.
    ///
    @RequiredArgsConstructor
    public enum Operator {
        ///
        /// The operator that passes if the part equals the literal.
        ///
        EQUAL("=="),
        
        ///
        /// The operator that passes if the part doesn't equal the literal.
        ///
        NOT_EQUAL("!="),
        
        ///
        /// The operator that passes if the part is less than the literal.
        ///
        LESS("<"),
        
        ///
        /// The operator that passes if the part is less than or equal to the literal.
        ///
        LESS_OR_EQUAL("<="),
        
        ///
        /// The operator that passes if the part is greater than the literal.
        ///
        GREATER(">"),
        
        ///
        /// The operator that passes if the part is greater than or equal to the literal.
        ///
        GREATER_OR_EQUAL(">=");
        
        ///
        /// The symbol of the operator.
        ///
        @Getter
        private final String symbol;
        
        ///
        /// If the operator compares by order.
        ///
        /// @return `true` for [#LESS], [#LESS_OR_EQUAL], [#GREATER], and [#GREATER_OR_EQUAL].
        ///
        public boolean isOrdered() {
            return this != Operator.EQUAL && this != Operator.NOT_EQUAL;
        }
    }
    
    ///
    /// A literal that the part at a path is compared against.
    ///
    public sealed interface Literal {
        
        ///
        /// A number literal.
        ///
        /// @param value The number.
        ///
        record Numeric(double value) implements Literal { }
        
        ///
        /// A text literal.
        ///
        /// @param value The text.
        ///
        record Text(String value) implements Literal { }
        
        ///
        /// A flag literal.
        ///
        /// @param value The flag.
        ///
        record Flag(boolean value) implements Literal { }
    }
}
