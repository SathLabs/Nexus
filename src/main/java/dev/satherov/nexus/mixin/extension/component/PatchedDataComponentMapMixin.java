package dev.satherov.nexus.mixin.extension.component;

import lombok.SneakyThrows;

import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.extension.component.MemoizedComponentMap;
import dev.satherov.nexus.internal.component.LookupGeneration;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.PatchedDataComponentMap;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.BiPredicate;

///
/// Implements [MemoizedComponentMap] on patched data component maps and counts the changes to them.
///
@Mixin(PatchedDataComponentMap.class)
public abstract class PatchedDataComponentMapMixin implements MemoizedComponentMap {
    
    ///
    /// The handle of [#nexus$stamp].
    ///
    @Unique
    private static final VarHandle nexus$STAMP = PatchedDataComponentMapMixin.nexus$findStamp();
    
    ///
    /// How many changes this map has had so far.
    ///
    @Unique
    private volatile long nexus$writes;
    
    ///
    /// The filter that was tested last on this map, or `null` if there was none.
    ///
    @Unique
    private @Nullable ComponentFilter nexus$lastFilter;
    
    ///
    /// The stamp of the stored result, which guards it as a seqlock and is read and written through [#nexus$STAMP].
    ///
    /// A store claims the stamp by moving it from even to odd and releases it at the next even value.
    /// A read only uses the stored result if the stamp was even and didn't change while reading.
    ///
    @Unique
    private int nexus$stamp;
    
    ///
    /// The filter of the stored result, or `null` if there is no stored result.
    ///
    @Unique
    private @Nullable ComponentFilter nexus$storedFilter;
    
    ///
    /// The holder of the stored result, or `null` if there is no stored result.
    ///
    @Unique
    private @Nullable DataComponentGetter nexus$storedHolder;
    
    ///
    /// The number of changes this map had when the stored result was evaluated.
    ///
    @Unique
    private long nexus$storedWrites;
    
    ///
    /// The [LookupGeneration] when the stored result was evaluated.
    ///
    @Unique
    private int nexus$storedGeneration;
    
    ///
    /// The stored result.
    ///
    @Unique
    private boolean nexus$storedResult;
    
    ///
    /// Finds the handle of the stamp field that this mixin adds.
    ///
    /// @return The handle of the stamp field.
    ///
    /// @throws ReflectiveOperationException If the stamp field could not be found.
    ///
    @Unique
    @SneakyThrows(ReflectiveOperationException.class)
    private static VarHandle nexus$findStamp() {
        return MethodHandles.lookup().findVarHandle(PatchedDataComponentMap.class, "nexus$stamp", int.class);
    }
    
    ///
    /// Tests the given filter with the stored result, or with the given evaluation if there is no stored result to use.
    ///
    /// If another filter was tested last, the given filter will be marked and evaluated without reading or writing the stored result.
    ///
    /// The number of changes to the map and the generation are read before the evaluation and stored with its result.
    ///
    /// @param filter     The filter to test.
    /// @param holder     The holder of this map.
    /// @param evaluation The evaluation of the filter, called with the given filter and holder if there is no stored result to use.
    ///
    /// @return If the holder passes the filter.
    ///
    @Override
    public boolean nexus$test(ComponentFilter filter, DataComponentGetter holder, BiPredicate<ComponentFilter, DataComponentGetter> evaluation) {
        if (this.nexus$lastFilter != filter) {
            this.nexus$lastFilter = filter;
            return evaluation.test(filter, holder);
        }
        
        // The handle is called with the target type because the mixin class can't be loaded at runtime.
        PatchedDataComponentMap self = (PatchedDataComponentMap) (Object) this;
        long writes = this.nexus$writes;
        int generation = LookupGeneration.current();
        int stamp = (int) PatchedDataComponentMapMixin.nexus$STAMP.getAcquire(self);
        boolean matches = this.nexus$storedFilter == filter && this.nexus$storedHolder == holder && this.nexus$storedWrites == writes && this.nexus$storedGeneration == generation;
        boolean stored = this.nexus$storedResult;
        VarHandle.acquireFence();
        boolean consistent = (stamp & 1) == 0 && stamp == (int) PatchedDataComponentMapMixin.nexus$STAMP.getOpaque(self);
        if (matches && consistent) {
            return stored;
        }
        
        boolean result = evaluation.test(filter, holder);
        if (consistent && PatchedDataComponentMapMixin.nexus$STAMP.compareAndSet(self, stamp, stamp + 1)) {
            VarHandle.storeStoreFence();
            this.nexus$storedFilter = filter;
            this.nexus$storedHolder = holder;
            this.nexus$storedWrites = writes;
            this.nexus$storedGeneration = generation;
            this.nexus$storedResult = result;
            PatchedDataComponentMapMixin.nexus$STAMP.setRelease(self, stamp + 2);
        }
        
        return result;
    }
    
    ///
    /// Counts every call to `set` or `remove` on this map.
    ///
    /// @param previous The value before the change, or `null` if there was none.
    ///
    /// @return The value before the change, or `null` if there was none.
    ///
    @ModifyReturnValue(
            method = {
                    "set(Lnet/minecraft/core/component/DataComponentType;Ljava/lang/Object;)Ljava/lang/Object;",
                    "remove"
            },
            at = @At("RETURN")
    )
    private @Nullable Object nexus$countComponentWrite(@Nullable Object previous) {
        //noinspection NonAtomicOperationOnVolatileField Vanilla never writes to one map from two threads at once.
        this.nexus$writes++;
        return previous;
    }
    
    ///
    /// Counts every call to `applyPatch`, `restorePatch`, or `clearPatch` on this map.
    ///
    /// @param callback The callback of the change.
    ///
    @Inject(
            method = {
                    "applyPatch(Lnet/minecraft/core/component/DataComponentPatch;)V",
                    "restorePatch",
                    "clearPatch"
            },
            at = @At("RETURN")
    )
    private void nexus$countPatchWrite(CallbackInfo callback) {
        //noinspection NonAtomicOperationOnVolatileField Vanilla never writes to one map from two threads at once.
        this.nexus$writes++;
    }
}
