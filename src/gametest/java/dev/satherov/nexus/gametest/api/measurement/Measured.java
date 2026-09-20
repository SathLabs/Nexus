package dev.satherov.nexus.gametest.api.measurement;

import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

///
/// Records how long each tick or frame takes once the test's body returned: ticks on a [ServerTest], frames on a
/// [ClientTest]. The numbers go into a file next to the report and never fail the test.
///
/// The body leaves succeeding to the harness: a body that succeeds itself ends the test before the window opens.
///
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Measured {

    ///
    /// The length of the recorded window, in ticks on the server and frames on the client.
    /// Must be positive.
    /// The method must also carry [ServerTest] or [ClientTest].
    /// On a [ServerTest], [ServerTest#maxTicks()] has to hold the window and the two ticks the harness takes beside it.
    ///
    /// @return The length of the recorded window.
    ///
    int value();

    ///
    /// If vanilla's profiler breakdown over the window is written next to the numbers.
    /// The durations of the window then carry the profiler's own cost, so they only compare to another profiled run.
    ///
    /// @return `true` if vanilla's profiler breakdown over the window is written next to the numbers.
    ///
    boolean profile() default false;
}
