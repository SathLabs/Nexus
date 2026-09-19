package dev.satherov.nexus.gametest.internal.option;

import dev.satherov.zelqro.mapping.ObjectMapping;

import net.minecraft.resources.Identifier;

import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/// The system properties a run was started with.
@ApiStatus.Internal
public record RunOptions(@Nullable String tests, boolean realtime, boolean show, @Nullable Path compare, boolean record, @Nullable Path goldens, Path report) {

    /// The id selector; `*` and `?` are wildcards.
    public static final String TESTS = "nexus.gametest.tests";
    /// `true` for the normal tick rate.
    public static final String REALTIME = "nexus.gametest.realtime";
    /// `true` for a visible client window.
    public static final String SHOW = "nexus.gametest.show";
    /// The directory holding an earlier run's measurement files.
    public static final String COMPARE = "nexus.gametest.compare";
    /// `true` to write goldens instead of checking them.
    public static final String RECORD = "nexus.gametest.record";
    /// The directory goldens are recorded into.
    public static final String GOLDENS = "nexus.gametest.goldens";
    /// The report file of the client run.
    public static final String REPORT = "nexus.gametest.report";
    /// The report file when [#REPORT] is not set.
    public static final Path DEFAULT_REPORT = Path.of("gametest-report.xml");

    /// Reads the `nexus.gametest.*` properties.
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

    private static @Nullable Path path(String property) {
        return ObjectMapping.mapNonNull(System.getProperty(property), Path::of);
    }

    /// If the selector matches the id; everything matches when there is no selector.
    public boolean selects(Identifier id) {
        return this.tests == null || FilenameUtils.wildcardMatch(id.toString(), this.tests);
    }
}
