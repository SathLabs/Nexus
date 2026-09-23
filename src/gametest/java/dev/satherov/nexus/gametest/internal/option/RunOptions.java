package dev.satherov.nexus.gametest.internal.option;

import dev.satherov.zelqro.mapping.ObjectMapping;

import net.minecraft.resources.Identifier;

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

///
/// The system properties that configure a run.
///
/// @param tests    All selected test identifiers to run, or `null` to run all tests.
/// @param realtime If the ticks / frames should run as fast as the computer can or at minecraft's regular rates.
/// @param show     If the client window is visible.
/// @param compare  The directory of an earlier run's measurement files, or `null` if we don't want to compare anything.
/// @param record   If golden images should be recoded instead of compared against.
/// @param goldens  The directory that the goldens are recorded into or `null` if none was given.
/// @param report   The path to place the report file of the run at.
///
@ApiStatus.Internal
public record RunOptions(@Nullable String tests, boolean realtime, boolean show, @Nullable Path compare, boolean record, @Nullable Path goldens, Path report) {
    
    ///
    /// The identifiers of the tests to run.
    ///
    /// `*` and `?` are wildcard selectors.
    ///
    public static final String TESTS = "nexus.gametest.tests";
    
    ///
    /// `true` to run the game at the normal tick rate instead.
    ///
    public static final String REALTIME = "nexus.gametest.realtime";
    
    ///
    /// `true` to show the client window.
    ///
    public static final String SHOW = "nexus.gametest.show";
    
    ///
    /// The directory where the files of an earlier measurement are stored.
    ///
    public static final String COMPARE = "nexus.gametest.compare";
    
    ///
    /// `true` to record goldens instead of comparing against them.
    ///
    public static final String RECORD = "nexus.gametest.record";
    
    ///
    /// The directory golden images are written into.
    ///
    public static final String GOLDENS = "nexus.gametest.goldens";
    
    ///
    /// The path to place the report file of the run at.
    ///
    public static final String REPORT = "nexus.gametest.report";
    
    ///
    /// The report file if [#REPORT] is not set.
    ///
    public static final Path DEFAULT_REPORT = Path.of("gametest-report.xml");
    
    ///
    /// Reads the `nexus.gametest` properties.
    ///
    /// @return The options the run was started with.
    ///
    public static RunOptions fromProperties() {
        return new RunOptions(
                System.getProperty(RunOptions.TESTS),
                Boolean.getBoolean(RunOptions.REALTIME),
                Boolean.getBoolean(RunOptions.SHOW),
                RunOptions.path(RunOptions.COMPARE),
                Boolean.getBoolean(RunOptions.RECORD),
                RunOptions.path(RunOptions.GOLDENS),
                Objects.requireNonNullElse(RunOptions.path(RunOptions.REPORT), RunOptions.DEFAULT_REPORT)
        );
    }
    
    ///
    /// The path that the given property points to, or `null` if it is not set.
    ///
    /// @param property The name of the system property.
    ///
    /// @return The path that the given property points to, or `null` if it is not set.
    ///
    private static @Nullable Path path(String property) {
        return ObjectMapping.mapNonNull(System.getProperty(property), Path::of);
    }
    
    ///
    /// If the given identifier matches any of the configured selectors.
    ///
    /// @param id The identifier of the test.
    ///
    /// @return `true` if the given identifier matches any of the configured selectors.
    ///
    public boolean selects(Identifier id) {
        return this.tests == null || FilenameUtils.wildcardMatch(id.toString(), this.tests);
    }
}
