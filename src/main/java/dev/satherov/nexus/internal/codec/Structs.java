package dev.satherov.nexus.internal.codec;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.CodecError;
import dev.satherov.nexus.api.codec.CodecException;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.StructCodec;
import dev.satherov.nexus.api.codec.StructField;

import com.mojang.datafixers.util.Function10;
import com.mojang.datafixers.util.Function11;
import com.mojang.datafixers.util.Function12;
import com.mojang.datafixers.util.Function13;
import com.mojang.datafixers.util.Function14;
import com.mojang.datafixers.util.Function15;
import com.mojang.datafixers.util.Function16;
import com.mojang.datafixers.util.Function3;
import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import com.mojang.datafixers.util.Function7;
import com.mojang.datafixers.util.Function8;
import com.mojang.datafixers.util.Function9;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

///
/// Utility for the struct codecs, with the inlinable struct, the struct over its fields, the bound field record, and the sixteen constructor adapters.
///
@UtilityClass
@ApiStatus.Internal
public class Structs {
    
    ///
    /// Creates the codec of the value of a field that may be empty, over the given codec of a present value.
    ///
    /// On the network, it writes a boolean that is `true` if the value is present and then the value.
    /// In JSON and NBT, it writes the present value and reads `null` as empty.
    ///
    /// Should only ever be given a present value in JSON and NBT.
    ///
    /// @param codec The codec of a present value.
    ///
    /// @return The codec of the value that may be empty.
    ///
    public static <T, A extends Access.Plain> Traversal<Optional<T>, A> optional(Traversal<T, A> codec) {
        return new Traversal<>("optional") {
            
            ///
            /// Writes whether the value is present and then the value on the network, and the present value otherwise.
            ///
            @Override
            protected <V> V write(Operations<V> operations, Optional<T> value) {
                if (!operations.isPositional()) {
                    return codec.write(operations, value.orElseThrow());
                }
                
                V buffer = operations.ofBoolean(value.isPresent());
                return value.isPresent() ? codec.write(operations, value.get()) : buffer;
            }
            
            ///
            /// Reads whether the value is present and then the value on the network, and reads `null` as empty otherwise.
            ///
            @Override
            protected <V> Optional<T> read(Operations<V> operations, V input) {
                if (operations.isPositional()) {
                    return operations.asBoolean(input) ? Optional.of(codec.read(operations, input)) : Optional.empty();
                }
                
                return operations.isNull(input) ? Optional.empty() : Optional.of(codec.read(operations, input));
            }
        };
    }
    
    ///
    /// Adapts the given constructor of a struct of one field to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B> Function<Object[], Z> adapt(Function<B, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply((B) values[0]);
    }
    
    ///
    /// Adapts the given constructor of a struct of two fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C> Function<Object[], Z> adapt(BiFunction<B, C, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply((B) values[0], (C) values[1]);
    }
    
    ///
    /// Adapts the given constructor of a struct of three fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D> Function<Object[], Z> adapt(Function3<B, C, D, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply((B) values[0], (C) values[1], (D) values[2]);
    }
    
    ///
    /// Adapts the given constructor of a struct of four fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E> Function<Object[], Z> adapt(Function4<B, C, D, E, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply((B) values[0], (C) values[1], (D) values[2], (E) values[3]);
    }
    
    ///
    /// Adapts the given constructor of a struct of five fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F> Function<Object[], Z> adapt(Function5<B, C, D, E, F, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply((B) values[0], (C) values[1], (D) values[2], (E) values[3], (F) values[4]);
    }
    
    ///
    /// Adapts the given constructor of a struct of six fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F, G> Function<Object[], Z> adapt(Function6<B, C, D, E, F, G, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply((B) values[0], (C) values[1], (D) values[2], (E) values[3], (F) values[4], (G) values[5]);
    }
    
    ///
    /// Adapts the given constructor of a struct of seven fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F, G, H> Function<Object[], Z> adapt(Function7<B, C, D, E, F, G, H, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply((B) values[0], (C) values[1], (D) values[2], (E) values[3], (F) values[4], (G) values[5], (H) values[6]);
    }
    
    ///
    /// Adapts the given constructor of a struct of eight fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F, G, H, I> Function<Object[], Z> adapt(Function8<B, C, D, E, F, G, H, I, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply((B) values[0], (C) values[1], (D) values[2], (E) values[3], (F) values[4], (G) values[5], (H) values[6], (I) values[7]);
    }
    
    ///
    /// Adapts the given constructor of a struct of nine fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F, G, H, I, J> Function<Object[], Z> adapt(Function9<B, C, D, E, F, G, H, I, J, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply(
                (B) values[0],
                (C) values[1],
                (D) values[2],
                (E) values[3],
                (F) values[4],
                (G) values[5],
                (H) values[6],
                (I) values[7],
                (J) values[8]
        );
    }
    
    ///
    /// Adapts the given constructor of a struct of ten fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F, G, H, I, J, K> Function<Object[], Z> adapt(Function10<B, C, D, E, F, G, H, I, J, K, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply(
                (B) values[0],
                (C) values[1],
                (D) values[2],
                (E) values[3],
                (F) values[4],
                (G) values[5],
                (H) values[6],
                (I) values[7],
                (J) values[8],
                (K) values[9]
        );
    }
    
    ///
    /// Adapts the given constructor of a struct of eleven fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F, G, H, I, J, K, L> Function<Object[], Z> adapt(Function11<B, C, D, E, F, G, H, I, J, K, L, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply(
                (B) values[0],
                (C) values[1],
                (D) values[2],
                (E) values[3],
                (F) values[4],
                (G) values[5],
                (H) values[6],
                (I) values[7],
                (J) values[8],
                (K) values[9],
                (L) values[10]
        );
    }
    
    ///
    /// Adapts the given constructor of a struct of twelve fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F, G, H, I, J, K, L, M> Function<Object[], Z> adapt(Function12<B, C, D, E, F, G, H, I, J, K, L, M, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply(
                (B) values[0],
                (C) values[1],
                (D) values[2],
                (E) values[3],
                (F) values[4],
                (G) values[5],
                (H) values[6],
                (I) values[7],
                (J) values[8],
                (K) values[9],
                (L) values[10],
                (M) values[11]
        );
    }
    
    ///
    /// Adapts the given constructor of a struct of thirteen fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F, G, H, I, J, K, L, M, N> Function<Object[], Z> adapt(Function13<B, C, D, E, F, G, H, I, J, K, L, M, N, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply(
                (B) values[0],
                (C) values[1],
                (D) values[2],
                (E) values[3],
                (F) values[4],
                (G) values[5],
                (H) values[6],
                (I) values[7],
                (J) values[8],
                (K) values[9],
                (L) values[10],
                (M) values[11],
                (N) values[12]
        );
    }
    
    ///
    /// Adapts the given constructor of a struct of fourteen fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F, G, H, I, J, K, L, M, N, O> Function<Object[], Z> adapt(Function14<B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply(
                (B) values[0],
                (C) values[1],
                (D) values[2],
                (E) values[3],
                (F) values[4],
                (G) values[5],
                (H) values[6],
                (I) values[7],
                (J) values[8],
                (K) values[9],
                (L) values[10],
                (M) values[11],
                (N) values[12],
                (O) values[13]
        );
    }
    
    ///
    /// Adapts the given constructor of a struct of fifteen fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P> Function<Object[], Z> adapt(Function15<B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply(
                (B) values[0],
                (C) values[1],
                (D) values[2],
                (E) values[3],
                (F) values[4],
                (G) values[5],
                (H) values[6],
                (I) values[7],
                (J) values[8],
                (K) values[9],
                (L) values[10],
                (M) values[11],
                (N) values[12],
                (O) values[13],
                (P) values[14]
        );
    }
    
    ///
    /// Adapts the given constructor of a struct of sixteen fields to the decoded values of its fields.
    ///
    /// @param constructor The constructor of the struct.
    ///
    /// @return The constructor, called with the decoded value of every field, in order.
    ///
    public static <Z, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q> Function<Object[], Z> adapt(Function16<B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z> constructor) {
        //noinspection unchecked The value at every index was decoded by the field whose type the constructor takes there.
        return values -> constructor.apply(
                (B) values[0],
                (C) values[1],
                (D) values[2],
                (E) values[3],
                (F) values[4],
                (G) values[5],
                (H) values[6],
                (I) values[7],
                (J) values[8],
                (K) values[9],
                (L) values[10],
                (M) values[11],
                (N) values[12],
                (O) values[13],
                (P) values[14],
                (Q) values[15]
        );
    }
    
    ///
    /// A field of a struct, bound to the getter of its value.
    ///
    /// @param codec    The codec of the value.
    /// @param name     The key of the value in JSON and NBT, or empty if this field is inline.
    /// @param getter   The getter of the value from the owner.
    /// @param fallback The value to read if the key is absent, which is left out when written, or `null` if the key is required.
    /// @param inlined  The struct whose keys this field merges into its owner, which is also its codec, or `null` if this field has a key of its own.
    ///
    /// @param <Z> The type of the struct the field belongs to.
    /// @param <T> The type of the value.
    /// @param <A> The access a format has to offer to be used with this field.
    ///
    public record BoundField<Z, T, A extends Access.Plain>(
            Traversal<T, ? super A> codec,
            String name,
            Function<Z, T> getter,
            @Nullable T fallback,
            @Nullable Inlinable<T, ?> inlined
    ) implements StructField<Z, T, A> {
        
        ///
        /// Creates a field with a key of its own.
        ///
        /// @param codec    The codec of the value.
        /// @param name     The key of the value in JSON and NBT.
        /// @param getter   The getter of the value from the owner.
        /// @param fallback The value to read if the key is absent, or `null` if the key is required.
        ///
        public BoundField(Traversal<T, ? super A> codec, String name, Function<Z, T> getter, @Nullable T fallback) {
            this(codec, name, getter, fallback, null);
        }
        
        ///
        /// Writes the value of this field of the given owner into the given object, or appends it to the buffer on the network.
        ///
        private <V> void write(Operations<V> operations, V object, Z owner) {
            T value = this.getter.apply(owner);
            Inlinable<T, ?> nested = this.inlined();
            if (operations.isPositional()) {
                this.codec.write(operations, value);
            } else if (nested != null) {
                nested.writeFields(operations, object, value);
            } else if (!value.equals(this.fallback)) {
                try {
                    operations.put(object, this.name, this.codec.write(operations, value));
                } catch (CodecException failure) {
                    throw Errors.prefixKey(failure, this.name);
                }
            }
        }
        
        ///
        /// Reads the value of this field from the given object, or from the buffer on the network.
        ///
        private <V> T read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                return this.codec.read(operations, input);
            }
            
            Inlinable<T, ?> nested = this.inlined();
            if (nested != null) {
                return nested.readFields(operations, input, null);
            }
            
            V value = operations.get(input, this.name);
            if (value == null) {
                if (this.fallback == null) throw new CodecException(List.of(new CodecError(this.name, "missing")));
                return this.fallback;
            }
            
            try {
                return this.codec.read(operations, value);
            } catch (CodecException failure) {
                throw Errors.prefixKey(failure, this.name);
            }
        }
    }
    
    ///
    /// A codec of a struct whose fields can be merged into the object of the struct that holds it.
    ///
    /// @param <T> The type of the struct.
    /// @param <A> The access a format has to offer to be used with this codec.
    ///
    public abstract static class Inlinable<T, A extends Access.Plain> extends Traversal<T, A> implements StructCodec<T, A> {
        
        ///
        /// Creates the codec of a struct with the given name.
        ///
        /// @param name The name of the struct, used in the failure message.
        ///
        protected Inlinable(String name) {
            super(name);
        }
        
        ///
        /// Creates a field with an empty name that merges this struct into its owner.
        ///
        @Override
        public <Z> StructField<Z, T, A> inline(Function<Z, T> getter) {
            return new BoundField<>(this, "", getter, null, this);
        }
        
        ///
        /// Creates the codec of this struct or its short form.
        ///
        @Override
        public <S> NexusCodec<T, A> orShort(NexusCodec<S, ? super A> shortForm, Function<? super S, ? extends T> fromShort, Function<? super T, Optional<S>> toShort) {
            return Combinators.orShort(this, shortForm, fromShort, toShort);
        }
        
        ///
        /// Creates the codec of the map codec view of this struct, which is a [MapCodec.MapCodecCodec] the same as a record codec of DFU.
        ///
        @Override
        public Codec<T> asDfu() {
            return this.asMapCodec().codec();
        }
        
        ///
        /// Creates the map codec of this struct, which runs the json or NBT format over the values of the ops.
        ///
        @Override
        public MapCodec<T> asMapCodec() {
            return VanillaAdapters.asMapCodec(this);
        }
        
        ///
        /// Every key this struct may write, including the keys of the structs of its inline fields.
        ///
        /// @return Every key this struct may write.
        ///
        protected abstract Set<String> keys();
        
        ///
        /// Writes every field of the given value under its key into the given object.
        ///
        /// @param operations The operations of the keyed format to write in.
        /// @param object     The object to write into.
        /// @param value      The value whose fields to write.
        ///
        /// @throws CodecException If a field could not be written, with the errors of every field that failed at its key.
        ///
        protected abstract <V> void writeFields(Operations<V> operations, V object, T value);
        
        ///
        /// Reads every field from the given object.
        ///
        /// @param operations The operations of the keyed format to read from.
        /// @param object     The object to read from.
        /// @param present    The keys of the object to refuse the unknown ones of, or `null` if this struct is inline or the format isn't strict.
        ///
        /// @return The value read.
        ///
        /// @throws CodecException If a field could not be read or a key is unknown, with every error at its key.
        ///
        protected abstract <V> T readFields(Operations<V> operations, V object, @Nullable Set<String> present);
    }
    
    ///
    /// A codec of a struct over its fields, which decodes their values into an array and calls the constructor once.
    ///
    /// On the network, it writes the fields in order and nothing else.
    /// In JSON and NBT, it writes every field under its key and throws once with the errors of every field that failed.
    ///
    /// @param <T> The type of the struct.
    /// @param <A> The access a format has to offer to be used with this codec.
    ///
    public static final class Struct<T, A extends Access.Plain> extends Inlinable<T, A> {
        
        ///
        /// The constructor of the struct, called with the value of every field, in order.
        ///
        private final Function<Object[], T> constructor;
        
        ///
        /// The fields, in the order they are written.
        ///
        private final List<BoundField<T, ?, ?>> fields;
        
        ///
        /// The keys of every field, including the keys of the structs of inline fields.
        ///
        private final Set<String> keys;
        
        ///
        /// Creates the codec of a struct with the given fields.
        ///
        /// @param name        The name of the struct, used in the failure message.
        /// @param arity       The number of values the constructor takes.
        /// @param constructor The constructor of the struct, called with the value of every field, in order.
        /// @param fields      The fields, in the order they are written.
        ///
        /// @throws IllegalArgumentException If the number of fields is not the arity, or if two fields have the same key.
        ///
        @SafeVarargs
        public Struct(String name, int arity, Function<Object[], T> constructor, StructField<T, ?, ? super A>... fields) {
            super(name);
            this.constructor = constructor;
            if (fields.length != arity) {
                throw new IllegalArgumentException("Struct '" + name + "' takes '" + arity + "' fields, found '" + fields.length + "'");
            }
            
            List<BoundField<T, ?, ?>> bound = new ArrayList<>(fields.length);
            Set<String> keys = new HashSet<>();
            for (StructField<T, ?, ? super A> field : fields) {
                if (!(field instanceof BoundField<T, ?, ?> binding)) {
                    throw new IllegalArgumentException("Struct '" + name + "' has a field that no codec created");
                }
                
                Inlinable<?, ?> nested = binding.inlined();
                for (String key : nested == null ? Set.of(binding.name()) : nested.keys()) {
                    if (!keys.add(key)) {
                        throw new IllegalArgumentException("Struct '" + name + "' has the key '" + key + "' twice");
                    }
                }
                
                bound.add(binding);
            }
            
            this.fields = List.copyOf(bound);
            this.keys = Set.copyOf(keys);
        }
        
        ///
        /// The keys of every field, including the keys of the structs of inline fields.
        ///
        @Override
        protected Set<String> keys() {
            return this.keys;
        }
        
        ///
        /// Appends every field in order on the network, and writes every field under its key into a new object otherwise.
        ///
        @Override
        protected <V> V write(Operations<V> operations, T value) {
            V object = operations.emptyObject();
            if (operations.isPositional()) {
                for (BoundField<T, ?, ?> field : this.fields) {
                    field.write(operations, object, value);
                }
                
                return object;
            }
            
            this.writeFields(operations, object, value);
            return object;
        }
        
        ///
        /// Writes every field under its key into the given object and throws once with the errors of every field that failed.
        ///
        @Override
        protected <V> void writeFields(Operations<V> operations, V object, T value) {
            List<CodecError> errors = null;
            for (BoundField<T, ?, ?> field : this.fields) {
                try {
                    field.write(operations, object, value);
                } catch (CodecException failure) {
                    errors = Struct.collect(errors, failure.errors());
                }
            }
            
            if (errors != null) throw new CodecException(errors);
        }
        
        ///
        /// Reads every field in order on the network, and every field under its key from an object otherwise.
        ///
        @Override
        protected <V> T read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                Object[] values = new Object[this.fields.size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = this.fields.get(i).read(operations, input);
                }
                
                return this.constructor.apply(values);
            }
            
            // Anything but an object fails here once, or it would fail again at every field.
            Set<String> keys = operations.keys(input);
            return this.readFields(operations, input, operations.isStrict() ? keys : null);
        }
        
        ///
        /// Reads every field from the given object, refuses those of the given keys this struct doesn't have, and throws once with every error.
        ///
        @Override
        protected <V> T readFields(Operations<V> operations, V object, @Nullable Set<String> present) {
            Object[] values = new Object[this.fields.size()];
            List<CodecError> errors = null;
            for (int i = 0; i < values.length; i++) {
                try {
                    values[i] = this.fields.get(i).read(operations, object);
                } catch (CodecException failure) {
                    errors = Struct.collect(errors, failure.errors());
                }
            }
            
            if (present != null && !this.keys.containsAll(present)) {
                List<CodecError> unknown = present.stream()
                        .filter(key -> !this.keys.contains(key))
                        .map(key -> new CodecError(key, "unknown key"))
                        .toList();
                errors = Struct.collect(errors, unknown);
            }
            
            if (errors != null) throw new CodecException(errors);
            return this.constructor.apply(values);
        }
        
        ///
        /// Adds the given errors to the given list, creating the list if it is `null`.
        ///
        private static List<CodecError> collect(@Nullable List<CodecError> errors, List<CodecError> added) {
            List<CodecError> collected = Objects.requireNonNullElseGet(errors, ArrayList::new);
            collected.addAll(added);
            return collected;
        }
    }
}
