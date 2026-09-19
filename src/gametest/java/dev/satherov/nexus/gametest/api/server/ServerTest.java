package dev.satherov.nexus.gametest.api.server;

import net.minecraft.gametest.framework.GameTestHelper;

import org.junit.platform.commons.annotation.Testable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

///
/// Marks a `public static void` method with exactly one [GameTestHelper] parameter as a server test.
/// The declaring class must be public, the id `<modid>:<class>/<method>` unique across all mods, [#maxTicks()]
/// positive, and [#setupTicks()] not negative. A method breaking any of these is reported as a failed test with the
/// reason.
///
/// The id is the class and method name in snake case, a nested class joined to its outer class with a `.`.
///
@Testable
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ServerTest {

    ///
    /// The structure a test runs in if [#structure()] is not given.
    ///
    String DEFAULT_STRUCTURE = "minecraft:empty";

    ///
    /// The ticks a test may take if [#maxTicks()] is not given.
    ///
    int DEFAULT_MAX_TICKS = 100;

    ///
    /// The ticks we wait before the body runs if [#setupTicks()] is not given.
    ///
    int DEFAULT_SETUP_TICKS = 0;

    ///
    /// The structure the test runs in.
    ///
    /// Defaults to {@value #DEFAULT_STRUCTURE}.
    ///
    String structure() default ServerTest.DEFAULT_STRUCTURE;

    ///
    /// The ticks the test may take before it fails.
    ///
    /// Defaults to {@value #DEFAULT_MAX_TICKS}.
    ///
    int maxTicks() default ServerTest.DEFAULT_MAX_TICKS;

    ///
    /// The ticks we wait after placing the structure before the body runs.
    ///
    /// Defaults to {@value #DEFAULT_SETUP_TICKS}.
    ///
    int setupTicks() default ServerTest.DEFAULT_SETUP_TICKS;

    ///
    /// If a failure fails the run.
    ///
    boolean required() default true;

    ///
    /// If the structure needs open sky above it.
    ///
    boolean skyAccess() default false;
}
