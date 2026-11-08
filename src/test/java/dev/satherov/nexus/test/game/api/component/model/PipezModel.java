package dev.satherov.nexus.test.game.api.component.model;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.internal.codec.path.TagReader;

import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

///
/// A model of the item filter of Pipez that tests an item stack the way `ItemPipeType#canInsert` does.
///
/// A component filter becomes Pipez filters in these ways:
/// - A top-level [ComponentFilter.AnyOf] becomes one Pipez filter per branch, and any other filter becomes a single Pipez filter.
/// - A top-level [ComponentFilter.Not] inverts the Pipez filters of its inner filter.
/// - An empty any-of becomes one inverted Pipez filter that matches every item stack.
/// - The metadata of a Pipez filter holds the exact values of its branch and each `==` against a text whose path has only keys.
/// - The rest of the branch runs after `PipeType#deepFuzzyCompare` and passes the same item stacks as Nexus.
/// - A `has` and a path read the encoded patch, and a vanilla predicate tests the item stack.
///
/// The model is cheaper than the code of the mod in these ways:
/// - It splits the inverted and the normal filters once when it's built, and the mod splits them on every insert check.
/// - Its Pipez filters have no item or tag and never use the exact metadata mode.
/// - It leaves out the copy of the item stack, the connection matching, and the inventory loop around the filter.
///
/// The model is dearer than the code of the mod in these ways:
/// - It adds to the encoded patch any component that the item stack has only through its item, and the mod reads only the patch.
/// - It checks an exact value for equality after the fuzzy compare, since the fuzzy compare also passes a value with more keys or list elements.
/// - It runs a `==` against a number or a flag after the fuzzy compare, since a tag in the metadata only matches a tag of the same type.
/// - It tests a `has` on a transient component against the item stack, since the encoded patch never holds one.
///
public final class PipezModel implements CompetitorModel {
    
    private static final CompoundTag EMPTY_COMPOUND = new CompoundTag();
    
    private static HolderLookup.Provider registries() {
        return Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer(), "Could not find a running server").registryAccess();
    }
    
    private static List<Filter> filters(ComponentFilter filter, HolderLookup.Provider registries) {
        return PipezModel.branches(filter).stream()
                .map(branch -> Filter.of(branch, registries))
                .toList();
    }
    
    private static List<ComponentFilter> branches(ComponentFilter filter) {
        return filter instanceof ComponentFilter.AnyOf any ? any.getAllFilters() : List.of(filter);
    }
    
    private static List<ComponentFilter> leaves(ComponentFilter branch) {
        return branch instanceof ComponentFilter.AllOf all ? all.getAllFilters() : List.of(branch);
    }
    
    private static @Nullable CompoundTag rule(ComponentFilter leaf, HolderLookup.Provider registries) {
        return switch (leaf) {
            case ComponentFilter.Exact exact -> PipezModel.nested(exact.getValue().type(), List.of(), PipezModel.encode(exact.getValue(), registries));
            case ComponentFilter.Compare compare -> {
                ComponentPath path = compare.getPath();
                yield compare.getComparison() instanceof Comparison(Comparison.Operator operator, Comparison.Literal.Text(String text)) && operator == Comparison.Operator.EQUAL ?
                        PipezModel.nested(path.getComponent(), path.getAllSteps(), StringTag.valueOf(text)) :
                        null;
            }
            case ComponentFilter.Has _, ComponentFilter.Matching _, ComponentFilter.AllOf _, ComponentFilter.AnyOf _, ComponentFilter.Not _ -> null;
        };
    }
    
    private static @Nullable CompoundTag nested(DataComponentType<?> component, List<ComponentPath.Step> steps, Tag leaf) {
        Tag tag = leaf;
        for (ComponentPath.Step step : steps.reversed()) {
            if (!(step instanceof ComponentPath.Step.Key(String name))) {
                return null;
            }
            
            CompoundTag parent = new CompoundTag();
            parent.put(name, tag);
            tag = parent;
        }
        
        CompoundTag rule = new CompoundTag();
        rule.put(PipezModel.id(component), tag);
        return rule;
    }
    
    private static @Nullable CompoundTag merge(CompoundTag metadata, CompoundTag rule) {
        CompoundTag merged = metadata.copy();
        for (Map.Entry<String, Tag> entry : rule.entrySet()) {
            Tag existing = merged.get(entry.getKey());
            if (existing == null) {
                merged.put(entry.getKey(), entry.getValue());
            } else if (existing instanceof CompoundTag held && entry.getValue() instanceof CompoundTag added && PipezModel.merge(held, added) instanceof CompoundTag nested) {
                merged.put(entry.getKey(), nested);
            } else {
                return null;
            }
        }
        
        return merged;
    }
    
    private static BiPredicate<ItemStack, CompoundTag> check(ComponentFilter filter, HolderLookup.Provider registries) {
        return switch (filter) {
            case ComponentFilter.Has has when has.getComponent().isTransient() -> (stack, _) -> stack.has(has.getComponent());
            case ComponentFilter.Has has -> {
                String id = PipezModel.id(has.getComponent());
                yield (_, nbt) -> nbt.contains(id);
            }
            case ComponentFilter.Exact exact -> {
                String id = PipezModel.id(exact.getValue().type());
                Tag value = PipezModel.encode(exact.getValue(), registries);
                yield (_, nbt) -> value.equals(nbt.get(id));
            }
            case ComponentFilter.Compare compare -> {
                String id = PipezModel.id(compare.getPath().getComponent());
                TagReader reader = new TagReader(compare.getPath().getAllSteps());
                Comparison comparison = compare.getComparison();
                yield (_, nbt) -> nbt.get(id) instanceof Tag value && reader.test(value, comparison);
            }
            case ComponentFilter.Matching matching -> (stack, _) -> matching.getPredicate().matches(stack);
            case ComponentFilter.AllOf all -> all.getAllFilters().stream()
                    .map(child -> PipezModel.check(child, registries))
                    .reduce(BiPredicate::and)
                    .orElse((_, _) -> true);
            case ComponentFilter.AnyOf any -> any.getAllFilters().stream()
                    .map(child -> PipezModel.check(child, registries))
                    .reduce(BiPredicate::or)
                    .orElse((_, _) -> false);
            case ComponentFilter.Not not -> PipezModel.check(not.getFilter(), registries).negate();
        };
    }
    
    private static void collect(ComponentFilter filter, Map<String, DataComponentType<?>> componentsById) {
        switch (filter) {
            case ComponentFilter.Has has when has.getComponent().isTransient() -> {
            }
            case ComponentFilter.Has has -> componentsById.put(PipezModel.id(has.getComponent()), has.getComponent());
            case ComponentFilter.Exact exact -> componentsById.put(PipezModel.id(exact.getValue().type()), exact.getValue().type());
            case ComponentFilter.Compare compare -> componentsById.put(PipezModel.id(compare.getPath().getComponent()), compare.getPath().getComponent());
            case ComponentFilter.Matching _ -> {
            }
            case ComponentFilter.AllOf all -> all.getAllFilters().forEach(child -> PipezModel.collect(child, componentsById));
            case ComponentFilter.AnyOf any -> any.getAllFilters().forEach(child -> PipezModel.collect(child, componentsById));
            case ComponentFilter.Not not -> PipezModel.collect(not.getFilter(), componentsById);
        }
    }
    
    private static Tag encode(TypedDataComponent<?> value, HolderLookup.Provider registries) {
        return value.encodeValue(registries.createSerializationContext(NbtOps.INSTANCE)).getOrThrow();
    }
    
    private static String id(DataComponentType<?> component) {
        return Util.getRegisteredName(BuiltInRegistries.DATA_COMPONENT_TYPE, component);
    }
    
    private static boolean deepFuzzyCompare(Tag metadata, Tag nbt) {
        if (metadata instanceof CompoundTag compound) {
            if (!(nbt instanceof CompoundTag present)) {
                return false;
            }
            
            for (String key : compound.keySet()) {
                Tag expected = compound.get(key);
                Tag actual = present.get(key);
                if (expected == null || actual == null || actual.getId() != expected.getId() || !PipezModel.deepFuzzyCompare(expected, actual)) {
                    return false;
                }
            }
            
            return true;
        }
        
        if (metadata instanceof ListTag list) {
            return nbt instanceof ListTag present && list.stream().allMatch(expected -> present.stream().anyMatch(actual -> PipezModel.deepFuzzyCompare(expected, actual)));
        }
        
        return metadata.equals(nbt);
    }
    
    @Override
    public String getName() {
        return "pipez";
    }
    
    @Override
    public Predicate<ItemStack> build(ComponentFilter filter) {
        HolderLookup.Provider registries = PipezModel.registries();
        if (filter instanceof ComponentFilter.Not not) {
            return new Filters(PipezModel.filters(not.getFilter(), registries), List.of());
        }
        
        if (filter instanceof ComponentFilter.AnyOf any && any.getAllFilters().isEmpty()) {
            return new Filters(PipezModel.filters(ComponentFilter.ANY, registries), List.of());
        }
        
        return new Filters(List.of(), PipezModel.filters(filter, registries));
    }
    
    @Override
    public boolean expresses(ComponentFilter filter) {
        ComponentFilter listed = filter instanceof ComponentFilter.Not not ? not.getFilter() : filter;
        boolean hasMetadata = PipezModel.filters(listed, PipezModel.registries()).stream()
                .anyMatch(built -> built.metadata() instanceof CompoundTag metadata && !metadata.isEmpty());
        return hasMetadata || PipezModel.branches(listed).stream()
                .allMatch(branch -> PipezModel.leaves(branch).isEmpty());
    }
    
    private record Filters(List<Filter> inverted, List<Filter> normal) implements Predicate<ItemStack> {
        
        @Override
        public boolean test(ItemStack stack) {
            for (Filter filter : this.inverted) {
                if (filter.matches(stack)) {
                    return false;
                }
            }
            
            if (this.normal.isEmpty()) {
                return true;
            }
            
            for (Filter filter : this.normal) {
                if (filter.matches(stack)) {
                    return true;
                }
            }
            
            return false;
        }
    }
    
    private record Filter(@Nullable CompoundTag metadata, Map<String, DataComponentType<?>> componentsById, BiPredicate<ItemStack, CompoundTag> checks, HolderLookup.Provider registries) {
        
        private static Filter of(ComponentFilter branch, HolderLookup.Provider registries) {
            CompoundTag metadata = new CompoundTag();
            List<ComponentFilter> rest = new ArrayList<>();
            for (ComponentFilter leaf : PipezModel.leaves(branch)) {
                CompoundTag rule = PipezModel.rule(leaf, registries);
                CompoundTag merged = rule == null ? null : PipezModel.merge(metadata, rule);
                if (merged != null) {
                    metadata = merged;
                }
                
                if (merged == null || leaf instanceof ComponentFilter.Exact) {
                    rest.add(leaf);
                }
            }
            
            Map<String, DataComponentType<?>> componentsById = new HashMap<>();
            PipezModel.collect(branch, componentsById);
            return new Filter(componentsById.isEmpty() ? null : metadata, Map.copyOf(componentsById), PipezModel.check(ComponentFilter.allOf(rest), registries), registries);
        }
        
        private boolean matches(ItemStack stack) {
            if (this.metadata == null) {
                return this.checks.test(stack, PipezModel.EMPTY_COMPOUND);
            }
            
            RegistryOps<Tag> ops = this.registries.createSerializationContext(NbtOps.INSTANCE);
            CompoundTag nbt = (CompoundTag) DataComponentPatch.CODEC.encodeStart(ops, stack.getComponentsPatch()).result().orElseGet(CompoundTag::new);
            for (Map.Entry<String, DataComponentType<?>> entry : this.componentsById.entrySet()) {
                if (!nbt.contains(entry.getKey()) && stack.getTyped(entry.getValue()) instanceof TypedDataComponent<?> prototype) {
                    prototype.encodeValue(ops).ifSuccess(tag -> nbt.put(entry.getKey(), tag));
                }
            }
            
            if (nbt.isEmpty() ? !this.metadata.isEmpty() : !PipezModel.deepFuzzyCompare(this.metadata, nbt)) {
                return false;
            }
            
            return this.checks.test(stack, nbt);
        }
    }
}
