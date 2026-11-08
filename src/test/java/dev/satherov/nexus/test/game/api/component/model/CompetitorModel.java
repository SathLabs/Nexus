package dev.satherov.nexus.test.game.api.component.model;

import dev.satherov.nexus.api.component.ComponentFilter;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

///
/// A model of the item filter of another mod that tests an item stack the way the code of that mod does.
///
/// The component filter is translated once per cell into what the mod evaluates.
///
public interface CompetitorModel {
    
    String getName();
    
    Predicate<ItemStack> build(ComponentFilter filter);
    
    boolean expresses(ComponentFilter filter);
    
    ///
    /// A component getter that forwards to one item stack at a time.
    ///
    /// A component filter tested on it never reads or stores the memo of the item stack.
    /// Meant to be used for the parts of a filter that a mod can't express, after the checks of the mod.
    ///
    /// Should only ever be used on one thread.
    ///
    final class Unmemoized implements DataComponentGetter {
        
        private ItemStack stack = ItemStack.EMPTY;
        
        public boolean test(ComponentFilter filter, ItemStack stack) {
            this.stack = stack;
            return filter.test(this);
        }
        
        @Override
        public <T> @Nullable T get(DataComponentType<? extends T> type) {
            return this.stack.get(type);
        }
    }
}
