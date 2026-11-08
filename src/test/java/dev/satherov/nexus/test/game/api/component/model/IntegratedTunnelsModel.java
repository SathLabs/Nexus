package dev.satherov.nexus.test.game.api.component.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;

import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

///
/// A model of the item filter of an Integrated Tunnels exporter, which is an Integrated Dynamics predicate over item values.
///
/// The model holds a copy of the operator evaluation of Integrated Dynamics, since neither mod is on the test classpath.
/// Each item stack is wrapped in an item value and then evaluated by the predicate, as the exporter does for every item stack it may move.
/// Each operator call wraps its values in variables, checks its input types, and counts how deep it recurses.
/// Applied, flipped, piped, combined, and composed operators copy and reorder their variables as the mod does.
/// The data keys and data values are read from the item on every evaluation, and a data value is encoded to NBT with the registries of the server.
///
/// The filter becomes the operators that a player would put together:
/// - All-of, any-of, and not filters become conjunctions, disjunctions, and negations, with two predicates in each conjunction or disjunction.
/// - A `has` filter checks that the data keys of the item contain the identifier of the component.
/// - An exact filter checks that the data value of the component equals the value encoded to NBT when the filter is built.
/// - A compare filter reads the data value of the component, then a tag for each key, a list element for each index, and every child for a wildcard.
/// - A compare filter then checks that the tag is present, converts it, and compares it against the literal.
/// - Vanilla predicates, `has` filters on transient components, ordered comparisons of text, and exact filters whose value can't be encoded are tested on a [CompetitorModel.Unmemoized].
///
/// Within an all-of or an any-of filter, the filters tested on the [CompetitorModel.Unmemoized] run after their siblings.
///
/// The model differs from the code of the mods in these ways:
/// - It leaves out the work of the exporter around the predicate, so it's cheaper.
/// - A failed evaluation throws while the exporter turns the part off and fails the item stack, at the same cost while nothing fails.
/// - A tag list read also reads byte, int, and long arrays, which a player would read with the list operator of the array, at about the same cost.
/// - A wildcard also goes through byte, int, and long arrays and the mod skips them, so the model is dearer for those arrays.
/// - It doesn't parse the path of a wildcard on every evaluation, so it's cheaper by a few checks and allocations.
/// - The presence check also fails a tag of another kind than the literal so that the result matches Nexus, at the same cost.
/// - It calls the steps of an NBT operator one after the other while the mod loops over a list of them, so it's cheaper by an iterator.
/// - It reads lists by index while the mod creates an iterator, so it's cheaper by an iterator.
/// - It leaves out the checks for infinite lists and for `null` input types, which can't fail here, so it's cheaper.
/// - Only doubles reach the number comparisons, so it never widens mixed number types and costs the same.
/// - A wildcard returns an empty stream for a tag without children while the mod returns `null`, and the model has no stage that filters out `null`.
///   Both cost about the same.
/// - It leaves out the objects that only wrap a stream of path matches, so it's cheaper by an allocation.
/// - An empty all-of or any-of filter is a conjunction or a disjunction of no predicates, which a player can't build, so the model is cheaper there.
///
public final class IntegratedTunnelsModel implements CompetitorModel {
    
    private static final int RECURSION_LIMIT = 256;
    private static final Set<ValueType> NUMBERS = Set.of(ValueType.INTEGER, ValueType.DOUBLE);
    
    private static final BaseOperator DATA_VALUE = new BaseOperator(new ValueType[]{ ValueType.ITEM, ValueType.STRING }, ValueType.NBT, IntegratedTunnelsModel::readData);
    private static final BaseOperator DATA_KEYS = new BaseOperator(new ValueType[]{ ValueType.ITEM }, ValueType.LIST, IntegratedTunnelsModel::listDataKeys);
    private static final BaseOperator LIST_CONTAINS = new BaseOperator(new ValueType[]{ ValueType.LIST, ValueType.ANY }, ValueType.BOOLEAN, IntegratedTunnelsModel::contains);
    private static final BaseOperator LIST_CONTAINS_PREDICATE = new BaseOperator(
            new ValueType[]{ ValueType.LIST, ValueType.OPERATOR },
            ValueType.BOOLEAN,
            IntegratedTunnelsModel::containsMatch
    );
    private static final BaseOperator LIST_ELEMENT_DEFAULT = new BaseOperator(
            new ValueType[]{ ValueType.LIST, ValueType.INTEGER, ValueType.ANY },
            ValueType.ANY,
            IntegratedTunnelsModel::elementOrDefault
    );
    private static final BaseOperator NBT_PATH_MATCH_ALL = new BaseOperator(new ValueType[]{ ValueType.STRING, ValueType.NBT }, ValueType.LIST, IntegratedTunnelsModel::matchAll);
    private static final BaseOperator NBT_AS_TAG_LIST = new BaseOperator(
            new ValueType[]{ ValueType.NBT },
            ValueType.LIST,
            arguments -> new ListValue(new NbtTags(arguments.getValue(0, NbtValue.class).tag()))
    );
    private static final BaseOperator NBT_COMPOUND_VALUE_TAG = new BaseOperator(
            new ValueType[]{ ValueType.NBT, ValueType.STRING },
            ValueType.NBT,
            IntegratedTunnelsModel.iterative(IntegratedTunnelsModel::readEntry, Function.identity(), tag -> NbtValue.of(tag.orElse(null)))
    );
    private static final BaseOperator NBT_AS_DOUBLE = new BaseOperator(
            new ValueType[]{ ValueType.NBT },
            ValueType.DOUBLE,
            IntegratedTunnelsModel.iterative(IntegratedTunnelsModel::readTag, tag -> tag.flatMap(Tag::asDouble).orElse(0.0D), DoubleValue::new)
    );
    private static final BaseOperator NBT_AS_STRING = new BaseOperator(
            new ValueType[]{ ValueType.NBT },
            ValueType.STRING,
            IntegratedTunnelsModel.iterative(IntegratedTunnelsModel::readTag, tag -> tag.flatMap(Tag::asString).orElse(""), StringValue::new)
    );
    private static final BaseOperator NBT_AS_BOOLEAN = new BaseOperator(
            new ValueType[]{ ValueType.NBT },
            ValueType.BOOLEAN,
            IntegratedTunnelsModel.iterative(IntegratedTunnelsModel::readTag, tag -> tag.flatMap(Tag::asBoolean).orElse(false), BooleanValue::of)
    );
    private static final BaseOperator LOGICAL_OR = new BaseOperator(new ValueType[]{ ValueType.BOOLEAN, ValueType.BOOLEAN }, ValueType.BOOLEAN, IntegratedTunnelsModel::or);
    private static final BaseOperator LOGICAL_NOT = new BaseOperator(
            new ValueType[]{ ValueType.BOOLEAN },
            ValueType.BOOLEAN,
            arguments -> BooleanValue.of(!arguments.getValue(0, BooleanValue.class).value())
    );
    private static final BaseOperator RELATIONAL_EQUALS = new BaseOperator(
            new ValueType[]{ ValueType.ANY, ValueType.ANY },
            ValueType.BOOLEAN,
            IntegratedTunnelsModel::validateEquals,
            arguments -> BooleanValue.of(arguments.getValue(0).equals(arguments.getValue(1)))
    );
    private static final BaseOperator RELATIONAL_GT = new BaseOperator(
            new ValueType[]{ ValueType.NUMBER, ValueType.NUMBER },
            ValueType.BOOLEAN,
            arguments -> BooleanValue.of(IntegratedTunnelsModel.isLess(arguments.getVariable(1), arguments.getVariable(0)))
    );
    private static final BaseOperator RELATIONAL_LT = new BaseOperator(
            new ValueType[]{ ValueType.NUMBER, ValueType.NUMBER },
            ValueType.BOOLEAN,
            arguments -> BooleanValue.of(IntegratedTunnelsModel.isLess(arguments.getVariable(0), arguments.getVariable(1)))
    );
    private static final BaseOperator RELATIONAL_NOTEQUALS = IntegratedTunnelsModel.compose(IntegratedTunnelsModel.LOGICAL_NOT, IntegratedTunnelsModel.RELATIONAL_EQUALS);
    private static final BaseOperator RELATIONAL_GE = IntegratedTunnelsModel.compose(
            IntegratedTunnelsModel.LOGICAL_OR,
            IntegratedTunnelsModel.RELATIONAL_EQUALS,
            IntegratedTunnelsModel.RELATIONAL_GT
    );
    private static final BaseOperator RELATIONAL_LE = IntegratedTunnelsModel.compose(
            IntegratedTunnelsModel.LOGICAL_OR,
            IntegratedTunnelsModel.RELATIONAL_EQUALS,
            IntegratedTunnelsModel.RELATIONAL_LT
    );
    
    private static Operator translate(ComponentFilter filter, CompetitorModel.Unmemoized unmemoized) {
        return switch (filter) {
            case ComponentFilter.Has has when !has.getComponent().isTransient() -> IntegratedTunnelsModel.translateHas(has.getComponent());
            case ComponentFilter.Exact exact -> IntegratedTunnelsModel.translateExact(exact, unmemoized);
            case ComponentFilter.Compare compare when !IntegratedTunnelsModel.isOrderedText(compare.getComparison()) -> IntegratedTunnelsModel.translateCompare(compare);
            case ComponentFilter.AllOf all -> IntegratedTunnelsModel.combine(
                    all.getAllFilters(),
                    unmemoized,
                    IntegratedTunnelsModel::conjunction,
                    IntegratedTunnelsModel.conjunction()
            );
            case ComponentFilter.AnyOf any -> IntegratedTunnelsModel.combine(
                    any.getAllFilters(),
                    unmemoized,
                    IntegratedTunnelsModel::disjunction,
                    IntegratedTunnelsModel.disjunction()
            );
            case ComponentFilter.Not not -> IntegratedTunnelsModel.negation(IntegratedTunnelsModel.translate(not.getFilter(), unmemoized));
            case ComponentFilter.Has _, ComponentFilter.Compare _, ComponentFilter.Matching _ -> new NexusLeaf(filter, unmemoized);
        };
    }
    
    private static boolean isOrderedText(Comparison comparison) {
        return comparison.literal() instanceof Comparison.Literal.Text && comparison.operator().isOrdered();
    }
    
    private static Operator combine(List<ComponentFilter> filters, CompetitorModel.Unmemoized unmemoized, BinaryOperator<Operator> combinator, Operator none) {
        return filters.stream()
                .map(filter -> IntegratedTunnelsModel.translate(filter, unmemoized))
                .sorted(Comparator.comparing(operator -> operator instanceof NexusLeaf))
                .reduce(combinator)
                .orElse(none);
    }
    
    private static Operator translateHas(DataComponentType<?> component) {
        StringValue key = new StringValue(Util.getRegisteredName(BuiltInRegistries.DATA_COMPONENT_TYPE, component));
        return IntegratedTunnelsModel.pipe(IntegratedTunnelsModel.DATA_KEYS, IntegratedTunnelsModel.apply(IntegratedTunnelsModel.flip(IntegratedTunnelsModel.LIST_CONTAINS), key));
    }
    
    private static Operator translateExact(ComponentFilter.Exact exact, CompetitorModel.Unmemoized unmemoized) {
        return exact.getValue()
                .encodeValue(IntegratedTunnelsModel.nbtContext())
                .result()
                .map(tag -> IntegratedTunnelsModel.pipe(
                        IntegratedTunnelsModel.readComponent(exact.getValue().type()),
                        IntegratedTunnelsModel.apply(IntegratedTunnelsModel.RELATIONAL_EQUALS, NbtValue.of(tag))
                ))
                .orElseGet(() -> new NexusLeaf(exact, unmemoized));
    }
    
    private static Operator translateCompare(ComponentFilter.Compare compare) {
        Comparison comparison = compare.getComparison();
        Operator predicate = switch (comparison.literal()) {
            case Comparison.Literal.Numeric(double number) -> IntegratedTunnelsModel.compareTag(
                    NumericTag.class,
                    IntegratedTunnelsModel.NBT_AS_DOUBLE,
                    comparison.operator(),
                    new DoubleValue(number)
            );
            case Comparison.Literal.Text(String text) -> IntegratedTunnelsModel.compareTag(
                    StringTag.class,
                    IntegratedTunnelsModel.NBT_AS_STRING,
                    comparison.operator(),
                    new StringValue(text)
            );
            case Comparison.Literal.Flag(boolean flag) -> IntegratedTunnelsModel.compareTag(
                    ByteTag.class,
                    IntegratedTunnelsModel.NBT_AS_BOOLEAN,
                    comparison.operator(),
                    BooleanValue.of(flag)
            );
        };
        
        for (ComponentPath.Step step : compare.getPath().getAllSteps().reversed()) {
            predicate = switch (step) {
                case ComponentPath.Step.Key(String name) -> IntegratedTunnelsModel.pipe(
                        IntegratedTunnelsModel.apply(IntegratedTunnelsModel.flip(IntegratedTunnelsModel.NBT_COMPOUND_VALUE_TAG), new StringValue(name)),
                        predicate
                );
                case ComponentPath.Step.Index(int index) -> IntegratedTunnelsModel.pipe(
                        IntegratedTunnelsModel.NBT_AS_TAG_LIST,
                        IntegratedTunnelsModel.pipe(IntegratedTunnelsModel.readElement(index), predicate)
                );
                case ComponentPath.Step.Wildcard() -> IntegratedTunnelsModel.pipe(
                        IntegratedTunnelsModel.apply(IntegratedTunnelsModel.NBT_PATH_MATCH_ALL, new StringValue("*")),
                        IntegratedTunnelsModel.apply(IntegratedTunnelsModel.flip(IntegratedTunnelsModel.LIST_CONTAINS_PREDICATE), new OperatorValue(predicate))
                );
            };
        }
        
        return IntegratedTunnelsModel.pipe(IntegratedTunnelsModel.readComponent(compare.getPath().getComponent()), predicate);
    }
    
    private static Operator readComponent(DataComponentType<?> component) {
        StringValue key = new StringValue(Util.getRegisteredName(BuiltInRegistries.DATA_COMPONENT_TYPE, component));
        return IntegratedTunnelsModel.apply(IntegratedTunnelsModel.flip(IntegratedTunnelsModel.DATA_VALUE), key);
    }
    
    private static Operator readElement(int index) {
        Operator indexed = IntegratedTunnelsModel.apply(IntegratedTunnelsModel.flip(IntegratedTunnelsModel.LIST_ELEMENT_DEFAULT), new IntegerValue(index));
        return IntegratedTunnelsModel.apply(IntegratedTunnelsModel.flip(indexed), NbtValue.of(null));
    }
    
    private static Operator compareTag(Class<? extends Tag> kind, Operator conversion, Comparison.Operator operator, Value literal) {
        BaseOperator isNullOrOther = new BaseOperator(new ValueType[]{ ValueType.ANY }, ValueType.BOOLEAN, arguments -> IntegratedTunnelsModel.isNullOrOther(arguments, kind));
        Operator compared = IntegratedTunnelsModel.pipe(conversion, IntegratedTunnelsModel.apply(IntegratedTunnelsModel.relation(operator), literal));
        return IntegratedTunnelsModel.conjunction(IntegratedTunnelsModel.compose(IntegratedTunnelsModel.LOGICAL_NOT, isNullOrOther), compared);
    }
    
    private static BaseOperator relation(Comparison.Operator operator) {
        // The literal is the first argument of the relation, so each operator is mirrored.
        return switch (operator) {
            case EQUAL -> IntegratedTunnelsModel.RELATIONAL_EQUALS;
            case NOT_EQUAL -> IntegratedTunnelsModel.RELATIONAL_NOTEQUALS;
            case LESS -> IntegratedTunnelsModel.RELATIONAL_GT;
            case LESS_OR_EQUAL -> IntegratedTunnelsModel.RELATIONAL_GE;
            case GREATER -> IntegratedTunnelsModel.RELATIONAL_LT;
            case GREATER_OR_EQUAL -> IntegratedTunnelsModel.RELATIONAL_LE;
        };
    }
    
    private static Operator apply(Operator operator, Value value) {
        return new CurriedOperator(operator, new Variable[]{ new ValueVariable(value) });
    }
    
    private static Operator flip(Operator operator) {
        ValueType[] original = operator.getAllInputTypes();
        ValueType[] flipped = new ValueType[original.length];
        for (int i = 0; i < flipped.length; i++) {
            flipped[i] = original[i < 2 ? 1 - i : i];
        }
        
        return new BaseOperator(flipped, operator.getOutputType(), arguments -> {
            int size = arguments.getAllVariables().length;
            Value[] values = new Value[size];
            for (int i = 0; i < size; i++) {
                values[i] = arguments.getValue(i < 2 ? 1 - i : i);
            }
            
            return IntegratedTunnelsModel.evaluate(operator, values);
        });
    }
    
    private static Operator pipe(Operator first, Operator second) {
        ValueType output = second.getOutputType() == ValueType.OPERATOR ? ValueType.ANY : second.getOutputType();
        return new BaseOperator(new ValueType[]{ first.getAllInputTypes()[0] }, output, arguments -> {
            Variable[] all = arguments.getAllVariables();
            Variable[] intermediates = { new ValueVariable(IntegratedTunnelsModel.evaluate(first, all[0])) };
            Variable[] remaining = Arrays.copyOfRange(all, 1, all.length);
            Variable[] piped = Arrays.copyOf(intermediates, intermediates.length + remaining.length);
            System.arraycopy(remaining, 0, piped, intermediates.length, remaining.length);
            return IntegratedTunnelsModel.evaluate(second, piped);
        });
    }
    
    private static Operator conjunction(Operator... operators) {
        return new BaseOperator(new ValueType[]{ ValueType.ANY }, ValueType.BOOLEAN, arguments -> {
            Value value = arguments.getValue(0);
            for (Operator operator : operators) {
                Value result = IntegratedTunnelsModel.evaluate(operator, value);
                IntegratedTunnelsModel.validatePredicateOutput(result);
                if (!((BooleanValue) result).value()) {
                    return BooleanValue.of(false);
                }
            }
            
            return BooleanValue.of(true);
        });
    }
    
    private static Operator disjunction(Operator... operators) {
        return new BaseOperator(new ValueType[]{ ValueType.ANY }, ValueType.BOOLEAN, arguments -> {
            Value value = arguments.getValue(0);
            for (Operator operator : operators) {
                Value result = IntegratedTunnelsModel.evaluate(operator, value);
                IntegratedTunnelsModel.validatePredicateOutput(result);
                if (((BooleanValue) result).value()) {
                    return BooleanValue.of(true);
                }
            }
            
            return BooleanValue.of(false);
        });
    }
    
    private static Operator negation(Operator operator) {
        return new BaseOperator(new ValueType[]{ ValueType.ANY }, ValueType.BOOLEAN, arguments -> {
            Value result = IntegratedTunnelsModel.evaluate(operator, arguments.getValue(0));
            IntegratedTunnelsModel.validatePredicateOutput(result);
            return BooleanValue.of(!((BooleanValue) result).value());
        });
    }
    
    private static BaseOperator compose(BaseOperator outer, BaseOperator... inner) {
        int length = Arrays.stream(inner)
                .mapToInt(BaseOperator::getRequiredInputLength)
                .min()
                .orElseThrow();
        BiPredicate<BaseOperator, ValueType[]> validator = (_, input) -> {
            if (input.length != length) {
                return false;
            }
            
            ValueType[] outputs = new ValueType[inner.length];
            for (int i = 0; i < inner.length; i++) {
                if (!inner[i].validateTypes(input)) {
                    return false;
                }
                
                outputs[i] = inner[i].getOutputType();
            }
            
            return outer.validateTypes(outputs);
        };
        return new BaseOperator(Arrays.copyOf(inner[0].getAllInputTypes(), length), outer.getOutputType(), validator, arguments -> {
            Variable[] variables = arguments.getAllVariables();
            Variable[] composed = new Variable[inner.length];
            for (int i = 0; i < inner.length; i++) {
                composed[i] = new LazyVariable(inner[i], variables);
            }
            
            return outer.evaluate(composed);
        });
    }
    
    private static <INPUT, OUTPUT> Function<Arguments, Value> iterative(Function<Arguments, INPUT> pre, Function<INPUT, OUTPUT> main, Function<OUTPUT, Value> post) {
        return arguments -> post.apply(main.apply(pre.apply(arguments)));
    }
    
    private static Value evaluate(Operator operator, Value... values) {
        Variable[] variables = new Variable[values.length];
        for (int i = 0; i < variables.length; i++) {
            Value value = values[i];
            variables[i] = new ValueVariable(value.type(), value);
        }
        
        return IntegratedTunnelsModel.evaluate(operator, variables);
    }
    
    private static Value evaluate(Operator operator, Variable... variables) {
        if (operator.getRequiredInputLength() != variables.length) {
            throw new IllegalStateException("Could not evaluate the operator, it takes '" + operator.getRequiredInputLength() + "' inputs and found '" + variables.length + "'");
        }
        
        return operator.evaluate(variables);
    }
    
    private static void validatePredicateOutput(Value result) {
        if (!(result instanceof BooleanValue)) {
            throw new IllegalStateException("Could not test the predicate, it returned a value of the type '" + result.type() + "'");
        }
    }
    
    private static boolean correspondsTo(ValueType first, ValueType second) {
        return first.correspondsTo(second) || second.correspondsTo(first);
    }
    
    private static boolean validateInputs(BaseOperator operator, ValueType[] input) {
        if (input.length != operator.getRequiredInputLength()) {
            return false;
        }
        
        for (int i = 0; i < input.length; i++) {
            if (!IntegratedTunnelsModel.correspondsTo(operator.getAllInputTypes()[i], input[i])) {
                return false;
            }
        }
        
        return true;
    }
    
    private static boolean validateEquals(BaseOperator operator, ValueType[] input) {
        if (input.length != operator.getRequiredInputLength()) {
            return false;
        }
        
        ValueType first = input[0].isNumber() ? ValueType.NUMBER : input[0];
        ValueType second = input[1].isNumber() ? ValueType.NUMBER : input[1];
        return IntegratedTunnelsModel.correspondsTo(first, second);
    }
    
    private static RegistryOps<Tag> nbtContext() {
        //noinspection DataFlowIssue The speed samples run on a running server.
        return ServerLifecycleHooks.getCurrentServer().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }
    
    private static Value readData(Arguments arguments) {
        ItemValue item = arguments.getValue(0, ItemValue.class);
        DataComponentType<?> component = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(arguments.getValue(1, StringValue.class).value()));
        //noinspection DataFlowIssue The identifier was read from the registry when the filter was built.
        TypedDataComponent<?> typed = item.stack().getComponents().getTyped(component);
        if (typed == null) {
            return NbtValue.of(null);
        }
        
        try {
            return NbtValue.of(typed.encodeValue(IntegratedTunnelsModel.nbtContext()).getOrThrow());
        } catch (IllegalStateException _) {
            return NbtValue.of(null);
        }
    }
    
    private static Value listDataKeys(Arguments arguments) {
        ItemValue item = arguments.getValue(0, ItemValue.class);
        if (item.stack().isEmpty()) {
            return new ListValue(new Materialized(ValueType.STRING, List.of()));
        }
        
        List<StringValue> keys = item.stack()
                .getComponents()
                .keySet()
                .stream()
                .filter(component -> !component.isTransient())
                .map(component -> new StringValue(String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component))))
                .sorted(Comparator.comparing(StringValue::value))
                .toList();
        return new ListValue(new Materialized(ValueType.STRING, keys));
    }
    
    private static Value contains(Arguments arguments) {
        ListProxy list = arguments.getValue(0, ListValue.class).proxy();
        Value input = arguments.getValue(1);
        int length = list.length();
        for (int i = 0; i < length; i++) {
            if (list.get(i).equals(input)) {
                return BooleanValue.of(true);
            }
        }
        
        return BooleanValue.of(false);
    }
    
    private static Value containsMatch(Arguments arguments) {
        ListProxy list = arguments.getValue(0, ListValue.class).proxy();
        Operator predicate = arguments.getValue(1, OperatorValue.class).operator();
        if (!IntegratedTunnelsModel.correspondsTo(predicate.getOutputType(), ValueType.BOOLEAN)) {
            throw new IllegalStateException("Could not test the list, the predicate returns values of the type '" + predicate.getOutputType() + "'");
        }
        
        int length = list.length();
        for (int i = 0; i < length; i++) {
            Value result = IntegratedTunnelsModel.evaluate(predicate, list.get(i));
            IntegratedTunnelsModel.validatePredicateOutput(result);
            if (((BooleanValue) result).value()) {
                return BooleanValue.of(true);
            }
        }
        
        return BooleanValue.of(false);
    }
    
    private static Value elementOrDefault(Arguments arguments) {
        ListProxy list = arguments.getValue(0, ListValue.class).proxy();
        int index = arguments.getValue(1, IntegerValue.class).value();
        if (index < list.length() && index >= 0) {
            return list.get(index);
        }
        
        if (!IntegratedTunnelsModel.correspondsTo(list.elementType(), arguments.getVariable(2).type())) {
            throw new IllegalStateException("Could not read the list, the default has the type '" + arguments.getVariable(2).type() + "'");
        }
        
        return arguments.getValue(2);
    }
    
    private static Value matchAll(Arguments arguments) {
        Optional<Tag> tag = arguments.getValue(1, NbtValue.class).tag();
        if (tag.isEmpty()) {
            return new ListValue(new Materialized(ValueType.NBT, List.of()));
        }
        
        List<NbtValue> matches = Stream.of(tag.get())
                .map(Context::new)
                .flatMap(IntegratedTunnelsModel::children)
                .map(Context::tag)
                .map(NbtValue::of)
                .collect(Collectors.toList());
        return new ListValue(new Materialized(ValueType.NBT, matches));
    }
    
    private static Stream<Context> children(Context context) {
        return switch (context.tag()) {
            case CollectionTag collection -> StreamSupport.stream(collection.spliterator(), false).map(child -> new Context(child, context));
            case CompoundTag compound -> compound.keySet().stream().map(key -> new Context(Objects.requireNonNull(compound.get(key)), context));
            default -> Stream.empty();
        };
    }
    
    private static Optional<Tag> readTag(Arguments arguments) {
        return arguments.getValue(0, NbtValue.class).tag();
    }
    
    private static Optional<Tag> readEntry(Arguments arguments) {
        NbtValue nbt = arguments.getValue(0, NbtValue.class);
        StringValue key = arguments.getValue(1, StringValue.class);
        return nbt.tag()
                .filter(tag -> tag instanceof CompoundTag)
                .map(tag -> ((CompoundTag) tag).get(key.value()));
    }
    
    private static Value or(Arguments arguments) {
        if (arguments.getValue(0, BooleanValue.class).value()) {
            return BooleanValue.of(true);
        }
        
        return arguments.getValue(1, BooleanValue.class);
    }
    
    private static Value isNullOrOther(Arguments arguments, Class<? extends Tag> kind) {
        Variable variable = arguments.getVariable(0);
        if (!IntegratedTunnelsModel.correspondsTo(variable.type(), ValueType.NULLABLE)) {
            return BooleanValue.of(false);
        }
        
        return BooleanValue.of(((NbtValue) variable.value()).tag().filter(kind::isInstance).isEmpty());
    }
    
    private static boolean isLess(Variable smaller, Variable larger) {
        ValueType type = IntegratedTunnelsModel.lowestType(IntegratedTunnelsModel.numberType(smaller), IntegratedTunnelsModel.numberType(larger));
        return ((DoubleValue) IntegratedTunnelsModel.castValue(type, smaller.value())).value() < ((DoubleValue) IntegratedTunnelsModel.castValue(type, larger.value())).value();
    }
    
    private static ValueType numberType(Variable variable) {
        return variable.type().isNumber() ? variable.type() : variable.value().type();
    }
    
    private static ValueType lowestType(ValueType... types) {
        ValueType first = types[0];
        for (int i = 1; i < types.length; i++) {
            if (types[i] != first) {
                throw new IllegalStateException("Could not compare the numbers, found the types '" + first + "' and '" + types[i] + "'");
            }
        }
        
        return first;
    }
    
    private static Value castValue(ValueType type, Value value) {
        if (value.type() != type) {
            throw new IllegalStateException("Could not cast the value of the type '" + value.type() + "' to '" + type + "'");
        }
        
        return value;
    }
    
    @Override
    public String getName() {
        return "integrated_tunnels";
    }
    
    @Override
    public Predicate<ItemStack> build(ComponentFilter filter) {
        Operator predicate = IntegratedTunnelsModel.translate(filter, new CompetitorModel.Unmemoized());
        return stack -> {
            Value result = IntegratedTunnelsModel.evaluate(predicate, new ItemValue(stack));
            IntegratedTunnelsModel.validatePredicateOutput(result);
            return ((BooleanValue) result).value();
        };
    }
    
    @Override
    public boolean expresses(ComponentFilter filter) {
        return switch (filter) {
            case ComponentFilter.AllOf all -> all.getAllFilters().stream().anyMatch(this::expresses);
            case ComponentFilter.AnyOf any -> any.getAllFilters().stream().anyMatch(this::expresses);
            case ComponentFilter.Not not -> this.expresses(not.getFilter());
            case ComponentFilter.Has _, ComponentFilter.Exact _, ComponentFilter.Compare _, ComponentFilter.Matching _ -> {
                yield !(IntegratedTunnelsModel.translate(filter, new CompetitorModel.Unmemoized()) instanceof NexusLeaf);
            }
        };
    }
    
    private enum ValueType {
        ANY,
        NUMBER,
        NULLABLE,
        ITEM,
        STRING,
        NBT,
        LIST,
        OPERATOR,
        BOOLEAN,
        INTEGER,
        DOUBLE;
        
        private boolean correspondsTo(ValueType other) {
            return switch (this) {
                case ANY -> true;
                case NUMBER -> other == this || IntegratedTunnelsModel.NUMBERS.contains(other);
                case NULLABLE -> other == this || other.isNullable();
                case ITEM, STRING, NBT, LIST, OPERATOR, BOOLEAN, INTEGER, DOUBLE -> this == other;
            };
        }
        
        private boolean isNumber() {
            return this == ValueType.INTEGER || this == ValueType.DOUBLE;
        }
        
        private boolean isNullable() {
            return this == ValueType.ITEM || this == ValueType.NBT;
        }
    }
    
    private sealed interface Value {
        
        ValueType type();
    }
    
    private record ItemValue(ItemStack stack) implements Value {
        
        @Override
        public ValueType type() {
            return ValueType.ITEM;
        }
    }
    
    private record StringValue(String value) implements Value {
        
        @Override
        public ValueType type() {
            return ValueType.STRING;
        }
    }
    
    private record IntegerValue(int value) implements Value {
        
        @Override
        public ValueType type() {
            return ValueType.INTEGER;
        }
    }
    
    private record DoubleValue(double value) implements Value {
        
        @Override
        public ValueType type() {
            return ValueType.DOUBLE;
        }
        
        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof DoubleValue(double number) && number == this.value;
        }
        
        @Override
        public int hashCode() {
            return Double.hashCode(this.value);
        }
    }
    
    private record BooleanValue(boolean value) implements Value {
        
        private static final BooleanValue TRUE = new BooleanValue(true);
        private static final BooleanValue FALSE = new BooleanValue(false);
        
        private static BooleanValue of(boolean value) {
            return value ? BooleanValue.TRUE : BooleanValue.FALSE;
        }
        
        @Override
        public ValueType type() {
            return ValueType.BOOLEAN;
        }
    }
    
    private record NbtValue(Optional<Tag> tag) implements Value {
        
        private static NbtValue of(@Nullable Tag tag) {
            return new NbtValue(Optional.ofNullable(tag));
        }
        
        @Override
        public ValueType type() {
            return ValueType.NBT;
        }
    }
    
    private record ListValue(ListProxy proxy) implements Value {
        
        @Override
        public ValueType type() {
            return ValueType.LIST;
        }
    }
    
    private record OperatorValue(Operator operator) implements Value {
        
        @Override
        public ValueType type() {
            return ValueType.OPERATOR;
        }
    }
    
    private interface ListProxy {
        
        ValueType elementType();
        
        int length();
        
        Value get(int index);
    }
    
    private record Materialized(ValueType elementType, List<? extends Value> values) implements ListProxy {
        
        @Override
        public int length() {
            return this.values.size();
        }
        
        @Override
        public Value get(int index) {
            return this.values.get(index);
        }
    }
    
    private record NbtTags(Optional<Tag> tag) implements ListProxy {
        
        @Override
        public ValueType elementType() {
            return ValueType.NBT;
        }
        
        @Override
        public int length() {
            try {
                return this.tag.map(value -> ((CollectionTag) value).size()).orElse(0);
            } catch (ClassCastException _) {
                return 0;
            }
        }
        
        @Override
        public Value get(int index) {
            if (index >= this.length()) throw new IndexOutOfBoundsException(index);
            return this.tag.map(value -> NbtValue.of(((CollectionTag) value).get(index))).orElseThrow();
        }
    }
    
    private record Context(Tag tag, @Nullable Context parent) {
        
        private Context(Tag tag) {
            this(tag, null);
        }
    }
    
    private interface Variable {
        
        ValueType type();
        
        Value value();
    }
    
    private record ValueVariable(ValueType type, Value value) implements Variable {
        
        private ValueVariable(Value value) {
            this(value.type(), value);
        }
    }
    
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class LazyVariable implements Variable {
        
        private final BaseOperator operator;
        private final Variable[] variables;
        
        @Override
        public ValueType type() {
            return this.operator.getOutputType();
        }
        
        @Override
        public Value value() {
            return this.operator.evaluate(this.variables);
        }
    }
    
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Arguments {
        
        private final Variable[] variables;
        
        private Variable[] getAllVariables() {
            return this.variables;
        }
        
        private Variable getVariable(int index) {
            return this.variables[index];
        }
        
        private Value getValue(int index) {
            return this.variables[index].value();
        }
        
        private <VALUE extends Value> VALUE getValue(int index, Class<VALUE> type) {
            return type.cast(this.getValue(index));
        }
    }
    
    private interface Operator {
        
        ValueType[] getAllInputTypes();
        
        ValueType getOutputType();
        
        int getRequiredInputLength();
        
        Value evaluate(Variable[] input);
    }
    
    private static final class BaseOperator implements Operator {
        
        private final ValueType[] inputTypes;
        @Getter
        private final ValueType outputType;
        private final BiPredicate<BaseOperator, ValueType[]> validator;
        private final Function<Arguments, Value> function;
        private int recursiveInvocations;
        
        private BaseOperator(ValueType[] inputTypes, ValueType outputType, BiPredicate<BaseOperator, ValueType[]> validator, Function<Arguments, Value> function) {
            this.inputTypes = inputTypes;
            this.outputType = outputType;
            this.validator = validator;
            this.function = function;
        }
        
        private BaseOperator(ValueType[] inputTypes, ValueType outputType, Function<Arguments, Value> function) {
            this(inputTypes, outputType, IntegratedTunnelsModel::validateInputs, function);
        }
        
        @Override
        public ValueType[] getAllInputTypes() {
            return this.inputTypes;
        }
        
        @Override
        public int getRequiredInputLength() {
            return this.inputTypes.length;
        }
        
        @Override
        public Value evaluate(Variable[] input) {
            if (this.recursiveInvocations++ > IntegratedTunnelsModel.RECURSION_LIMIT) {
                this.recursiveInvocations = 0;
                throw new IllegalStateException("Could not evaluate the operator, it recursed more than '" + IntegratedTunnelsModel.RECURSION_LIMIT + "' times");
            }
            
            ValueType[] types = new ValueType[input.length];
            for (int i = 0; i < types.length; i++) {
                types[i] = input[i].type();
            }
            
            if (!this.validateTypes(types)) {
                this.recursiveInvocations--;
                throw new IllegalStateException("Could not evaluate the operator, it doesn't take the input types '" + Arrays.toString(types) + "'");
            }
            
            Value result = this.function.apply(new Arguments(input));
            this.recursiveInvocations--;
            return result;
        }
        
        private boolean validateTypes(ValueType[] input) {
            return this.validator.test(this, input);
        }
    }
    
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class CurriedOperator implements Operator {
        
        private final Operator base;
        private final Variable[] applied;
        
        @Override
        public ValueType[] getAllInputTypes() {
            ValueType[] baseTypes = this.base.getAllInputTypes();
            return Arrays.copyOfRange(baseTypes, this.applied.length, baseTypes.length);
        }
        
        @Override
        public ValueType getOutputType() {
            return this.base.getOutputType();
        }
        
        @Override
        public int getRequiredInputLength() {
            return this.base.getRequiredInputLength() - this.applied.length;
        }
        
        @Override
        public Value evaluate(Variable[] input) {
            Variable[] full = new Variable[Math.min(this.base.getRequiredInputLength(), input.length + this.applied.length)];
            System.arraycopy(this.applied, 0, full, 0, this.applied.length);
            System.arraycopy(input, 0, full, this.applied.length, full.length - this.applied.length);
            return this.base.evaluate(full);
        }
    }
    
    private record NexusLeaf(ComponentFilter filter, CompetitorModel.Unmemoized unmemoized) implements Operator {
        
        @Override
        public ValueType[] getAllInputTypes() {
            return new ValueType[]{ ValueType.ITEM };
        }
        
        @Override
        public ValueType getOutputType() {
            return ValueType.BOOLEAN;
        }
        
        @Override
        public int getRequiredInputLength() {
            return 1;
        }
        
        @Override
        public Value evaluate(Variable[] input) {
            return BooleanValue.of(this.unmemoized.test(this.filter, ((ItemValue) input[0].value()).stack()));
        }
    }
}
