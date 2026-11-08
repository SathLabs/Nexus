package dev.satherov.nexus.test.game.api.component.scenario;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

///
/// A source of the item stacks of each flow and of the check stacks of its rules.
///
public final class Source {
    
    private final int size;
    private final int checkSize;
    private final long seed;
    private final BiConsumer<RandomSource, List<ItemStack>> dropper;
    private final RandomSource flowRandom;
    private @Nullable List<ItemStack> inventory;
    private @Nullable List<ItemStack> checks;
    
    public Source(String name, int size, int checkSize, BiConsumer<RandomSource, List<ItemStack>> dropper) {
        this.size = size;
        this.checkSize = checkSize;
        this.seed = name.hashCode();
        this.dropper = dropper;
        this.flowRandom = RandomSource.create(this.seed + 1L);
    }
    
    public List<ItemStack> stacks(Scenario.Flow flow) {
        return switch (flow) {
            case SCAN -> this.inventory();
            case FLOW -> this.drops(this.flowRandom, this.size);
            case COPY -> this.inventory()
                    .stream()
                    .map(ItemStack::copy)
                    .toList();
        };
    }
    
    public List<ItemStack> checkStacks() {
        if (this.checks == null) {
            this.checks = this.drops(RandomSource.create(this.seed + 2L), this.checkSize);
        }
        
        return this.checks;
    }
    
    private List<ItemStack> drops(RandomSource random, int count) {
        List<ItemStack> drops = new ArrayList<>();
        while (drops.size() < count) {
            this.dropper.accept(random, drops);
        }
        
        return List.copyOf(drops.subList(0, count));
    }
    
    private List<ItemStack> inventory() {
        if (this.inventory != null) {
            return this.inventory;
        }
        
        RandomSource random = RandomSource.create(this.seed);
        List<ItemStack> slots = new ArrayList<>();
        Map<ItemStack, ItemStack> open = new Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
        List<ItemStack> drops = new ArrayList<>();
        while (slots.size() < this.size) {
            this.dropper.accept(random, drops);
            for (ItemStack drop : drops) {
                ItemStack slot = open.get(drop);
                if (slot != null) {
                    int moved = Math.min(drop.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(moved);
                    drop.shrink(moved);
                    if (slot.getCount() == slot.getMaxStackSize()) {
                        open.remove(slot);
                    }
                }
                
                if (!drop.isEmpty() && slots.size() < this.size) {
                    slots.add(drop);
                    if (drop.getCount() < drop.getMaxStackSize()) {
                        open.put(drop, drop);
                    }
                }
            }
            
            drops.clear();
        }
        
        this.inventory = List.copyOf(slots);
        return this.inventory;
    }
}
