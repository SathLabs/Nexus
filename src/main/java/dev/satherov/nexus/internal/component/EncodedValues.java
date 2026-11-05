package dev.satherov.nexus.internal.component;

import lombok.experimental.UtilityClass;

import net.neoforged.neoforge.common.CommonHooks;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;

import com.google.common.collect.MapMaker;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.TestOnly;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

///
/// The NBT trees of foreign component values, in one weak, identity-keyed, concurrent map per type.
///
/// The maps are shared by the client and the server thread of a single-player game, and an entry dies with its value object.
///
@UtilityClass
@ApiStatus.Internal
public class EncodedValues {
    
    ///
    /// The NBT registry ops every tree is encoded with, over the registries of the current side.
    ///
    private static final RegistryOps<Tag> OPS = RegistryOps.create(NbtOps.INSTANCE, new CurrentLookup());
    
    ///
    /// The trees by value object, by type.
    ///
    private static final Map<DataComponentType<?>, Map<Object, Tag>> TREES = new ConcurrentHashMap<>();
    
    ///
    /// Encodes the given value with the codec of the given type.
    ///
    private static <VALUE> Tag encode(DataComponentType<VALUE> type, VALUE value) {
        Tag tree = type.codecOrThrow()
                .encodeStart(EncodedValues.OPS, value)
                .getOrThrow(message -> new IllegalStateException("Could not encode the value of '" + type + "', " + message));
        
        // A tag value may be its own tree or a part of it, and the map would then hold its weak key strongly.
        return value instanceof Tag ? tree.copy() : tree;
    }
    
    ///
    /// Gets the NBT tree of the given value, encoded once per value object.
    ///
    /// @param type  The type of the component.
    /// @param value The value of the component.
    ///
    /// @return The NBT tree of the value.
    ///
    /// @throws IllegalStateException If the value could not be encoded.
    ///
    public static <VALUE> Tag tree(DataComponentType<VALUE> type, VALUE value) {
        Map<Object, Tag> trees = EncodedValues.TREES.computeIfAbsent(type, _ -> new MapMaker().weakKeys().makeMap());
        Tag tree = trees.get(value);
        return tree != null ? tree : trees.computeIfAbsent(value, _ -> EncodedValues.encode(type, value));
    }
    
    ///
    /// @param type The type of the component.
    ///
    /// @return `true` if a tree is held for any value of the given type.
    ///
    @TestOnly
    public static boolean holdsAny(DataComponentType<?> type) {
        Map<Object, Tag> trees = EncodedValues.TREES.get(type);
        return trees != null && !trees.isEmpty();
    }
    
    ///
    /// The registry lookup of the current side, resolved per registry on every call.
    ///
    private static final class CurrentLookup implements RegistryOps.RegistryInfoLookup {
        
        ///
        /// The built-in registries, used for a registry that neither the server nor the client has.
        ///
        private static final RegistryAccess BUILT_IN = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        
        ///
        /// Resolves the given registry through [CommonHooks#resolveLookup(ResourceKey)], the server's and then the client's, or through the built-in registries if neither has it.
        ///
        @Override
        public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryKey) {
            //noinspection unchecked Vanilla's own lookup adapter reads the registry of a subtype the same way.
            HolderLookup.RegistryLookup<T> resolved = CommonHooks.resolveLookup((ResourceKey<? extends Registry<T>>) registryKey);
            Optional<? extends HolderLookup.RegistryLookup<T>> lookup = resolved != null ? Optional.of(resolved) : CurrentLookup.BUILT_IN.lookup(registryKey);
            return lookup.map(RegistryOps.RegistryInfo::fromRegistryLookup);
        }
    }
}
