package dev.satherov.nexus.test.game.api.component.model;

import lombok.SneakyThrows;

import dev.satherov.nexus.api.component.ComponentFilter;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Predicate;

///
/// A model of the item filter of another mod that tests an item stack the way the code of that mod does.
///
/// The component filter is translated once per cell into what the mod evaluates.
///
/// The parts of a filter that a mod can't express are tested with [#testUnmemoized(ComponentFilter, ItemStack)] after the checks of the mod.
/// The test runs on the item stack and never reads or stores its memo.
///
public interface CompetitorModel {
    
    MethodHandle EVALUATION = CompetitorModel.findEvaluation();
    
    @SneakyThrows(ReflectiveOperationException.class)
    private static MethodHandle findEvaluation() {
        return MethodHandles.privateLookupIn(ComponentFilter.class, MethodHandles.lookup())
                .findVirtual(ComponentFilter.class, "evaluate", MethodType.methodType(boolean.class, DataComponentGetter.class));
    }
    
    String getName();
    
    Predicate<ItemStack> build(ComponentFilter filter);
    
    boolean expresses(ComponentFilter filter);
    
    @SneakyThrows
    static boolean testUnmemoized(ComponentFilter filter, ItemStack stack) {
        return (boolean) CompetitorModel.EVALUATION.invokeExact(filter, (DataComponentGetter) stack);
    }
}
