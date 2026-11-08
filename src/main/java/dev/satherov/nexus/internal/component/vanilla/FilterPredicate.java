package dev.satherov.nexus.internal.component.vanilla;

import dev.satherov.nexus.api.component.ComponentFilter;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.predicates.DataComponentPredicate;

import org.jetbrains.annotations.ApiStatus;

///
/// A data component predicate that passes what its component filter passes.
///
/// @param filter The component filter.
///
@ApiStatus.Internal
public record FilterPredicate(ComponentFilter filter) implements DataComponentPredicate {

    ///
    /// The `nexus:filter` predicate type, whose codec is the DFU codec of the component filter.
    ///
    /// No concrete predicate type is sent over a neoforge connection inside `DataComponentMatchers`, since the registry of predicate types isn't synced.
    ///
    public static final DataComponentPredicate.Type<FilterPredicate> TYPE = new DataComponentPredicate.ConcreteType<>(ComponentFilter.CODEC.asDfu().xmap(FilterPredicate::new, FilterPredicate::filter));

    ///
    /// Tests the given holder with the component filter.
    ///
    /// @param components The holder of the components.
    ///
    /// @return `true` if the holder passes the component filter.
    ///
    @Override
    public boolean matches(DataComponentGetter components) {
        return this.filter.test(components);
    }
}
