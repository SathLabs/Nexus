package dev.satherov.nexus.internal.codec.path;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.internal.codec.format.NbtOperations;
import dev.satherov.nexus.internal.network.client.ClientConnection;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

///
/// Utility for encoding the parts of a component to NBT with the registries of the current side.
///
/// The registries are the running server's, or the client's if no server is running.
/// A registry that neither of them has is taken from the built-in registries.
///
@UtilityClass
@ApiStatus.Internal
public class Encoding {
    
    ///
    /// The registries while no server is running and the client isn't connected, which are looked up again on every call.
    ///
    private static final SideRegistries UNCONNECTED = new SideRegistries(null);
    
    ///
    /// The registries that were used last, or `null` if none were used yet.
    ///
    private static volatile @Nullable SideRegistries lastRegistries;
    
    ///
    /// Encodes the given value with the given codec to NBT.
    ///
    /// Encoding attempts with the same registries use equal registry ops, so vanilla's encoder cache hits on a repeated attempt.
    ///
    /// @param codec The codec of the value.
    /// @param value The value to encode.
    ///
    /// @return The tag, or `null` if encoding threw any exception.
    ///
    public static <T> @Nullable Tag encode(NexusCodec<T, ?> codec, T value) {
        //noinspection unchecked Every access is a supertype of Access.Registries, the last of the sealed accesses.
        NexusCodec<T, ? super Access.Registries> viewed = (NexusCodec<T, ? super Access.Registries>) codec;
        try {
            return viewed.encode(Encoding.current().format, value);
        } catch (RuntimeException failure) {
            return null;
        }
    }
    
    ///
    /// The owner of the given registry in the registries that [#encode(NexusCodec, Object)] uses right now.
    ///
    /// A holder of the registry only encodes if it can be serialized in this owner.
    /// In singleplayer, the client level has the registries of the integrated server, so a holder of the client can be serialized in them.
    ///
    /// @param registry The resource key of the registry.
    ///
    /// @return The owner of the registry, or `null` if the registries don't have it.
    ///
    public static <T> @Nullable HolderOwner<T> owner(ResourceKey<? extends Registry<T>> registry) {
        return Encoding.current().find(registry);
    }
    
    ///
    /// Gets the registries of the current side and creates new ones only if their source changed.
    ///
    private static SideRegistries current() {
        Object source = Encoding.sourceOfRegistries();
        if (source == null) {
            return Encoding.UNCONNECTED;
        }
        
        SideRegistries registries = Encoding.lastRegistries;
        if (registries == null || registries.source != source) {
            registries = new SideRegistries(source);
            Encoding.lastRegistries = registries;
        }
        
        return registries;
    }
    
    ///
    /// Gets the registry access of the running server, or the listener of the client's play connection if no server is running, or `null` if neither exists.
    ///
    private static @Nullable Object sourceOfRegistries() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        
        if (!FMLEnvironment.getDist().isClient()) {
            return null;
        }
        
        return ClientConnection.tryGetListener();
    }
    
    ///
    /// The registries of one side, with the NBT format over them.
    ///
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class SideRegistries implements RegistryOps.RegistryInfoLookup {
        
        ///
        /// The registry access of the running server or the listener of the client's play connection, or `null` if neither exists.
        ///
        /// Both keep the same registries for as long as they exist.
        ///
        private final @Nullable Object source;
        
        ///
        /// The registries found so far, by their resource key.
        ///
        private final Map<ResourceKey<?>, HolderLookup.RegistryLookup<?>> lookupsByKey = new ConcurrentHashMap<>();
        
        ///
        /// The NBT format over these registries.
        ///
        private final CodecFormat<Tag, Access.Registries> format = new NbtOperations<>(false, this);
        
        ///
        /// Finds the registry under the given key in these registries.
        ///
        @Override
        public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registry) {
            //noinspection unchecked The values of a registry of a subtype of T are values of T.
            HolderLookup.RegistryLookup<T> found = this.find((ResourceKey<? extends Registry<T>>) registry);
            return found != null ? Optional.of(RegistryOps.RegistryInfo.fromRegistryLookup(found)) : Optional.empty();
        }
        
        ///
        /// Finds the given registry and keeps it if these registries have a source.
        ///
        private <T> HolderLookup.@Nullable RegistryLookup<T> find(ResourceKey<? extends Registry<T>> registry) {
            if (this.source == null) {
                return SideRegistries.resolve(registry);
            }
            
            HolderLookup.RegistryLookup<?> found = this.lookupsByKey.get(registry);
            if (found == null) {
                found = SideRegistries.resolve(registry);
                if (found != null) {
                    this.lookupsByKey.put(registry, found);
                }
            }
            
            //noinspection unchecked The lookup was found under the given key.
            return (HolderLookup.RegistryLookup<T>) found;
        }
        
        ///
        /// Finds the given registry through neoforge, or in the built-in registries if neoforge doesn't find it.
        ///
        private static <T> HolderLookup.@Nullable RegistryLookup<T> resolve(ResourceKey<? extends Registry<T>> registry) {
            HolderLookup.RegistryLookup<T> resolved = CommonHooks.resolveLookup(registry);
            if (resolved != null) {
                return resolved;
            }
            
            //noinspection unchecked The built-in registry under the given key holds values of T.
            return (Registry<T>) BuiltInRegistries.REGISTRY.getValue(registry.identifier());
        }
    }
}
