package dev.satherov.nexus.gametest.api.client;

import org.junit.platform.commons.annotation.Testable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

///
/// Marks a `public static void` method with exactly one [Client] parameter as a client test.
/// The declaring class must be public, the id `<modid>:<class>/<method>` unique across all mods, and [#maxFrames()]
/// positive. A method breaking any of these is reported as a failed test with the reason.
///
/// The id is the class and method name in snake case, a nested class joined to its outer class with a `.`.
///
@Testable
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ClientTest {

    ///
    /// The frames a test may pump if [#maxFrames()] is not given.
    ///
    int DEFAULT_MAX_FRAMES = 1200;

    ///
    /// The frames the script may pump before the test fails.
    ///
    /// Defaults to {@value #DEFAULT_MAX_FRAMES}.
    ///
    int maxFrames() default ClientTest.DEFAULT_MAX_FRAMES;

    ///
    /// If a failure fails the run.
    ///
    boolean required() default true;
}
