package dev.satherov.nexus.test.game.api.component.model;

import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.Comparison;

import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

///
/// A model of FTB Filter System that translates a component filter into the filter tree of the mod once.
///
/// Every test looks the tree up in a cache under a read lock and then tests the item stack against it, as the filter API of the mod does.
///
/// A component filter becomes these filters of the mod:
/// - The filters of a top-level [ComponentFilter.AllOf] go straight into the root filter of the mod, which passes an item stack if all of them pass it.
/// - An [ComponentFilter.AllOf], an [ComponentFilter.AnyOf], and a [ComponentFilter.Not] become `and`, `or`, and `not`, which test their children through a stream.
/// - The [ComponentFilter.Exact] filters of one [ComponentFilter.AllOf] and its [ComponentFilter.Compare] filters with `==` on a whole component become one fuzzy `component` filter at the start of the `and`, since a player types them into one box.
/// - Any other [ComponentFilter.Exact] filter, or [ComponentFilter.Compare] filter with `==` on a whole component, becomes a fuzzy `component` filter of its own.
/// - A [ComponentFilter.Compare] filter on the nutrition of the food component against a whole number becomes `food_value`.
/// - A [ComponentFilter.Compare] filter with any operator other than `==` on the max stack size against a whole number becomes `max_stack_size`.
/// - A [ComponentFilter.Compare] filter for damage above `0` becomes `durability(<100%)`, since that is how a player filters for damaged items.
/// - Any other filter is tested with [CompetitorModel#testUnmemoized(ComponentFilter, ItemStack)] after the filters of the mod in the same parent.
///
/// The model differs from the code of the mod in these ways:
/// - It doesn't read the filter text from a smart filter item before the lookup, so it's cheaper.
/// - It builds the tree from the component filter instead of parsing the filter text and decodes component values from NBT instead of SNBT, which changes only the cost of building.
/// - Its cache is keyed by the text form of the component filter and has no capacity bound, so a lookup costs the same.
/// - The mod would test a filter it can't express with its `custom` filter, which posts an event on every test, so the model is cheaper there.
/// - The `component` filter of the mod fails every item stack without a component patch unless its map is empty.
///   The model checks the default components of such an item stack, so it's dearer there and one check cheaper on an item stack with a patch.
/// - `food_value` fails an item stack without food while the mod compares `0`, at the same cost.
/// - `max_stack_size` fails an item stack without the max stack size component while the mod compares `1`.
///   The model reads the component once more if a size of `1` passes, so it's dearer there.
/// - `durability` tests an item stack whose max damage is `0` with [CompetitorModel#testUnmemoized(ComponentFilter, ItemStack)] while the mod compares `0`, so the model is dearer there.
/// - A comparison against a whole number is one `IntPredicate` per operator instead of the comparison record and operator enum of the mod, at about the same cost.
///
public final class FtbFilterModel implements CompetitorModel {
    
    private static final List<ComponentPath.Step> NUTRITION = List.of(new ComponentPath.Step.Key("nutrition"));
    
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Predicate<ItemStack>> filtersByText = new LinkedHashMap<>(16, 0.75F, true);
    
    private static RegistryOps<Tag> registryOps() {
        return Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer(), "Could not find a running server").registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }
    
    private static Predicate<ItemStack> and(List<ComponentFilter> filters, RegistryOps<Tag> ops) {
        CompoundTag components = new CompoundTag();
        List<ComponentFilter> others = new ArrayList<>();
        for (ComponentFilter filter : filters) {
            Optional<CompoundTag> entry = FtbFilterModel.componentEntry(filter, ops);
            if (entry.isPresent() && Collections.disjoint(components.keySet(), entry.get().keySet())) {
                components.merge(entry.get());
            } else {
                others.add(filter);
            }
        }
        
        List<Predicate<ItemStack>> children = FtbFilterModel.children(others, ops);
        if (!components.isEmpty()) {
            children.addFirst(FtbFilterModel.component(components, ops));
        }
        
        return stack -> children.stream().allMatch(child -> child.test(stack));
    }
    
    private static Predicate<ItemStack> or(List<ComponentFilter> filters, RegistryOps<Tag> ops) {
        List<Predicate<ItemStack>> children = FtbFilterModel.children(filters, ops);
        return stack -> children.stream().anyMatch(child -> child.test(stack));
    }
    
    private static Predicate<ItemStack> not(ComponentFilter filter, RegistryOps<Tag> ops) {
        List<Predicate<ItemStack>> children = FtbFilterModel.children(List.of(filter), ops);
        return stack -> !children.isEmpty() && !children.getFirst().test(stack);
    }
    
    private static List<Predicate<ItemStack>> children(List<ComponentFilter> filters, RegistryOps<Tag> ops) {
        List<Predicate<ItemStack>> children = new ArrayList<>();
        List<Predicate<ItemStack>> leaves = new ArrayList<>();
        for (ComponentFilter filter : filters) {
            Predicate<ItemStack> child = FtbFilterModel.translate(filter, ops);
            if (child != null) {
                children.add(child);
            } else {
                leaves.add(stack -> CompetitorModel.testUnmemoized(filter, stack));
            }
        }
        
        children.addAll(leaves);
        return children;
    }
    
    private static @Nullable Predicate<ItemStack> translate(ComponentFilter filter, RegistryOps<Tag> ops) {
        return switch (filter) {
            case ComponentFilter.AllOf all -> FtbFilterModel.and(all.getAllFilters(), ops);
            case ComponentFilter.AnyOf any -> FtbFilterModel.or(any.getAllFilters(), ops);
            case ComponentFilter.Not not -> FtbFilterModel.not(not.getFilter(), ops);
            case ComponentFilter.Has _, ComponentFilter.Exact _, ComponentFilter.Compare _, ComponentFilter.Matching _ -> FtbFilterModel.translateLeaf(filter, ops);
        };
    }
    
    private static @Nullable Predicate<ItemStack> translateLeaf(ComponentFilter filter, RegistryOps<Tag> ops) {
        if (filter instanceof ComponentFilter.Compare compare && compare.getComparison().literal() instanceof Comparison.Literal.Numeric(double literal) && (int) literal == literal) {
            DataComponentType<?> component = compare.getPath().getComponent();
            boolean whole = compare.getPath().getAllSteps().isEmpty();
            Comparison.Operator operator = compare.getComparison().operator();
            int number = (int) literal;
            if (component == DataComponents.FOOD && compare.getPath().getAllSteps().equals(FtbFilterModel.NUTRITION)) {
                return FtbFilterModel.foodValue(FtbFilterModel.comparison(operator, number));
            }
            
            if (component == DataComponents.MAX_STACK_SIZE && whole && operator != Comparison.Operator.EQUAL) {
                return FtbFilterModel.maxStackSize(FtbFilterModel.comparison(operator, number));
            }
            
            if (component == DataComponents.DAMAGE && whole && ((operator == Comparison.Operator.GREATER && number == 0) || (operator == Comparison.Operator.GREATER_OR_EQUAL && number == 1))) {
                return FtbFilterModel.durability(FtbFilterModel.comparison(Comparison.Operator.LESS, 100), compare);
            }
        }
        
        return FtbFilterModel.componentEntry(filter, ops)
                .map(entry -> FtbFilterModel.component(entry, ops))
                .orElse(null);
    }
    
    private static boolean isExpressed(List<ComponentFilter> filters, RegistryOps<Tag> ops) {
        return filters.isEmpty() || filters.stream().anyMatch(filter -> switch (filter) {
            case ComponentFilter.AllOf all -> FtbFilterModel.isExpressed(all.getAllFilters(), ops);
            case ComponentFilter.AnyOf any -> FtbFilterModel.isExpressed(any.getAllFilters(), ops);
            case ComponentFilter.Not not -> FtbFilterModel.isExpressed(List.of(not.getFilter()), ops);
            case ComponentFilter.Has _, ComponentFilter.Exact _, ComponentFilter.Compare _, ComponentFilter.Matching _ -> FtbFilterModel.translateLeaf(filter, ops) != null;
        });
    }
    
    private static Optional<CompoundTag> componentEntry(ComponentFilter filter, RegistryOps<Tag> ops) {
        return switch (filter) {
            case ComponentFilter.Exact exact -> exact.getValue()
                    .encodeValue(ops)
                    .result()
                    .flatMap(value -> FtbFilterModel.decode(exact.getValue().type(), value, exact, ops));
            case ComponentFilter.Compare compare when compare.getComparison().operator() == Comparison.Operator.EQUAL && compare.getPath().getAllSteps().isEmpty() -> {
                Tag literal = switch (compare.getComparison().literal()) {
                    case Comparison.Literal.Numeric(double value) when (int) value == value -> IntTag.valueOf((int) value);
                    case Comparison.Literal.Numeric(double value) -> DoubleTag.valueOf(value);
                    case Comparison.Literal.Text(String value) -> StringTag.valueOf(value);
                    case Comparison.Literal.Flag(boolean value) -> ByteTag.valueOf(value);
                };
                
                yield FtbFilterModel.decode(compare.getPath().getComponent(), literal, compare, ops);
            }
            case ComponentFilter.Has _, ComponentFilter.Compare _, ComponentFilter.Matching _, ComponentFilter.AllOf _, ComponentFilter.AnyOf _, ComponentFilter.Not _ -> Optional.empty();
        };
    }
    
    private static <VALUE> Optional<CompoundTag> decode(DataComponentType<VALUE> component, Tag value, ComponentFilter leaf, RegistryOps<Tag> ops) {
        return component.codecOrThrow()
                .parse(ops, value)
                .result()
                .filter(decoded -> leaf.test(DataComponentMap.builder().set(component, decoded).build()))
                .map(_ -> Util.make(new CompoundTag(), entry -> entry.put(Util.getRegisteredName(BuiltInRegistries.DATA_COMPONENT_TYPE, component), value)));
    }
    
    private static Predicate<ItemStack> component(CompoundTag entries, RegistryOps<Tag> ops) {
        DataComponentMap expected = DataComponentMap.CODEC.parse(ops, entries).getOrThrow();
        return stack -> {
            DataComponentMap components = stack.getComponents();
            return expected.stream().allMatch(component -> components.has(component.type()) && component.value().equals(components.get(component.type())));
        };
    }
    
    private static Predicate<ItemStack> foodValue(IntPredicate comparison) {
        return stack -> {
            //noinspection DataFlowIssue The stack has the component.
            return stack.has(DataComponents.FOOD) && comparison.test(stack.get(DataComponents.FOOD).nutrition());
        };
    }
    
    private static Predicate<ItemStack> maxStackSize(IntPredicate comparison) {
        return stack -> {
            int size = stack.getMaxStackSize();
            return comparison.test(size) && (size != 1 || stack.has(DataComponents.MAX_STACK_SIZE));
        };
    }
    
    private static Predicate<ItemStack> durability(IntPredicate comparison, ComponentFilter leaf) {
        return stack -> {
            if (stack.getMaxDamage() == 0) {
                return CompetitorModel.testUnmemoized(leaf, stack);
            }
            
            int durability = stack.getMaxDamage() - stack.getDamageValue();
            return comparison.test((int) (durability * 100L / stack.getMaxDamage()));
        };
    }
    
    private static IntPredicate comparison(Comparison.Operator operator, int literal) {
        return switch (operator) {
            case EQUAL -> value -> value == literal;
            case NOT_EQUAL -> value -> value != literal;
            case LESS -> value -> value < literal;
            case LESS_OR_EQUAL -> value -> value <= literal;
            case GREATER -> value -> value > literal;
            case GREATER_OR_EQUAL -> value -> value >= literal;
        };
    }
    
    @Override
    public String getName() {
        return "ftb_filter";
    }
    
    @Override
    public boolean expresses(ComponentFilter filter) {
        return FtbFilterModel.isExpressed(List.of(filter), FtbFilterModel.registryOps());
    }
    
    @Override
    public Predicate<ItemStack> build(ComponentFilter filter) {
        RegistryOps<Tag> ops = FtbFilterModel.registryOps();
        Predicate<ItemStack> root = FtbFilterModel.and(filter instanceof ComponentFilter.AllOf all ? all.getAllFilters() : List.of(filter), ops);
        String text = filter.toString();
        this.lock.writeLock().lock();
        try {
            this.filtersByText.put(text, root);
        } finally {
            this.lock.writeLock().unlock();
        }
        
        return stack -> this.lookup(text).test(stack);
    }
    
    private Predicate<ItemStack> lookup(String text) {
        this.lock.readLock().lock();
        try {
            return Objects.requireNonNull(this.filtersByText.get(text));
        } finally {
            this.lock.readLock().unlock();
        }
    }
}
