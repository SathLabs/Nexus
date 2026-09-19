package dev.satherov.nexus.test.gametest.internal.option;

import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

///
/// Checks that [RunOptions] reads every property and matches ids against the selector.
///
public class RunOptionsTest {

    private static final List<String> PROPERTIES = List.of(RunOptions.TESTS, RunOptions.REALTIME, RunOptions.SHOW, RunOptions.COMPARE, RunOptions.RECORD, RunOptions.GOLDENS, RunOptions.REPORT);

    @AfterEach
    void clearProperties() {
        RunOptionsTest.PROPERTIES.forEach(System::clearProperty);
    }

    @Test
    void readsDefaultsWithoutProperties() {
        Assertions.assertThat(RunOptions.fromProperties()).isEqualTo(new RunOptions(null, false, false, null, false, null, RunOptions.DEFAULT_REPORT));
    }

    @Test
    void readsEveryProperty() {
        System.setProperty(RunOptions.TESTS, "nexus:*");
        System.setProperty(RunOptions.REALTIME, "true");
        System.setProperty(RunOptions.SHOW, "true");
        System.setProperty(RunOptions.COMPARE, "earlier");
        System.setProperty(RunOptions.RECORD, "true");
        System.setProperty(RunOptions.GOLDENS, "goldens");
        System.setProperty(RunOptions.REPORT, "report.xml");
        Assertions.assertThat(RunOptions.fromProperties()).isEqualTo(new RunOptions("nexus:*", true, true, Path.of("earlier"), true, Path.of("goldens"), Path.of("report.xml")));
    }

    @Test
    void selectsEverythingWithoutSelector() {
        Assertions.assertThat(new RunOptions(null, false, false, null, false, null, RunOptions.DEFAULT_REPORT).selects(Identifier.fromNamespaceAndPath("nexus", "some/test"))).isTrue();
    }

    @Test
    void selectsByWildcards() {
        RunOptions options = new RunOptions("nexus:some/*", false, false, null, false, null, RunOptions.DEFAULT_REPORT);
        Assertions.assertThat(options.selects(Identifier.fromNamespaceAndPath("nexus", "some/test"))).isTrue();
        Assertions.assertThat(options.selects(Identifier.fromNamespaceAndPath("nexus", "other/test"))).isFalse();
        Assertions.assertThat(options.selects(Identifier.fromNamespaceAndPath("other", "some/test"))).isFalse();
    }
}
