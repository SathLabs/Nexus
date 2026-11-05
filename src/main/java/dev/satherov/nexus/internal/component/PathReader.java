package dev.satherov.nexus.internal.component;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.Literal;
import dev.satherov.nexus.api.component.Operator;
import dev.satherov.nexus.internal.codec.Scalars;
import dev.satherov.nexus.internal.codec.struct.Structs;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

///
/// The resolved form of a [ComponentPath].
///
/// A [StructReader] reads a Nexus struct through its field getters and a [TreeReader] walks the encoded tree of any other value.
///
@ApiStatus.Internal
public sealed interface PathReader permits StructReader, TreeReader {

    ///
    /// Resolves the given steps below the given component into a struct reader for a Nexus struct or a tree reader for any other codec.
    ///
    /// If a field on the path is neither a struct nor a scalar, the whole path will be read as a tree.
    ///
    /// @param type  The component the path starts at.
    /// @param steps The keys and indices down to the value.
    ///
    /// @return The reader of the value at the path.
    ///
    /// @throws IllegalArgumentException If a struct on the path doesn't have the key or index of a step, or if the path continues below a scalar field.
    ///
    static PathReader resolve(DataComponentType<?> type, List<Step> steps) {
        if (!(ComponentCodecs.of(type) instanceof Structs.Struct<?, ?> root)) {
            return new TreeReader(type, steps);
        }
        
        List<Function<Object, Object>> getters = new ArrayList<>();
        NexusCodec<?, ?> codec = root;
        for (Step step : steps) {
            if (!(codec instanceof Structs.Struct<?, ?> struct)) {
                if (PathReader.kindOf(codec) != Kind.UNKNOWN) {
                    throw new IllegalArgumentException("The component '" + type + "' has a scalar where the path continues");
                }
                
                return new TreeReader(type, steps);
            }
            
            if (!(step instanceof Step.Key(String name))) {
                throw new IllegalArgumentException("The component '" + type + "' has a struct where the path has an index");
            }
            
            NexusCodec<?, ?> field = PathReader.field(struct, name, getters);
            if (field == null) {
                if (!struct.keys().contains(name)) {
                    throw new IllegalArgumentException("The component '" + type + "' has no key '" + name + "'");
                }
                
                // The key belongs to a dispatch inlined into the struct, whose fields depend on the value.
                return new TreeReader(type, steps);
            }
            
            codec = field;
        }
        
        Kind kind = PathReader.kindOf(codec);
        return kind == Kind.UNKNOWN ? new TreeReader(type, steps) : new StructReader(type, List.copyOf(getters), kind);
    }
    
    ///
    /// Gets the kind of the given codec, [Kind#OTHER] for a struct and [Kind#UNKNOWN] for anything the struct reader can't read.
    ///
    private static Kind kindOf(NexusCodec<?, ?> codec) {
        if (codec instanceof Structs.Struct<?, ?>) {
            return Kind.OTHER;
        }
        
        if (codec == Scalars.BOOL) {
            return Kind.FLAG;
        }
        
        if (codec == Scalars.STRING) {
            return Kind.TEXT;
        }
        
        Set<NexusCodec<?, ?>> numbers = Set.of(
                Scalars.BYTE,
                Scalars.SHORT,
                Scalars.INT,
                Scalars.VAR_INT,
                Scalars.LONG,
                Scalars.VAR_LONG,
                Scalars.FLOAT,
                Scalars.DOUBLE
        );
        
        return numbers.contains(codec) ? Kind.NUMBER : Kind.UNKNOWN;
    }
    
    ///
    /// Gets the codec of the field with the given key in the given struct or an inlined one, adding the getters down to it.
    ///
    private static @Nullable NexusCodec<?, ?> field(Structs.Struct<?, ?> struct, String name, List<Function<Object, Object>> getters) {
        for (Structs.BoundField<?, ?, ?> field : struct.fields()) {
            if (field.inlined() == null && field.name().equals(name)) {
                getters.add(PathReader.getter(field));
                return field.codec();
            }
            
            if (field.inlined() instanceof Structs.Struct<?, ?> nested && nested.keys().contains(name)) {
                getters.add(PathReader.getter(field));
                return PathReader.field(nested, name, getters);
            }
        }
        
        return null;
    }
    
    ///
    /// The getter of the given field, over any value.
    ///
    private static Function<Object, Object> getter(Structs.BoundField<?, ?, ?> field) {
        //noinspection unchecked A getter is only ever applied to the value of the struct that holds its field.
        return (Function<Object, Object>) field.getter();
    }
    
    ///
    /// If a value with the given order against a literal passes the given operator.
    ///
    /// @param operator The comparison of the value against the literal.
    /// @param order    The order of the value against the literal, in the sign convention of [Comparable#compareTo(Object)].
    ///
    /// @return `true` if the value passes the operator.
    ///
    static boolean passes(Operator operator, int order) {
        return switch (operator) {
            case EQUAL -> order == 0;
            case NOT_EQUAL -> order != 0;
            case LESS -> order < 0;
            case AT_MOST -> order <= 0;
            case GREATER -> order > 0;
            case AT_LEAST -> order >= 0;
        };
    }
    
    ///
    /// The kind the path reads, or [Kind#UNKNOWN] if it is only seen once a value is read.
    ///
    /// @return The kind the path reads.
    ///
    Kind kind();
    
    ///
    /// Compares the value of the given holder at the path against the given literal with the given operator.
    ///
    /// An absent component, a missing key, or a value of another kind fails.
    ///
    /// @param holder   The holder of the component.
    /// @param operator The comparison of the value against the literal.
    /// @param literal  The literal to compare the value against.
    ///
    /// @return `true` if the value at the path passes the operator against the literal.
    ///
    boolean compare(DataComponentGetter holder, Operator operator, Literal literal);
    
    ///
    /// The kind of value a path reads.
    ///
    enum Kind {
        ///
        /// A number of any type.
        ///
        NUMBER,
        
        ///
        /// A string.
        ///
        TEXT,
        
        ///
        /// A boolean.
        ///
        FLAG,
        
        ///
        /// A struct, which passes no comparison.
        ///
        OTHER,
        
        ///
        /// A value whose kind is only seen once it is read.
        ///
        UNKNOWN
    }
    
    ///
    /// One step of a path, a key or an index.
    ///
    sealed interface Step permits Step.Key, Step.Index {

        ///
        /// A key of a struct or a map.
        ///
        /// @param name The key, dotted in the string form if it is a plain identifier and quoted otherwise.
        ///
        record Key(String name) implements Step { }

        ///
        /// An index of a list.
        ///
        /// @param index The index, starting at `0`.
        ///
        record Index(int index) implements Step { }
    }
}
