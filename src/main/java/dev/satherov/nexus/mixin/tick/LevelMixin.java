package dev.satherov.nexus.mixin.tick;

import dev.satherov.nexus.internal.tick.EntityTickables;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.function.Consumer;

///
/// Binds the declared tickers of the block entities that load in a level.
///
@Mixin(Level.class)
public abstract class LevelMixin {
    
    ///
    /// Binds the declared tickers of every fresh block entity that is not removed and has a level, right after neoforge called its `onLoad`.
    ///
    /// @param entities The fresh block entities.
    /// @param action   The consumer that calls `onLoad` on a fresh block entity.
    /// @param original The wrapped `forEach` over the fresh block entities.
    ///
    @WrapOperation(
            method = "tickBlockEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/ArrayList;forEach(Ljava/util/function/Consumer;)V"
            )
    )
    private void nexus$bindTickers(ArrayList<BlockEntity> entities, Consumer<BlockEntity> action, Operation<Void> original) {
        original.call(entities, action.andThen(entity -> {
            if (!entity.isRemoved() && entity.hasLevel()) {
                EntityTickables.bind(entity);
            }
        }));
    }
}
