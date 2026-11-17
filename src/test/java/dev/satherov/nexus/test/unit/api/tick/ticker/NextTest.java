package dev.satherov.nexus.test.unit.api.tick.ticker;

import dev.satherov.nexus.api.tick.ticker.Next;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

///
/// Checks that [Next#in(int)] and [Next.In] accept one pass and refuse fewer.
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
    
    @Test
    public void constructorAcceptsOnePass() {
        Assertions.assertThat(new Next.In(1).ticks()).isEqualTo(1);
    }
    
    @Test
    public void constructorRefusesZeroPasses() {
        Assertions.assertThatThrownBy(() -> new Next.In(0)).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void constructorRefusesNegativePasses() {
        Assertions.assertThatThrownBy(() -> new Next.In(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
