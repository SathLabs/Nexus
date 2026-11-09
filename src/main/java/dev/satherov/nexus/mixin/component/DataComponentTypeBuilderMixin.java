package dev.satherov.nexus.mixin.component;

import dev.satherov.nexus.extension.component.ComponentTypeCodec;

import net.minecraft.core.component.DataComponentType;

import com.mojang.serialization.Codec;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

///
/// Keeps the codec that a data component type builder got on the type it builds.
///
@Mixin(DataComponentType.Builder.class)
public abstract class DataComponentTypeBuilderMixin {
    
    ///
    /// The codec that the builder got, or `null` if the type is transient.
    ///
    @Shadow
    private @Nullable Codec<?> codec;
    
    ///
    /// Keeps the codec that the builder got on the built type, before [DataComponentType.Builder#cacheEncoding()] wrapped it.
    ///
    /// @param callback The callback that holds the built type.
    ///
    @Inject(
            method = "build",
            at = @At("RETURN")
    )
    private void nexus$keepCodec(CallbackInfoReturnable<DataComponentType<?>> callback) {
        ((ComponentTypeCodec) callback.getReturnValue()).nexus$setCodec(this.codec);
    }
}
