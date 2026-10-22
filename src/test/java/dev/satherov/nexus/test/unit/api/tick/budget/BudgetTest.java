package dev.satherov.nexus.test.unit.api.tick.budget;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.tick.Scheduler;
import dev.satherov.nexus.api.tick.budget.Budget;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

///
/// Checks the names and durations a scheduler accepts for a budget.
///
public class BudgetTest {
    
    private static final Scheduler SCHEDULER = Nexus.getMod().getScheduler();
    
    @Test
    public void prefixesTheNameWithTheModId() {
        Budget budget = BudgetTest.SCHEDULER.budget("budget_test_prefixed", Duration.ZERO, Duration.ZERO);
        Assertions.assertThat(budget.getName()).isEqualTo("nexus:budget_test_prefixed");
    }
    
    @Test
    public void refusesATakenName() {
        Budget _ = BudgetTest.SCHEDULER.budget("budget_test_taken", Duration.ZERO, Duration.ZERO);
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> BudgetTest.SCHEDULER.budget("budget_test_taken", Duration.ZERO, Duration.ZERO));
    }
    
    @Test
    public void refusesANegativeMinimum() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> BudgetTest.SCHEDULER.budget("budget_test_negative", Duration.ofMillis(-1L), Duration.ofMillis(1L)));
    }
    
    @Test
    public void refusesAMinimumAboveTheMaximum() {
        Assertions.assertThatIllegalArgumentException().isThrownBy(() -> BudgetTest.SCHEDULER.budget("budget_test_inverted", Duration.ofMillis(2L), Duration.ofMillis(1L)));
    }
    
    @Test
    public void acceptsZeroDurations() {
        Budget budget = BudgetTest.SCHEDULER.budget("budget_test_zero", Duration.ZERO, Duration.ZERO);
        Assertions.assertThat(budget.getMinimum()).isEqualTo(Duration.ZERO);
        Assertions.assertThat(budget.getMaximum()).isEqualTo(Duration.ZERO);
    }
    
    @Test
    public void acceptsAMinimumEqualToTheMaximum() {
        Budget budget = BudgetTest.SCHEDULER.budget("budget_test_equal", Duration.ofMillis(1L), Duration.ofMillis(1L));
        Assertions.assertThat(budget.getMinimum()).isEqualTo(Duration.ofMillis(1L));
        Assertions.assertThat(budget.getMaximum()).isEqualTo(Duration.ofMillis(1L));
    }
}
