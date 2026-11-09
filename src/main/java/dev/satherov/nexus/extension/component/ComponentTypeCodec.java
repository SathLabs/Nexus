package dev.satherov.nexus.extension.component;

import net.minecraft.core.component.DataComponentType;

import com.mojang.serialization.Codec;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// Extension interface for the data component types that [DataComponentType.Builder] builds.
///
/// Can be reached by casting a data component type to it.
///
@ApiStatus.Internal
public interface ComponentTypeCodec {

    ///
    /// The codec given to the builder of this type, before it was wrapped by [DataComponentType.Builder#cacheEncoding()].
    ///
    /// @return The codec, or `null` if the type is transient.
    ///
    @Nullable Codec<?> nexus$getCodec();
    
    ///
    /// Sets the codec given to the builder of this type.
    ///
    /// Should only ever be called by Nexus itself.
    ///
    /// @param codec The codec, or `null` if the type is transient.
    ///
    void nexus$setCodec(@Nullable Codec<?> codec);
}
