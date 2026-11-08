package dev.satherov.nexus.internal.component;

import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.extension.component.ComponentTypeCodec;
import dev.satherov.nexus.internal.codec.CodecErrors;
import dev.satherov.nexus.internal.codec.CollectionCodecs;
import dev.satherov.nexus.internal.codec.Scalars;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.format.Operations;
import dev.satherov.nexus.internal.codec.vanilla.DfuAdapter;
import dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

///
/// The data and network form of [ComponentFilter].
///
/// The cases are the constants of [Case], whose ordinal is the index of the case on the network and which hold its keys.
/// Every error is reported at the key of the value that failed.
///
/// On the network, a predicate type is written as its network id in the built-in registry, since the registry of predicate types isn't synced.
///
@ApiStatus.Internal
public final class FilterCodec extends Traversal<ComponentFilter, Access.Registries> {
    
    ///
    /// The key of the component of [ComponentFilter.Has].
    ///
    private static final String HAS_KEY = "has";
    
    ///
    /// The key of the component of [ComponentFilter.Exact].
    ///
    private static final String EXACT_KEY = "exact";
    
    ///
    /// The key of the path of [ComponentFilter.Compare].
    ///
    private static final String PATH_KEY = "path";
    
    ///
    /// The key of the operator of [ComponentFilter.Compare].
    ///
    private static final String IS_KEY = "is";
    
    ///
    /// The key of the value of [ComponentFilter.Exact], the literal of [ComponentFilter.Compare], and the predicate of [ComponentFilter.Matching].
    ///
    private static final String VALUE_KEY = "value";
    
    ///
    /// The key of the predicate type of [ComponentFilter.Matching].
    ///
    private static final String MATCHES_KEY = "matches";
    
    ///
    /// The key of the filters of [ComponentFilter.AllOf].
    ///
    private static final String ALL_KEY = "all";
    
    ///
    /// The key of the filters of [ComponentFilter.AnyOf].
    ///
    private static final String ANY_KEY = "any";
    
    ///
    /// The key of the inner filter of [ComponentFilter.Not].
    ///
    private static final String NOT_KEY = "not";
    
    ///
    /// The kind that marks a number literal on the network.
    ///
    private static final int NUMBER = 0;
    
    ///
    /// The kind that marks a text literal on the network.
    ///
    private static final int TEXT = 1;
    
    ///
    /// The kind that marks a flag literal on the network.
    ///
    private static final int FLAG = 2;
    
    ///
    /// The first key of every case, in the order the cases are tried in.
    ///
    private static final List<String> CASE_KEYS = Arrays.stream(Case.values()).map(kind -> kind.keys.getFirst()).toList();
    
    ///
    /// The symbols of every operator, in the order of their ordinals.
    ///
    private static final List<String> SYMBOLS = Arrays.stream(Comparison.Operator.values()).map(Comparison.Operator::getSymbol).toList();
    
    ///
    /// The codec of a component path in its text form.
    ///
    private static final Traversal<ComponentPath, Access.Plain> PATH = (Traversal<ComponentPath, Access.Plain>) ComponentPath.CODEC;
    
    ///
    /// The codec of the filters of [ComponentFilter.AllOf] and [ComponentFilter.AnyOf].
    ///
    private final Traversal<List<ComponentFilter>, Access.Registries> children = CollectionCodecs.list(this, CollectionCodecs.LIMIT);
    
    ///
    /// Should only ever be called from [ComponentFilter#CODEC].
    ///
    public FilterCodec() {
        super("ComponentFilter");
    }
    
    ///
    /// Runs the given encoding and puts the result under the given key in JSON and NBT.
    ///
    /// On the network, the encoding appends the value to the buffer.
    ///
    /// @param operations The operations of the format.
    /// @param object     The object to put the result into.
    /// @param key        The key of the result.
    /// @param encoded    The encoding of the value.
    ///
    /// @throws NexusCodecException If the encoding fails.
    ///
    private static <V> void put(Operations<V> operations, V object, String key, Supplier<V> encoded) {
        V value;
        try {
            value = encoded.get();
        } catch (NexusCodecException failure) {
            throw CodecErrors.prefixKey(failure, key);
        }
        
        if (!operations.isPositional()) {
            operations.put(object, key, value);
        }
    }
    
    ///
    /// Reads the value under the given key in JSON and NBT, or the next value on the network.
    ///
    /// @param operations The operations of the format.
    /// @param input      The object to read from.
    /// @param key        The key of the value.
    /// @param reader     The reader of the value.
    ///
    /// @return The value that the reader read.
    ///
    /// @throws NexusCodecException If the key is missing or the reader fails.
    ///
    private static <V, FIELD> FIELD field(Operations<V> operations, V input, String key, Function<V, FIELD> reader) {
        V value = operations.isPositional() ? input : operations.get(input, key);
        if (value == null) {
            throw CodecErrors.missing(key);
        }
        
        try {
            return reader.apply(value);
        } catch (NexusCodecException failure) {
            throw CodecErrors.prefixKey(failure, key);
        }
    }
    
    ///
    /// Writes the network id of the given element on the network and its identifier otherwise.
    ///
    /// @param operations The operations of the format.
    /// @param registry   The registry of the element.
    /// @param element    The element to write.
    ///
    /// @return The network id or the identifier of the element.
    ///
    /// @throws NexusCodecException If the registry doesn't have the element.
    ///
    private static <V, ELEMENT> V writeElement(Operations<V> operations, Registry<ELEMENT> registry, ELEMENT element) {
        Identifier id = registry.getKey(element);
        if (id == null) {
            throw CodecErrors.unregistered(registry.key());
        }
        
        return operations.isPositional() ? operations.ofVarInt(registry.getId(element)) : Scalars.IDENTIFIER.write(operations, id);
    }
    
    ///
    /// Reads the network id of an element on the network and its identifier otherwise.
    ///
    /// @param operations The operations of the format.
    /// @param input      The network id or the identifier.
    /// @param registry   The registry of the element.
    ///
    /// @return The element.
    ///
    /// @throws NexusCodecException If the registry doesn't have the network id or the identifier.
    ///
    private static <V, ELEMENT> ELEMENT readElement(Operations<V> operations, V input, Registry<ELEMENT> registry) {
        if (operations.isPositional()) {
            int id = operations.asVarInt(input);
            ELEMENT element = registry.byId(id);
            if (element == null) {
                throw CodecErrors.unknownElement(registry.key(), id);
            }
            
            return element;
        }
        
        Identifier id = Scalars.IDENTIFIER.read(operations, input);
        return registry.getOptional(id).orElseThrow(() -> CodecErrors.unknownElement(registry.key(), id.toString()));
    }
    
    ///
    /// Reads a data component type that isn't transient.
    ///
    /// @param operations The operations of the format.
    /// @param input      The network id or the identifier of the data component type.
    ///
    /// @return The data component type.
    ///
    /// @throws NexusCodecException If the data component type is transient.
    ///
    private static <V> DataComponentType<?> readPersistent(Operations<V> operations, V input) {
        DataComponentType<?> component = FilterCodec.readElement(operations, input, BuiltInRegistries.DATA_COMPONENT_TYPE);
        if (component.isTransient()) {
            throw CodecErrors.mismatch("a persistent component type", Util.getRegisteredName(BuiltInRegistries.DATA_COMPONENT_TYPE, component));
        }
        
        return component;
    }
    
    ///
    /// Gets the codec of the values of the given data component type.
    ///
    /// If Nexus didn't build the data component type from a codec, this will return its vanilla codec and stream codec as one codec.
    ///
    /// @param component The data component type.
    ///
    /// @return The codec of the values.
    ///
    private static <VALUE> Traversal<VALUE, ?> valueCodec(DataComponentType<VALUE> component) {
        if (component instanceof ComponentTypeCodec extension && extension.nexus$getCodec() instanceof DfuAdapter(Traversal<?, ?> codec)) {
            //noinspection unchecked The builder of the component got a codec of its values.
            return (Traversal<VALUE, ?>) codec;
        }
        
        return VanillaAdapters.ofRegistryVanilla(component.codecOrThrow(), component.streamCodec());
    }
    
    ///
    /// Writes the value of the given typed component with the codec of its data component type.
    ///
    /// @param operations The operations of the format.
    /// @param component  The typed component.
    ///
    /// @return The encoded value.
    ///
    private static <V, VALUE> V writeValue(Operations<V> operations, TypedDataComponent<VALUE> component) {
        return FilterCodec.valueCodec(component.type()).write(operations, component.value());
    }
    
    ///
    /// Reads the value of an exact filter on the given data component type.
    ///
    /// @param operations The operations of the format.
    /// @param input      The object to read from.
    /// @param component  The data component type of the value.
    ///
    /// @return The exact filter.
    ///
    private static <V, VALUE> ComponentFilter readExact(Operations<V> operations, V input, DataComponentType<VALUE> component) {
        return ComponentFilter.exact(component, FilterCodec.field(operations, input, FilterCodec.VALUE_KEY, value -> FilterCodec.valueCodec(component).read(operations, value)));
    }
    
    ///
    /// Writes the text form of the given component path.
    ///
    /// @param operations The operations of the format.
    /// @param path       The component path.
    ///
    /// @return The text form of the component path.
    ///
    /// @throws NexusCodecException If the data component type of the component path isn't registered.
    ///
    private static <V> V writePath(Operations<V> operations, ComponentPath path) {
        if (BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(path.getComponent()) == null) {
            throw CodecErrors.unregistered(Registries.DATA_COMPONENT_TYPE);
        }
        
        return FilterCodec.PATH.write(operations, path);
    }
    
    ///
    /// Writes the ordinal of the given operator on the network and its symbol otherwise.
    ///
    /// @param operations The operations of the format.
    /// @param operator   The operator.
    ///
    /// @return The ordinal or the symbol of the operator.
    ///
    private static <V> V writeOperator(Operations<V> operations, Comparison.Operator operator) {
        return operations.isPositional() ? operations.ofVarInt(operator.ordinal()) : Scalars.STRING.write(operations, operator.getSymbol());
    }
    
    ///
    /// Reads the ordinal of an operator on the network and its symbol otherwise.
    ///
    /// @param operations The operations of the format.
    /// @param input      The ordinal or the symbol.
    ///
    /// @return The operator.
    ///
    /// @throws NexusCodecException If the symbol isn't the symbol of an operator.
    ///
    private static <V> Comparison.Operator readOperator(Operations<V> operations, V input) {
        if (operations.isPositional()) {
            return FilterCodec.byIndex(Comparison.Operator.values(), operations.asVarInt(input));
        }
        
        String symbol = Scalars.STRING.read(operations, input);
        int index = FilterCodec.SYMBOLS.indexOf(symbol);
        if (index < 0) {
            throw CodecErrors.unknownName(FilterCodec.SYMBOLS, symbol);
        }
        
        return Comparison.Operator.values()[index];
    }
    
    ///
    /// Writes the kind and then the value of the given literal on the network, and the bare value otherwise.
    ///
    /// A number is always a `double` on the network.
    /// In JSON and NBT, a whole number that fits an `int` is an `int`, unless it's `-0.0`.
    /// NBT still loses the sign of `-0.0` in vanilla's `DoubleTag.valueOf`.
    ///
    /// @param operations The operations of the format.
    /// @param literal    The literal.
    ///
    /// @return The value of the literal.
    ///
    private static <V> V writeLiteral(Operations<V> operations, Comparison.Literal literal) {
        if (operations.isPositional()) {
            //noinspection ResultOfMethodCallIgnored This can only be reached with a positional format.
            operations.ofVarInt(switch (literal) {
                case Comparison.Literal.Numeric _ -> FilterCodec.NUMBER;
                case Comparison.Literal.Text _ -> FilterCodec.TEXT;
                case Comparison.Literal.Flag _ -> FilterCodec.FLAG;
            });
        }
        
        return switch (literal) {
            case Comparison.Literal.Numeric(double number) when !operations.isPositional() && Double.compare(number, (int) number) == 0 -> operations.ofInt((int) number);
            case Comparison.Literal.Numeric(double number) -> operations.ofDouble(number);
            case Comparison.Literal.Text(String text) -> Scalars.STRING.write(operations, text);
            case Comparison.Literal.Flag(boolean flag) -> operations.ofBoolean(flag);
        };
    }
    
    ///
    /// Reads the kind and then the value of a literal on the network, and the bare value otherwise.
    ///
    /// A bare value is read as a flag first, then as a number, and then as text.
    /// It's only a flag if it equals what the format writes for `true` or `false`, so in NBT a byte of `0` or `1` is a flag.
    ///
    /// @param operations The operations of the format.
    /// @param input      The encoded literal.
    ///
    /// @return The literal.
    ///
    /// @throws NexusCodecException If the kind on the network is unknown, or the bare value isn't a flag, a number, or text.
    ///
    private static <V> Comparison.Literal readLiteral(Operations<V> operations, V input) {
        if (operations.isPositional()) {
            int kind = operations.asVarInt(input);
            return switch (kind) {
                case FilterCodec.NUMBER -> new Comparison.Literal.Numeric(operations.asDouble(input));
                case FilterCodec.TEXT -> new Comparison.Literal.Text(Scalars.STRING.read(operations, input));
                case FilterCodec.FLAG -> new Comparison.Literal.Flag(operations.asBoolean(input));
                default -> throw CodecErrors.outOfRange(FilterCodec.NUMBER, FilterCodec.FLAG, kind);
            };
        }
        
        if (input.equals(operations.ofBoolean(true))) {
            return new Comparison.Literal.Flag(true);
        }
        
        if (input.equals(operations.ofBoolean(false))) {
            return new Comparison.Literal.Flag(false);
        }
        
        try {
            return new Comparison.Literal.Numeric(operations.asDouble(input));
        } catch (NexusCodecException _) {
            try {
                return new Comparison.Literal.Text(Scalars.STRING.read(operations, input));
            } catch (NexusCodecException _) {
                throw CodecErrors.mismatch("a boolean, a number, or a string", input);
            }
        }
    }
    
    ///
    /// Reads the path, the operator, and the literal of [ComponentFilter.Compare].
    ///
    /// A refused comparison is reported at `is`.
    ///
    /// @param operations The operations of the format.
    /// @param input      The object to read from.
    ///
    /// @return The compare filter.
    ///
    /// @throws NexusCodecException If the comparison is refused by [Comparison] or [ComponentFilter#compare(ComponentPath, Comparison)].
    ///
    private static <V> ComponentFilter readCompare(Operations<V> operations, V input) {
        ComponentPath path = FilterCodec.field(operations, input, FilterCodec.PATH_KEY, value -> FilterCodec.PATH.read(operations, value));
        Comparison.Operator operator = FilterCodec.field(operations, input, FilterCodec.IS_KEY, value -> FilterCodec.readOperator(operations, value));
        Comparison.Literal literal = FilterCodec.field(operations, input, FilterCodec.VALUE_KEY, value -> FilterCodec.readLiteral(operations, value));
        try {
            return ComponentFilter.compare(path, new Comparison(operator, literal));
        } catch (IllegalArgumentException failure) {
            throw CodecErrors.prefixKey(CodecErrors.refused(failure), FilterCodec.IS_KEY);
        }
    }
    
    ///
    /// Writes the given predicate with the DFU codec of the given predicate type.
    ///
    /// @param operations The operations of the format.
    /// @param type       The predicate type.
    /// @param predicate  The predicate.
    ///
    /// @return The encoded predicate.
    ///
    private static <V, PREDICATE extends DataComponentPredicate> V writePredicate(Operations<V> operations, DataComponentPredicate.Type<PREDICATE> type, DataComponentPredicate predicate) {
        //noinspection unchecked A matching filter holds a predicate of its own type.
        return VanillaAdapters.ofRegistryDfu(type.codec()).write(operations, (PREDICATE) predicate);
    }
    
    ///
    /// Reads the predicate of [ComponentFilter.Matching] with the given predicate type.
    ///
    /// @param operations The operations of the format.
    /// @param input      The object to read from.
    /// @param type       The predicate type.
    ///
    /// @return The matching filter.
    ///
    private static <V, PREDICATE extends DataComponentPredicate> ComponentFilter readMatching(Operations<V> operations, V input, DataComponentPredicate.Type<PREDICATE> type) {
        return ComponentFilter.matching(type, FilterCodec.field(operations, input, FilterCodec.VALUE_KEY, value -> VanillaAdapters.ofRegistryDfu(type.codec()).read(operations, value)));
    }
    
    ///
    /// Gets the value at the given index.
    ///
    /// @param values The values.
    /// @param index  The index of the value.
    ///
    /// @return The value.
    ///
    /// @throws NexusCodecException If the index is out of range.
    ///
    private static <VALUE> VALUE byIndex(VALUE[] values, int index) {
        if (index < 0 || index >= values.length) {
            throw CodecErrors.outOfRange(0, values.length - 1, index);
        }
        
        return values[index];
    }
    
    ///
    /// Writes the case index and then the fields of the filter on the network, and an object with the keys of its case otherwise.
    ///
    /// @param operations The operations of the format.
    /// @param value      The filter.
    ///
    /// @return The encoded filter.
    ///
    @Override
    public <V> V write(Operations<V> operations, ComponentFilter value) {
        V object = operations.emptyObject();
        if (operations.isPositional()) {
            //noinspection ResultOfMethodCallIgnored This can only be reached with a positional format.
            operations.ofVarInt(Case.of(value).ordinal());
        }
        
        switch (value) {
            case ComponentFilter.Has has -> FilterCodec.put(operations, object, FilterCodec.HAS_KEY, () -> FilterCodec.writeElement(operations, BuiltInRegistries.DATA_COMPONENT_TYPE, has.getComponent()));
            case ComponentFilter.Exact exact -> {
                FilterCodec.put(operations, object, FilterCodec.EXACT_KEY, () -> FilterCodec.writeElement(operations, BuiltInRegistries.DATA_COMPONENT_TYPE, exact.getValue().type()));
                FilterCodec.put(operations, object, FilterCodec.VALUE_KEY, () -> FilterCodec.writeValue(operations, exact.getValue()));
            }
            case ComponentFilter.Compare compare -> {
                FilterCodec.put(operations, object, FilterCodec.PATH_KEY, () -> FilterCodec.writePath(operations, compare.getPath()));
                FilterCodec.put(operations, object, FilterCodec.IS_KEY, () -> FilterCodec.writeOperator(operations, compare.getComparison().operator()));
                FilterCodec.put(operations, object, FilterCodec.VALUE_KEY, () -> FilterCodec.writeLiteral(operations, compare.getComparison().literal()));
            }
            case ComponentFilter.Matching matching -> {
                FilterCodec.put(operations, object, FilterCodec.MATCHES_KEY, () -> FilterCodec.writeElement(operations, BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE, matching.getType()));
                FilterCodec.put(operations, object, FilterCodec.VALUE_KEY, () -> FilterCodec.writePredicate(operations, matching.getType(), matching.getPredicate()));
            }
            case ComponentFilter.AllOf all -> FilterCodec.put(operations, object, FilterCodec.ALL_KEY, () -> this.children.write(operations, all.getAllFilters()));
            case ComponentFilter.AnyOf any -> FilterCodec.put(operations, object, FilterCodec.ANY_KEY, () -> this.children.write(operations, any.getAllFilters()));
            case ComponentFilter.Not not -> FilterCodec.put(operations, object, FilterCodec.NOT_KEY, () -> this.write(operations, not.getFilter()));
        }
        
        return object;
    }
    
    ///
    /// Reads the case index and then the fields of its case on the network, and the first case whose first key is present otherwise.
    ///
    /// In a strict format, every key that the case doesn't have is refused.
    ///
    /// @param operations The operations of the format.
    /// @param input      The encoded filter.
    ///
    /// @return The filter.
    ///
    /// @throws NexusCodecException If the object has none of the case keys, or a strict format finds a key that the case doesn't have.
    ///
    @Override
    public <V> ComponentFilter read(Operations<V> operations, V input) {
        if (operations.isPositional()) {
            return this.readCase(operations, input, FilterCodec.byIndex(Case.values(), operations.asVarInt(input)));
        }
        
        Set<String> keys = operations.keys(input);
        Case kind = Arrays.stream(Case.values())
                .filter(candidate -> keys.contains(candidate.keys.getFirst()))
                .findFirst()
                .orElseThrow(() -> CodecErrors.mismatch("an object with one of the keys " + FilterCodec.CASE_KEYS, input));
        
        if (!operations.isStrict() || kind.keys.containsAll(keys)) {
            return this.readCase(operations, input, kind);
        }
        
        List<CodecError> unknown = keys.stream()
                .filter(key -> !kind.keys.contains(key))
                .map(CodecErrors::unknownKey)
                .toList();
        
        try {
            //noinspection ResultOfMethodCallIgnored Read only to join its errors with the unknown keys.
            this.readCase(operations, input, kind);
        } catch (NexusCodecException failure) {
            throw new NexusCodecException(Stream.concat(failure.errors().stream(), unknown.stream()).toList());
        }
        
        throw new NexusCodecException(unknown);
    }
    
    ///
    /// Reads the fields of the given case.
    ///
    /// @param operations The operations of the format.
    /// @param input      The encoded filter.
    /// @param kind       The case.
    ///
    /// @return The filter.
    ///
    private <V> ComponentFilter readCase(Operations<V> operations, V input, Case kind) {
        return switch (kind) {
            case HAS -> ComponentFilter.has(FilterCodec.field(operations, input, FilterCodec.HAS_KEY, value -> FilterCodec.readElement(operations, value, BuiltInRegistries.DATA_COMPONENT_TYPE)));
            case EXACT -> FilterCodec.readExact(operations, input, FilterCodec.field(operations, input, FilterCodec.EXACT_KEY, value -> FilterCodec.readPersistent(operations, value)));
            case COMPARE -> FilterCodec.readCompare(operations, input);
            case MATCHING -> FilterCodec.readMatching(operations, input, FilterCodec.field(operations, input, FilterCodec.MATCHES_KEY, value -> FilterCodec.readElement(operations, value, BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE)));
            case ALL -> ComponentFilter.allOf(FilterCodec.field(operations, input, FilterCodec.ALL_KEY, value -> this.children.read(operations, value)));
            case ANY -> ComponentFilter.anyOf(FilterCodec.field(operations, input, FilterCodec.ANY_KEY, value -> this.children.read(operations, value)));
            case NOT -> FilterCodec.field(operations, input, FilterCodec.NOT_KEY, value -> this.read(operations, value)).negate();
        };
    }
    
    ///
    /// A case of a filter, whose ordinal is its index on the network.
    ///
    @RequiredArgsConstructor
    private enum Case {
        ///
        /// The case of [ComponentFilter.Has].
        ///
        HAS(List.of(FilterCodec.HAS_KEY)),
        
        ///
        /// The case of [ComponentFilter.Exact].
        ///
        EXACT(List.of(FilterCodec.EXACT_KEY, FilterCodec.VALUE_KEY)),
        
        ///
        /// The case of [ComponentFilter.Compare].
        ///
        COMPARE(List.of(FilterCodec.PATH_KEY, FilterCodec.IS_KEY, FilterCodec.VALUE_KEY)),
        
        ///
        /// The case of [ComponentFilter.Matching].
        ///
        MATCHING(List.of(FilterCodec.MATCHES_KEY, FilterCodec.VALUE_KEY)),
        
        ///
        /// The case of [ComponentFilter.AllOf].
        ///
        ALL(List.of(FilterCodec.ALL_KEY)),
        
        ///
        /// The case of [ComponentFilter.AnyOf].
        ///
        ANY(List.of(FilterCodec.ANY_KEY)),
        
        ///
        /// The case of [ComponentFilter.Not].
        ///
        NOT(List.of(FilterCodec.NOT_KEY));
        
        ///
        /// The keys of the case in JSON and NBT, with the key that selects the case first.
        ///
        private final List<String> keys;
        
        ///
        /// Gets the case of the given filter.
        ///
        /// @param filter The filter.
        ///
        /// @return The case.
        ///
        private static Case of(ComponentFilter filter) {
            return switch (filter) {
                case ComponentFilter.Has _ -> Case.HAS;
                case ComponentFilter.Exact _ -> Case.EXACT;
                case ComponentFilter.Compare _ -> Case.COMPARE;
                case ComponentFilter.Matching _ -> Case.MATCHING;
                case ComponentFilter.AllOf _ -> Case.ALL;
                case ComponentFilter.AnyOf _ -> Case.ANY;
                case ComponentFilter.Not _ -> Case.NOT;
            };
        }
    }
}
