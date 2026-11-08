package dev.satherov.nexus.api.component;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.extension.component.MemoizedComponentMap;
import dev.satherov.nexus.internal.component.FilterCodec;
import dev.satherov.nexus.internal.component.vanilla.FilterFluidIngredient;
import dev.satherov.nexus.internal.component.vanilla.FilterIngredient;
import dev.satherov.nexus.internal.component.vanilla.FilterPredicate;
import dev.satherov.nexus.mixin.component.FluidResourceAccess;
import dev.satherov.nexus.mixin.component.ItemResourceAccess;

import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import net.minecraft.advancements.criterion.DataComponentMatchers;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.Unmodifiable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

///
/// A filter that tests the data components of an item stack, a fluid stack, an item or fluid resource, or a component map.
///
/// Two filters are equal if they are the same case built from equal parts.
///
/// Every filter costs as much as its most expensive read:
/// - [Has] and [Exact] cost [PathReader.Cost#DIRECT].
/// - [Compare] costs what the reader of its path costs.
/// - [Matching] costs [PathReader.Cost#ENCODE].
/// - [Not] costs what its inner filter costs.
/// - [AllOf] and [AnyOf] cost what their most expensive filter costs.
///
public abstract sealed class ComponentFilter permits ComponentFilter.Has, ComponentFilter.Exact, ComponentFilter.Compare, ComponentFilter.Matching, ComponentFilter.AllOf, ComponentFilter.AnyOf, ComponentFilter.Not {
    
    ///
    /// The empty all-of filter, which passes every holder.
    ///
    public static final ComponentFilter ANY = new AllOf(List.of());
    
    ///
    /// The codec of a filter in JSON, NBT, and on the network.
    ///
    /// In JSON and NBT, a filter is an object with the keys of one case:
    /// - `has` with the identifier of a component.
    /// - `exact` with the identifier of a component that isn't transient, and `value` with the value of that component.
    /// - `path` with a [ComponentPath] in its text form, `is` with the symbol of an operator, and `value` with a number, a string, or a boolean.
    /// - `matches` with the identifier of a vanilla predicate type, and `value` with the predicate.
    /// - `all` or `any` with a list of filters.
    /// - `not` with a filter.
    ///
    /// Decoding picks the first case in this list whose first key is present.
    /// If the format is strict, decoding will fail on a key that the case doesn't have.
    /// The `value` of a path is read as a boolean if it is one, as a number if it is one, and as a string otherwise.
    /// If the registries haven't frozen yet, decoding a path will fail like any other value that can't be decoded.
    ///
    /// In JSON and NBT, a whole number that fits into an `int` is written as an `int`, except for `-0.0`, and every other number as a `double`.
    /// On the network, a filter is written with the index of its case in the list above, and every number as a `double`.
    /// Encoding fails if a component, the component of a path, or a predicate type in the filter isn't registered.
    ///
    public static final NexusCodec<ComponentFilter, Access.Registries> CODEC = new FilterCodec();
    
    ///
    /// The cost of the most expensive read of this filter.
    ///
    private final PathReader.Cost cost;
    
    ///
    /// The number of reads in this filter, which is the number of [Has], [Exact], [Compare], and [Matching] filters in it.
    ///
    private final int reads;
    
    ///
    /// If a patched data component map should store the result of this filter.
    ///
    private final boolean memoized;
    
    ///
    /// Creates a filter with the given cost and number of reads.
    ///
    /// @param cost  The cost of the most expensive read of the filter.
    /// @param reads The number of reads in the filter.
    ///
    private ComponentFilter(PathReader.Cost cost, int reads) {
        this.cost = cost;
        this.reads = reads;
        this.memoized = cost != PathReader.Cost.DIRECT || reads > 1;
    }
    
    ///
    /// Creates a filter that passes a holder that has the given component, with any value.
    ///
    /// A component that the holder has by default counts too.
    ///
    /// @param component The data component type that a holder has to have.
    ///
    /// @return The filter.
    ///
    public static ComponentFilter has(DataComponentType<?> component) {
        return new Has(component);
    }
    
    ///
    /// Creates a filter that passes a holder whose value of the given component equals the given value.
    ///
    /// @param component The data component type to compare.
    /// @param value     The value that the value of a holder has to equal.
    ///
    /// @return The filter.
    ///
    /// @throws IllegalArgumentException If the component is transient.
    ///
    public static <VALUE> ComponentFilter exact(DataComponentType<VALUE> component, VALUE value) {
        if (component.isTransient()) {
            throw new IllegalArgumentException("The component '" + Util.getRegisteredName(BuiltInRegistries.DATA_COMPONENT_TYPE, component) + "' is transient");
        }
        
        return new Exact(new TypedDataComponent<>(component, value));
    }
    
    ///
    /// Creates a filter that passes a holder whose part at the given path passes the given comparison.
    ///
    /// If the holder doesn't have the component or its value has nothing at the path, the filter will fail for every operator, even for `!=`.
    ///
    /// @param path       The path to the part to compare.
    /// @param comparison The comparison that the part has to pass.
    ///
    /// @return The filter.
    ///
    /// @throws IllegalArgumentException If an operator that compares by order is used on a path whose values have no order.
    ///
    public static ComponentFilter compare(ComponentPath path, Comparison comparison) {
        if (comparison.operator().isOrdered() && !path.reader().isOrdered()) {
            throw new IllegalArgumentException("The values at the path '" + path + "' have no order, found the operator '" + comparison.operator().getSymbol() + "'");
        }
        
        return new Compare(path, comparison);
    }
    
    ///
    /// Creates a filter that passes a holder that the given vanilla predicate matches.
    ///
    /// @param type      The type of the predicate.
    /// @param predicate The predicate that a holder has to match.
    ///
    /// @return The filter.
    ///
    public static <PREDICATE extends DataComponentPredicate> ComponentFilter matching(DataComponentPredicate.Type<PREDICATE> type, PREDICATE predicate) {
        return new Matching(type, predicate);
    }
    
    ///
    /// Creates a filter that passes a holder that every one of the given filters passes.
    ///
    /// An [AllOf] among the given filters is replaced by its own filters.
    /// If no filter is given, this will return [#ANY], and if one filter is given, this will return that filter.
    ///
    /// @param filters The filters that a holder has to pass.
    ///
    /// @return The filter.
    ///
    public static ComponentFilter allOf(List<ComponentFilter> filters) {
        List<ComponentFilter> flat = filters.stream()
                .flatMap(filter -> filter instanceof AllOf all ? all.filters.stream() : Stream.of(filter))
                .toList();
        
        return switch (flat.size()) {
            case 0 -> ComponentFilter.ANY;
            case 1 -> flat.getFirst();
            default -> new AllOf(flat);
        };
    }
    
    ///
    /// Creates a filter that passes a holder that at least one of the given filters passes.
    ///
    /// An [AnyOf] among the given filters is replaced by its own filters.
    /// If no filter is given, this will return a filter that passes nothing, and if one filter is given, this will return that filter.
    ///
    /// @param filters The filters that a holder has to pass one of.
    ///
    /// @return The filter.
    ///
    public static ComponentFilter anyOf(List<ComponentFilter> filters) {
        List<ComponentFilter> flat = filters.stream()
                .flatMap(filter -> filter instanceof AnyOf any ? any.filters.stream() : Stream.of(filter))
                .toList();
        
        return flat.size() == 1 ? flat.getFirst() : new AnyOf(flat);
    }
    
    ///
    /// Gets the given filters from the cheapest to the most expensive, with filters of the same cost in the given order.
    ///
    /// @param filters The filters to sort.
    ///
    /// @return The filters, from the cheapest to the most expensive.
    ///
    private static ComponentFilter[] cheapestFirst(List<ComponentFilter> filters) {
        return filters.stream()
                .sorted(Comparator.comparing(filter -> filter.cost))
                .toArray(ComponentFilter[]::new);
    }
    
    ///
    /// Gets the cost of the most expensive of the given filters, which is [PathReader.Cost#DIRECT] if there are none.
    ///
    /// @param filters The filters to compare the costs of.
    ///
    /// @return The highest cost.
    ///
    private static PathReader.Cost highestCost(List<ComponentFilter> filters) {
        return filters.stream()
                .map(filter -> filter.cost)
                .max(Comparator.naturalOrder())
                .orElse(PathReader.Cost.DIRECT);
    }
    
    ///
    /// Gets the total number of reads in the given filters.
    ///
    /// @param filters The filters to count the reads of.
    ///
    /// @return The number of reads.
    ///
    private static int totalReads(List<ComponentFilter> filters) {
        return filters.stream()
                .mapToInt(filter -> filter.reads)
                .sum();
    }
    
    ///
    /// Creates the all-of filter of this filter and the given filter.
    ///
    /// @param other The filter that a holder has to pass as well.
    ///
    /// @return The filter.
    ///
    /// @see #allOf(List)
    ///
    public ComponentFilter and(ComponentFilter other) {
        return ComponentFilter.allOf(List.of(this, other));
    }
    
    ///
    /// Creates the any-of filter of this filter and the given filter.
    ///
    /// @param other The filter that a holder may pass instead.
    ///
    /// @return The filter.
    ///
    /// @see #anyOf(List)
    ///
    public ComponentFilter or(ComponentFilter other) {
        return ComponentFilter.anyOf(List.of(this, other));
    }
    
    ///
    /// Creates a filter that passes exactly the holders that this filter fails.
    ///
    /// If this filter is a [Not], this will return its inner filter.
    ///
    /// @return The filter.
    ///
    public ComponentFilter negate() {
        return this instanceof Not not ? not.filter : new Not(this);
    }
    
    ///
    /// Tests the components of the given holder.
    ///
    /// Reads only the components that this filter needs, and allocates nothing if every read costs [PathReader.Cost#DIRECT].
    /// An item resource or a fluid resource is read through its stack.
    ///
    /// If this filter tests the same unchanged item stack or fluid stack more than twice in a row,
    /// every test after the second will return the result of the second.
    /// The result is never stored for a [Has], [Exact], or [Compare] whose read costs [PathReader.Cost#DIRECT], on its own or under a [Not].
    ///
    /// @param holder The holder of the components.
    ///
    /// @return `true` if the holder passes this filter.
    ///
    public boolean test(DataComponentGetter holder) {
        DataComponentGetter source = switch (holder) {
            case ItemResource resource -> ((ItemResourceAccess) (Object) resource).getInnerStack();
            case FluidResource resource -> ((FluidResourceAccess) (Object) resource).getInnerStack();
            default -> holder;
        };
        
        if (!this.memoized) {
            return this.evaluate(source);
        }
        
        DataComponentGetter components = source instanceof DataComponentHolder stack ? stack.getComponents() : source;
        return components instanceof MemoizedComponentMap memo ? memo.nexus$test(this, source, ComponentFilter::evaluate) : this.evaluate(source);
    }
    
    ///
    /// Tests the given holder by reading its components, with the filters of an [AllOf] or an [AnyOf] from the cheapest up.
    ///
    /// @param holder The holder of the components.
    ///
    /// @return `true` if the holder passes this filter.
    ///
    private boolean evaluate(DataComponentGetter holder) {
        switch (this) {
            case Has has -> {
                return holder.has(has.component);
            }
            case Exact exact -> {
                return exact.value.value().equals(holder.get(exact.value.type()));
            }
            case Compare compare -> {
                Object value = holder.get(compare.path.getComponent());
                return value != null && compare.read.test(value);
            }
            case Matching matching -> {
                return matching.predicate.matches(holder);
            }
            case AllOf all -> {
                for (ComponentFilter filter : all.ordered) {
                    if (!filter.evaluate(holder)) {
                        return false;
                    }
                }
                
                return true;
            }
            case AnyOf any -> {
                for (ComponentFilter filter : any.ordered) {
                    if (filter.evaluate(holder)) {
                        return true;
                    }
                }
                
                return false;
            }
            case Not not -> {
                return !not.filter.evaluate(holder);
            }
        }
    }
    
    ///
    /// Creates the vanilla matchers that hold this filter as their one predicate.
    ///
    /// The predicate has the type `nexus:filter`.
    ///
    /// The matchers can't be sent over a neoforge connection, since neoforge doesn't sync the registry of predicate types.
    /// A filter can be sent with [#CODEC] instead.
    ///
    /// @return The matchers.
    ///
    public DataComponentMatchers asMatchers() {
        return DataComponentMatchers.Builder.components()
                .partial(FilterPredicate.TYPE, new FilterPredicate(this))
                .build();
    }
    
    ///
    /// Creates an ingredient that passes an item stack whose item is in the given set and which passes this filter.
    ///
    /// @param items The items that the ingredient accepts.
    ///
    /// @return The ingredient.
    ///
    public Ingredient asIngredient(HolderSet<Item> items) {
        return new FilterIngredient(items, this).toVanilla();
    }
    
    ///
    /// Creates a fluid ingredient that passes a fluid stack whose fluid is in the given set and which passes this filter.
    ///
    /// @param fluids The fluids that the ingredient accepts.
    ///
    /// @return The fluid ingredient.
    ///
    public FluidIngredient asFluidIngredient(HolderSet<Fluid> fluids) {
        return new FilterFluidIngredient(fluids, this);
    }
    
    ///
    /// A filter that passes a holder that has a component.
    ///
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static final class Has extends ComponentFilter {
        
        ///
        /// The data component type that a holder has to have.
        ///
        @Getter
        private final DataComponentType<?> component;
        
        ///
        /// Creates the filter of the given component.
        ///
        /// @param component The data component type that a holder has to have.
        ///
        private Has(DataComponentType<?> component) {
            super(PathReader.Cost.DIRECT, 1);
            this.component = component;
        }
    }
    
    ///
    /// A filter that passes a holder whose value of a component equals a value.
    ///
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static final class Exact extends ComponentFilter {
        
        ///
        /// The component and the value that the value of a holder has to equal.
        ///
        @Getter
        private final TypedDataComponent<?> value;
        
        ///
        /// Creates the filter of the given component and value.
        ///
        /// @param value The component and the value that the value of a holder has to equal.
        ///
        private Exact(TypedDataComponent<?> value) {
            super(PathReader.Cost.DIRECT, 1);
            this.value = value;
        }
    }
    
    ///
    /// A filter that passes a holder whose part at a path passes a comparison.
    ///
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static final class Compare extends ComponentFilter {
        
        ///
        /// The path to the part to compare.
        ///
        @Getter
        private final ComponentPath path;
        
        ///
        /// The comparison that the part has to pass.
        ///
        @Getter
        private final Comparison comparison;
        
        ///
        /// The test of a component value, bound from the reader of the path when this filter was created.
        ///
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private final Predicate<Object> read;
        
        ///
        /// Creates the filter of the given path and comparison and binds the reader of the path.
        ///
        /// @param path       The path to the part to compare.
        /// @param comparison The comparison that the part has to pass.
        ///
        private Compare(ComponentPath path, Comparison comparison) {
            super(path.reader().getCost(), 1);
            this.path = path;
            this.comparison = comparison;
            
            //noinspection unchecked The reader of a path reads the values of its component.
            this.read = (Predicate<Object>) path.reader().bind(comparison);
        }
    }
    
    ///
    /// A filter that passes a holder that a vanilla predicate matches.
    ///
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static final class Matching extends ComponentFilter {
        
        ///
        /// The type of the predicate.
        ///
        @Getter
        private final DataComponentPredicate.Type<?> type;
        
        ///
        /// The predicate that a holder has to match.
        ///
        @Getter
        private final DataComponentPredicate predicate;
        
        ///
        /// Creates the filter of the given predicate type and predicate.
        ///
        /// @param type      The type of the predicate.
        /// @param predicate The predicate that a holder has to match.
        ///
        private Matching(DataComponentPredicate.Type<?> type, DataComponentPredicate predicate) {
            super(PathReader.Cost.ENCODE, 1);
            this.type = type;
            this.predicate = predicate;
        }
    }
    
    ///
    /// A filter that passes a holder that every one of its filters passes.
    ///
    /// The filters are tested from the cheapest to the most expensive, and filters of the same cost in the order they were given.
    ///
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static final class AllOf extends ComponentFilter {
        
        ///
        /// The filters, in the order they were given.
        ///
        private final @Unmodifiable List<ComponentFilter> filters;
        
        ///
        /// The filters, from the cheapest to the most expensive.
        ///
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private final ComponentFilter[] ordered;
        
        ///
        /// Creates the filter of the given filters.
        ///
        /// @param filters The filters that a holder has to pass, in the order they were given.
        ///
        private AllOf(@Unmodifiable List<ComponentFilter> filters) {
            super(ComponentFilter.highestCost(filters), ComponentFilter.totalReads(filters));
            this.filters = filters;
            
            this.ordered = ComponentFilter.cheapestFirst(filters);
        }
        
        ///
        /// The filters that a holder has to pass, in the order they were given.
        ///
        /// @return The filters.
        ///
        public @Unmodifiable List<ComponentFilter> getAllFilters() {
            return this.filters;
        }
    }
    
    ///
    /// A filter that passes a holder that at least one of its filters passes.
    ///
    /// The filters are tested from the cheapest to the most expensive, and filters of the same cost in the order they were given.
    ///
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static final class AnyOf extends ComponentFilter {
        
        ///
        /// The filters, in the order they were given.
        ///
        private final @Unmodifiable List<ComponentFilter> filters;
        
        ///
        /// The filters, from the cheapest to the most expensive.
        ///
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private final ComponentFilter[] ordered;
        
        ///
        /// Creates the filter of the given filters.
        ///
        /// @param filters The filters that a holder has to pass one of, in the order they were given.
        ///
        private AnyOf(@Unmodifiable List<ComponentFilter> filters) {
            super(ComponentFilter.highestCost(filters), ComponentFilter.totalReads(filters));
            this.filters = filters;
            
            this.ordered = ComponentFilter.cheapestFirst(filters);
        }
        
        ///
        /// The filters that a holder has to pass one of, in the order they were given.
        ///
        /// @return The filters.
        ///
        public @Unmodifiable List<ComponentFilter> getAllFilters() {
            return this.filters;
        }
    }
    
    ///
    /// A filter that passes a holder that its inner filter fails.
    ///
    @ToString
    @EqualsAndHashCode(callSuper = false)
    public static final class Not extends ComponentFilter {
        
        ///
        /// The inner filter that a holder has to fail.
        ///
        @Getter
        private final ComponentFilter filter;
        
        ///
        /// Creates the filter that negates the given filter.
        ///
        /// @param filter The inner filter that a holder has to fail.
        ///
        private Not(ComponentFilter filter) {
            super(filter.cost, filter.reads);
            this.filter = filter;
        }
    }
}
