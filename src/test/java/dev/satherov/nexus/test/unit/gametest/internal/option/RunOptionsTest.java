package dev.satherov.nexus.test.unit.gametest.internal.option;

import dev.satherov.nexus.gametest.internal.option.RunOptions;

import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

///
/// Checks that [RunOptions] reads every property and matches identifiers against the selector.
///
public class RunOptionsTest {
    
    private static final List<String> PROPERTIES = List.of(RunOptions.TESTS, RunOptions.REALTIME, RunOptions.SHOW, RunOptions.COMPARE, RunOptions.RECORD, RunOptions.GOLDENS, RunOptions.REPORT);
    
    @AfterEach
    public void clearProperties() {
        RunOptionsTest.PROPERTIES.forEach(System::clearProperty);
    }
    
    @Test
    public void readsEveryProperty() {
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
    public void defaultsEveryPropertyThatIsNotSet() {
        Assertions.assertThat(RunOptions.fromProperties()).isEqualTo(new RunOptions(null, false, false, null, false, null, RunOptions.DEFAULT_REPORT));
    }
    
    @Test
    public void selectsEverythingWithoutASelector() {
        Assertions.assertThat(RunOptionsTest.selecting(null).selects(Identifier.fromNamespaceAndPath("nexus", "some/test"))).isTrue();
    }
    
    @Test
    public void matchesAnyCharactersWithAStar() {
        RunOptions options = RunOptionsTest.selecting("nexus:some/*");
        Assertions.assertThat(options.selects(Identifier.fromNamespaceAndPath("nexus", "some/test"))).isTrue();
        Assertions.assertThat(options.selects(Identifier.fromNamespaceAndPath("nexus", "other/test"))).isFalse();
    }
    
    @Test
    public void matchesOneCharacterWithAQuestionMark() {
        RunOptions options = RunOptionsTest.selecting("nexus:some/tes?");
        Assertions.assertThat(options.selects(Identifier.fromNamespaceAndPath("nexus", "some/test"))).isTrue();
        Assertions.assertThat(options.selects(Identifier.fromNamespaceAndPath("nexus", "some/tests"))).isFalse();
    }
    
    @Test
    public void matchesTheNamespace() {
        RunOptions options = RunOptionsTest.selecting("nexus:*");
        Assertions.assertThat(options.selects(Identifier.fromNamespaceAndPath("nexus", "some/test"))).isTrue();
        Assertions.assertThat(options.selects(Identifier.fromNamespaceAndPath("other", "some/test"))).isFalse();
    }
    
    private static RunOptions selecting(@Nullable String tests) {
        return new RunOptions(tests, false, false, null, false, null, RunOptions.DEFAULT_REPORT);
    }
}
