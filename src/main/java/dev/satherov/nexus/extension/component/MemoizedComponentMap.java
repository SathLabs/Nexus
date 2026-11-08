package dev.satherov.nexus.extension.component;

import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.internal.component.LookupGeneration;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.PatchedDataComponentMap;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.BiPredicate;

///
/// Extension interface for [PatchedDataComponentMap] that stores the result of one filter at a time.
///
/// The interface isn't declared in `interfaces.json` and can only be reached by casting a patched data component map to it.
/// A copy of the map starts without a stored result.
///
@ApiStatus.Internal
public interface MemoizedComponentMap {
    
    ///
    /// Tests the given filter on the given holder with the given evaluation or returns the stored result of an earlier test.
    ///
    /// The stored result is only used for the same filter and holder objects,
    /// and only if the map wasn't written to and the [LookupGeneration] didn't change since it was stored.
    /// A result that depends on anything outside the map is only refreshed by a change to the map, an update of the tags, or a new owner of the registries.
    ///
    /// A result is only stored if the same filter is tested twice in a row on this map.
    ///
    /// @param filter     The filter to test.
    /// @param holder     The holder of this map.
    /// @param evaluation The evaluation of the filter, called with the given filter and holder if there is no stored result to use.
    ///
    /// @return If the holder passes the filter.
    ///
    boolean nexus$test(ComponentFilter filter, DataComponentGetter holder, BiPredicate<ComponentFilter, DataComponentGetter> evaluation);
}
