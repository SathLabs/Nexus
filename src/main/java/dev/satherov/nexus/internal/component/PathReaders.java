package dev.satherov.nexus.internal.component;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.api.event.component.RegisterPathReadersEvent;
import dev.satherov.nexus.extension.component.ComponentTypeCodec;
import dev.satherov.nexus.impl.component.VanillaReaders;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters;
import dev.satherov.nexus.mixin.component.GameDataMixin;

import net.neoforged.fml.ModLoader;

import net.minecraft.core.component.DataComponentType;

import com.google.common.collect.Iterables;
import com.mojang.serialization.Codec;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

///
/// Utility that resolves the reader of a component path, from the readers registered through [RegisterPathReadersEvent] or from the codec of the component.
///
@UtilityClass
@ApiStatus.Internal
public class PathReaders {
    
    ///
    /// The functions registered by component, empty until [#collect()] ran.
    ///
    private static Map<DataComponentType<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> componentReaders = Map.of();
    
    ///
    /// The functions registered by codec, compared by identity, empty until [#collect()] ran.
    ///
    private static Map<Codec<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> codecReaders = Map.of();
    
    ///
    /// If [#collect()] ran.
    ///
    private static boolean collected;
    
    ///
    /// Copies the given registrations into an unmodifiable map that compares its keys by identity.
    ///
    private static <KEY> Map<KEY, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> freeze(
            Map<KEY, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> registrations
    ) {
        Map<KEY, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> frozen = new IdentityHashMap<>();
        registrations.forEach((key, readers) -> frozen.put(key, List.copyOf(readers)));
        return Collections.unmodifiableMap(frozen);
    }
    
    ///
    /// Posts the [RegisterPathReadersEvent], registers Nexus's own readers to it, and then freezes the registrations.
    ///
    /// Nexus's own readers are registered after the event, so a reader that a mod registers for the same path is tried first.
    ///
    /// @see GameDataMixin
    ///
    public static void collect() {
        Map<DataComponentType<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> byComponent = new IdentityHashMap<>();
        Map<Codec<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> byCodec = new IdentityHashMap<>();
        RegisterPathReadersEvent event = new RegisterPathReadersEvent(byComponent, byCodec);
        ModLoader.postEvent(event);
        VanillaReaders.register(event);
        PathReaders.componentReaders = PathReaders.freeze(byComponent);
        PathReaders.codecReaders = PathReaders.freeze(byCodec);
        PathReaders.collected = true;
    }
    
    ///
    /// Resolves the reader of the given steps in the values of the given data component type.
    ///
    /// The functions registered for the type are called before those registered for its codec, and the first reader that isn't `null` is taken.
    /// A codec matches if it's the same object as the codec that the builder of the type got, or as `type.codec()` if the type doesn't keep one.
    ///
    /// Without a registered reader, the reader is created from that codec.
    /// If that reader reads by encoding the value, it encodes with `type.codec()` instead, so vanilla's encoder cache is used for a type built with `cacheEncoding()`.
    ///
    /// The cache matches a value by `equals`, and neoforge compares two holder references only by their kind and resource key.
    /// If a value holds a holder from other registries, it will read like an equal value encoded before it, even though encoding it on its own fails and reads as absent.
    ///
    /// @param type  The data component type that the path starts at.
    /// @param steps The steps of the path.
    ///
    /// @return The reader of the steps.
    ///
    /// @throws IllegalStateException If [#collect()] didn't run yet.
    ///
    public static <VALUE> PathReader<VALUE> resolve(DataComponentType<VALUE> type, List<ComponentPath.Step> steps) {
        if (!PathReaders.collected) {
            throw new IllegalStateException("The path readers are not registered yet");
        }
        
        Codec<VALUE> codec = type.codecOrThrow();
        //noinspection unchecked The builder of the type got a codec of its values.
        Codec<VALUE> built = type instanceof ComponentTypeCodec extension ? (Codec<VALUE>) Objects.requireNonNullElse(extension.nexus$getCodec(), codec) : codec;
        for (Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>> readers : Iterables.concat(
                PathReaders.componentReaders.getOrDefault(type, List.of()),
                PathReaders.codecReaders.getOrDefault(built, List.of())
        )) {
            PathReader<?> reader = readers.apply(steps);
            if (reader != null) {
                //noinspection unchecked The function was registered for this type or for the codec it was built with.
                return (PathReader<VALUE>) reader;
            }
        }
        
        PathReader<VALUE> walk = VanillaAdapters.ofRegistryDfu(built).reader(steps, 0);
        if (built != codec && walk instanceof Traversal.EncodedReader<VALUE>) {
            return VanillaAdapters.ofRegistryDfu(codec).reader(steps, 0);
        }
        
        return walk;
    }
}
