package dev.satherov.nexus.test.game.api.component.model;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;

import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.component.predicates.EnchantmentsPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.PrimitiveTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

///
/// A model of a Logistics Networks filter item, set up from a component filter the way a player would set one up.
///
/// An `AnyOf` at the top becomes the entries of the filter item, a `Not` at the top turns the filter item into a blacklist, and an `AllOf` becomes the rules of one entry.
/// The vanilla predicate that passes any enchanted item becomes a rule on the enchanted value that the mod derives from the item stack.
/// Whatever the mod can't express runs inside its entry after the rules, with the meaning it has in the component filter:
/// - A `has` filter in an entry that has rules, because the mod keeps raw NBT only in an entry without rules.
/// - An `AnyOf` or an `AllOf` inside an entry.
/// - A `Not` over anything other than an exact value or an equality.
/// - A comparison whose path has a wildcard step, read from the same encoded tag as the rules.
/// - Any other vanilla predicate.
///
/// The model is cheaper than the mod in these ways:
/// - It tests one filter item, so the loop over the filter items and the match-all mode are left out.
/// - It builds the filter view once, while the mod reads it from its read cache three times per item stack.
/// - It goes through its entries only, while the mod goes through every slot and checks what kind of entry the slot holds.
/// - It leaves out the work of the pipe outside the filter, such as copying the item stack and going through the inventory.
/// - It leaves out the code for fluid stacks.
///
/// The model is dearer than the mod if the item stack has a component only through its item, because the model encodes the value from the item stack and the mod finds nothing.
///
/// The model differs from the mod in these ways at the same cost:
/// - A `has` filter passes any tag of its component, while the raw NBT of the mod passes only a compound.
/// - An entry without rules and without raw NBT still runs its other checks, while the mod ignores the entry.
/// - A filter item without entries passes nothing unless it's a blacklist, while the mod passes every item stack.
/// - Match-any is never set, and an entry may hold more than eight rules.
/// - A path to the whole component map and the key of a removed component read nothing, while the mod encodes the whole patch.
///
/// The model keeps these semantics of the mod, so the item stacks and filters it's tested with must not depend on them:
/// - Not equal passes if nothing is at the path.
/// - Equal and not equal compare the type of the tag as well, and a number becomes an int tag if it's whole and a double tag otherwise.
/// - The ordered operators read text as a number.
/// - The derived rarity is raised for an enchanted item.
/// - The enchanted value reads only the `enchantments` component, while the vanilla predicate reads every enchantment of the item.
/// - An empty item stack fails.
///
public final class LogisticsNetworksModel implements CompetitorModel {
    
    private static final String DURABILITY = "minecraft:durability";
    private static final String ENCHANTED = "minecraft:enchanted";
    private static final ComponentPath.Step[] COMPONENTS = { new ComponentPath.Step.Key("components") };
    private static final ComponentPath.Step[] FLUID_COMPONENTS = { new ComponentPath.Step.Key("fluid"), new ComponentPath.Step.Key("components") };
    private static final ComponentPath.Step[] DURABILITY_PATH = { new ComponentPath.Step.Key(LogisticsNetworksModel.DURABILITY) };
    private static final ComponentPath.Step[] ENCHANTED_PATH = { new ComponentPath.Step.Key(LogisticsNetworksModel.ENCHANTED) };
    
    private static @Nullable Criterion rule(ComponentFilter filter, HolderLookup.Provider registries) {
        return switch (filter) {
            case ComponentFilter.Exact exact -> LogisticsNetworksModel.exact(exact, "=", registries);
            case ComponentFilter.Compare compare -> LogisticsNetworksModel.criterion(compare.getPath(), compare.getComparison().operator().getSymbol(), compare.getComparison().literal());
            case ComponentFilter.Not not when not.getFilter() instanceof ComponentFilter.Exact exact -> LogisticsNetworksModel.exact(exact, "!=", registries);
            case ComponentFilter.Not not when not.getFilter() instanceof ComponentFilter.Compare compare && compare.getComparison().operator() == Comparison.Operator.EQUAL -> LogisticsNetworksModel.criterion(compare.getPath(), "!=", compare.getComparison().literal());
            case ComponentFilter.Matching matching when LogisticsNetworksModel.isAnyEnchantment(matching, registries) -> new Criterion(LogisticsNetworksModel.ENCHANTED_PATH, "=", ByteTag.valueOf(true));
            case ComponentFilter.Has _, ComponentFilter.Matching _, ComponentFilter.AllOf _, ComponentFilter.AnyOf _, ComponentFilter.Not _ -> null;
        };
    }
    
    private static Criterion exact(ComponentFilter.Exact exact, String operator, HolderLookup.Provider registries) {
        ComponentPath.Step[] path = { new ComponentPath.Step.Key(LogisticsNetworksModel.id(exact.getValue().type())) };
        return new Criterion(path, operator, exact.getValue().encodeValue(registries.createSerializationContext(NbtOps.INSTANCE)).getOrThrow());
    }
    
    private static @Nullable Criterion criterion(ComponentPath path, String operator, Comparison.Literal literal) {
        List<ComponentPath.Step> steps = path.getAllSteps();
        if (steps.stream().anyMatch(ComponentPath.Step.Wildcard.class::isInstance)) {
            return null;
        }
        
        ComponentPath.Step[] full = Stream.concat(Stream.of(new ComponentPath.Step.Key(LogisticsNetworksModel.id(path.getComponent()))), steps.stream()).toArray(ComponentPath.Step[]::new);
        Tag value = switch (literal) {
            case Comparison.Literal.Numeric(double number) when number == Math.rint(number) && Math.abs(number) <= Integer.MAX_VALUE -> IntTag.valueOf((int) number);
            case Comparison.Literal.Numeric(double number) -> DoubleTag.valueOf(number);
            case Comparison.Literal.Text(String text) -> StringTag.valueOf(text);
            case Comparison.Literal.Flag(boolean flag) -> ByteTag.valueOf(flag);
        };
        
        return new Criterion(full, operator, value);
    }
    
    private static boolean isAnyEnchantment(ComponentFilter.Matching matching, HolderLookup.Provider registries) {
        return (
                matching.getPredicate() instanceof EnchantmentsPredicate.Enchantments enchantments &&
                DataComponentPredicates.ENCHANTMENTS.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), enchantments).getOrThrow() instanceof ListTag list &&
                list.size() == 1 &&
                list.getFirst() instanceof CompoundTag entry &&
                entry.isEmpty()
        );
    }
    
    private static Predicate<Candidate> check(ComponentFilter filter, HolderLookup.Provider registries) {
        Criterion rule = LogisticsNetworksModel.rule(filter, registries);
        return switch (filter) {
            case ComponentFilter.Has has -> {
                Set<String> keys = Set.of(LogisticsNetworksModel.id(has.getComponent()));
                yield candidate -> candidate.hasAll(keys);
            }
            case ComponentFilter.Compare compare when rule == null -> {
                String key = LogisticsNetworksModel.id(compare.getPath().getComponent());
                List<ComponentPath.Step> steps = compare.getPath().getAllSteps();
                yield candidate -> LogisticsNetworksModel.passes(candidate.get(key), steps, 0, compare.getComparison());
            }
            case ComponentFilter.Matching matching when rule == null -> candidate -> matching.getPredicate().matches(candidate.stack);
            case ComponentFilter.AllOf all -> all.getAllFilters().stream()
                    .map(child -> LogisticsNetworksModel.check(child, registries))
                    .reduce(Predicate::and)
                    .orElse(candidate -> true);
            case ComponentFilter.AnyOf any -> any.getAllFilters().stream()
                    .map(child -> LogisticsNetworksModel.check(child, registries))
                    .reduce(Predicate::or)
                    .orElse(candidate -> false);
            case ComponentFilter.Not not when rule == null -> LogisticsNetworksModel.check(not.getFilter(), registries).negate();
            case ComponentFilter.Exact _, ComponentFilter.Compare _, ComponentFilter.Matching _, ComponentFilter.Not _ -> {
                Criterion held = Objects.requireNonNull(rule);
                yield candidate -> held.matches(candidate.resolve(held.path()));
            }
        };
    }
    
    private static boolean passes(@Nullable Tag tag, List<ComponentPath.Step> steps, int from, Comparison comparison) {
        if (tag == null) {
            return false;
        }
        
        if (from == steps.size()) {
            return comparison.test(tag);
        }
        
        return switch (steps.get(from)) {
            case ComponentPath.Step.Key(String name) -> tag instanceof CompoundTag compound && LogisticsNetworksModel.passes(compound.get(name), steps, from + 1, comparison);
            case ComponentPath.Step.Index(int index) -> tag instanceof CollectionTag list && index < list.size() && LogisticsNetworksModel.passes(list.get(index), steps, from + 1, comparison);
            case ComponentPath.Step.Wildcard() -> switch (tag) {
                case CollectionTag list -> list.stream().anyMatch(element -> LogisticsNetworksModel.passes(element, steps, from + 1, comparison));
                case CompoundTag compound -> compound.values().stream().anyMatch(value -> LogisticsNetworksModel.passes(value, steps, from + 1, comparison));
                case PrimitiveTag _, EndTag _ -> false;
            };
        };
    }
    
    private static String id(DataComponentType<?> component) {
        return Util.getRegisteredName(BuiltInRegistries.DATA_COMPONENT_TYPE, component);
    }
    
    private static HolderLookup.Provider registries() {
        return Objects.requireNonNull(ServerLifecycleHooks.getCurrentServer()).registryAccess();
    }
    
    @Override
    public String getName() {
        return "logistics_networks";
    }
    
    @Override
    public Predicate<ItemStack> build(ComponentFilter filter) {
        HolderLookup.Provider registries = LogisticsNetworksModel.registries();
        ComponentFilter listed = filter instanceof ComponentFilter.Not not ? not.getFilter() : filter;
        List<ComponentFilter> branches = listed instanceof ComponentFilter.AnyOf any ? any.getAllFilters() : List.of(listed);
        List<Entry> entries = branches.stream()
                .map(branch -> Entry.of(branch, registries))
                .toList();
        return new FilterItem(filter instanceof ComponentFilter.Not, entries, registries);
    }
    
    @Override
    public boolean expresses(ComponentFilter filter) {
        return switch (filter) {
            case ComponentFilter.Has _ -> true;
            case ComponentFilter.Exact _, ComponentFilter.Compare _, ComponentFilter.Matching _ -> LogisticsNetworksModel.rule(filter, LogisticsNetworksModel.registries()) != null;
            case ComponentFilter.AllOf all -> all.getAllFilters().stream().anyMatch(this::expresses);
            case ComponentFilter.AnyOf any -> any.getAllFilters().stream().anyMatch(this::expresses);
            case ComponentFilter.Not not -> this.expresses(not.getFilter());
        };
    }
    
    private record FilterItem(boolean blacklist, List<Entry> entries, HolderLookup.Provider registries) implements Predicate<ItemStack> {
        
        @Override
        public boolean test(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            
            Candidate candidate = new Candidate(stack, this.registries);
            for (Entry entry : this.entries) {
                if (entry.matches(candidate)) {
                    return !this.blacklist;
                }
            }
            
            return this.blacklist;
        }
    }
    
    private record Entry(List<Criterion> rules, @Nullable Set<String> raw, List<Predicate<Candidate>> rest) {
        
        private static Entry of(ComponentFilter branch, HolderLookup.Provider registries) {
            List<ComponentFilter> leaves = branch instanceof ComponentFilter.AllOf all ? all.getAllFilters() : List.of(branch);
            List<Criterion> rules = new ArrayList<>();
            Set<String> raw = new HashSet<>();
            List<Predicate<Candidate>> rest = new ArrayList<>();
            for (ComponentFilter leaf : leaves) {
                Criterion rule = LogisticsNetworksModel.rule(leaf, registries);
                if (rule != null) {
                    rules.add(rule);
                } else if (leaf instanceof ComponentFilter.Has has) {
                    raw.add(LogisticsNetworksModel.id(has.getComponent()));
                } else {
                    rest.add(LogisticsNetworksModel.check(leaf, registries));
                }
            }
            
            if (rules.isEmpty()) {
                return new Entry(List.of(), raw.isEmpty() ? null : Set.copyOf(raw), List.copyOf(rest));
            }
            
            if (!raw.isEmpty()) {
                Set<String> keys = Set.copyOf(raw);
                rest.add(candidate -> candidate.hasAll(keys));
            }
            
            return new Entry(List.copyOf(rules), null, List.copyOf(rest));
        }
        
        private boolean matches(Candidate candidate) {
            boolean damageable = candidate.stack.isDamageableItem();
            for (Criterion rule : this.rules) {
                if (!damageable && Arrays.equals(rule.path(), LogisticsNetworksModel.DURABILITY_PATH)) {
                    continue;
                }
                
                if (!rule.matches(candidate.resolve(rule.path()))) {
                    return false;
                }
            }
            
            if (this.raw != null && !candidate.hasAll(this.raw)) {
                return false;
            }
            
            for (Predicate<Candidate> check : this.rest) {
                if (!check.test(candidate)) {
                    return false;
                }
            }
            
            return true;
        }
    }
    
    private record Criterion(ComponentPath.Step[] path, String operator, Tag value) {
        
        private Criterion {
            operator = switch (operator) {
                case "!=", ">", "<", ">=", "<=" -> operator;
                default -> "=";
            };
        }
        
        private static double number(Tag tag) {
            if (tag instanceof NumericTag numeric) {
                return numeric.doubleValue();
            }
            
            if (!(tag instanceof StringTag(String text))) {
                return Double.NaN;
            }
            
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException _) {
                return Double.NaN;
            }
        }
        
        private boolean matches(@Nullable Tag actual) {
            if (actual == null) {
                return "!=".equals(this.operator);
            }
            
            return switch (this.operator) {
                case "!=" -> !this.value.equals(actual);
                case ">", "<", ">=", "<=" -> this.compareNumbers(actual);
                default -> this.value.equals(actual);
            };
        }
        
        private boolean compareNumbers(Tag actual) {
            double expected = Criterion.number(this.value);
            double present = Criterion.number(actual);
            if (Double.isNaN(expected) || Double.isNaN(present)) {
                return false;
            }
            
            return switch (this.operator) {
                case ">" -> present > expected;
                case "<" -> present < expected;
                case ">=" -> present >= expected;
                case "<=" -> present <= expected;
                default -> false;
            };
        }
    }
    
    private static final class Candidate {
        
        private final ItemStack stack;
        private final HolderLookup.Provider registries;
        private final Map<String, @Nullable Tag> values = new HashMap<>();
        private @Nullable DataComponentPatch patch;
        
        private Candidate(ItemStack stack, HolderLookup.Provider registries) {
            this.stack = stack;
            this.registries = registries;
        }
        
        private static boolean startsWith(ComponentPath.Step[] path, ComponentPath.Step[] prefix) {
            return path.length >= prefix.length && Arrays.equals(path, 0, prefix.length, prefix, 0, prefix.length);
        }
        
        private static @Nullable Tag find(ComponentPath.Step[] path, @Nullable Tag root) {
            Tag tag = root;
            for (ComponentPath.Step step : path) {
                if (tag == null) {
                    return null;
                }
                
                tag = switch (step) {
                    case ComponentPath.Step.Key(String name) -> tag instanceof CompoundTag compound && !compound.isEmpty() ? compound.get(name) : null;
                    case ComponentPath.Step.Index(int index) -> tag instanceof CollectionTag list && index < list.size() ? list.get(index) : null;
                    case ComponentPath.Step.Wildcard() -> null;
                };
            }
            
            return tag;
        }
        
        private @Nullable Tag resolve(ComponentPath.Step[] path) {
            if (path.length == 0 || Arrays.equals(path, LogisticsNetworksModel.COMPONENTS) || Arrays.equals(path, LogisticsNetworksModel.FLUID_COMPONENTS)) {
                return null;
            }
            
            ComponentPath.Step[] rest = path;
            if (Candidate.startsWith(rest, LogisticsNetworksModel.FLUID_COMPONENTS)) {
                rest = Arrays.copyOfRange(rest, 2, rest.length);
            } else if (Candidate.startsWith(rest, LogisticsNetworksModel.COMPONENTS)) {
                rest = Arrays.copyOfRange(rest, 1, rest.length);
            }
            
            if (!(rest[0] instanceof ComponentPath.Step.Key(String key))) {
                return null;
            }
            
            Tag found = Candidate.find(Arrays.copyOfRange(rest, 1, rest.length), this.get(key));
            return found == null ? null : found.copy();
        }
        
        private boolean hasAll(Set<String> keys) {
            CompoundTag selected = new CompoundTag();
            for (String key : keys) {
                Tag value = this.get(key);
                if (value != null) {
                    selected.put(key, value);
                }
            }
            
            return selected.keySet().containsAll(keys);
        }
        
        private @Nullable Tag get(String key) {
            Tag value = this.values.get(key);
            if (value == null && !this.values.containsKey(key)) {
                value = this.compute(key);
                this.values.put(key, value);
            }
            
            return value;
        }
        
        private @Nullable Tag compute(String key) {
            if (key.startsWith("!")) {
                return null;
            }
            
            if (key.equals(LogisticsNetworksModel.DURABILITY) || key.equals(LogisticsNetworksModel.ENCHANTED)) {
                return this.derived(key);
            }
            
            Identifier id = Identifier.tryParse(key);
            DataComponentType<?> type = id == null || !id.toString().equals(key) ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
            return type == null || type.isTransient() ? this.derived(key) : this.read(key, type);
        }
        
        private <VALUE> @Nullable Tag read(String key, DataComponentType<VALUE> type) {
            if (this.patch == null) {
                this.patch = this.stack.getComponentsPatch();
            }
            
            Optional<VALUE> patched = this.patch.getPatch(type);
            if (patched != null && patched.isPresent()) {
                return this.encode(type, patched.get());
            }
            
            Tag derived = this.derived(key);
            if (derived != null) {
                return derived;
            }
            
            VALUE value = this.stack.get(type);
            return value == null ? null : this.encode(type, value);
        }
        
        private <VALUE> Tag encode(DataComponentType<VALUE> type, VALUE value) {
            return type.codecOrThrow().encodeStart(this.registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow();
        }
        
        private @Nullable Tag derived(String key) {
            boolean damageable = this.stack.isDamageableItem();
            return switch (key) {
                case "minecraft:max_stack_size" -> IntTag.valueOf(this.stack.getMaxStackSize());
                case "minecraft:rarity" -> StringTag.valueOf(this.stack.getRarity().getSerializedName());
                case "minecraft:damage" -> damageable ? IntTag.valueOf(this.stack.getDamageValue()) : null;
                case "minecraft:max_damage" -> damageable ? IntTag.valueOf(this.stack.getMaxDamage()) : null;
                case LogisticsNetworksModel.DURABILITY -> IntTag.valueOf(damageable ? Math.max(0, this.stack.getMaxDamage() - this.stack.getDamageValue()) : 0);
                case LogisticsNetworksModel.ENCHANTED -> ByteTag.valueOf(this.stack.isEnchanted());
                default -> null;
            };
        }
    }
}
