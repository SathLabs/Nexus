package dev.satherov.nexus.test.game.api.component.scenario;

import net.minecraft.world.item.ItemStack;

import java.util.List;

///
/// A family of rules and the item stacks they run over.
///
public interface Scenario {
    
    List<Rule> getAllRules();
    
    List<ItemStack> stacks(Rule rule, Flow flow, int pass);
    
    List<ItemStack> checkStacks(Rule rule);
    
    boolean isRejectOnly(Rule rule);
    
    ///
    /// The kind of item stacks that a rule runs over on each pass:
    /// - [#SCAN]: The same item stacks on every pass.
    /// - [#FLOW]: Item stacks that no earlier pass has seen.
    /// - [#COPY]: Fresh copies of the scan stacks.
    ///
    enum Flow { SCAN, FLOW, COPY }
}
