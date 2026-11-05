package dev.satherov.nexus.internal.component;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.registry.component.DataComponentBuilder;
import dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters;

import net.minecraft.core.component.DataComponentType;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

///
/// The Nexus codec of every component type built through [DataComponentBuilder] or [VanillaAdapters#asDataComponentType(NexusCodec)].
///
@UtilityClass
@ApiStatus.Internal
public class ComponentCodecs {
    
    ///
    /// The codecs by type.
    ///
    private static final Map<DataComponentType<?>, NexusCodec<?, ?>> CODECS = new ConcurrentHashMap<>();
    
    ///
    /// Records the given codec of the given type.
    ///
    /// Should only ever be called once per type, from [DataComponentBuilder#create()] and [VanillaAdapters#asDataComponentType(NexusCodec)].
    ///
    /// @param type  The type of the component.
    /// @param codec The codec the type was built with.
    ///
    public static void record(DataComponentType<?> type, NexusCodec<?, ?> codec) {
        ComponentCodecs.CODECS.put(type, codec);
    }
    
    ///
    /// The codec of the given type, or `null` if the type is a foreign component.
    ///
    /// @param type The type of the component.
    ///
    /// @return The codec, or `null` for a foreign component.
    ///
    public static @Nullable NexusCodec<?, ?> of(DataComponentType<?> type) {
        return ComponentCodecs.CODECS.get(type);
    }
}
