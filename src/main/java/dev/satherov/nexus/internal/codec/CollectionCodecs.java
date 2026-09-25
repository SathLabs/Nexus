package dev.satherov.nexus.internal.codec;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.CodecError;
import dev.satherov.nexus.api.codec.CodecFormat;
import dev.satherov.nexus.api.codec.MapKey;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.NexusCodecException;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;

///
/// Utility for the list, set, and map codecs with their limits, and the keys behind [MapKey].
///
@UtilityClass
@ApiStatus.Internal
public class CollectionCodecs {
    
    ///
    /// The default maximum number of entries of a list, a set, or a map.
    ///
    public static final int LIMIT = 32_767;
    
    ///
    /// The json operations, which turn a key into its string form and back.
    ///
    private static final Operations<JsonElement> JSON = Operations.of(CodecFormat.JSON);
    
    ///
    /// The key behind [MapKey#STRING].
    ///
    public static final MapKey<String, Access.Plain> STRING_KEY = CollectionCodecs.stringKey(Scalars.STRING);
    
    ///
    /// The key behind [MapKey#IDENTIFIER].
    ///
    public static final MapKey<Identifier, Access.Plain> IDENTIFIER_KEY = CollectionCodecs.stringKey(Scalars.IDENTIFIER);
    
    ///
    /// The key behind [MapKey#UUID], which reads its string form with the codec, as the codec reads UUID strings too.
    ///
    public static final MapKey<UUID, Access.Plain> UUID_KEY = new Key<>(
            Scalars.UNIQUE_ID,
            UUID::toString,
            text -> Scalars.UNIQUE_ID.read(CollectionCodecs.JSON, new JsonPrimitive(text))
    );
    
    ///
    /// The key behind [MapKey#INT].
    ///
    public static final MapKey<Integer, Access.Plain> INT_KEY = new Key<>(
            Scalars.INT,
            String::valueOf,
            text -> (int) CollectionCodecs.parseInteger(text, Integer.MIN_VALUE, Integer.MAX_VALUE)
    );
    
    ///
    /// The key behind [MapKey#LONG].
    ///
    public static final MapKey<Long, Access.Plain> LONG_KEY = new Key<>(
            Scalars.LONG,
            String::valueOf,
            text -> CollectionCodecs.parseInteger(text, Long.MIN_VALUE, Long.MAX_VALUE)
    );
    
    ///
    /// Creates the codec behind [NexusCodec#list(int)].
    ///
    /// @param codec The codec of the elements.
    /// @param limit The maximum number of elements.
    ///
    /// @return The codec of the list.
    ///
    public static <T, A extends Access.Plain> Traversal<List<T>, A> list(Traversal<T, ? super A> codec, int limit) {
        return new CollectionTraversal<>("list", codec, ArrayList::new, limit);
    }
    
    ///
    /// Creates the codec behind [NexusCodec#set(int)], which decodes into a set that keeps the order of its elements.
    ///
    /// @param codec The codec of the elements.
    /// @param limit The maximum number of elements.
    ///
    /// @return The codec of the set.
    ///
    public static <T, A extends Access.Plain> Traversal<Set<T>, A> set(Traversal<T, ? super A> codec, int limit) {
        return new CollectionTraversal<>("set", codec, LinkedHashSet::newLinkedHashSet, limit);
    }
    
    ///
    /// Creates the codec behind [NexusCodec#mapOf(MapKey, NexusCodec, int)], which decodes into a map that keeps the order its entries were read in.
    ///
    /// @param key   The key of the entries.
    /// @param value The codec of the values.
    /// @param limit The maximum number of entries.
    ///
    /// @return The codec of the map.
    ///
    public static <K, V, A extends Access.Plain> Traversal<Map<K, V>, A> map(MapKey<K, ? super A> key, NexusCodec<V, ? super A> value, int limit) {
        return new MapTraversal<>((Key<K, ? super A>) key, (Traversal<V, ? super A>) value, limit);
    }
    
    ///
    /// Creates the key behind [MapKey#enumOf(Class)].
    ///
    /// @param type The class of the enum.
    ///
    /// @return The key of the enum's constants.
    ///
    public static <E extends Enum<E>> MapKey<E, Access.Plain> enumKey(Class<E> type) {
        return CollectionCodecs.stringKey(Scalars.enumOf(type));
    }
    
    ///
    /// Creates the key over the given codec whose string form is the string the codec writes in JSON.
    ///
    private static <K> MapKey<K, Access.Plain> stringKey(Traversal<K, Access.Plain> codec) {
        return new Key<>(codec, key -> codec.write(CollectionCodecs.JSON, key).getAsString(), text -> codec.read(CollectionCodecs.JSON, new JsonPrimitive(text)));
    }
    
    ///
    /// Creates the key behind [MapKey#of(NexusCodec, Function, Function)].
    ///
    /// @param codec      The codec of the key on the network.
    /// @param toString   The writer of the string form of a key.
    /// @param fromString The reader of a key from its string form.
    ///
    /// @return The key.
    ///
    public static <K, A extends Access.Plain> MapKey<K, A> key(NexusCodec<K, A> codec, Function<? super K, String> toString, Function<String, ? extends K> fromString) {
        return new Key<>((Traversal<K, A>) codec, toString, fromString);
    }
    
    ///
    /// Reads an integer within the given range from the given decimal string.
    ///
    private static long parseInteger(String text, long min, long max) {
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException _) {
            throw Errors.mismatch("an integer", text);
        }
        
        if (value < min || value > max) throw Errors.outOfRange(min, max, value);
        return value;
    }
    
    ///
    /// Checks that the given limit of the codec with the given name is not negative.
    ///
    private static int requireLimit(String name, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Could not create a " + name + " codec with the negative limit '" + limit + "'");
        }
        
        return limit;
    }
    
    ///
    /// Creates a failure for a collection with more entries than the given limit.
    ///
    private static NexusCodecException tooMany(int limit, int count) {
        return new NexusCodecException("expected at most " + limit + " entries, found " + count);
    }
    
    ///
    /// Adds the errors of the given failure to the given list, creating the list if it is `null`.
    ///
    private static List<CodecError> collect(@Nullable List<CodecError> errors, NexusCodecException failure) {
        List<CodecError> collected = Objects.requireNonNullElseGet(errors, ArrayList::new);
        collected.addAll(failure.errors());
        return collected;
    }
    
    ///
    /// A key of a map, which is its codec for the network and its string form for JSON and NBT.
    ///
    /// @param codec   The codec of the key on the network.
    /// @param printer The writer of the string form of a key.
    /// @param parser  The reader of a key from its string form.
    /// @param <K>     The type of the key.
    /// @param <A>     The access a format has to offer to be used with this key.
    ///
    private record Key<K, A extends Access.Plain>(
            Traversal<K, A> codec,
            Function<? super K, String> printer,
            Function<String, ? extends K> parser
    ) implements MapKey<K, A> {
        
        ///
        /// Writes the string form of the given key, with anything other than a [NexusCodecException] that the printer throws turned into one.
        ///
        private String print(K key) {
            try {
                return this.printer.apply(key);
            } catch (RuntimeException failure) {
                throw failure instanceof NexusCodecException refused ? refused : new NexusCodecException("could not write the key, " + failure);
            }
        }
        
        ///
        /// Reads a key from the given string form, with anything other than a [NexusCodecException] that the parser throws turned into one.
        ///
        private K parse(String text) {
            try {
                return this.parser.apply(text);
            } catch (RuntimeException failure) {
                throw failure instanceof NexusCodecException refused ? refused : new NexusCodecException("could not read the key, " + failure);
            }
        }
    }
    
    ///
    /// A codec of a list or a set over the codec of its elements, which fails on an element that the collection does not add.
    ///
    /// On the network, it writes the number of elements and then every element.
    /// In JSON and NBT, it writes a list and throws once with the errors of every element that failed.
    ///
    /// @param <T> The type of the elements.
    /// @param <C> The type of the collection.
    /// @param <A> The access a format has to offer to be used with this codec.
    ///
    private static final class CollectionTraversal<T, C extends Collection<T>, A extends Access.Plain> extends Traversal<C, A> {
        
        ///
        /// The codec of the elements.
        ///
        private final Traversal<T, ? super A> codec;
        
        ///
        /// The constructor of an empty collection, called with the number of elements it should have room for.
        ///
        private final IntFunction<C> factory;
        
        ///
        /// The maximum number of elements.
        ///
        private final int limit;
        
        ///
        /// Creates the codec of a collection with the given name.
        ///
        private CollectionTraversal(String name, Traversal<T, ? super A> codec, IntFunction<C> factory, int limit) {
            super(name);
            this.codec = codec;
            this.factory = factory;
            
            this.limit = CollectionCodecs.requireLimit(name, limit);
        }
        
        ///
        /// Writes the number of elements and then every element on the network, and a list of the elements otherwise.
        ///
        @Override
        protected <V> V write(Operations<V> operations, C value) {
            if (operations.isPositional()) {
                V buffer = operations.emptyObject();
                operations.writeCount(value.size(), this.limit);
                for (T element : value) {
                    this.codec.write(operations, element);
                }
                
                return buffer;
            }
            
            if (value.size() > this.limit) throw CollectionCodecs.tooMany(this.limit, value.size());
            List<V> elements = new ArrayList<>(value.size());
            List<CodecError> errors = null;
            int index = 0;
            for (T element : value) {
                try {
                    elements.add(this.codec.write(operations, element));
                } catch (NexusCodecException failure) {
                    errors = CollectionCodecs.collect(errors, Errors.prefixIndex(failure, index));
                }
                
                index++;
            }
            
            if (errors != null) throw new NexusCodecException(errors);
            return operations.ofList(elements);
        }
        
        ///
        /// Reads the number of elements and then every element on the network, and the elements of a list otherwise.
        ///
        @Override
        protected <V> C read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                int count = operations.readCount(this.limit);
                // The count may still be far more than the buffer holds, so the room is capped the same as vanilla does.
                C collection = this.factory.apply(Math.min(count, ByteBufCodecs.MAX_INITIAL_COLLECTION_SIZE));
                for (int i = 0; i < count; i++) {
                    CollectionTraversal.add(collection, this.codec.read(operations, input));
                }
                
                return collection;
            }
            
            List<V> elements = operations.asList(input);
            if (elements.size() > this.limit) throw CollectionCodecs.tooMany(this.limit, elements.size());
            C collection = this.factory.apply(elements.size());
            List<CodecError> errors = null;
            for (int i = 0; i < elements.size(); i++) {
                try {
                    CollectionTraversal.add(collection, this.codec.read(operations, elements.get(i)));
                } catch (NexusCodecException failure) {
                    errors = CollectionCodecs.collect(errors, Errors.prefixIndex(failure, i));
                }
            }
            
            if (errors != null) throw new NexusCodecException(errors);
            return collection;
        }
        
        ///
        /// Adds the given element to the given collection, and fails if the collection does not add it.
        ///
        private static <T> void add(Collection<T> collection, T element) {
            if (!collection.add(element)) throw new NexusCodecException("duplicate element");
        }
    }
    
    ///
    /// A codec of a map over its key and the codec of its values, which fails on a key that the map already holds.
    ///
    /// On the network, it writes the number of entries and then every key and its value.
    /// In JSON and NBT, it writes an object and throws once with the errors of every entry that failed.
    ///
    /// @param <K> The type of the keys.
    /// @param <T> The type of the values.
    /// @param <A> The access a format has to offer to be used with this codec.
    ///
    private static final class MapTraversal<K, T, A extends Access.Plain> extends Traversal<Map<K, T>, A> {
        
        ///
        /// The key of the entries.
        ///
        private final Key<K, ? super A> key;
        
        ///
        /// The codec of the values.
        ///
        private final Traversal<T, ? super A> value;
        
        ///
        /// The maximum number of entries.
        ///
        private final int limit;
        
        ///
        /// Creates the codec of a map.
        ///
        private MapTraversal(Key<K, ? super A> key, Traversal<T, ? super A> value, int limit) {
            super("map");
            this.key = key;
            this.value = value;
            
            this.limit = CollectionCodecs.requireLimit("map", limit);
        }
        
        ///
        /// Writes the number of entries and then every key and its value on the network, and every value under the string form of its key otherwise.
        ///
        /// In JSON and NBT, the errors of an entry whose key has no string form are at the index of the entry.
        ///
        @Override
        protected <V> V write(Operations<V> operations, Map<K, T> map) {
            if (operations.isPositional()) {
                V buffer = operations.emptyObject();
                operations.writeCount(map.size(), this.limit);
                for (Map.Entry<K, T> entry : map.entrySet()) {
                    this.key.codec().write(operations, entry.getKey());
                    this.value.write(operations, entry.getValue());
                }
                
                return buffer;
            }
            
            if (map.size() > this.limit) throw CollectionCodecs.tooMany(this.limit, map.size());
            V object = operations.emptyObject();
            List<CodecError> errors = null;
            int index = 0;
            for (Map.Entry<K, T> entry : map.entrySet()) {
                String name = null;
                try {
                    name = this.key.print(entry.getKey());
                    operations.put(object, name, this.value.write(operations, entry.getValue()));
                } catch (NexusCodecException failure) {
                    errors = CollectionCodecs.collect(errors, name == null ? Errors.prefixIndex(failure, index) : Errors.prefixMapKey(failure, name));
                }
                
                index++;
            }
            
            if (errors != null) throw new NexusCodecException(errors);
            return object;
        }
        
        ///
        /// Reads the number of entries and then every key and its value on the network, and every key of an object and its value otherwise.
        ///
        @Override
        protected <V> Map<K, T> read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                int count = operations.readCount(this.limit);
                // The count may still be far more than the buffer holds, so the room is capped the same as vanilla does.
                Map<K, T> map = LinkedHashMap.newLinkedHashMap(Math.min(count, ByteBufCodecs.MAX_INITIAL_COLLECTION_SIZE));
                for (int i = 0; i < count; i++) {
                    MapTraversal.put(map, this.key.codec().read(operations, input), this.value.read(operations, input));
                }
                
                return map;
            }
            
            Set<String> names = operations.keys(input);
            if (names.size() > this.limit) throw CollectionCodecs.tooMany(this.limit, names.size());
            Map<K, T> map = LinkedHashMap.newLinkedHashMap(names.size());
            List<CodecError> errors = null;
            for (String name : names) {
                try {
                    //noinspection DataFlowIssue The name is one of the keys of the object.
                    MapTraversal.put(map, this.key.parse(name), this.value.read(operations, operations.get(input, name)));
                } catch (NexusCodecException failure) {
                    errors = CollectionCodecs.collect(errors, Errors.prefixMapKey(failure, name));
                }
            }
            
            if (errors != null) throw new NexusCodecException(errors);
            return map;
        }
        
        ///
        /// Puts the given value under the given key of the given map, and fails if the map already holds the key.
        ///
        private static <K, T> void put(Map<K, T> map, K key, T value) {
            if (map.putIfAbsent(key, value) != null) throw new NexusCodecException("duplicate key");
        }
    }
}
