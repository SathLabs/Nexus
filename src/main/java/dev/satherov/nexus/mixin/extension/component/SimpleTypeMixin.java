package dev.satherov.nexus.mixin.extension.component;

import dev.satherov.nexus.extension.component.ComponentTypeCodec;

import com.mojang.serialization.Codec;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

///
/// Implements [ComponentTypeCodec] on the data component types that a builder builds.
///
@Mixin(targets = "net.minecraft.core.component.DataComponentType$Builder$SimpleType")
public abstract class SimpleTypeMixin implements ComponentTypeCodec {
    
    ///
    /// The codec that the builder of this type got, or `null` if the type is transient.
    ///
    @Unique
    private @Nullable Codec<?> nexus$codec;
    
    ///
    /// The codec that the builder of this type got.
    ///
    /// @return The codec, or `null` if the type is transient.
    ///
    @Override
    public @Nullable Codec<?> nexus$getCodec() {
        return this.nexus$codec;
    }
    
    ///
    /// Sets the codec that the builder of this type got.
    ///
    /// @param codec The codec, or `null` if the type is transient.
    ///
    @Override
    public void nexus$setCodec(@Nullable Codec<?> codec) {
        this.nexus$codec = codec;
    }
}
