package dev.satherov.nexus.internal.codec;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.CodecError;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.NexusCodecException;
import dev.satherov.nexus.api.codec.StructCodec;

import net.minecraft.resources.Identifier;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Either;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
            protected <V> V write(Operations<V> operations, Either<L, R> value) {
                if (operations.isPositional()) {
                    operations.ofBoolean(value.left().isPresent());
                }
                
                return value.map(held -> first.write(operations, held), held -> second.write(operations, held));
            }
            
            ///
            /// Reads the side its boolean picks on the network, and tries the left codec and then the right otherwise.
            ///
            @Override
            protected <V> Either<L, R> read(Operations<V> operations, V input) {
                if (operations.isPositional()) {
                    return operations.asBoolean(input) ? Either.left(first.read(operations, input)) : Either.right(second.read(operations, input));
                }
                
                return Combinators.readEither(operations, input, first, second);
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
            protected <V> V write(Operations<V> operations, List<T> value) {
                if (operations.isPositional() || value.size() != 1) {
                    return list.write(operations, value);
                }
                
                return codec.write(operations, value.getFirst());
            }
            
            ///
            /// Reads a list on the network, and tries a list and then a bare element otherwise.
            ///
            @Override
            protected <V> List<T> read(Operations<V> operations, V input) {
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
    /// Creates the codec behind [StructCodec#orShort(NexusCodec, Function, Function)], which is the either of the short form and the struct.
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
                Combinators.<S, T, A>either(shortForm, struct),
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
            /// Writes the value with the codec the definition returns.
            ///
            @Override
            protected <V> V write(Operations<V> operations, T value) {
                return this.defined.get().write(operations, value);
            }
            
            ///
            /// Reads a value with the codec the definition returns.
            ///
            @Override
            protected <V> T read(Operations<V> operations, V input) {
                return this.defined.get().read(operations, input);
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
            if (error != null) throw new NexusCodecException(error);
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
                throw failure instanceof NexusCodecException refused ? refused : new NexusCodecException("could not " + verb + " the value, " + failure);
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
            Function<? super T, ? extends S> from
    ) {
        return new Traversal<>(name) {
            
            ///
            /// Maps the value back and then writes it with the codec.
            ///
            @Override
            protected <V> V write(Operations<V> operations, T value) {
                return codec.write(operations, from.apply(value));
            }
            
            ///
            /// Reads a value with the codec and then maps it.
            ///
            @Override
            protected <V> T read(Operations<V> operations, V input) {
                return to.apply(codec.read(operations, input));
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
        protected Set<String> keys() {
            return this.keys;
        }
        
        ///
        /// Writes the identifier and then the fields of the subtype on the network, and the fields and the identifier into a new object otherwise.
        ///
        @Override
        protected <V> V write(Operations<V> operations, T value) {
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
        protected <V> void writeFields(Operations<V> operations, V object, T value) {
            Identifier id = this.keyOf.apply(value);
            Structs.Inlinable<T, ? super A> subtype;
            V encoded;
            try {
                subtype = this.subtype(id);
                encoded = Scalars.IDENTIFIER.write(operations, id);
            } catch (NexusCodecException failure) {
                throw Errors.prefixKey(failure, this.key);
            }
            
            subtype.writeFields(operations, object, value);
            operations.put(object, this.key, encoded);
        }
        
        ///
        /// Reads the identifier and then the fields of its subtype on the network, and the fields of the subtype from an object otherwise.
        ///
        @Override
        protected <V> T read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                return this.subtype(Scalars.IDENTIFIER.read(operations, input)).read(operations, input);
            }
            
            return this.readFields(operations, input, operations.isStrict() ? operations.keys(input) : null);
        }
        
        ///
        /// Reads the identifier under the key and then the fields of its subtype, and on a strict format refuses the keys that neither has.
        ///
        @Override
        protected <V> T readFields(Operations<V> operations, V object, @Nullable Set<String> present) {
            V encoded = operations.get(object, this.key);
            if (encoded == null) throw new NexusCodecException(List.of(new CodecError(this.key, "missing")));
            
            Structs.Inlinable<T, ? super A> subtype;
            try {
                subtype = this.subtype(Scalars.IDENTIFIER.read(operations, encoded));
            } catch (NexusCodecException failure) {
                throw Errors.prefixKey(failure, this.key);
            }
            
            List<CodecError> unknown = operations.isStrict() ? this.unknownKeys(operations, object, present, subtype) : null;
            if (unknown == null) {
                return subtype.readFields(operations, object, null);
            }
            
            try {
                subtype.readFields(operations, object, null);
            } catch (NexusCodecException failure) {
                throw Combinators.joined(failure.errors(), unknown);
            }
            
            throw new NexusCodecException(unknown);
        }
        
        ///
        /// Creates an error for every key of the given object that is neither the key nor a key of the given subtype, or `null` if none exists.
        /// If this struct is inline, only the keys of the other subtypes count, since its owner checks the rest.
        ///
        private <V> @Nullable List<CodecError> unknownKeys(Operations<V> operations, V object, @Nullable Set<String> present, Structs.Inlinable<?, ?> subtype) {
            List<CodecError> unknown = null;
            for (String name : operations.keys(object)) {
                boolean known = name.equals(this.key) || subtype.keys().contains(name);
                if (!known && (present != null || this.keys.contains(name))) {
                    unknown = Objects.requireNonNullElseGet(unknown, ArrayList::new);
                    unknown.add(new CodecError(name, "unknown key"));
                }
            }
            
            return unknown;
        }
        
        ///
        /// Gets the codec of the subtype with the given identifier, and fails listing the identifiers of all subtypes if there is none.
        ///
        private Structs.Inlinable<T, ? super A> subtype(Identifier id) {
            Structs.Inlinable<? extends T, ? super A> subtype = this.subtypes.get(id);
            if (subtype == null) {
                throw Errors.unknownName(this.subtypes.keySet().stream().map(Identifier::toString).sorted().toList(), id.toString());
            }
            
            //noinspection unchecked A value is only ever written by the subtype that its own identifier picks.
            return (Structs.Inlinable<T, ? super A>) subtype;
        }
    }
}
