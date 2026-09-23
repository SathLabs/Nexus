package dev.satherov.nexus.gametest.api.client;

import org.jetbrains.annotations.Range;
import org.junit.platform.commons.annotation.Testable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

///
/// Marks a method as a client test.
///
/// The method must be `public` `static`, return `void` and take exactly one [Client] parameter.
///
/// The declaring class must be `public` and the generated identifier `<modid>:<class>/<method>` must be unique across all mods.
/// - `<class>` is the class name in snake case. Nested classes are joined with a `.`.
/// - `<method>` is the method name in snake case.
///
@Testable
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ClientTest {
    
    ///
    /// The default number of frames a test is allowed to run for if unspecified.
    ///
    int DEFAULT_MAX_FRAMES = 1200;
    
    ///
    /// The number of frames a test is allowed to run for before it fails.
    ///
    /// Must be positive.
    ///
    /// Defaults to {@value #DEFAULT_MAX_FRAMES}.
    ///
    /// @return The number of frames a test is allowed to run for before it fails.
    ///
    @Range(from = 1, to = Integer.MAX_VALUE) int maxFrames() default ClientTest.DEFAULT_MAX_FRAMES;
    
    ///
    /// If this test failing causes the entire test-run to fail.
    ///
    /// @return `true` if this test failing causes the entire test-run to fail.
    ///
    boolean required() default true;
}
