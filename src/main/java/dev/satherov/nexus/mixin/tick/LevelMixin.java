package dev.satherov.nexus.mixin.tick;

import dev.satherov.nexus.internal.tick.EntityTickables;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

///
/// Binds the declared tickers of the block entities that load in a level.
///
@Mixin(Level.class)
public abstract class LevelMixin {
    
    ///
    /// The block entities that loaded since the last tick.
    ///
    @Shadow
    @Final
    private ArrayList<BlockEntity> freshBlockEntities;
    
    ///
    /// Binds the declared tickers of every fresh block entity that is not removed and has a level, after neoforge called its `onLoad`.
    ///
    /// @param callback The callback of the injection.
    ///
    @Inject(
            method = "tickBlockEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/ArrayList;forEach(Ljava/util/function/Consumer;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void bindTickers(CallbackInfo callback) {
        for (BlockEntity entity : this.freshBlockEntities) {
            if (!entity.isRemoved() && entity.hasLevel()) {
                EntityTickables.bind(entity);
            }
        }
    }
}
