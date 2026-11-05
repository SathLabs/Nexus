package dev.satherov.nexus.internal.component;

import dev.satherov.nexus.api.component.ComponentFilter;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.predicates.DataComponentPredicate;

import org.jetbrains.annotations.ApiStatus;

///
/// The vanilla component predicate `nexus:filter`, which holds one filter.
///
/// @param filter The filter the components must pass.
///
@ApiStatus.Internal
public record FilterPredicate(ComponentFilter filter) implements DataComponentPredicate {
    
    ///
    /// The predicate type, registered as `nexus:filter`.
    ///
    public static final DataComponentPredicate.Type<FilterPredicate> TYPE = new DataComponentPredicate.ConcreteType<>(
            ComponentFilter.CODEC.asDfu().xmap(FilterPredicate::new, FilterPredicate::filter)
    );
    
    ///
    /// If the filter passes the given components.
    ///
    @Override
    public boolean matches(DataComponentGetter components) {
        return this.filter.test(components);
    }
}
