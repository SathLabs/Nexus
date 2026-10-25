package dev.satherov.nexus.test.unit.api.tick.ticker;

import dev.satherov.nexus.api.tick.ticker.Next;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

///
/// Checks that [Next#in(int)] accepts one pass and refuses fewer.
///
public class NextTest {
    
    @Test
    public void inAcceptsOnePass() {
        Assertions.assertThat(Next.in(1)).isEqualTo(new Next.In(1));
    }
    
    @Test
    public void inRefusesZeroPasses() {
        Assertions.assertThatThrownBy(() -> Next.in(0)).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void inRefusesNegativePasses() {
        Assertions.assertThatThrownBy(() -> Next.in(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
