package dev.satherov.nexus.gametest.api.measurement;

import dev.satherov.nexus.gametest.api.client.ClientTest;
import dev.satherov.nexus.gametest.api.server.ServerTest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

///
/// Can be used to profile how long a specific action took to execute.
///
/// Must be placed on a method with either of the following:
/// - [ServerTest]: Measures the time a specific number of `ticks` took to execute.
/// - [ClientTest]: Measures the time a specific number of `frames` took to execute.
///
/// The results can optionally be written to a file.
///
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Measured {
    
    ///
    /// The number of ticks or frames to record for.
    ///
    /// Must be positive.
    ///
    /// @return The length of the window to record.
    ///
    int value();
    
    ///
    /// If vanilla's default result should be written to the file as well.
    ///
    /// This will add the duration it takes vanilla to write the results as well, so you should only compare those results to another one with this flag.
    ///
    /// @return `true` if vanilla's profiler report should be written down as well.
    ///
    boolean profile() default false;
}
