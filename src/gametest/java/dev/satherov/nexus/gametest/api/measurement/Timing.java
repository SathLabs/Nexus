package dev.satherov.nexus.gametest.api.measurement;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.gametest.internal.server.ServerTests;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.regex.Pattern;

///
/// Utility for recording durations that a server test measures itself,
/// beside the tick window the harness measures for a [Measured] test.
///
/// Can only be used on the server run.
///
@UtilityClass
public class Timing {
    
    ///
    /// The pattern that the name of a series has to match.
    ///
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");
    
    ///
    /// Records the given durations as a series of the test with the given helper.
    ///
    /// The series will be written as `<test>.<name>.json` in the same directory as the measurement file of the test.
    /// The file has the shape of a measurement file and holds the series identifier `<test>.<name>` under `test`,
    /// the durations under `nanos`, and their summary under `summary`.
    ///
    /// If there are no durations, no file will be written and a warning will be logged,
    /// but the name still counts as recorded.
    ///
    /// @param helper The helper of the test that measured the durations.
    /// @param name   The name of the series, unique within the test.
    /// @param nanos  The durations in nanoseconds.
    ///
    /// @throws IllegalArgumentException If the name does not match `[a-z0-9_]+` or
    /// the test already recorded a series under that name.
    ///
    public static void record(GameTestHelper helper, String name, long[] nanos) {
        if (!Timing.NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("The series name '" + name + "' is not valid");
        }
        
        ServerTests.getMeasurements().write(helper.testInfo.id(), name, nanos);
    }
}
