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
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Measured {

    ///
    /// The length of the recorded window, in ticks on the server and frames on the client.
    /// Must be positive; the method must also carry [ServerTest] or [ClientTest].
    ///
    int value();

    ///
    /// If vanilla's profiler breakdown over the window is written next to the numbers.
    ///
    boolean profile() default false;
}
