package dev.satherov.nexus.api.component;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

///
/// The comparison of a value against a literal.
///
/// The ordered operators apply to numbers and strings only. Booleans, structs, lists, and registry holders take [#EQUAL] and [#NOT_EQUAL].
/// A registry holder is compared by its identifier.
///
@RequiredArgsConstructor
@Accessors(fluent = true)
public enum Operator {
    ///
    /// Equal to the literal, written as `==`.
    ///
    EQUAL("=="),
    
    ///
    /// Not equal to the literal, written as `!=`.
    ///
    NOT_EQUAL("!="),
    
    ///
    /// Less than the literal, written as `<`.
    ///
    LESS("<"),
    
    ///
    /// Less than or equal to the literal, written as `<=`.
    ///
    AT_MOST("<="),
    
    ///
    /// Greater than the literal, written as `>`.
    ///
    GREATER(">"),
    
    ///
    /// Greater than or equal to the literal, written as `>=`.
    ///
    AT_LEAST(">=");
    
    ///
    /// The symbol of this operator, as written in the data form.
    ///
    @Getter
    private final String symbol;
    
    ///
    /// If this operator compares by the order of the values.
    ///
    /// @return `true` for `<`, `<=`, `>`, and `>=`.
    ///
    public boolean isOrdered() {
        return switch (this) {
            case EQUAL, NOT_EQUAL -> false;
            case LESS, AT_MOST, GREATER, AT_LEAST -> true;
        };
    }
}
