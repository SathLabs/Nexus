package dev.satherov.nexus.api.component;

///
/// A value that a path is compared against.
///
/// In JSON and NBT, it's written as a bare number, string, or boolean.
/// On the network, the kind is written as an index before the value.
///
public sealed interface Literal permits Literal.Numeric, Literal.Text, Literal.Flag {

    ///
    /// A literal that is a number.
    ///
    /// Written as an `int` if it is a whole number that fits one, and as a `double` otherwise.
    ///
    /// @param value The number, compared numerically whatever the number type at the path is.
    ///
    record Numeric(double value) implements Literal { }

    ///
    /// A literal that is a string.
    ///
    /// @param value The string, compared by equality and lexically for the ordered operators.
    ///
    record Text(String value) implements Literal { }

    ///
    /// A literal that is a boolean.
    ///
    /// In NBT, it's written as a byte `0` or `1`.
    ///
    /// @param value The boolean, compared by equality only.
    ///
    record Flag(boolean value) implements Literal { }
}
