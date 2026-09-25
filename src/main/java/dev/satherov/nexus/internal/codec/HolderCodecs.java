package dev.satherov.nexus.internal.codec;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.CodecError;
import dev.satherov.nexus.api.codec.CodecException;
import dev.satherov.nexus.api.codec.NexusCodec;

import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.holdersets.HolderSetType;
import net.neoforged.neoforge.registries.holdersets.ICustomHolderSet;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

///
/// Utility for the codecs of holders and holder sets, which look up their registry in the format on every call.
///
@UtilityClass
@ApiStatus.Internal
public class HolderCodecs {
    
    ///
    /// The id a direct holder is written with on the network, in the place of the id of a reference holder plus one.
    ///
    private static final int DIRECT_ID = 0;
    
    ///
    /// Creates the codec behind [NexusCodec#holder(ResourceKey)].
    ///
    /// @param registry The key of the registry.
    ///
    /// @return The codec of the holder.
    ///
    public static <T> Traversal<Holder<T>, Access.Registries> holder(ResourceKey<? extends Registry<T>> registry) {
        return new Traversal<>("holder") {
            
            ///
            /// Writes the id of the holder's value as a VarInt on the network, and the identifier of a reference holder otherwise.
            ///
            @Override
            protected <V> V write(Operations<V> operations, Holder<T> value) {
                if (operations.isPositional()) {
                    return operations.ofVarInt(HolderCodecs.idOf(operations, registry, value));
                }
                
                return Scalars.IDENTIFIER.write(operations, HolderCodecs.keyOf(operations, registry, value).identifier());
            }
            
            ///
            /// Reads the id of a holder as a VarInt on the network, and the identifier of a holder otherwise.
            ///
            @Override
            protected <V> Holder<T> read(Operations<V> operations, V input) {
                if (operations.isPositional()) {
                    return HolderCodecs.byId(operations, registry, operations.asVarInt(input));
                }
                
                return HolderCodecs.byIdentifier(operations, registry, Scalars.IDENTIFIER.read(operations, input));
            }
        };
    }
    
    ///
    /// Creates the codec behind [NexusCodec#holderOrInline(ResourceKey, NexusCodec)].
    ///
    /// @param registry The key of the registry.
    /// @param element  The codec of the value of a direct holder.
    ///
    /// @return The codec of the holder.
    ///
    public static <T> Traversal<Holder<T>, Access.Registries> holderOrInline(ResourceKey<? extends Registry<T>> registry, NexusCodec<T, ? super Access.Registries> element) {
        Traversal<T, ? super Access.Registries> inline = (Traversal<T, ? super Access.Registries>) element;
        return new Traversal<>("holderOrInline") {
            
            ///
            /// Writes a direct holder inline, after `0` on the network.
            /// Writes a reference holder as the id of its value plus one on the network, and as its identifier otherwise.
            ///
            @Override
            protected <V> V write(Operations<V> operations, Holder<T> value) {
                if (value.kind() == Holder.Kind.DIRECT) {
                    if (operations.isPositional()) {
                        operations.ofVarInt(HolderCodecs.DIRECT_ID);
                    }
                    
                    return inline.write(operations, value.value());
                }
                
                if (operations.isPositional()) {
                    return operations.ofVarInt(HolderCodecs.idOf(operations, registry, value) + 1);
                }
                
                return Scalars.IDENTIFIER.write(operations, HolderCodecs.keyOf(operations, registry, value).identifier());
            }
            
            ///
            /// Reads a direct holder after `0` and a reference holder by its id plus one on the network.
            /// Otherwise, it reads a string that is a valid identifier as a reference holder, and anything else as a direct holder.
            ///
            @Override
            protected <V> Holder<T> read(Operations<V> operations, V input) {
                if (operations.isPositional()) {
                    int id = operations.asVarInt(input);
                    return id == HolderCodecs.DIRECT_ID ? Holder.direct(inline.read(operations, input)) : HolderCodecs.byId(operations, registry, id - 1);
                }
                
                String text = HolderCodecs.textOf(input);
                Identifier id = text != null ? Identifier.tryParse(text) : null;
                if (id == null) {
                    return Holder.direct(inline.read(operations, input));
                }
                
                return HolderCodecs.byIdentifier(operations, registry, id);
            }
        };
    }
    
    ///
    /// Creates the codec behind [NexusCodec#holderSet(ResourceKey)].
    ///
    /// @param registry The key of the registry.
    ///
    /// @return The codec of the set.
    ///
    public static <T> Traversal<HolderSet<T>, Access.Registries> holderSet(ResourceKey<? extends Registry<T>> registry) {
        return new HolderSetTraversal<>(registry);
    }
    
    ///
    /// Gets the key of the given reference holder, after checking that it's valid in the registries of the given format.
    ///
    private static <T> ResourceKey<T> keyOf(Operations<?> operations, ResourceKey<? extends Registry<T>> registry, Holder<T> holder) {
        if (!holder.canSerializeIn(HolderCodecs.infoOf(operations, registry).owner())) {
            throw new CodecException("expected a holder of the registry '" + registry.identifier() + "', found one of other registries");
        }
        
        return holder.unwrapKey().orElseThrow(() -> new CodecException("expected a holder of the registry '" + registry.identifier() + "', found a direct holder"));
    }
    
    ///
    /// Gets the reference holder of the given identifier in the given registry of the given format.
    ///
    private static <T> Holder<T> byIdentifier(Operations<?> operations, ResourceKey<? extends Registry<T>> registry, Identifier id) {
        return HolderCodecs.infoOf(operations, registry)
                .getter()
                .get(ResourceKey.create(registry, id))
                .orElseThrow(() -> Errors.mismatch("an element of the registry '" + registry.identifier() + "'", id.toString()));
    }
    
    ///
    /// Gets the given registry in the registries of the given format, and fails naming it if the format doesn't have it.
    ///
    private static <T> RegistryOps.RegistryInfo<T> infoOf(Operations<?> operations, ResourceKey<? extends Registry<T>> registry) {
        RegistryOps.RegistryInfoLookup registries = operations.registries();
        Optional<RegistryOps.RegistryInfo<T>> info = registries != null ? registries.lookup(registry) : Optional.empty();
        return info.orElseThrow(() -> HolderCodecs.inaccessible(registry));
    }
    
    ///
    /// Gets the id of the value of the given holder in the given registry of the given netty format.
    ///
    private static <T> int idOf(Operations<?> operations, ResourceKey<? extends Registry<T>> registry, Holder<T> holder) {
        int id = HolderCodecs.syncedRegistryOf(operations, registry).getId(holder.value());
        if (id == -1) {
            throw new CodecException("expected an element of the registry '" + registry.identifier() + "', found an unregistered value");
        }
        
        return id;
    }
    
    ///
    /// Gets the reference holder of the given id in the given registry of the given netty format.
    ///
    private static <T> Holder<T> byId(Operations<?> operations, ResourceKey<? extends Registry<T>> registry, int id) {
        return HolderCodecs.syncedRegistryOf(operations, registry)
                .get(id)
                .orElseThrow(() -> Errors.mismatch("an id of the registry '" + registry.identifier() + "'", id));
    }
    
    ///
    /// Gets the given registry in the registry access of the given netty format, and fails naming it if it's missing or a built-in registry that isn't synced.
    ///
    private static <T> Registry<T> syncedRegistryOf(Operations<?> operations, ResourceKey<? extends Registry<T>> registry) {
        RegistryAccess access = operations.registryAccess();
        Optional<Registry<T>> found = access != null ? access.lookup(registry) : Optional.empty();
        Registry<T> synced = found.orElseThrow(() -> HolderCodecs.inaccessible(registry));
        if (BuiltInRegistries.REGISTRY.containsKey(registry.identifier()) && !synced.doesSync()) {
            throw new CodecException("could not use the ids of the registry '" + registry.identifier() + "' since it isn't synced");
        }
        
        return synced;
    }
    
    ///
    /// Creates the failure for a registry that the format doesn't have.
    ///
    private static CodecException inaccessible(ResourceKey<?> registry) {
        return new CodecException("could not access the registry '" + registry.identifier() + "'");
    }
    
    ///
    /// Gets the text of the given value if it's a json string or a string tag, or `null` if it's anything else.
    ///
    private static @Nullable String textOf(Object value) {
        return switch (value) {
            case JsonPrimitive primitive when primitive.isString() -> primitive.getAsString();
            case StringTag(String text) -> text;
            default -> null;
        };
    }
    
    ///
    /// The codec of a set of holders of one registry, which leaves NeoForge's custom sets to the codecs of their type.
    ///
    /// @param <T> The type of the values of the registry.
    ///
    private static final class HolderSetTraversal<T> extends Traversal<HolderSet<T>, Access.Registries> {
        
        ///
        /// The number a tag is written with on the network, in the place of the number of holders plus one.
        ///
        private static final int TAG = 0;
        
        ///
        /// The key of the type of a custom set in JSON and NBT.
        ///
        private static final String TYPE_KEY = "type";
        
        ///
        /// The key of the registry.
        ///
        private final ResourceKey<? extends Registry<T>> registry;
        
        ///
        /// The codec of one holder of the set.
        ///
        private final Traversal<Holder<T>, Access.Registries> holder;
        
        ///
        /// The codec of the holders of the set as a list in JSON and NBT, which has no limit, the same as vanilla.
        ///
        private final Traversal<List<Holder<T>>, Access.Registries> list;
        
        ///
        /// NeoForge's codec that writes a custom set in JSON and NBT, with its type under `type`.
        ///
        private final Codec<ICustomHolderSet<T>> custom;
        
        ///
        /// Creates the codec of a set of holders of the given registry.
        ///
        private HolderSetTraversal(ResourceKey<? extends Registry<T>> registry) {
            super("holderSet");
            this.registry = registry;
            
            this.holder = HolderCodecs.holder(registry);
            this.list = CollectionCodecs.list(this.holder, Integer.MAX_VALUE);
            this.custom = NeoForgeRegistries.HOLDER_SET_TYPES.byNameCodec().dispatch(ICustomHolderSet::type, type -> type.makeCodec(registry, RegistryFixedCodec.create(registry), false));
        }
        
        ///
        /// Writes the set in the layout of vanilla on the network.
        /// Otherwise, it writes a tag as `#` and its identifier, one holder bare, any other holders as a list, and a custom set with NeoForge's codec.
        ///
        @Override
        protected <V> V write(Operations<V> operations, HolderSet<T> value) {
            if (operations.isPositional()) {
                return this.writeBuffer(operations, value);
            }
            
            if (!value.canSerializeIn(HolderCodecs.infoOf(operations, this.registry).owner())) {
                throw new CodecException("expected a holder set of the registry '" + this.registry.identifier() + "', found one of other registries");
            }
            
            if (value instanceof ICustomHolderSet<T> set) {
                HolderSetTraversal.requireRegistered(set);
                return this.custom.encodeStart(HolderSetTraversal.registryOps(operations, this.registry), set).getOrThrow(CodecException::new);
            }
            
            return value.unwrap().map(
                    tag -> operations.ofString("#" + tag.location(), FriendlyByteBuf.MAX_STRING_LENGTH),
                    contents -> contents.size() == 1 ? this.holder.write(operations, contents.getFirst()) : this.list.write(operations, contents)
            );
        }
        
        ///
        /// Writes the set in the layout of [ByteBufCodecs#holderSet(ResourceKey)], with a custom set in NeoForge's layout only if the buffer is for a connection to NeoForge.
        ///
        private <V> V writeBuffer(Operations<V> operations, HolderSet<T> value) {
            V buffer = operations.emptyObject();
            if (buffer instanceof RegistryFriendlyByteBuf registryBuffer && registryBuffer.getConnectionType().isNeoForge() && value instanceof ICustomHolderSet<T> set) {
                HolderSetTraversal.requireRegistered(set);
                operations.ofVarInt(-1 - NeoForgeRegistries.HOLDER_SET_TYPES.getId(set.type()));
                HolderSetTraversal.writeCustom(set.type().makeStreamCodec(this.registry), registryBuffer, set);
                return buffer;
            }
            
            Optional<TagKey<T>> tag = value.unwrapKey();
            if (tag.isPresent()) {
                operations.ofVarInt(HolderSetTraversal.TAG);
                return Scalars.IDENTIFIER.write(operations, tag.get().location());
            }
            
            operations.ofVarInt(value.size() + 1);
            for (Holder<T> element : value) {
                this.holder.write(operations, element);
            }
            
            return buffer;
        }
        
        ///
        /// Checks that the type of the given custom set is registered.
        ///
        private static void requireRegistered(ICustomHolderSet<?> set) {
            if (NeoForgeRegistries.HOLDER_SET_TYPES.getKey(set.type()) == null) {
                throw new CodecException("expected a holder set of a registered type, found an unregistered type");
            }
        }
        
        ///
        /// Writes the content of the given custom set with the given stream codec of its type, with anything other than a [CodecException] turned into one.
        ///
        private static <T, S extends ICustomHolderSet<T>> void writeCustom(StreamCodec<RegistryFriendlyByteBuf, S> codec, RegistryFriendlyByteBuf buffer, ICustomHolderSet<T> set) {
            try {
                //noinspection unchecked The stream codec is the one of the set's own type.
                codec.encode(buffer, (S) set);
            } catch (RuntimeException failure) {
                throw failure instanceof CodecException refused ? refused : new CodecException("could not write the custom holder set, " + failure);
            }
        }
        
        ///
        /// Reads a set in the layout of vanilla on the network.
        /// Otherwise, it reads a string as a tag if it starts with `#` and as one holder if it doesn't, an object as a custom set, and anything else as a list.
        ///
        @Override
        protected <V> HolderSet<T> read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                return this.readBuffer(operations, input);
            }
            
            String text = HolderCodecs.textOf(input);
            if (text != null && text.startsWith("#")) {
                Identifier location = Identifier.tryParse(text.substring(1));
                if (location == null) throw Errors.mismatch("a tag", text);
                return this.tagOf(operations, location);
            }
            
            if (text != null) {
                return HolderSet.direct(List.of(this.holder.read(operations, input)));
            }
            
            if (input instanceof JsonObject || input instanceof CompoundTag) {
                return this.readObject(operations, input);
            }
            
            return HolderSet.direct(this.list.read(operations, input));
        }
        
        ///
        /// Reads a custom set from the given object with the codec of its type, and on a strict format refuses the keys that neither `type` nor the codec has.
        ///
        private <V> HolderSet<T> readObject(Operations<V> operations, V object) {
            V encoded = operations.get(object, HolderSetTraversal.TYPE_KEY);
            if (encoded == null) throw new CodecException(List.of(new CodecError(HolderSetTraversal.TYPE_KEY, "missing")));
            
            HolderSetType type;
            try {
                type = HolderSetTraversal.typeOf(Scalars.IDENTIFIER.read(operations, encoded));
            } catch (CodecException failure) {
                throw Errors.prefixKey(failure, HolderSetTraversal.TYPE_KEY);
            }
            
            RegistryOps<V> ops = HolderSetTraversal.registryOps(operations, this.registry);
            MapCodec<? extends ICustomHolderSet<T>> codec = type.makeCodec(this.registry, RegistryFixedCodec.create(this.registry), false);
            if (operations.isStrict()) {
                Set<String> known = codec.keys(ops)
                        .map(key -> operations.asString(key, FriendlyByteBuf.MAX_STRING_LENGTH))
                        .collect(Collectors.toSet());
                List<CodecError> unknown = operations.keys(object)
                        .stream()
                        .filter(key -> !key.equals(HolderSetTraversal.TYPE_KEY) && !known.contains(key))
                        .map(key -> new CodecError(key, "unknown key"))
                        .toList();
                
                if (!unknown.isEmpty()) throw new CodecException(unknown);
            }
            
            return codec.codec().parse(ops, object).getOrThrow(CodecException::new);
        }
        
        ///
        /// Gets the holder set type of the given identifier, and fails listing the identifiers of all types if there is none.
        ///
        private static HolderSetType typeOf(Identifier id) {
            HolderSetType type = NeoForgeRegistries.HOLDER_SET_TYPES.getValue(id);
            if (type == null) {
                throw Errors.unknownName(NeoForgeRegistries.HOLDER_SET_TYPES.keySet().stream().map(Identifier::toString).sorted().toList(), id.toString());
            }
            
            return type;
        }
        
        ///
        /// Reads a set in the layout of [ByteBufCodecs#holderSet(ResourceKey)].
        ///
        private <V> HolderSet<T> readBuffer(Operations<V> operations, V input) {
            int header = operations.asVarInt(input);
            if (header < HolderSetTraversal.TAG) {
                return this.readCustom(input, -1 - header);
            }
            
            if (header == HolderSetTraversal.TAG) {
                return this.tagOf(operations, Scalars.IDENTIFIER.read(operations, input));
            }
            
            int count = header - 1;
            List<Holder<T>> contents = new ArrayList<>(Math.min(count, ByteBufCodecs.MAX_INITIAL_COLLECTION_SIZE));
            for (int i = 0; i < count; i++) {
                contents.add(this.holder.read(operations, input));
            }
            
            return HolderSet.direct(contents);
        }
        
        ///
        /// Reads a custom set with the stream codec of the type with the given id, with anything other than a [CodecException] turned into one.
        ///
        private <V> HolderSet<T> readCustom(V input, int type) {
            HolderSetType found = NeoForgeRegistries.HOLDER_SET_TYPES.byId(type);
            if (found == null) throw Errors.mismatch("the id of a holder set type", type);
            if (!(input instanceof RegistryFriendlyByteBuf buffer)) throw HolderCodecs.inaccessible(this.registry);
            try {
                return found.makeStreamCodec(this.registry).decode(buffer);
            } catch (RuntimeException failure) {
                throw failure instanceof CodecException refused ? refused : new CodecException("could not read the custom holder set, " + failure);
            }
        }
        
        ///
        /// Gets the tag of the given identifier in the registry of the given format.
        ///
        private HolderSet<T> tagOf(Operations<?> operations, Identifier location) {
            return HolderCodecs.infoOf(operations, this.registry)
                    .getter()
                    .get(TagKey.create(this.registry, location))
                    .orElseThrow(() -> Errors.mismatch("a tag of the registry '" + this.registry.identifier() + "'", "#" + location));
        }
        
        ///
        /// Creates the registry ops over the json or NBT ops of the given format, with the registries of the format.
        ///
        private static <V> RegistryOps<V> registryOps(Operations<V> operations, ResourceKey<?> registry) {
            RegistryOps.RegistryInfoLookup registries = operations.registries();
            if (registries == null) throw HolderCodecs.inaccessible(registry);
            //noinspection unchecked The json and NBT formats are over the value types of JsonOps and NbtOps.
            DynamicOps<V> ops = (DynamicOps<V>) (operations instanceof JsonOperations<?> ? JsonOps.INSTANCE : NbtOps.INSTANCE);
            return RegistryOps.create(ops, registries);
        }
    }
}
