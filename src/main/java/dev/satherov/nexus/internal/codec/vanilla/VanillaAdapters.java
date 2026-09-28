package dev.satherov.nexus.internal.codec.vanilla;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.BufferFormat;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.format.JsonOperations;
import dev.satherov.nexus.internal.codec.format.NbtOperations;
import dev.satherov.nexus.internal.codec.format.Operations;
import dev.satherov.nexus.internal.codec.struct.Structs;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
    public static <T> Codec<T> asDfu(NexusCodec<T, ?> codec) {
        NexusCodec<T, ? super Access.Registries> viewed = VanillaAdapters.onAnyFormat(codec);
        return new Codec<>() {
            
            ///
            /// Decodes the input with the codec and returns the empty value of the ops as the rest of the input.
            ///
            @Override
            public <V> DataResult<Pair<T, V>> decode(DynamicOps<V> ops, @Nullable V input) {
                try {
                    return DataResult.success(Pair.of(VanillaAdapters.decode(viewed, ops, input), ops.empty()));
                } catch (NexusCodecException failure) {
                    return DataResult.error(failure::getMessage);
                }
            }
            
            ///
            /// Encodes the value with the codec and fails if the prefix isn't the empty value of the ops.
            ///
            @Override
            public <V> DataResult<V> encode(T input, DynamicOps<V> ops, V prefix) {
                try {
                    return ops.mergeToPrimitive(prefix, VanillaAdapters.toOps(ops, VanillaAdapters.encodeFor(viewed, ops, input)));
                } catch (NexusCodecException failure) {
                    return DataResult.error(failure::getMessage);
                }
            }
        };
    }
    
    ///
    /// Creates the map codec behind [StructCodec#asMapCodec()].
    ///
    /// @param struct The struct to view.
    ///
    /// @return The map codec, which fails with the message of a [NexusCodecException] and without a partial result.
    ///
    public static <T> MapCodec<T> asMapCodec(Structs.Inlinable<T, ?> struct) {
        NexusCodec<T, ? super Access.Registries> viewed = VanillaAdapters.onAnyFormat(struct);
        return new MapCodec<>() {
            
            ///
            /// Creates every key the struct may write, in the given ops.
            ///
            @Override
            public <V> Stream<V> keys(DynamicOps<V> ops) {
                return struct.keys().stream().map(ops::createString);
            }
            
            ///
            /// Decodes the struct from an object of every entry of the input whose value isn't `null`.
            ///
            @Override
            public <V> DataResult<T> decode(DynamicOps<V> ops, MapLike<V> input) {
                try {
                    // JavaOps reads a null value the same as a missing key, and its maps can't hold one.
                    V object = ops.createMap(input.entries().filter(entry -> entry.getSecond() != null));
                    return DataResult.success(VanillaAdapters.decode(viewed, ops, object));
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
                    object = VanillaAdapters.encodeFor(viewed, ops, input);
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
        };
    }
    
    ///
    /// Encodes the given value with the given codec in NBT over the values of NBT, and in json over any other values, into a tag or a json element.
    ///
    private static <T> Object encodeFor(NexusCodec<T, ? super Access.Registries> codec, DynamicOps<?> ops, T value) {
        if (ops.compressMaps()) throw new NexusCodecException(VanillaAdapters.COMPRESSED);
        RegistryOps.RegistryInfoLookup registries = VanillaAdapters.registriesOf(ops);
        if (ops.empty() instanceof Tag) {
            return codec.encode(new NbtOperations<Access.Registries>(false, registries), value);
        }
        
        return codec.encode(new JsonOperations<Access.Registries>(false, registries), value);
    }
    
    ///
    /// Gets the given tag or json element as a value of the given ops, converted from json if the ops are over any other values.
    ///
    private static <V> @Nullable V toOps(DynamicOps<V> ops, Object encoded) {
        // JavaOps's empty value is null, so the switch has to accept it.
        return switch (ops.empty()) {
            case JsonElement _, Tag _ -> //noinspection unchecked The values of the ops are of the type of its empty value.
                    (V) encoded;
            case null, default -> VanillaAdapters.fromJson(ops, (JsonElement) encoded);
        };
    }
    
    ///
    /// Converts the given json element into a value of the given ops, keeping the type of every number that has one.
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
    /// Runs the json or NBT format directly if the ops are over its values, and converts to json otherwise.
    ///
    private static <T, V> T decode(NexusCodec<T, ? super Access.Registries> codec, DynamicOps<V> ops, @Nullable V input) {
        if (ops.compressMaps()) throw new NexusCodecException(VanillaAdapters.COMPRESSED);
        RegistryOps.RegistryInfoLookup registries = VanillaAdapters.registriesOf(ops);
        return switch (ops.empty()) {
            case JsonElement _ -> codec.decode(new JsonOperations<Access.Registries>(false, registries), Objects.requireNonNullElse((JsonElement) input, JsonNull.INSTANCE));
            case Tag _ -> codec.decode(new NbtOperations<Access.Registries>(false, registries), Objects.requireNonNullElse((Tag) input, EndTag.INSTANCE));
            case null, default -> codec.decode(new JsonOperations<Access.Registries>(false, registries), ops.convertTo(JsonOps.INSTANCE, input));
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
    }
}
