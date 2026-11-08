package dev.satherov.nexus.internal.codec.vanilla;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.BufferFormat;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.format.JsonOperations;
import dev.satherov.nexus.internal.codec.format.NbtOperations;
import dev.satherov.nexus.internal.codec.format.Operations;
import dev.satherov.nexus.internal.codec.path.Encoding;
import dev.satherov.nexus.internal.codec.struct.Structs;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.google.common.collect.Comparators;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.BaseMapCodec;
import com.mojang.serialization.codecs.ListCodec;
import com.mojang.serialization.codecs.UnboundedMapCodec;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

///
/// Utility for the DFU and stream codec views of codecs, and for the codecs over DFU and stream codecs.
///
@UtilityClass
@ApiStatus.Internal
public class VanillaAdapters {
    
    ///
    /// The error message a view fails with if the ops compress maps.
    ///
    private static final String COMPRESSED = "could not use ops that compress maps";
    
    ///
    /// The registries of a view over ops without registries, which fail on every lookup.
    ///
    private static final RegistryOps.RegistryInfoLookup WITHOUT_REGISTRIES = new RegistryOps.RegistryInfoLookup() {
        
        ///
        /// Throws a [NexusCodecException] since the ops have no registries.
        ///
        @Override
        @Contract("_ -> fail")
        public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registry) {
            throw new NexusCodecException("Could not access the registry '" + registry.identifier() + "' without registry ops");
        }
    };
    
    ///
    /// The synthetic fields of DFU that are read to unwrap the layers of a DFU codec, or `null` if they could not be made accessible.
    ///
    /// If it's `null`, every DFU codec reads by encoding the value.
    ///
    private static final @Nullable SyntheticFields SYNTHETIC_FIELDS = SyntheticFields.find();
    
    ///
    /// The classes of the maps whose `get` compares keys with `equals`, besides the immutable maps of Guava that aren't sorted.
    ///
    private static final Set<Class<?>> EQUALS_MAPS = Set.of(
            HashMap.class,
            LinkedHashMap.class,
            Object2ObjectOpenHashMap.class,
            Object2ObjectLinkedOpenHashMap.class,
            Object2ObjectArrayMap.class,
            Map.of().getClass(),
            Map.of("", "").getClass()
    );
    
    ///
    /// Creates the codec behind [NexusCodec#ofDfu(Codec)].
    ///
    /// @param codec The DFU codec.
    ///
    /// @return The codec over the DFU codec.
    ///
    public static <T> Traversal<T, Access.Plain> ofDfu(Codec<T> codec) {
        return new Wrapped<>("ofDfu", codec, false, ByteBufCodecs.fromCodec(codec));
    }
    
    ///
    /// Creates the codec behind [NexusCodec#ofDfu(Codec, Class)].
    ///
    /// @param codec The DFU codec, which may need registry ops.
    ///
    /// @return The codec over the DFU codec.
    ///
    public static <T> Traversal<T, Access.Registries> ofRegistryDfu(Codec<T> codec) {
        return new Wrapped<>("ofDfu", codec, true, VanillaAdapters.onAnyBuffer(ByteBufCodecs.fromCodecWithRegistries(codec)));
    }
    
    ///
    /// Creates the codec behind [NexusCodec#ofStream(StreamCodec)].
    ///
    /// @param stream The stream codec.
    ///
    /// @return The codec over the stream codec.
    ///
    public static <T> Traversal<T, Access.Plain> ofStream(StreamCodec<? super FriendlyByteBuf, T> stream) {
        return new Wrapped<>("ofStream", null, false, stream);
    }
    
    ///
    /// Creates the codec behind [NexusCodec#ofStream(StreamCodec, Class)].
    ///
    /// @param stream The stream codec, which may need a [RegistryFriendlyByteBuf].
    ///
    /// @return The codec over the stream codec.
    ///
    public static <T> Traversal<T, Access.Registries> ofRegistryStream(StreamCodec<? super RegistryFriendlyByteBuf, T> stream) {
        return new Wrapped<>("ofStream", null, false, VanillaAdapters.onAnyBuffer(stream));
    }
    
    ///
    /// Creates the codec behind [NexusCodec#ofVanilla(Codec, StreamCodec)].
    ///
    /// @param codec  The DFU codec.
    /// @param stream The stream codec.
    ///
    /// @return The codec over both.
    ///
    public static <T> Traversal<T, Access.Plain> ofVanilla(Codec<T> codec, StreamCodec<? super FriendlyByteBuf, T> stream) {
        return new Wrapped<>("ofVanilla", codec, false, stream);
    }
    
    ///
    /// Creates the codec behind [NexusCodec#ofVanilla(Codec, StreamCodec, Class)].
    ///
    /// @param codec  The DFU codec, which may need registry ops.
    /// @param stream The stream codec, which may need a [RegistryFriendlyByteBuf].
    ///
    /// @return The codec over both.
    ///
    public static <T> Traversal<T, Access.Registries> ofRegistryVanilla(Codec<T> codec, StreamCodec<? super RegistryFriendlyByteBuf, T> stream) {
        return new Wrapped<>("ofVanilla", codec, true, VanillaAdapters.onAnyBuffer(stream));
    }
    
    ///
    /// Gets the given stream codec of a registry buffer as one of any buffer.
    ///
    private static <T> StreamCodec<? super FriendlyByteBuf, T> onAnyBuffer(StreamCodec<? super RegistryFriendlyByteBuf, T> stream) {
        //noinspection unchecked A codec that needs registries only ever runs on the netty format of a registry buffer.
        return (StreamCodec<? super FriendlyByteBuf, T>) stream;
    }
    
    ///
    /// Creates the DFU codec behind [NexusCodec#asDfu()].
    ///
    /// @param codec The codec to view.
    ///
    /// @return The DFU codec, which fails with the message of a [NexusCodecException] and without a partial result.
    ///
    public static <T> Codec<T> asDfu(Traversal<T, ?> codec) {
        return new DfuAdapter<>(codec);
    }
    
    ///
    /// Creates the map codec behind [StructCodec#asMapCodec()].
    ///
    /// @param struct The struct to view.
    ///
    /// @return The map codec, which fails with the message of a [NexusCodecException] and without a partial result.
    ///
    public static <T> MapCodec<T> asMapCodec(Structs.Inlinable<T, ?> struct) {
        return new StructMapCodec<>(struct);
    }
    
    ///
    /// Encodes the given value with the given codec in NBT over the values of NBT, and in JSON over any other values.
    ///
    /// @param codec The codec of the value.
    /// @param ops   The ops to encode for, with the registries of the codec if they are registry ops.
    /// @param value The value to encode.
    ///
    /// @return The tag or the JSON element.
    ///
    /// @throws NexusCodecException If the ops compress maps or the value could not be encoded.
    ///
    public static <T> Object encodeFor(NexusCodec<T, ?> codec, DynamicOps<?> ops, T value) {
        if (ops.compressMaps()) throw new NexusCodecException(VanillaAdapters.COMPRESSED);
        NexusCodec<T, ? super Access.Registries> viewed = VanillaAdapters.onAnyFormat(codec);
        RegistryOps.RegistryInfoLookup registries = VanillaAdapters.registriesOf(ops);
        if (ops.empty() instanceof Tag) {
            return viewed.encode(new NbtOperations<Access.Registries>(false, registries), value);
        }
        
        return viewed.encode(new JsonOperations<Access.Registries>(false, registries), value);
    }
    
    ///
    /// Gets the given tag or JSON element as a value of the given ops, converted from JSON if the ops are over any other values.
    ///
    /// @param ops     The ops to get the value for.
    /// @param encoded The tag or the JSON element.
    ///
    /// @return The value of the ops, or `null` if the value is JSON null and the empty value of the ops is `null`.
    ///
    public static <V> @Nullable V toOps(DynamicOps<V> ops, Object encoded) {
        // JavaOps's empty value is null, so the switch has to accept it.
        return switch (ops.empty()) {
            case JsonElement _, Tag _ -> //noinspection unchecked The values of the ops are of the type of its empty value.
                    (V) encoded;
            case null, default -> VanillaAdapters.fromJson(ops, (JsonElement) encoded);
        };
    }
    
    ///
    /// Converts the given JSON element into a value of the given ops, keeping the type of every number that has one.
    /// Json null becomes the empty value of the ops, and an entry whose value is `null` is left out.
    ///
    private static <V> @Nullable V fromJson(DynamicOps<V> ops, JsonElement json) {
        return switch (json) {
            case JsonObject object -> //noinspection DataFlowIssue,ConstantValue fromJson can return null so we must filter here
                    ops.createMap(object.entrySet()
                            .stream()
                            .map(entry -> Pair.of(
                                    ops.createString(entry.getKey()),
                                    VanillaAdapters.fromJson(ops, entry.getValue()))
                            ).filter(entry -> entry.getSecond() != null));
            case JsonArray array -> ops.createList(array.asList().stream().map(element -> VanillaAdapters.fromJson(ops, element)));
            case JsonPrimitive primitive when primitive.isNumber() -> switch (primitive.getAsNumber()) {
                case Byte number -> ops.createByte(number);
                case Short number -> ops.createShort(number);
                case Integer number -> ops.createInt(number);
                case Long number -> ops.createLong(number);
                case Float number -> ops.createFloat(number);
                case Double number -> ops.createDouble(number);
                default -> JsonOps.INSTANCE.convertTo(ops, primitive);
            };
            case JsonPrimitive primitive -> JsonOps.INSTANCE.convertTo(ops, primitive);
            default -> ops.empty();
        };
    }
    
    ///
    /// Decodes a value from the given input of the given ops with the given codec.
    ///
    /// Runs the JSON or NBT format directly if the ops are over its values, and converts to JSON otherwise.
    ///
    /// @param codec The codec of the value.
    /// @param ops   The ops of the input, with the registries of the codec if they are registry ops.
    /// @param input The input to decode, or `null` as the empty value of ops that have one.
    ///
    /// @return The decoded value.
    ///
    /// @throws NexusCodecException If the ops compress maps or the input could not be decoded.
    ///
    public static <T, V> T decode(NexusCodec<T, ?> codec, DynamicOps<V> ops, @Nullable V input) {
        if (ops.compressMaps()) throw new NexusCodecException(VanillaAdapters.COMPRESSED);
        NexusCodec<T, ? super Access.Registries> viewed = VanillaAdapters.onAnyFormat(codec);
        RegistryOps.RegistryInfoLookup registries = VanillaAdapters.registriesOf(ops);
        return switch (ops.empty()) {
            case JsonElement _ -> viewed.decode(new JsonOperations<Access.Registries>(false, registries), Objects.requireNonNullElse((JsonElement) input, JsonNull.INSTANCE));
            case Tag _ -> viewed.decode(new NbtOperations<Access.Registries>(false, registries), Objects.requireNonNullElse((Tag) input, EndTag.INSTANCE));
            case null, default -> viewed.decode(new JsonOperations<Access.Registries>(false, registries), ops.convertTo(JsonOps.INSTANCE, input));
        };
    }
    
    ///
    /// Gets the registries of the given ops if they are registry ops, and registries that fail on every lookup otherwise.
    ///
    private static RegistryOps.RegistryInfoLookup registriesOf(DynamicOps<?> ops) {
        return ops instanceof RegistryOps<?> registryOps ? registryOps.lookupProvider : VanillaAdapters.WITHOUT_REGISTRIES;
    }
    
    ///
    /// Creates the stream codec behind [NexusCodec#asStream()].
    ///
    /// @param codec The codec to view.
    ///
    /// @return The stream codec over the netty format of a registry buffer.
    ///
    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> asStream(NexusCodec<T, ?> codec) {
        return VanillaAdapters.streamOf(VanillaAdapters.onAnyFormat(codec), CodecFormat::netty);
    }
    
    ///
    /// Creates the stream codec behind [NexusCodec#plainStream(NexusCodec)].
    ///
    /// @param codec The codec to view.
    ///
    /// @return The stream codec over the netty format of a plain buffer.
    ///
    public static <T> StreamCodec<FriendlyByteBuf, T> plainStream(NexusCodec<T, Access.Plain> codec) {
        return VanillaAdapters.streamOf(codec, CodecFormat::netty);
    }
    
    ///
    /// Creates a stream codec that runs the given codec on the netty format of the buffer, and throws a failure the way vanilla's stream codecs do.
    ///
    private static <T, A extends Access.Plain, B extends FriendlyByteBuf> StreamCodec<B, T> streamOf(NexusCodec<T, A> codec, Function<B, BufferFormat<B, ? extends A>> formatOf) {
        return new StreamCodec<>() {
            
            ///
            /// Decodes a value from the given buffer, and throws a [DecoderException] with the message of a failure.
            ///
            @Override
            public T decode(B buffer) {
                try {
                    return codec.decode(formatOf.apply(buffer));
                } catch (NexusCodecException failure) {
                    throw new DecoderException(failure.getMessage(), failure);
                }
            }
            
            ///
            /// Encodes the given value into the given buffer, and throws an [EncoderException] with the message of a failure.
            ///
            @Override
            public void encode(B buffer, T value) {
                try {
                    codec.encode(formatOf.apply(buffer), value);
                } catch (NexusCodecException failure) {
                    throw new EncoderException(failure.getMessage(), failure);
                }
            }
        };
    }
    
    ///
    /// Gets the given codec as one that runs on formats with registries, which every codec does.
    ///
    private static <T> NexusCodec<T, ? super Access.Registries> onAnyFormat(NexusCodec<T, ?> codec) {
        //noinspection unchecked Every access is a supertype of Access.Registries, the last of the sealed accesses.
        return (NexusCodec<T, ? super Access.Registries>) codec;
    }
    
    ///
    /// Creates the data component type behind [NexusCodec#asDataComponentType()].
    ///
    /// @param codec The codec of the component.
    ///
    /// @return The data component type, saved with the DFU view and synced with the stream view of the codec.
    ///
    public static <T> DataComponentType<T> asDataComponentType(NexusCodec<T, ?> codec) {
        return DataComponentType.<T>builder()
                .persistent(codec.asDfu())
                .networkSynchronized(codec.asStream())
                .build();
    }
    
    ///
    /// Creates the recipe serializer behind [StructCodec#recipeSerializer(StructCodec)].
    ///
    /// @param codec The codec of the recipe.
    ///
    /// @return The recipe serializer over the map codec view and the stream view of the codec.
    ///
    public static <R extends Recipe<?>> RecipeSerializer<R> recipeSerializer(StructCodec<R, ?> codec) {
        return new RecipeSerializer<>(codec.asMapCodec(), codec.asStream());
    }
    
    ///
    /// Parses the given name as an identifier, or returns `null` if it isn't one or doesn't encode back to the same name.
    ///
    private static @Nullable Identifier identifierOf(String name) {
        Identifier identifier = Identifier.tryParse(name);
        return identifier != null && identifier.toString().equals(name) ? identifier : null;
    }
    
    ///
    /// The codec behind the wrappers, which runs a DFU codec in JSON and NBT and a stream codec on the network.
    ///
    /// @param <T> The type of value this codec encodes and decodes.
    /// @param <A> The access a format has to offer to be used with this codec.
    ///
    private static final class Wrapped<T, A extends Access.Plain> extends Traversal<T, A> {
        
        ///
        /// The DFU codec, or `null` if this codec only runs on the network.
        ///
        private final @Nullable Codec<T> codec;
        
        ///
        /// If the DFU codec runs over registry ops with the registries of the format.
        ///
        private final boolean registryOps;
        
        ///
        /// The stream codec, which only ever gets the buffer of a registry netty format if this codec needs registries.
        ///
        private final StreamCodec<? super FriendlyByteBuf, T> stream;
        
        ///
        /// Creates a codec over the given DFU codec and stream codec.
        ///
        private Wrapped(String name, @Nullable Codec<T> codec, boolean registryOps, StreamCodec<? super FriendlyByteBuf, T> stream) {
            super(name);
            this.codec = codec;
            this.registryOps = registryOps;
            this.stream = stream;
        }
        
        ///
        /// Appends the value with the stream codec on the network, and encodes it with the DFU codec otherwise.
        ///
        @Override
        public <V> V write(Operations<V> operations, T value) {
            if (!operations.isPositional()) {
                return this.dfu().encodeStart(this.opsOf(operations), value).getOrThrow(NexusCodecException::new);
            }
            
            V buffer = operations.emptyObject();
            try {
                this.stream.encode((FriendlyByteBuf) buffer, value);
            } catch (RuntimeException failure) {
                throw failure instanceof NexusCodecException refused ? refused : new NexusCodecException("Could not write to the buffer, " + failure);
            }
            
            return buffer;
        }
        
        ///
        /// Reads a value with the stream codec on the network, and decodes it with the DFU codec otherwise.
        ///
        @Override
        public <V> T read(Operations<V> operations, V input) {
            if (!operations.isPositional()) {
                return this.dfu().parse(this.opsOf(operations), input).getOrThrow(NexusCodecException::new);
            }
            
            try {
                return this.stream.decode((FriendlyByteBuf) input);
            } catch (RuntimeException failure) {
                throw failure instanceof NexusCodecException refused ? refused : new NexusCodecException("Could not read from the buffer, " + failure);
            }
        }
        
        ///
        /// Gets the DFU codec, and fails if this codec only runs on the network.
        ///
        private Codec<T> dfu() {
            Codec<T> dfu = this.codec;
            if (dfu == null) throw new NexusCodecException("Could not use a stream codec outside of netty");
            return dfu;
        }
        
        ///
        /// Gets the DFU ops of the given keyed format, as registry ops with its registries if the DFU codec runs over them.
        ///
        private <V> DynamicOps<V> opsOf(Operations<V> operations) {
            if (!this.registryOps) {
                return operations.dynamicOps();
            }
            
            //noinspection DataFlowIssue A codec that needs registries only ever runs on formats with registries.
            return RegistryOps.create(operations.dynamicOps(), operations.lookup());
        }
        
        ///
        /// Creates the reader from the DFU codec, or reads by encoding if this codec doesn't have a DFU codec or the reader can't be created from it.
        ///
        @Override
        public PathReader<T> reader(List<ComponentPath.Step> steps, int from) {
            PathReader<T> walked = this.codec != null ? this.walk(this.codec, steps, from) : null;
            return walked != null ? walked : super.reader(steps, from);
        }
        
        ///
        /// Creates the reader of the steps from the given index on in the values of the given DFU codec, or returns `null` if its structure is unknown.
        ///
        /// Unwraps the layers that `xmap`, `flatXmap`, and `validate` add, and then reads the steps in a map, in a list, or in the codec that the DFU codec was created from.
        ///
        private @Nullable PathReader<T> walk(Codec<T> dfu, List<ComponentPath.Step> steps, int from) {
            SyntheticFields fields = VanillaAdapters.SYNTHETIC_FIELDS;
            if (fields == null) {
                return null;
            }
            
            List<Function<Object, @Nullable Object>> layers = new ArrayList<>();
            Object current = dfu;
            try {
                while (current.getClass() == fields.ofEncoder().getDeclaringClass()) {
                    Object encoder = fields.ofEncoder().get(current);
                    if (encoder.getClass() == fields.comapEncoder().getDeclaringClass()) {
                        //noinspection unchecked The function maps the values of the codec around the encoder.
                        layers.add((Function<Object, @Nullable Object>) fields.comapFunction().get(encoder));
                        current = fields.comapEncoder().get(encoder);
                    } else if (encoder.getClass() == fields.flatComapEncoder().getDeclaringClass()) {
                        //noinspection unchecked The function maps the values of the codec around the encoder.
                        Function<Object, DataResult<?>> function = (Function<Object, DataResult<?>>) fields.flatComapFunction().get(encoder);
                        layers.add(value -> function.apply(value).result().orElse(null));
                        current = fields.flatComapEncoder().get(encoder);
                    } else {
                        return null;
                    }
                }
            } catch (IllegalAccessException failure) {
                return null;
            }
            
            PathReader<?> inner = switch (current) {
                case DfuAdapter<?> adapter -> adapter.codec().reader(steps, from);
                case MapCodec.MapCodecCodec<?>(StructMapCodec<?> map) -> map.struct.reader(steps, from);
                case UnboundedMapCodec<?, ?> map -> this.mapReader(map, steps, from);
                case ExtraCodecs.StrictUnboundedMapCodec<?, ?> map -> this.mapReader(map, steps, from);
                case ListCodec<?> list -> this.listReader(list, steps, from);
                default -> null;
            };
            
            if (inner == null) {
                return null;
            }
            
            //noinspection unchecked Without layers, the innermost codec is the DFU codec itself.
            return layers.isEmpty() ? (PathReader<T>) inner : new LayeredReader<>(List.copyOf(layers), inner);
        }
        
        ///
        /// Creates the reader of a key or a wildcard step in the maps of the given map codec, or returns `null` if no step is left or the step is an index.
        ///
        private <K, V> @Nullable PathReader<Map<K, V>> mapReader(BaseMapCodec<K, V> map, List<ComponentPath.Step> steps, int from) {
            if (from == steps.size()) {
                return null;
            }
            
            return switch (steps.get(from)) {
                case ComponentPath.Step.Key(String name) when map.keyCodec() == Codec.STRING -> new KeyReader<>(name, this.adapter(map.elementCodec()).reader(steps, from + 1));
                case ComponentPath.Step.Key(String name) when map.keyCodec() == Identifier.CODEC -> new KeyReader<>(VanillaAdapters.identifierOf(name), this.adapter(map.elementCodec()).reader(steps, from + 1));
                case ComponentPath.Step.Key(String name) -> new EncodedKeyReader<>(this.adapter(map.keyCodec()), name, this.adapter(map.elementCodec()).reader(steps, from + 1));
                case ComponentPath.Step.Wildcard _ -> new AnyReader<>(Map::values, 0, Integer.MAX_VALUE, this.adapter(map.elementCodec()).reader(steps, from + 1));
                case ComponentPath.Step.Index _ -> null;
            };
        }
        
        ///
        /// Creates the reader of an index or a wildcard step in the lists of the given list codec, or returns `null` if no step is left or the step is a key.
        ///
        private <E> @Nullable PathReader<List<E>> listReader(ListCodec<E> list, List<ComponentPath.Step> steps, int from) {
            if (from == steps.size()) {
                return null;
            }
            
            return switch (steps.get(from)) {
                case ComponentPath.Step.Index(int index) -> new IndexReader<>(list.minSize(), list.maxSize(), index, this.adapter(list.elementCodec()).reader(steps, from + 1));
                case ComponentPath.Step.Wildcard _ -> new AnyReader<List<E>, E>(Function.identity(), list.minSize(), list.maxSize(), this.adapter(list.elementCodec()).reader(steps, from + 1));
                case ComponentPath.Step.Key _ -> null;
            };
        }
        
        ///
        /// Creates the codec over the given DFU codec, with registry ops if this codec runs its DFU codec over them.
        ///
        private <E> Traversal<E, ?> adapter(Codec<E> codec) {
            return this.registryOps ? VanillaAdapters.ofRegistryDfu(codec) : VanillaAdapters.ofDfu(codec);
        }
    }
    
    ///
    /// The map codec behind [StructCodec#asMapCodec()], which fails with the message of a [NexusCodecException] and without a partial result.
    ///
    /// The reader of a DFU codec recognizes this class inside [Structs.Inlinable#asDfu()] and reads the steps in the struct.
    ///
    /// @param <T> The type of value the struct encodes and decodes.
    ///
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class StructMapCodec<T> extends MapCodec<T> {
        
        ///
        /// The struct to encode and decode with.
        ///
        private final Structs.Inlinable<T, ?> struct;
        
        ///
        /// Creates every key the struct may write, in the given ops.
        ///
        @Override
        public <V> Stream<V> keys(DynamicOps<V> ops) {
            return this.struct.keys().stream().map(ops::createString);
        }
        
        ///
        /// Decodes the struct from an object of every entry of the input whose value isn't `null`.
        ///
        @Override
        public <V> DataResult<T> decode(DynamicOps<V> ops, MapLike<V> input) {
            try {
                // JavaOps reads a null value the same as a missing key, and its maps can't hold one.
                V object = ops.createMap(input.entries().filter(entry -> entry.getSecond() != null));
                return DataResult.success(VanillaAdapters.decode(this.struct, ops, object));
            } catch (NexusCodecException failure) {
                return DataResult.error(failure::getMessage);
            }
        }
        
        ///
        /// Encodes the struct into an object and then adds every entry of it whose value isn't `null` in the ops to the prefix.
        ///
        @Override
        public <V> RecordBuilder<V> encode(T input, DynamicOps<V> ops, RecordBuilder<V> prefix) {
            Object object;
            try {
                object = VanillaAdapters.encodeFor(this.struct, ops, input);
            } catch (NexusCodecException failure) {
                return prefix.withErrorsFrom(DataResult.error(failure::getMessage));
            }
            
            // The entries are read from the object itself, since ops such as HashOps can build a map but never read one.
            Set<? extends Map.Entry<String, ?>> entries = object instanceof CompoundTag tag ? tag.entrySet() : ((JsonObject) object).entrySet();
            RecordBuilder<V> builder = prefix;
            for (Map.Entry<String, ?> entry : entries) {
                V value = VanillaAdapters.toOps(ops, entry.getValue());
                if (value != null) {
                    builder = builder.add(entry.getKey(), value);
                }
            }
            
            return builder;
        }
    }
    
    ///
    /// The synthetic fields of DFU 9 that the reader of a DFU codec reads to unwrap its layers.
    ///
    /// @param ofEncoder         The encoder of a codec that [Codec#of(Encoder, Decoder, String)] creates.
    /// @param comapEncoder      The encoder that an encoder of [Encoder#comap(Function)] encodes the mapped value with.
    /// @param comapFunction     The function that an encoder of [Encoder#comap(Function)] maps the value with.
    /// @param flatComapEncoder  The encoder that an encoder of [Encoder#flatComap(Function)] encodes the mapped value with.
    /// @param flatComapFunction The function that an encoder of [Encoder#flatComap(Function)] maps the value with.
    ///
    private record SyntheticFields(Field ofEncoder, Field comapEncoder, Field comapFunction, Field flatComapEncoder, Field flatComapFunction) {
        
        ///
        /// Finds the fields on the classes of an `xmap` and a `validate` codec built for it, or returns `null` if DFU doesn't have them or they could not be made accessible.
        ///
        private static @Nullable SyntheticFields find() {
            Codec<Integer> mapped = Codec.INT.xmap(Function.identity(), Function.identity());
            Codec<Integer> validated = Codec.INT.validate(DataResult::success);
            try {
                Field ofEncoder = SyntheticFields.accessible(mapped.getClass(), "val$encoder");
                Class<?> comap = ofEncoder.get(mapped).getClass();
                Class<?> flatComap = ofEncoder.get(validated).getClass();
                return new SyntheticFields(
                        ofEncoder,
                        SyntheticFields.accessible(comap, "this$0"),
                        SyntheticFields.accessible(comap, "val$function"),
                        SyntheticFields.accessible(flatComap, "this$0"),
                        SyntheticFields.accessible(flatComap, "val$function")
                );
            } catch (ReflectiveOperationException | RuntimeException failure) {
                return null;
            }
        }
        
        ///
        /// Gets the given declared field of the given class and makes it accessible.
        ///
        private static Field accessible(Class<?> type, String name) throws NoSuchFieldException {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }
    
    ///
    /// The reader of a DFU codec with layers, which maps the value through the encode function of every layer and then reads the steps in the innermost codec.
    ///
    /// @param layers The encode functions of the layers, outermost first, which return `null` if the value can't be encoded.
    /// @param inner  The reader of the innermost codec.
    /// @param <T>    The type of value the outermost codec encodes.
    ///
    private record LayeredReader<T>(List<Function<Object, @Nullable Object>> layers, PathReader<?> inner) implements PathReader<T> {
        
        ///
        /// The cost of the reader of the innermost codec.
        ///
        @Override
        public PathReader.Cost getCost() {
            return this.inner.getCost();
        }
        
        ///
        /// If the reader of the innermost codec is ordered.
        ///
        @Override
        public boolean isOrdered() {
            return this.inner.isOrdered();
        }
        
        ///
        /// Creates the test that maps the value through every layer and then tests it with the reader of the innermost codec.
        ///
        /// If a layer can't encode the value or throws, the value reads as absent.
        ///
        @Override
        public Predicate<T> bind(Comparison comparison) {
            //noinspection unchecked The innermost codec encodes the values that the last layer returns.
            Predicate<Object> test = (Predicate<Object>) this.inner.bind(comparison);
            return value -> {
                Object mapped = value;
                try {
                    for (int i = 0; i < this.layers.size() && mapped != null; i++) {
                        mapped = this.layers.get(i).apply(mapped);
                    }
                    
                    return mapped != null && test.test(mapped);
                } catch (RuntimeException failure) {
                    return false;
                }
            };
        }
    }
    
    ///
    /// The reader of an index in the lists of a DFU list codec.
    ///
    /// @param minSize The minimum number of elements of a list the list codec encodes.
    /// @param maxSize The maximum number of elements of a list the list codec encodes.
    /// @param index   The index of the element to read.
    /// @param element The reader of the steps after the index in the element.
    /// @param <E>     The type of the elements.
    ///
    private record IndexReader<E>(int minSize, int maxSize, int index, PathReader<E> element) implements PathReader<List<E>> {
        
        ///
        /// The cost of the reader of the element.
        ///
        @Override
        public PathReader.Cost getCost() {
            return this.element.getCost();
        }
        
        ///
        /// If the reader of the element is ordered.
        ///
        @Override
        public boolean isOrdered() {
            return this.element.isOrdered();
        }
        
        ///
        /// Creates the test of the element at the index.
        ///
        /// If the list codec can't encode a list of that size, the list reads as absent.
        ///
        @Override
        public Predicate<List<E>> bind(Comparison comparison) {
            Predicate<E> test = this.element.bind(comparison);
            return list -> list.size() >= this.minSize && list.size() <= this.maxSize && this.index < list.size() && test.test(list.get(this.index));
        }
    }
    
    ///
    /// The reader of a wildcard in the lists of a DFU list codec or in the maps of a DFU map codec.
    ///
    /// @param elements The elements of a value, which are the list itself or the values of the map.
    /// @param minSize  The minimum number of elements the codec encodes.
    /// @param maxSize  The maximum number of elements the codec encodes.
    /// @param element  The reader of the steps after the wildcard in an element.
    /// @param <C>      The type of the lists or maps.
    /// @param <E>      The type of the elements.
    ///
    private record AnyReader<C, E>(Function<C, ? extends Collection<E>> elements, int minSize, int maxSize, PathReader<E> element) implements PathReader<C> {
        
        ///
        /// The cost of the reader of the element, but at least [PathReader.Cost#SCAN].
        ///
        @Override
        public PathReader.Cost getCost() {
            return Comparators.max(PathReader.Cost.SCAN, this.element.getCost());
        }
        
        ///
        /// If the reader of the element is ordered.
        ///
        @Override
        public boolean isOrdered() {
            return this.element.isOrdered();
        }
        
        ///
        /// Creates the test that passes if any element passes.
        ///
        /// If the codec can't encode a value of that size, the value reads as absent.
        ///
        @Override
        public Predicate<C> bind(Comparison comparison) {
            Predicate<E> test = this.element.bind(comparison);
            return value -> {
                Collection<E> values = this.elements.apply(value);
                if (values.size() < this.minSize || values.size() > this.maxSize) {
                    return false;
                }
                
                for (E element : values) {
                    if (test.test(element)) {
                        return true;
                    }
                }
                
                return false;
            };
        }
    }
    
    ///
    /// The reader of a key in the maps of a DFU map codec whose keys are strings or identifiers.
    ///
    /// @param key     The key that encodes to the name of the step, or `null` if no key does.
    /// @param element The reader of the steps after the key in the value.
    /// @param <K>     The type of the keys.
    /// @param <V>     The type of the values.
    ///
    private record KeyReader<K, V>(@Nullable Object key, PathReader<V> element) implements PathReader<Map<K, V>> {
        
        ///
        /// The cost of the reader of the value, but at least [PathReader.Cost#SCAN], since a map that doesn't find a key by `equals` is scanned.
        ///
        @Override
        public PathReader.Cost getCost() {
            return Comparators.max(PathReader.Cost.SCAN, this.element.getCost());
        }
        
        ///
        /// If the reader of the value is ordered.
        ///
        @Override
        public boolean isOrdered() {
            return this.element.isOrdered();
        }
        
        ///
        /// Creates the test of the value under the key.
        ///
        /// The key is looked up with `get` in the maps that compare keys with `equals`, and searched for in every other map.
        ///
        @Override
        public Predicate<Map<K, V>> bind(Comparison comparison) {
            Predicate<V> test = this.element.bind(comparison);
            Object key = this.key;
            if (key == null) {
                return _ -> false;
            }
            
            return map -> {
                if (VanillaAdapters.EQUALS_MAPS.contains(map.getClass()) || map instanceof ImmutableMap<K, V> && !(map instanceof ImmutableSortedMap<K, V>)) {
                    V value = map.get(key);
                    return value != null && test.test(value);
                }
                
                for (Map.Entry<K, V> entry : map.entrySet()) {
                    if (key.equals(entry.getKey())) {
                        return test.test(entry.getValue());
                    }
                }
                
                return false;
            };
        }
    }
    
    ///
    /// The reader of a key in the maps of a DFU map codec, which encodes every key of a map to compare it against the name of the step.
    ///
    /// @param keyCodec The codec of the keys.
    /// @param name     The name of the key.
    /// @param element  The reader of the steps after the key in the value.
    /// @param <K>      The type of the keys.
    /// @param <V>      The type of the values.
    ///
    private record EncodedKeyReader<K, V>(Traversal<K, ?> keyCodec, String name, PathReader<V> element) implements PathReader<Map<K, V>> {
        
        ///
        /// Will always return [PathReader.Cost#ENCODE].
        ///
        @Override
        public PathReader.Cost getCost() {
            return PathReader.Cost.ENCODE;
        }
        
        ///
        /// If the reader of the value is ordered.
        ///
        @Override
        public boolean isOrdered() {
            return this.element.isOrdered();
        }
        
        ///
        /// Creates the test of the value under the last key that encodes to the name.
        ///
        /// If a key could not be encoded, it's skipped.
        ///
        @Override
        public Predicate<Map<K, V>> bind(Comparison comparison) {
            Predicate<V> test = this.element.bind(comparison);
            return map -> {
                V found = null;
                for (Map.Entry<K, V> entry : map.entrySet()) {
                    // A later key overwrites an earlier one in the compound tag that encoding the map builds, so the last one is kept.
                    if (Encoding.encode(this.keyCodec, entry.getKey()) instanceof StringTag(String encoded) && encoded.equals(this.name)) {
                        found = entry.getValue();
                    }
                }
                
                return found != null && test.test(found);
            };
        }
    }
}
