package dev.satherov.nexus.internal.component;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.api.event.component.RegisterPathReadersEvent;
import dev.satherov.nexus.extension.component.ComponentTypeCodec;
import dev.satherov.nexus.impl.component.VanillaReaders;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.vanilla.DfuAdapter;
import dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters;
import dev.satherov.nexus.mixin.component.GameDataMixin;

import net.neoforged.fml.ModLoader;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import com.google.common.collect.Iterables;
import com.mojang.serialization.Codec;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

///
/// Utility that resolves the reader of a component path, from the readers registered through [RegisterPathReadersEvent] or from the codec of the component.
///
/// It also keeps what the text form of a path needs to know about the identifiers of the registered components, found when the registries freeze.
///
@UtilityClass
@ApiStatus.Internal
public class PathReaders {
    
    ///
    /// The functions that mods registered to the event, empty until [#collect()] ran.
    ///
    private static Registered modReaders = Registered.NONE;
    
    ///
    /// The functions that Nexus registered after the event, empty until [#collect()] ran.
    ///
    private static Registered nexusReaders = Registered.NONE;
    
    ///
    /// The length of the longest identifier of a registered data component type.
    ///
    private static int longestIdentifierLength;
    
    ///
    /// The data component types whose identifier another registered identifier starts with, followed by a dot, empty until [#collect()] ran.
    ///
    private static Set<DataComponentType<?>> extendedComponents = Set.of();
    
    ///
    /// If [#collect()] ran.
    ///
    private static boolean collected;
    
    ///
    /// Copies the given registrations into an unmodifiable map that compares its keys by identity.
    ///
    private static <KEY> @UnmodifiableView Map<KEY, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> freeze(
            Map<KEY, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> registrations
    ) {
        Map<KEY, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> frozen = new IdentityHashMap<>();
        registrations.forEach((key, readers) -> frozen.put(key, List.copyOf(readers)));
        return Collections.unmodifiableMap(frozen);
    }
    
    ///
    /// Gets the key that the functions registered for the given codec are kept under.
    ///
    /// Every DFU codec that [NexusCodec#asDfu()] creates from one codec has that codec as its key, and every other codec is its own key.
    ///
    /// @param codec The DFU codec.
    ///
    /// @return The key of the codec, which is compared by identity.
    ///
    public static Object keyOf(Codec<?> codec) {
        return codec instanceof DfuAdapter(Traversal<?, ?> traversal) ? traversal : codec;
    }
    
    ///
    /// Posts the [RegisterPathReadersEvent], registers Nexus's own readers to a second event, and then freezes the registrations.
    ///
    /// The functions of mods are kept apart from Nexus's own, so a reader that a mod registers for the same path is tried first, by component or by codec.
    ///
    /// Also finds the length of the longest identifier of a registered data component type, and the types whose identifier another one extends.
    ///
    /// @see GameDataMixin
    ///
    public static void collect() {
        PathReaders.modReaders = Registered.of(ModLoader::postEvent);
        PathReaders.nexusReaders = Registered.of(VanillaReaders::register);
        
        int longest = 0;
        Set<DataComponentType<?>> extended = new HashSet<>();
        for (Identifier id : BuiltInRegistries.DATA_COMPONENT_TYPE.keySet()) {
            longest = Math.max(longest, id.toString().length());
            for (int dot = id.getPath().indexOf('.'); dot >= 0; dot = id.getPath().indexOf('.', dot + 1)) {
                BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id.withPath(id.getPath().substring(0, dot))).ifPresent(extended::add);
            }
        }
        
        PathReaders.longestIdentifierLength = longest;
        PathReaders.extendedComponents = Set.copyOf(extended);
        PathReaders.collected = true;
    }
    
    ///
    /// Gets the length of the longest identifier of a registered data component type.
    ///
    /// @return The number of characters in the text form of the longest identifier.
    ///
    /// @throws IllegalStateException If [#collect()] didn't run yet.
    ///
    public static int getLongestIdentifierLength() {
        if (!PathReaders.collected) {
            throw new IllegalStateException("The identifiers of the components are not collected yet");
        }
        
        return PathReaders.longestIdentifierLength;
    }
    
    ///
    /// Checks if another registered identifier starts with the identifier of the given data component type, followed by a dot.
    ///
    /// @param type The data component type.
    ///
    /// @return `true` if another registered identifier extends the identifier of the type, and `false` for every type until [#collect()] ran.
    ///
    public static boolean isExtended(DataComponentType<?> type) {
        return PathReaders.extendedComponents.contains(type);
    }
    
    ///
    /// Resolves the reader of the given steps in the values of the given data component type.
    ///
    /// The functions that mods registered for the type and then for its codec are called before the functions that Nexus registered,
    /// in the same order, and the first reader that isn't `null` is taken.
    /// A codec matches if [#keyOf(Codec)] gets the same key for it as for the codec that the builder of the type got, or as for `type.codec()`.
    ///
    /// Without a registered reader, the reader is created from the codec that the builder of the type got, or from `type.codec()` if the type doesn't keep one.
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
                PathReaders.modReaders.readersOf(type, built, codec),
                PathReaders.nexusReaders.readersOf(type, built, codec)
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
    
    ///
    /// The functions registered to one [RegisterPathReadersEvent].
    ///
    /// @param byComponent The functions registered by component.
    /// @param byCodec     The functions registered by codec, under the key that [PathReaders#keyOf(Codec)] gets for the codec.
    ///
    private record Registered(
            Map<DataComponentType<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> byComponent,
            Map<Object, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> byCodec
    ) {
        
        ///
        /// No functions at all, which is what is registered until [PathReaders#collect()] ran.
        ///
        private static final Registered NONE = new Registered(Map.of(), Map.of());
        
        ///
        /// Runs the given registration with a new event and freezes what it registered.
        ///
        private static Registered of(Consumer<RegisterPathReadersEvent> registration) {
            Map<DataComponentType<?>, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> byComponent = new IdentityHashMap<>();
            Map<Object, List<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>>> byCodec = new IdentityHashMap<>();
            registration.accept(new RegisterPathReadersEvent(byComponent, byCodec));
            return new Registered(PathReaders.freeze(byComponent), PathReaders.freeze(byCodec));
        }
        
        ///
        /// Gets the functions registered for the given data component type, then for the codec that its builder got, and then for `type.codec()`.
        ///
        private Iterable<Function<List<ComponentPath.Step>, ? extends @Nullable PathReader<?>>> readersOf(DataComponentType<?> type, Codec<?> built, Codec<?> codec) {
            return Iterables.concat(
                    this.byComponent.getOrDefault(type, List.of()),
                    this.byCodec.getOrDefault(PathReaders.keyOf(built), List.of()),
                    built != codec ? this.byCodec.getOrDefault(PathReaders.keyOf(codec), List.of()) : List.of()
            );
        }
    }
}
