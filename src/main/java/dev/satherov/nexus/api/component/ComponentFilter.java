package dev.satherov.nexus.api.component;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.internal.codec.CodecErrors;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.format.Operations;
import dev.satherov.nexus.internal.component.ComponentCodecs;
import dev.satherov.nexus.internal.component.PathReader;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.predicates.DataComponentPredicate;
import net.minecraft.core.registries.BuiltInRegistries;

import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

///
/// A filter that tests the data components of a holder, such as an item stack, a fluid stack, or a component map.
///
/// @see ComponentPath
///
public sealed interface ComponentFilter extends Predicate<DataComponentGetter> permits ComponentFilter.Has, ComponentFilter.Exact, ComponentFilter.Comparison, ComponentFilter.Matching, ComponentFilter.AllOf, ComponentFilter.AnyOf, ComponentFilter.Not {
    
    ///
    /// The codec of a filter, with one key per case:
    /// - `{"has": "mymod:tier"}`
    /// - `{"exact": "mymod:tier", "value": {...}}`
    /// - `{"path": "mymod:tier.level", "is": ">=", "value": 3}`
    /// - `{"matches": "minecraft:enchantments", "value": {...}}`
    /// - `{"all": [...]}`
    /// - `{"any": [...]}`
    /// - `{"not": {...}}`
    ///
    /// Decoding fails at the key of an unknown component, an unknown operator, a malformed path, or a literal that is not a number, a string, or a boolean.
    /// An exact on a transient component can neither be decoded nor encoded and fails at its key.
    /// A strict format refuses every other key.
    ///
    /// On the network, the case is written as its index and a component or predicate type as its identifier.
    ///
    NexusCodec<ComponentFilter, Access.Registries> CODEC = ComponentFilter.codec();

    ///
    /// The all-of without filters, which passes every holder.
    ///
    ComponentFilter.AllOf ANY = new ComponentFilter.AllOf(List.of());

    ///
    /// Creates a filter that passes every holder with the given component, an item's default included.
    ///
    /// @param type The component the holder must have.
    ///
    /// @return The filter.
    ///
    static ComponentFilter.Has has(DataComponentType<?> type) {
        return new ComponentFilter.Has(type);
    }

    ///
    /// Creates a filter that passes every holder whose component equals the given value.
    ///
    /// A holder without the component fails.
    ///
    /// @param type  The component.
    /// @param value The value the component of the holder must equal.
    ///
    /// @return The filter.
    ///
    static <VALUE> ComponentFilter.Exact<VALUE> exact(DataComponentType<VALUE> type, VALUE value) {
        return new ComponentFilter.Exact<>(type, value);
    }

    ///
    /// Creates a filter that passes a holder if the given vanilla predicate does.
    ///
    /// A holder without the component fails.
    ///
    /// @param type      The vanilla predicate type.
    /// @param predicate The predicate the holder must pass.
    ///
    /// @return The filter.
    ///
    static <PREDICATE extends DataComponentPredicate> ComponentFilter.Matching<PREDICATE> matching(DataComponentPredicate.Type<PREDICATE> type, PREDICATE predicate) {
        return new ComponentFilter.Matching<>(type, predicate);
    }

    ///
    /// Creates a filter that passes a holder if every given filter does.
    ///
    /// If no filter is given, the filter will equal [#ANY].
    ///
    /// @param filters The filters that must all pass.
    ///
    /// @return The filter.
    ///
    static ComponentFilter.AllOf allOf(ComponentFilter... filters) {
        return new ComponentFilter.AllOf(List.of(filters));
    }

    ///
    /// Creates a filter that passes a holder if at least one of the given filters does.
    ///
    /// If no filter is given, the filter will fail every holder.
    ///
    /// @param filters The filters of which one must pass.
    ///
    /// @return The filter.
    ///
    static ComponentFilter.AnyOf anyOf(ComponentFilter... filters) {
        return new ComponentFilter.AnyOf(List.of(filters));
    }

    ///
    /// Creates the codec behind [#CODEC].
    ///
    private static NexusCodec<ComponentFilter, Access.Registries> codec() {
        return new Traversal<>("componentFilter") {

            ///
            /// The keys of each case by its index, starting with the key that picks the case.
            ///
            private final List<List<String>> cases = List.of(
                    List.of("has"),
                    List.of("exact", "value"),
                    List.of("path", "is", "value"),
                    List.of("matches", "value"),
                    List.of("all"),
                    List.of("any"),
                    List.of("not")
            );
            
            ///
            /// The codec of a component type, written as its identifier.
            ///
            private final Traversal<DataComponentType<?>, Access.Plain> component = ComponentFilter.registered(BuiltInRegistries.DATA_COMPONENT_TYPE, "component");

            ///
            /// The codec of a persistent component type, written as its identifier.
            ///
            private final Traversal<DataComponentType<?>, Access.Plain> persistent = (Traversal<DataComponentType<?>, Access.Plain>) this.component.validate(
                    type -> type.isTransient() ? "Expected a persistent component, found " + CodecErrors.describe(type.toString()) : null
            );
            
            ///
            /// The codec of a vanilla predicate type, written as its identifier.
            ///
            private final Traversal<DataComponentPredicate.Type<?>, Access.Plain> predicate = ComponentFilter.registered(BuiltInRegistries.DATA_COMPONENT_PREDICATE_TYPE, "component predicate");

            ///
            /// The codec of an operator, written as its symbol.
            ///
            private final Traversal<Operator, Access.Plain> operator = (Traversal<Operator, Access.Plain>) NexusCodec.STRING.flatXmap(ComponentFilter::operatorOf, Operator::symbol);

            ///
            /// The codec of a path, written as its string form.
            ///
            private final Traversal<ComponentPath, Access.Plain> path = (Traversal<ComponentPath, Access.Plain>) ComponentPath.CODEC;
            
            ///
            /// The codec of a literal.
            ///
            private final Traversal<Literal, Access.Plain> literal = ComponentFilter.literal();
            
            ///
            /// The codec of the filters of an all-of or an any-of.
            ///
            private final Traversal<List<ComponentFilter>, Access.Registries> filters = (Traversal<List<ComponentFilter>, Access.Registries>) this.list();
            
            ///
            /// Writes the index of the case on the network and then the values of the case under their keys.
            ///
            @Override
            public <V> V write(Operations<V> operations, ComponentFilter value) {
                V object = operations.emptyObject();
                if (operations.isPositional()) {
                    //noinspection ResultOfMethodCallIgnored This can only be reached with a positional format
                    operations.ofVarInt(ComponentFilter.caseOf(value));
                }
                
                switch (value) {
                    case ComponentFilter.Has(DataComponentType<?> type) -> ComponentFilter.writeField(operations, object, "has", this.component, type);
                    case ComponentFilter.Exact<?> exact -> this.writeExact(operations, object, exact);
                    case ComponentFilter.Comparison(ComponentPath path, Operator operator, Literal literal) -> {
                        ComponentFilter.writeField(operations, object, "path", this.path, path);
                        ComponentFilter.writeField(operations, object, "is", this.operator, operator);
                        ComponentFilter.writeField(operations, object, "value", this.literal, literal);
                    }
                    case ComponentFilter.Matching<?> matching -> this.writeMatching(operations, object, matching);
                    case ComponentFilter.AllOf(List<ComponentFilter> filters) -> ComponentFilter.writeField(operations, object, "all", this.filters, filters);
                    case ComponentFilter.AnyOf(List<ComponentFilter> filters) -> ComponentFilter.writeField(operations, object, "any", this.filters, filters);
                    case ComponentFilter.Not(ComponentFilter filter) -> ComponentFilter.writeField(operations, object, "not", this, filter);
                }
                
                return object;
            }
            
            ///
            /// Writes the component of the given exact and then its value with the codec of the component.
            ///
            private <V, VALUE> void writeExact(Operations<V> operations, V object, ComponentFilter.Exact<VALUE> exact) {
                ComponentFilter.writeField(operations, object, "exact", this.persistent, exact.type());
                ComponentFilter.writeField(operations, object, "value", ComponentFilter.valueCodec(exact.type()), exact.value());
            }
            
            ///
            /// Writes the predicate type of the given matching and then its predicate with the codec of the type.
            ///
            private <V, PREDICATE extends DataComponentPredicate> void writeMatching(Operations<V> operations, V object, ComponentFilter.Matching<PREDICATE> matching) {
                ComponentFilter.writeField(operations, object, "matches", this.predicate, matching.type());
                ComponentFilter.writeField(operations, object, "value", ComponentFilter.predicateCodec(matching.type()), matching.predicate());
            }
            
            ///
            /// Picks the case by its index on the network or by the key of the object otherwise and then reads its values.
            ///
            @Override
            public <V> ComponentFilter read(Operations<V> operations, V input) {
                int index = operations.isPositional() ? operations.asVarInt(input) : this.caseOf(operations, input);
                return switch (index) {
                    case 0 -> new ComponentFilter.Has(ComponentFilter.readField(operations, input, "has", this.component));
                    case 1 -> this.readExact(operations, input, ComponentFilter.readField(operations, input, "exact", this.persistent));
                    case 2 -> this.readComparison(operations, input);
                    case 3 -> this.readMatching(operations, input, ComponentFilter.readField(operations, input, "matches", this.predicate));
                    case 4 -> new ComponentFilter.AllOf(ComponentFilter.readField(operations, input, "all", this.filters));
                    case 5 -> new ComponentFilter.AnyOf(ComponentFilter.readField(operations, input, "any", this.filters));
                    case 6 -> new ComponentFilter.Not(ComponentFilter.readField(operations, input, "not", this));
                    default -> throw CodecErrors.outOfRange(0, this.cases.size() - 1, index);
                };
            }
            
            ///
            /// Gets the index of the case whose key the given object has and refuses every other key on a strict format.
            ///
            private <V> int caseOf(Operations<V> operations, V object) {
                List<String> keys = this.cases.stream()
                        .filter(candidate -> operations.get(object, candidate.getFirst()) != null)
                        .findFirst()
                        .orElseThrow(() -> new NexusCodecException("Expected one of the keys " + this.cases.stream().map(List::getFirst).toList() + ", found none"));
                
                if (operations.isStrict()) {
                    List<CodecError> unknown = operations.keys(object)
                            .stream()
                            .filter(key -> !keys.contains(key))
                            .map(key -> new CodecError(key, "Unknown key"))
                            .toList();
                    if (!unknown.isEmpty()) {
                        throw new NexusCodecException(unknown);
                    }
                }
                
                return this.cases.indexOf(keys);
            }
            
            ///
            /// Reads the value of an exact with the codec of the given component.
            ///
            private <V, VALUE> ComponentFilter.Exact<VALUE> readExact(Operations<V> operations, V input, DataComponentType<VALUE> type) {
                return new ComponentFilter.Exact<>(type, ComponentFilter.readField(operations, input, "value", ComponentFilter.valueCodec(type)));
            }
            
            ///
            /// Reads the path, the operator, and the literal of a comparison, and fails at the operator if the comparison refuses them.
            ///
            private <V> ComponentFilter.Comparison readComparison(Operations<V> operations, V input) {
                ComponentPath path = ComponentFilter.readField(operations, input, "path", this.path);
                Operator operator = ComponentFilter.readField(operations, input, "is", this.operator);
                Literal literal = ComponentFilter.readField(operations, input, "value", this.literal);
                try {
                    return new ComponentFilter.Comparison(path, operator, literal);
                } catch (IllegalArgumentException failure) {
                    throw CodecErrors.prefixKey(new NexusCodecException(failure.getMessage()), "is");
                }
            }
            
            ///
            /// Reads the predicate of a matching with the codec of the given predicate type.
            ///
            private <V, PREDICATE extends DataComponentPredicate> ComponentFilter.Matching<PREDICATE> readMatching(
                    Operations<V> operations,
                    V input,
                    DataComponentPredicate.Type<PREDICATE> type
            ) {
                return new ComponentFilter.Matching<>(type, ComponentFilter.readField(operations, input, "value", ComponentFilter.predicateCodec(type)));
            }
        };
    }
    
    ///
    /// Gets the index of the case of the given filter on the network.
    ///
    private static int caseOf(ComponentFilter filter) {
        return switch (filter) {
            case ComponentFilter.Has _ -> 0;
            case ComponentFilter.Exact<?> _ -> 1;
            case ComponentFilter.Comparison _ -> 2;
            case ComponentFilter.Matching<?> _ -> 3;
            case ComponentFilter.AllOf _ -> 4;
            case ComponentFilter.AnyOf _ -> 5;
            case ComponentFilter.Not _ -> 6;
        };
    }
    
    ///
    /// Creates the codec of the entries of the given registry as their identifier, with the given kind in the failure message.
    ///
    private static <ENTRY> Traversal<ENTRY, Access.Plain> registered(Registry<ENTRY> registry, String kind) {
        return (Traversal<ENTRY, Access.Plain>) NexusCodec.IDENTIFIER.flatXmap(
                id -> registry.getOptional(id).orElseThrow(() -> CodecErrors.mismatch("a registered " + kind, id.toString())),
                entry -> registry.getResourceKey(entry).orElseThrow(() -> new NexusCodecException("Expected a registered " + kind + ", found an unregistered one")).identifier()
        );
    }
    
    ///
    /// Gets the operator with the given symbol, or throws with every symbol if there is none.
    ///
    private static Operator operatorOf(String symbol) {
        return Arrays.stream(Operator.values())
                .filter(operator -> operator.symbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> CodecErrors.unknownName(Arrays.stream(Operator.values()).map(Operator::symbol).toList(), symbol));
    }
    
    ///
    /// Creates the codec of a literal.
    ///
    private static Traversal<Literal, Access.Plain> literal() {
        return new Traversal<>("literal") {
            
            ///
            /// The codec of the string of a text literal.
            ///
            private final Traversal<String, Access.Plain> text = (Traversal<String, Access.Plain>) NexusCodec.STRING;
            
            ///
            /// Writes the index of the kind on the network and then the value of the literal.
            ///
            @Override
            public <V> V write(Operations<V> operations, Literal value) {
                if (operations.isPositional()) {
                    //noinspection ResultOfMethodCallIgnored This can only be reached with a positional format
                    operations.ofVarInt(switch (value) {
                        case Literal.Numeric _ -> 0;
                        case Literal.Text _ -> 1;
                        case Literal.Flag _ -> 2;
                    });
                }
                
                return switch (value) {
                    case Literal.Numeric(double number) when !operations.isPositional() && number == (int) number -> operations.ofInt((int) number);
                    case Literal.Numeric(double number) -> operations.ofDouble(number);
                    case Literal.Text(String string) -> this.text.write(operations, string);
                    case Literal.Flag(boolean flag) -> operations.ofBoolean(flag);
                };
            }
            
            ///
            /// Reads the index of the kind and then the value on the network and otherwise tries a boolean, a number, and then a string.
            ///
            @Override
            public <V> Literal read(Operations<V> operations, V input) {
                if (operations.isPositional()) {
                    int kind = operations.asVarInt(input);
                    return switch (kind) {
                        case 0 -> new Literal.Numeric(operations.asDouble(input));
                        case 1 -> new Literal.Text(this.text.read(operations, input));
                        case 2 -> new Literal.Flag(operations.asBoolean(input));
                        default -> throw CodecErrors.outOfRange(0, 2, kind);
                    };
                }
                
                // NBT has no booleans, so only the values a boolean is written as are read as one.
                if (input.equals(operations.ofBoolean(true)) || input.equals(operations.ofBoolean(false))) {
                    return new Literal.Flag(operations.asBoolean(input));
                }
                
                try {
                    return new Literal.Numeric(operations.asDouble(input));
                } catch (NexusCodecException _) {
                    try {
                        return new Literal.Text(this.text.read(operations, input));
                    } catch (NexusCodecException _) {
                        throw CodecErrors.mismatch("a number, a string, or a boolean", input);
                    }
                }
            }
        };
    }
    
    ///
    /// Writes the given value with its codec under the given key of the object, or into the buffer on the network.
    ///
    private static <V, T> void writeField(Operations<V> operations, V object, String key, Traversal<T, ?> codec, T value) {
        V encoded;
        try {
            encoded = codec.write(operations, value);
        } catch (NexusCodecException failure) {
            throw CodecErrors.prefixKey(failure, key);
        }
        
        if (!operations.isPositional()) {
            operations.put(object, key, encoded);
        }
    }
    
    ///
    /// Reads the value under the given key of the input with the given codec, or the next value of the buffer on the network.
    ///
    private static <V, T> T readField(Operations<V> operations, V input, String key, Traversal<T, ?> codec) {
        V encoded = operations.isPositional() ? input : operations.get(input, key);
        if (encoded == null) {
            throw new NexusCodecException(List.of(new CodecError(key, "Missing")));
        }
        
        try {
            return codec.read(operations, encoded);
        } catch (NexusCodecException failure) {
            throw CodecErrors.prefixKey(failure, key);
        }
    }
    
    ///
    /// Gets the Nexus codec the given component was built with, or wraps its vanilla codecs if it has none.
    ///
    private static <VALUE> Traversal<VALUE, ?> valueCodec(DataComponentType<VALUE> type) {
        NexusCodec<?, ?> codec = Objects.requireNonNullElseGet(
                ComponentCodecs.of(type),
                () -> NexusCodec.ofVanilla(type.codecOrThrow(), type.streamCodec(), Access.Registries.class)
        );
        
        //noinspection unchecked A component built through Nexus has the codec of its own values.
        return (Traversal<VALUE, ?>) codec;
    }
    
    ///
    /// Gets the codec of the predicates of the given vanilla predicate type.
    ///
    private static <PREDICATE extends DataComponentPredicate> Traversal<PREDICATE, ?> predicateCodec(DataComponentPredicate.Type<PREDICATE> type) {
        return (Traversal<PREDICATE, ?>) NexusCodec.ofDfu(type.codec(), Access.Registries.class);
    }
    
    ///
    /// If the given holder passes this filter.
    ///
    /// Reads only the components this filter refers to and allocates nothing, except that a primitive field of a Nexus struct is boxed.
    ///
    /// @param holder The holder to test.
    ///
    /// @return `true` if the holder passes.
    ///
    @Override
    boolean test(DataComponentGetter holder);
    
    ///
    /// Creates an all-of of this filter and the given filter.
    ///
    /// An all-of operand adds its filters instead of itself.
    ///
    /// @param other The filter that must pass as well.
    ///
    /// @return The all-of.
    ///
    default ComponentFilter.AllOf and(ComponentFilter other) {
        return new ComponentFilter.AllOf(
                Stream.of(this, other)
                        .flatMap(operand -> operand instanceof ComponentFilter.AllOf(List<ComponentFilter> filters) ? filters.stream() : Stream.of(operand))
                        .toList()
        );
    }
    
    ///
    /// Creates an any-of of this filter and the given filter.
    ///
    /// An any-of operand adds its filters instead of itself.
    ///
    /// @param other The filter that may pass instead.
    ///
    /// @return The any-of.
    ///
    default ComponentFilter.AnyOf or(ComponentFilter other) {
        return new ComponentFilter.AnyOf(
                Stream.of(this, other)
                        .flatMap(operand -> operand instanceof ComponentFilter.AnyOf(List<ComponentFilter> filters) ? filters.stream() : Stream.of(operand))
                        .toList()
        );
    }
    
    ///
    /// Creates a not of this filter, or returns its filter if this is a not.
    ///
    /// @return The opposite of this filter.
    ///
    @Override
    default ComponentFilter negate() {
        return new ComponentFilter.Not(this);
    }
    
    ///
    /// A filter that passes a holder with the component.
    ///
    /// @param type The component the holder must have.
    ///
    record Has(DataComponentType<?> type) implements ComponentFilter {
        
        ///
        /// If the holder has the component.
        ///
        @Override
        public boolean test(DataComponentGetter holder) {
            return holder.has(this.type);
        }
    }
    
    ///
    /// A filter that passes a holder whose component equals the value.
    ///
    /// @param type    The component.
    /// @param value   The value the component of the holder must equal.
    /// @param <VALUE> The type of the value of the component.
    ///
    record Exact<VALUE>(DataComponentType<VALUE> type, VALUE value) implements ComponentFilter {
        
        ///
        /// If the component of the holder equals the value.
        ///
        @Override
        public boolean test(DataComponentGetter holder) {
            return this.value.equals(holder.get(this.type));
        }
    }
    
    ///
    /// A filter that compares the value at the path against the literal with the operator.
    ///
    /// @param path     The path to the value that is compared.
    /// @param operator The comparison.
    /// @param literal  The value compared against.
    ///
    record Comparison(ComponentPath path, Operator operator, Literal literal) implements ComponentFilter {

        ///
        /// Creates a new comparison.
        ///
        /// @throws IllegalArgumentException If the operator is ordered and the literal is a boolean or the path is known to read a boolean or a struct.
        ///
        public Comparison {
            PathReader.Kind kind = path.kind();
            if (operator.isOrdered() && (literal instanceof Literal.Flag || kind == PathReader.Kind.FLAG)) {
                throw new IllegalArgumentException("Could not compare '" + path + "' with '" + operator.symbol() + "', a boolean has no order");
            }
            
            if (operator.isOrdered() && kind == PathReader.Kind.OTHER) {
                throw new IllegalArgumentException("Could not compare '" + path + "' with '" + operator.symbol() + "', a struct or a list has no order");
            }
        }
        
        ///
        /// If the value of the holder at the path passes the operator against the literal.
        ///
        @Override
        public boolean test(DataComponentGetter holder) {
            return this.path.test(holder, this.operator, this.literal);
        }
    }
    
    ///
    /// A filter that passes a holder if the vanilla predicate does.
    ///
    /// @param type        The vanilla predicate type.
    /// @param predicate   The predicate the holder must pass.
    /// @param <PREDICATE> The type of the vanilla predicate.
    ///
    record Matching<PREDICATE extends DataComponentPredicate>(DataComponentPredicate.Type<PREDICATE> type, PREDICATE predicate) implements ComponentFilter {

        ///
        /// If the predicate passes the holder.
        ///
        @Override
        public boolean test(DataComponentGetter holder) {
            return this.predicate.matches(holder);
        }
    }
    
    ///
    /// A filter that passes a holder if every filter does.
    ///
    /// @param filters The filters that must all pass.
    ///
    record AllOf(@Unmodifiable List<ComponentFilter> filters) implements ComponentFilter {

        ///
        /// Creates a new all-of with a copy of the given filters.
        ///
        public AllOf {
            filters = List.copyOf(filters);
        }

        ///
        /// If every filter passes the holder.
        ///
        @Override
        public boolean test(DataComponentGetter holder) {
            for (int i = 0; i < this.filters.size(); i++) {
                if (!this.filters.get(i).test(holder)) {
                    return false;
                }
            }
            
            return true;
        }
    }
    
    ///
    /// A filter that passes a holder if at least one filter does.
    ///
    /// @param filters The filters of which one must pass.
    ///
    record AnyOf(@Unmodifiable List<ComponentFilter> filters) implements ComponentFilter {

        ///
        /// Creates a new any-of with a copy of the given filters.
        ///
        public AnyOf {
            filters = List.copyOf(filters);
        }

        ///
        /// If at least one filter passes the holder.
        ///
        @Override
        public boolean test(DataComponentGetter holder) {
            for (int i = 0; i < this.filters.size(); i++) {
                if (this.filters.get(i).test(holder)) {
                    return true;
                }
            }
            
            return false;
        }
    }
    
    ///
    /// A filter that passes a holder if the filter fails it.
    ///
    /// @param filter The filter that must fail.
    ///
    record Not(ComponentFilter filter) implements ComponentFilter {

        ///
        /// If the filter fails the holder.
        ///
        @Override
        public boolean test(DataComponentGetter holder) {
            return !this.filter.test(holder);
        }

        ///
        /// Returns the filter of this not.
        ///
        @Override
        public ComponentFilter negate() {
            return this.filter;
        }
    }
}
