package dev.satherov.nexus.test.unit.api.keybind.activation;

import dev.satherov.nexus.api.keybind.activation.Activation;
import dev.satherov.nexus.api.keybind.activation.KeyTick;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

///
/// Checks the ticks the built-in activations fire on, their progress and the default progress.
///
public class ActivationTest {

    private static List<KeyTick> run(String script) {
        List<KeyTick> ticks = new ArrayList<>();
        boolean wasDown = false;
        int held = 0;
        int idle = 0;
        for (char step : script.toCharArray()) {
            boolean down = step == '#';
            if (down) {
                held++;
            } else {
                held = 0;
                idle = wasDown ? 1 : idle + 1;
            }

            ticks.add(new KeyTick(down, wasDown, held, down && wasDown ? 0 : idle, false));
            wasDown = down;
        }

        return ticks;
    }

    private static List<Integer> firings(Activation activation, String script) {
        List<KeyTick> ticks = ActivationTest.run(script);
        return IntStream.range(0, ticks.size())
                .filter(index -> activation.fires(ticks.get(index)))
                .boxed()
                .toList();
    }

    private static List<Float> progress(Activation activation, String script) {
        return ActivationTest.run(script)
                .stream()
                .map(activation::progress)
                .toList();
    }

    @Test
    public void pressFiresOnTheTickTheKeyGoesDown() {
        Assertions.assertThat(ActivationTest.firings(Activation.press(), "__###__##_#")).containsExactly(2, 7, 10);
    }

    @Test
    public void pressProgressIsOneWhileDown() {
        Assertions.assertThat(ActivationTest.progress(Activation.press(), "_##_")).containsExactly(0.0F, 1.0F, 1.0F, 0.0F);
    }

    @Test
    public void releaseFiresOnTheTickTheKeyGoesUp() {
        Assertions.assertThat(ActivationTest.firings(Activation.release(), "__###__#__")).containsExactly(5, 8);
    }

    @Test
    public void releaseIsSilentOnAnInterruptedTick() {
        Assertions.assertThat(Activation.release().fires(new KeyTick(false, true, 0, 1, true))).isFalse();
        Assertions.assertThat(Activation.release().fires(new KeyTick(false, true, 0, 1, false))).isTrue();
    }

    @Test
    public void heldForFiresOnceOnTheTickTheHoldIsReached() {
        Assertions.assertThat(ActivationTest.firings(Activation.heldFor(3), "_######_##_###")).containsExactly(3, 13);
    }

    @Test
    public void heldForOneFiresOnTheTickTheKeyGoesDown() {
        Assertions.assertThat(ActivationTest.firings(Activation.heldFor(1), "_###_#")).containsExactly(1, 5);
    }

    @Test
    public void heldForProgressGrowsWithTheHoldAndStopsAtOne() {
        Assertions.assertThat(ActivationTest.progress(Activation.heldFor(4), "_######_"))
                .containsExactly(0.0F, 0.25F, 0.5F, 0.75F, 1.0F, 1.0F, 1.0F, 0.0F);
    }

    @Test
    public void repeatFiresOnPressAndThenEveryInterval() {
        Assertions.assertThat(ActivationTest.firings(Activation.repeat(3), "_########_##")).containsExactly(1, 4, 7, 10);
    }

    @Test
    public void repeatOfOneFiresOnEveryTickTheKeyIsDown() {
        Assertions.assertThat(ActivationTest.firings(Activation.repeat(1), "_###__#")).containsExactly(1, 2, 3, 6);
    }

    @Test
    public void repeatProgressIsTheShareOfTheIntervalPassed() {
        Assertions.assertThat(ActivationTest.progress(Activation.repeat(4), "_######_"))
                .containsExactly(0.0F, 1.0F, 0.25F, 0.5F, 0.75F, 1.0F, 0.25F, 0.0F);
    }

    @Test
    public void refusesAHoldBelowOne() {
        Assertions.assertThatThrownBy(() -> Activation.heldFor(0)).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> Activation.heldFor(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void refusesAnIntervalBelowOne() {
        Assertions.assertThatThrownBy(() -> Activation.repeat(0)).isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> Activation.repeat(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void customActivationProgressIsOneWhileDown() {
        Activation never = _ -> false;
        Assertions.assertThat(ActivationTest.progress(never, "_##_")).containsExactly(0.0F, 1.0F, 1.0F, 0.0F);
    }
}
