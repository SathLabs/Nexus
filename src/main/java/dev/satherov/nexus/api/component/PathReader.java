package dev.satherov.nexus.api.component;

import dev.satherov.nexus.api.event.component.RegisterPathReadersEvent;

import java.util.function.Predicate;

///
/// A reader that tests the part at one path in a component value without encoding the value.
///
/// A reader should pass exactly the values that pass when the part at the path is encoded to NBT with its codec and the tag is tested with the comparison.
/// The reader should ignore it if another part of the value fails to encode.
///
/// @param <VALUE> The type of the values of the component.
///
/// @see RegisterPathReadersEvent
///
public interface PathReader<VALUE> {
    
    ///
    /// The cost of one read.
    ///
    /// The tests of sibling paths run from the cheapest reader up, and the result of an [Cost#ENCODE] read is reused.
    /// A reader made of parts costs what its most expensive part costs, and a `[*]` step costs at least [Cost#SCAN].
    ///
    /// @return The cost of one read.
    ///
    Cost getCost();
    
    ///
    /// If the values at the path have an order.
    ///
    /// Flags, holders, and structs have no order.
    ///
    /// @return `true` if the values at the path have an order.
    ///
    boolean isOrdered();
    
    ///
    /// Creates the test of a present component value against the given comparison.
    ///
    /// Called once per comparison, when the filter is built.
    /// The test allocates nothing where the reader's cost is DIRECT and fails where the path is absent.
    ///
    /// @param comparison The comparison of the part at the path.
    ///
    /// @return The test of a component value.
    ///
    Predicate<VALUE> bind(Comparison comparison);
    
    ///
    /// A cost that one read may have, from the cheapest to the most expensive.
    ///
    enum Cost {
        ///
        /// The read follows fields and accessors to the part.
        ///
        DIRECT,
        
        ///
        /// The read goes through the elements of a list or the entries of a map.
        ///
        SCAN,
        
        ///
        /// The read encodes the value with its codec.
        ///
        ENCODE
    }
}
