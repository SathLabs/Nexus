package dev.satherov.nexus.test.game.api.component.scenario;

import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

///
/// A scenario whose rules each run over the item stacks of one source.
///
public abstract class SourcedScenario implements Scenario {
    
    private final Map<Rule, Source> sources = new LinkedHashMap<>();
    private final Set<Rule> rejectOnly = new HashSet<>();
    
    protected static ComponentFilter compare(DataComponentType<?> component, String steps, Comparison.Operator operator, double number) {
        return ComponentFilter.compare(ComponentPath.of(component, steps), new Comparison(operator, new Comparison.Literal.Numeric(number)));
    }
    
    protected static ComponentFilter compare(DataComponentType<?> component, String steps, Comparison.Operator operator, String text) {
        return ComponentFilter.compare(ComponentPath.of(component, steps), new Comparison(operator, new Comparison.Literal.Text(text)));
    }
    
    protected static ComponentFilter compare(DataComponentType<?> component, String steps, Comparison.Operator operator, boolean flag) {
        return ComponentFilter.compare(ComponentPath.of(component, steps), new Comparison(operator, new Comparison.Literal.Flag(flag)));
    }
    
    protected static ComponentFilter enchanted(DataComponentType<ItemEnchantments> component, ResourceKey<Enchantment> enchantment, int level) {
        return SourcedScenario.compare(component, "['" + enchantment.identifier() + "']", Comparison.Operator.GREATER_OR_EQUAL, level);
    }
    
    protected static ComponentFilter rarity(Identifier rarity) {
        return SourcedScenario.compare(TestComponents.RARITY, "", Comparison.Operator.EQUAL, rarity.toString());
    }
    
    protected static ComponentFilter affix(Identifier affix, double level) {
        return SourcedScenario.compare(TestComponents.AFFIXES, "['" + affix + "']", Comparison.Operator.GREATER_OR_EQUAL, level);
    }
    
    protected static int level(ItemStack stack, DataComponentType<ItemEnchantments> component, Holder<Enchantment> enchantment) {
        return stack.getOrDefault(component, ItemEnchantments.EMPTY).getLevel(enchantment);
    }
    
    protected static int damage(ItemStack stack) {
        return stack.getOrDefault(DataComponents.DAMAGE, 0);
    }
    
    protected static boolean isPotion(ItemStack stack, Holder<Potion> potion) {
        return stack.get(DataComponents.POTION_CONTENTS) instanceof PotionContents contents && contents.is(potion);
    }
    
    protected static boolean isNamed(ItemStack stack, String name) {
        return stack.get(DataComponents.CUSTOM_NAME) instanceof Component component && name.equals(component.tryCollapseToString());
    }
    
    protected static CompoundTag customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).tag;
    }
    
    protected static Map<Identifier, Float> affixes(ItemStack stack) {
        return stack.getOrDefault(TestComponents.AFFIXES, Map.of());
    }
    
    protected static float affixLevel(ItemStack stack, Identifier affix) {
        return SourcedScenario.affixes(stack).getOrDefault(affix, 0.0F);
    }
    
    protected static boolean holds(ItemStack stack, DataComponentType<ItemContainerContents> component, Item item) {
        for (ItemStackTemplate template : stack.getOrDefault(component, ItemContainerContents.EMPTY).nonEmptyItems()) {
            if (template.is(item)) {
                return true;
            }
        }
        
        return false;
    }
    
    protected final void add(Source source, Rule... rules) {
        for (Rule rule : rules) {
            this.sources.put(rule, source);
        }
    }
    
    protected final void addRejectOnly(Source source, Rule... rules) {
        this.add(source, rules);
        this.rejectOnly.addAll(List.of(rules));
    }
    
    @Override
    public final List<Rule> getAllRules() {
        return List.copyOf(this.sources.keySet());
    }
    
    @Override
    public final List<ItemStack> stacks(Rule rule, Scenario.Flow flow, int pass) {
        return this.source(rule).stacks(flow);
    }
    
    @Override
    public final List<ItemStack> checkStacks(Rule rule) {
        return this.source(rule).checkStacks();
    }
    
    @Override
    public final boolean isRejectOnly(Rule rule) {
        return this.rejectOnly.contains(rule);
    }
    
    private Source source(Rule rule) {
        Source source = this.sources.get(rule);
        if (source == null) {
            throw new IllegalArgumentException("The rule '" + rule.name() + "' is not part of this scenario");
        }
        
        return source;
    }
}
