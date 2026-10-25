package dev.satherov.nexus.mixin.tick;

import dev.satherov.nexus.internal.tick.Bindings;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

///
/// Drops the tickers bound to a block entity once it is removed, which includes the unload of its chunk.
///
@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {
    
    ///
    /// Cancels and forgets every ticker bound to this block entity.
    ///
    /// @param callback The callback of the injection.
    ///
    @Inject(
            method = "setRemoved",
            at = @At("TAIL")
    )
    private void nexus$dropTickers(CallbackInfo callback) {
        Bindings.drop((BlockEntity) (Object) this);
    }
}
