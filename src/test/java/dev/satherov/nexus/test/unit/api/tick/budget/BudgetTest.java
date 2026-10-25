package dev.satherov.nexus.test.unit.api.tick.budget;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.budget.Budget;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

///
/// Checks the names and times a scheduler accepts for a budget.
///
public class BudgetTest {
    
    private static final Scheduler SCHEDULER = Nexus.getMod().getScheduler();
    
    @Test
    public void prefixesTheNameWithTheModId() {
        Budget budget = BudgetTest.SCHEDULER.budget("budget_test_prefixed", 1, 1);
        Assertions.assertThat(budget.getName()).isEqualTo("nexus:budget_test_prefixed");
    }
    
    @Test
    public void refusesATakenName() {
        Budget _ = BudgetTest.SCHEDULER.budget("budget_test_taken", 1, 1);
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> BudgetTest.SCHEDULER.budget("budget_test_taken", 1, 1));
    }
    
    @Test
    public void refusesAMinimumBelowTheSmallest() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> BudgetTest.SCHEDULER.budget("budget_test_below", Budget.SMALLEST - 1, 1));
    }
    
    @Test
    public void refusesAMaximumAboveTheLargest() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> BudgetTest.SCHEDULER.budget("budget_test_above", 1, Budget.LARGEST + 1));
    }
    
    @Test
    public void refusesAMinimumAboveTheMaximum() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> BudgetTest.SCHEDULER.budget("budget_test_inverted", 2, 1));
    }
    
    @Test
    public void acceptsTheSmallestAndTheLargest() {
        Budget budget = BudgetTest.SCHEDULER.budget("budget_test_bounds", Budget.SMALLEST, Budget.LARGEST);
        Assertions.assertThat(budget.getMinimum()).isEqualTo(Budget.SMALLEST);
        Assertions.assertThat(budget.getMaximum()).isEqualTo(Budget.LARGEST);
    }
    
    @Test
    public void acceptsAMinimumEqualToTheMaximum() {
        Budget budget = BudgetTest.SCHEDULER.budget("budget_test_equal", 1_000, 1_000);
        Assertions.assertThat(budget.getMinimum()).isEqualTo(1_000);
        Assertions.assertThat(budget.getMaximum()).isEqualTo(1_000);
    }
}
