package dev.satherov.nexus.internal.codec;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.internal.codec.format.Operations;
import dev.satherov.nexus.internal.codec.struct.Structs;

import net.minecraft.resources.Identifier;

import com.google.common.base.Suppliers;
import com.google.common.collect.Comparators;
import com.mojang.datafixers.util.Either;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

///
/// Utility for the codecs built on other codecs, which are either, one or many, the short form, dispatch, recursion, and the mappings.
///
@UtilityClass
@ApiStatus.Internal
public class Combinators {
    
    ///
    /// Creates the codec behind [NexusCodec#either(NexusCodec, NexusCodec)].
    ///
    /// @param left  The codec of the left side.
    /// @param right The codec of the right side.
    ///
    /// @return The codec of the value of either side.
    ///
    public static <L, R, A extends Access.Plain> Traversal<Either<L, R>, A> either(NexusCodec<L, ? super A> left, NexusCodec<R, ? super A> right) {
        Traversal<L, ? super A> first = (Traversal<L, ? super A>) left;
        Traversal<R, ? super A> second = (Traversal<R, ? super A>) right;
        return new Traversal<>("either") {
            
            ///
            /// Writes the side held with its codec, after a boolean that is `true` for the left side on the network.
            ///
            @Override
            public <V> V write(Operations<V> operations, Either<L, R> value) {
                if (operations.isPositional()) {
                    //noinspection ResultOfMethodCallIgnored This can only be reached with a positional format
                    operations.ofBoolean(value.left().isPresent());
                }
                
                return value.map(held -> first.write(operations, held), held -> second.write(operations, held));
            }
            
            ///
            /// Reads the side its boolean picks on the network and tries the left codec and then the right otherwise.
            ///
            @Override
            public <V> Either<L, R> read(Operations<V> operations, V input) {
                if (operations.isPositional()) {
                    return operations.asBoolean(input) ?
                            Either.left(first.read(operations, input)) :
                            Either.right(second.read(operations, input));
                }
                
                return Combinators.readEither(operations, input, first, second);
            }
            
            ///
            /// Reads the side that the value holds with the reader of its codec.
            ///
            /// If neither codec can hold the steps, the failure of the left codec is thrown and the failure of the right one is added to it as suppressed.
            ///
            @Override
            public PathReader<Either<L, R>> reader(List<ComponentPath.Step> steps, int from) {
                List<IllegalArgumentException> failures = new ArrayList<>();
                PathReader<L> left = Combinators.branchReader(first, steps, from, failures);
                PathReader<R> right = Combinators.branchReader(second, steps, from, failures);
                if (left == null && right == null) {
                    throw Combinators.refused(failures);
                }
                
                if (left instanceof Traversal.EncodedReader<L> && right instanceof Traversal.EncodedReader<R>) {
                    return super.reader(steps, from);
                }
                
                return new EitherReader<>(left, right);
            }
        };
    }
    
    ///
    /// Creates the codec behind [NexusCodec#oneOrMany()].
    ///
    /// @param codec The codec of the elements.
    ///
    /// @return The codec of the list.
    ///
    public static <T, A extends Access.Plain> Traversal<List<T>, A> oneOrMany(Traversal<T, ? super A> codec) {
        Traversal<List<T>, A> list = CollectionCodecs.list(codec, CollectionCodecs.LIMIT);
        return new Traversal<>("oneOrMany") {
            
            ///
            /// Writes a single element bare and any other number of elements as a list, which it always does on the network.
            ///
            @Override
            public <V> V write(Operations<V> operations, List<T> value) {
                if (operations.isPositional() || value.size() != 1) {
                    return list.write(operations, value);
                }
                
                return codec.write(operations, value.getFirst());
            }
            
            ///
            /// Reads a list on the network, and tries a list and then a bare element otherwise.
            ///
            @Override
            public <V> List<T> read(Operations<V> operations, V input) {
                if (operations.isPositional()) {
                    return list.read(operations, input);
                }
                
                return Combinators.readEither(operations, input, list, codec).map(Function.identity(), List::of);
            }
        };
    }
    
    ///
    /// Reads the given input with the first codec, or with the second if the first fails, and throws the errors of both if neither can read it.
    ///
    private static <F, S, V> Either<F, S> readEither(Operations<V> operations, V input, Traversal<F, ?> first, Traversal<S, ?> second) {
        try {
            return Either.left(first.read(operations, input));
        } catch (NexusCodecException firstFailure) {
            try {
                return Either.right(second.read(operations, input));
            } catch (NexusCodecException secondFailure) {
                throw Combinators.joined(firstFailure.errors(), secondFailure.errors());
            }
        }
    }
    
    ///
    /// Creates a failure with the first errors and then the second.
    ///
    private static NexusCodecException joined(List<CodecError> first, List<CodecError> second) {
        return new NexusCodecException(Stream.concat(first.stream(), second.stream()).toList());
    }
    
    ///
    /// Creates the reader of the given branch, or returns `null` and adds the failure to the given list if the branch can't hold the steps.
    ///
    private static <T> @Nullable PathReader<T> branchReader(Traversal<T, ?> branch, List<ComponentPath.Step> steps, int from, List<IllegalArgumentException> failures) {
        try {
            return branch.reader(steps, from);
        } catch (IllegalArgumentException failure) {
            failures.add(failure);
            return null;
        }
    }
    
    ///
    /// Gets the first of the given failures of the branches, with the others added as suppressed.
    ///
    private static IllegalArgumentException refused(List<IllegalArgumentException> failures) {
        IllegalArgumentException first = failures.getFirst();
        failures.subList(1, failures.size()).forEach(first::addSuppressed);
        return first;
    }
    
    ///
    /// Creates the codec behind [StructCodec#orShort(NexusCodec, Function, Function)], which is the `either` of the short form and the struct.
    ///
    /// @param struct    The codec of the struct.
    /// @param shortForm The codec of the short form.
    /// @param fromShort The mapping from the short form to a struct.
    /// @param toShort   The mapping from a struct to its short form, which is empty if the struct has none.
    ///
    /// @return The codec of the struct or its short form.
    ///
    public static <T, S, A extends Access.Plain> Traversal<T, A> orShort(
            Traversal<T, ? super A> struct,
            NexusCodec<S, ? super A> shortForm,
            Function<? super S, ? extends T> fromShort,
            Function<? super T, Optional<S>> toShort
    ) {
        return Combinators.map(
                "orShort",
                Combinators.either(shortForm, struct),
                either -> either.map(fromShort, Function.identity()),
                value -> toShort.apply(value).<Either<S, T>>map(Either::left).orElseGet(() -> Either.right(value))
        );
    }
    
    ///
    /// Creates the codec behind [NexusCodec#dispatch(String, Function, Map)].
    ///
    /// @param key      The key of the identifier in JSON and NBT.
    /// @param keyOf    The getter of the identifier of the subtype of a value.
    /// @param subtypes The codec of every subtype, by its identifier.
    ///
    /// @return The codec of the struct.
    ///
    public static <T, A extends Access.Plain> Structs.Inlinable<T, A> dispatch(
            String key,
            Function<? super T, Identifier> keyOf,
            Map<Identifier, ? extends StructCodec<? extends T, ? super A>> subtypes
    ) {
        return new Dispatch<>(key, keyOf, subtypes);
    }
    
    ///
    /// Creates the codec behind [NexusCodec#recursive(String, Function)].
    ///
    /// @param name       The name of the codec, used in the failure message.
    /// @param definition The definition of the codec, called with the codec itself.
    ///
    /// @return The codec.
    ///
    public static <T, A extends Access.Plain> Traversal<T, A> recursive(String name, Function<NexusCodec<T, A>, NexusCodec<T, A>> definition) {
        return new Traversal<>(name) {
            
            ///
            /// The codec the definition returns, created the first time this codec is used.
            ///
            private final Supplier<Traversal<T, A>> defined = Suppliers.memoize(() -> (Traversal<T, A>) definition.apply(this));
            
            ///
            /// The indexes of the steps that a reader of this codec is being created for on the current thread.
            ///
            private final ThreadLocal<BitSet> building = ThreadLocal.withInitial(BitSet::new);
            
            ///
            /// Writes the value with the codec the definition returns.
            ///
            @Override
            public <V> V write(Operations<V> operations, T value) {
                return this.defined.get().write(operations, value);
            }
            
            ///
            /// Reads a value with the codec the definition returns.
            ///
            @Override
            public <V> T read(Operations<V> operations, V input) {
                return this.defined.get().read(operations, input);
            }
            
            ///
            /// Creates the reader of the codec that the definition returns.
            ///
            /// If the definition reaches this codec again at the same step, that inner reader reads by encoding instead.
            ///
            @Override
            public PathReader<T> reader(List<ComponentPath.Step> steps, int from) {
                BitSet building = this.building.get();
                if (building.get(from)) {
                    return super.reader(steps, from);
                }
                
                building.set(from);
                try {
                    return this.defined.get().reader(steps, from);
                } finally {
                    building.clear(from);
                }
            }
        };
    }
    
    ///
    /// Creates the codec behind [NexusCodec#xmap(Function, Function)].
    ///
    /// @param codec The codec of the values to map.
    /// @param to    The mapping from a value of the codec.
    /// @param from  The mapping to a value of the codec.
    ///
    /// @return The codec of the mapped values.
    ///
    public static <S, T, A extends Access.Plain> Traversal<T, A> xmap(Traversal<S, ? super A> codec, Function<? super S, ? extends T> to, Function<? super T, ? extends S> from) {
        return Combinators.map("xmap", codec, to, from);
    }
    
    ///
    /// Creates the codec behind [NexusCodec#flatXmap(Function, Function)].
    ///
    /// @param codec The codec of the values to map.
    /// @param to    The mapping from a value of the codec, which throws a [NexusCodecException] to refuse it.
    /// @param from  The mapping to a value of the codec, which throws a [NexusCodecException] to refuse it.
    ///
    /// @return The codec of the mapped values.
    ///
    public static <S, T, A extends Access.Plain> Traversal<T, A> flatXmap(Traversal<S, ? super A> codec, Function<? super S, ? extends T> to, Function<? super T, ? extends S> from) {
        return Combinators.map("flatXmap", codec, Combinators.guarded("map", to), Combinators.guarded("map", from));
    }
    
    ///
    /// Creates the codec behind [NexusCodec#validate(Function)].
    ///
    /// @param codec The codec of the values to check.
    /// @param check The check of a value, which returns the error message, or `null` if the value is valid.
    ///
    /// @return The codec of the checked values.
    ///
    public static <T, A extends Access.Plain> Traversal<T, A> validate(Traversal<T, ? super A> codec, Function<? super T, @Nullable String> check) {
        Function<T, T> checked = Combinators.guarded("check", value -> {
            String error = check.apply(value);
            if (error != null) {
                throw new NexusCodecException(error);
            }
            
            return value;
        });
        
        return Combinators.map("validate", codec, checked, checked);
    }
    
    ///
    /// Wraps the given function so that any exception it throws other than a [NexusCodecException] becomes one, with the given verb in its message.
    ///
    private static <F, R> Function<F, R> guarded(String verb, Function<? super F, ? extends R> function) {
        return value -> {
            try {
                return function.apply(value);
            } catch (RuntimeException failure) {
                throw failure instanceof NexusCodecException refused ?
                        refused :
                        new NexusCodecException("Could not " + verb + " the value, " + failure);
            }
        };
    }
    
    ///
    /// Creates a codec with the given name that maps the values of the given codec both ways.
    ///
    private static <S, T, A extends Access.Plain> Traversal<T, A> map(
            String name,
            Traversal<S, ? super A> codec,
            Function<? super S, ? extends T> to,
            Function<? super T, ? extends S> back
    ) {
        return new Traversal<>(name) {
            
            ///
            /// Maps the value back and then writes it with the codec.
            ///
            @Override
            public <V> V write(Operations<V> operations, T value) {
                return codec.write(operations, back.apply(value));
            }
            
            ///
            /// Reads a value with the codec and then maps it.
            ///
            @Override
            public <V> T read(Operations<V> operations, V input) {
                return to.apply(codec.read(operations, input));
            }
            
            ///
            /// Maps the value back and then reads it with the reader of the codec.
            ///
            @Override
            public PathReader<T> reader(List<ComponentPath.Step> steps, int from) {
                PathReader<S> mapped = codec.reader(steps, from);
                return mapped instanceof Traversal.EncodedReader<S> ? super.reader(steps, from) : new MappedReader<>(back, mapped);
            }
        };
    }
    
    ///
    /// A codec of a struct whose fields are those of the subtype that the identifier under its key picks.
    ///
    /// On the network, it writes the identifier and then the fields of the subtype.
    /// In JSON and NBT, it writes the fields of the subtype and then the identifier, which is the order DFU writes them in.
    ///
    /// @param <T> The type of the struct.
    /// @param <A> The access a format has to offer to be used with this codec.
    ///
    private static final class Dispatch<T, A extends Access.Plain> extends Structs.Inlinable<T, A> {
        
        ///
        /// The key of the identifier in JSON and NBT.
        ///
        private final String key;
        
        ///
        /// The getter of the identifier of the subtype of a value.
        ///
        private final Function<? super T, Identifier> keyOf;
        
        ///
        /// The codec of every subtype, by its identifier.
        ///
        private final Map<Identifier, Structs.Inlinable<? extends T, ? super A>> subtypes;
        
        ///
        /// The key and the keys of every subtype.
        ///
        private final Set<String> keys;
        
        ///
        /// Creates the codec of a struct with the given subtypes, picked by the identifier under the given key, and refuses a subtype that has the key.
        ///
        private Dispatch(String key, Function<? super T, Identifier> keyOf, Map<Identifier, ? extends StructCodec<? extends T, ? super A>> subtypes) {
            super("dispatch");
            this.key = key;
            this.keyOf = keyOf;
            
            //noinspection unchecked Every struct codec is inlinable.
            this.subtypes = Map.copyOf((Map<Identifier, Structs.Inlinable<? extends T, ? super A>>) subtypes);
            Set<String> keys = new HashSet<>(Set.of(key));
            this.subtypes.forEach((id, subtype) -> {
                if (subtype.keys().contains(key)) {
                    throw new IllegalArgumentException("Subtype '" + id + "' has the dispatch key '" + key + "'");
                }
                
                keys.addAll(subtype.keys());
            });
            this.keys = Set.copyOf(keys);
        }
        
        ///
        /// The key and the keys of every subtype.
        ///
        @Override
        public @Unmodifiable Set<String> keys() {
            return this.keys;
        }
        
        ///
        /// Writes the identifier and then the fields of the subtype on the network, and the fields and the identifier into a new object otherwise.
        ///
        @Override
        public <V> V write(Operations<V> operations, T value) {
            if (!operations.isPositional()) {
                V object = operations.emptyObject();
                this.writeFields(operations, object, value);
                return object;
            }
            
            Identifier id = this.keyOf.apply(value);
            Structs.Inlinable<T, ? super A> subtype = this.subtype(id);
            Scalars.IDENTIFIER.write(operations, id);
            return subtype.write(operations, value);
        }
        
        ///
        /// Writes the fields of the subtype and then the identifier under the key, and fails at the key if the identifier has no subtype.
        ///
        @Override
        public <V> void writeFields(Operations<V> operations, V object, T value) {
            Identifier id = this.keyOf.apply(value);
            Structs.Inlinable<T, ? super A> subtype;
            V encoded;
            try {
                subtype = this.subtype(id);
                encoded = Scalars.IDENTIFIER.write(operations, id);
            } catch (NexusCodecException failure) {
                throw CodecErrors.prefixKey(failure, this.key);
            }
            
            subtype.writeFields(operations, object, value);
            operations.put(object, this.key, encoded);
        }
        
        ///
        /// Reads the identifier and then the fields of its subtype on the network, and the fields of the subtype from an object otherwise.
        ///
        @Override
        public <V> T read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                return this.subtype(Scalars.IDENTIFIER.read(operations, input)).read(operations, input);
            }
            
            return this.readFields(operations, input, operations.isStrict() ? operations.keys(input) : null);
        }
        
        ///
        /// Reads the identifier under the key, or a field of the subtypes with the reader of the subtype that the value picks.
        ///
        /// A wildcard reads by encoding the struct, and a reader with no steps left never passes.
        /// If no subtype can hold the steps, the first failure is thrown and the others are added to it as suppressed.
        ///
        @Override
        public PathReader<T> reader(List<ComponentPath.Step> steps, int from) {
            if (from == steps.size()) {
                return new WholeReader<>();
            }
            
            if (steps.get(from) instanceof ComponentPath.Step.Wildcard) {
                return super.reader(steps, from);
            }
            
            ComponentPath.Step step = steps.get(from);
            if (!(step instanceof ComponentPath.Step.Key(String name)) || !this.keys.contains(name)) {
                throw CodecErrors.unknownStep(this.keys, step);
            }
            
            if (name.equals(this.key)) {
                return new MappedReader<>(this::subtypeId, Scalars.IDENTIFIER.reader(steps, from + 1));
            }
            
            List<IllegalArgumentException> failures = new ArrayList<>();
            Map<Identifier, PathReader<T>> readers = new HashMap<>();
            this.subtypes.forEach((id, subtype) -> {
                PathReader<? extends T> reader = subtype.keys().contains(name) ? Combinators.branchReader(subtype, steps, from, failures) : null;
                if (reader != null) {
                    //noinspection unchecked A value is only ever read by the subtype that its own identifier picks.
                    readers.put(id, (PathReader<T>) reader);
                }
            });
            
            if (readers.isEmpty()) {
                throw Combinators.refused(failures);
            }
            
            return new DispatchReader<>(this.keyOf, Map.copyOf(readers));
        }
        
        ///
        /// Gets the identifier of the subtype of the given value, or `null` if no subtype has it.
        ///
        private @Nullable Identifier subtypeId(T value) {
            Identifier id = this.keyOf.apply(value);
            return this.subtypes.containsKey(id) ? id : null;
        }
        
        ///
        /// Reads the identifier under the key and then the fields of its subtype.
        ///
        /// If the format is strict, the keys that neither has will be refused.
        ///
        @Override
        public <V> T readFields(Operations<V> operations, V object, @Nullable Set<String> present) {
            V encoded = operations.get(object, this.key);
            if (encoded == null) {
                throw new NexusCodecException(List.of(new CodecError(this.key, "Missing")));
            }
            
            Structs.Inlinable<T, ? super A> subtype;
            try {
                subtype = this.subtype(Scalars.IDENTIFIER.read(operations, encoded));
            } catch (NexusCodecException failure) {
                throw CodecErrors.prefixKey(failure, this.key);
            }
            
            List<CodecError> unknown = operations.isStrict() ?
                    this.unknownKeys(operations, object, present, subtype) :
                    null;
            
            if (unknown == null) {
                return subtype.readFields(operations, object, null);
            }
            
            try {
                //noinspection ResultOfMethodCallIgnored Read only to join its errors with the unknown keys.
                subtype.readFields(operations, object, null);
            } catch (NexusCodecException failure) {
                throw Combinators.joined(failure.errors(), unknown);
            }
            
            throw new NexusCodecException(unknown);
        }
        
        ///
        /// Creates an error for every key of the given object that is neither the key nor a key of the given subtype, or `null` if none exists.
        ///
        /// If this struct is inline, only the keys of the other subtypes count, since its owner checks the rest.
        ///
        private <V> @Nullable List<CodecError> unknownKeys(Operations<V> operations, V object, @Nullable Set<String> present, Structs.Inlinable<?, ?> subtype) {
            List<CodecError> unknown = null;
            for (String name : operations.keys(object)) {
                boolean known = name.equals(this.key) || subtype.keys().contains(name);
                if (!known && (present != null || this.keys.contains(name))) {
                    unknown = Objects.requireNonNullElseGet(unknown, ArrayList::new);
                    unknown.add(new CodecError(name, "Unknown key"));
                }
            }
            
            return unknown;
        }
        
        ///
        /// Gets the codec of the subtype with the given identifier and fails to list the identifiers of all subtypes if there is none.
        ///
        private Structs.Inlinable<T, ? super A> subtype(Identifier id) {
            Structs.Inlinable<? extends T, ? super A> subtype = this.subtypes.get(id);
            if (subtype == null) {
                throw CodecErrors.unknownName(this.subtypes.keySet().stream().map(Identifier::toString).sorted().toList(), id.toString());
            }
            
            //noinspection unchecked A value is only ever written by the subtype that its own identifier picks.
            return (Structs.Inlinable<T, ? super A>) subtype;
        }
    }
    
    ///
    /// A reader of the side that an either holds.
    ///
    /// @param left  The reader of the left side, or `null` if its codec can't hold the steps.
    /// @param right The reader of the right side, or `null` if its codec can't hold the steps.
    /// @param <L>   The type of the left side.
    /// @param <R>   The type of the right side.
    ///
    private record EitherReader<L, R>(@Nullable PathReader<L> left, @Nullable PathReader<R> right) implements PathReader<Either<L, R>> {
        
        ///
        /// The higher of the costs of the sides.
        ///
        @Override
        public PathReader.Cost getCost() {
            PathReader.Cost left = this.left != null ? this.left.getCost() : PathReader.Cost.DIRECT;
            PathReader.Cost right = this.right != null ? this.right.getCost() : PathReader.Cost.DIRECT;
            return Comparators.max(left, right);
        }
        
        ///
        /// If the values of either side have an order.
        ///
        @Override
        public boolean isOrdered() {
            return (this.left != null && this.left.isOrdered()) || (this.right != null && this.right.isOrdered());
        }
        
        ///
        /// Creates the test of the side that the value holds.
        ///
        /// A side without a reader reads as absent.
        ///
        @Override
        public Predicate<Either<L, R>> bind(Comparison comparison) {
            Function<L, Boolean> left = this.left != null ? this.left.bind(comparison)::test : _ -> false;
            Function<R, Boolean> right = this.right != null ? this.right.bind(comparison)::test : _ -> false;
            return value -> value.map(left, right);
        }
    }
    
    ///
    /// A reader of a value mapped by a function.
    ///
    /// @param mapping The function that maps the value.
    /// @param mapped  The reader of the mapped value.
    /// @param <S>     The type of the mapped value.
    /// @param <T>     The type of the value.
    ///
    private record MappedReader<S, T>(Function<? super T, ? extends @Nullable S> mapping, PathReader<S> mapped) implements PathReader<T> {
        
        ///
        /// The cost of the mapped value.
        ///
        @Override
        public PathReader.Cost getCost() {
            return this.mapped.getCost();
        }
        
        ///
        /// If the mapped values have an order.
        ///
        @Override
        public boolean isOrdered() {
            return this.mapped.isOrdered();
        }
        
        ///
        /// Creates the test of the mapped value.
        ///
        /// If the function maps the value to `null` or throws on it, the value reads as absent.
        ///
        @Override
        public Predicate<T> bind(Comparison comparison) {
            Predicate<S> test = this.mapped.bind(comparison);
            return value -> {
                try {
                    S mapped = this.mapping.apply(value);
                    return mapped != null && test.test(mapped);
                } catch (RuntimeException _) {
                    return false;
                }
            };
        }
    }
    
    ///
    /// A reader of a struct with subtypes, which reads the value with the reader of the subtype its identifier picks.
    ///
    /// @param keyOf   The getter of the identifier of the subtype of a value.
    /// @param readers The reader of every subtype that can hold the steps, by its identifier.
    /// @param <T>     The type of the struct.
    ///
    private record DispatchReader<T>(Function<? super T, Identifier> keyOf, Map<Identifier, PathReader<T>> readers) implements PathReader<T> {
        
        ///
        /// The highest of the costs of the subtypes.
        ///
        @Override
        public PathReader.Cost getCost() {
            return this.readers.values().stream().map(PathReader::getCost).reduce(PathReader.Cost.DIRECT, Comparators::max);
        }
        
        ///
        /// If the values of any subtype have an order.
        ///
        @Override
        public boolean isOrdered() {
            return this.readers.values().stream().anyMatch(PathReader::isOrdered);
        }
        
        ///
        /// Creates the test with the reader of the subtype that the value picks.
        ///
        /// If the subtype of the value has no reader or the getter of its identifier throws, the value reads as absent.
        ///
        @Override
        public Predicate<T> bind(Comparison comparison) {
            Map<Identifier, Predicate<T>> tests = new HashMap<>();
            this.readers.forEach((id, reader) -> tests.put(id, reader.bind(comparison)));
            return value -> {
                try {
                    Predicate<T> test = tests.get(this.keyOf.apply(value));
                    return test != null && test.test(value);
                } catch (RuntimeException _) {
                    return false;
                }
            };
        }
    }
    
    ///
    /// A reader of a whole struct with subtypes, which fails every comparison since its compound tag has no value to compare.
    ///
    /// @param <T> The type of the struct with subtypes.
    ///
    private static final class WholeReader<T> implements PathReader<T> {
        
        ///
        /// Will always return [PathReader.Cost#DIRECT].
        ///
        @Override
        public PathReader.Cost getCost() {
            return PathReader.Cost.DIRECT;
        }
        
        ///
        /// Will always return `false`.
        ///
        @Override
        public boolean isOrdered() {
            return false;
        }
        
        ///
        /// Creates the test that always fails.
        ///
        @Override
        public Predicate<T> bind(Comparison comparison) {
            return _ -> false;
        }
    }
}
