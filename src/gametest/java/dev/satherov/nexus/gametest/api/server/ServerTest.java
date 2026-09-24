package dev.satherov.nexus.gametest.api.server;

import net.minecraft.gametest.framework.GameTestHelper;

import org.jetbrains.annotations.Range;
import org.junit.platform.commons.annotation.Testable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

///
/// Marks a method as a server test.
///
/// The method must be `public` `static`, return `void` and take exactly one [GameTestHelper] parameter.
///
/// The declaring class must be `public` and the generated identifier `<modid>:<class>/<method>` must be unique across all mods.
/// - `<class>` is the class name in snake case. Nested classes are joined with a `.`.
/// - `<method>` is the method name in snake case.
///
@Testable
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ServerTest {
    
    ///
    /// The default structure to use if [#structure()] does not specify anything.
    ///
    String DEFAULT_STRUCTURE = "minecraft:empty";
    
    ///
    /// The default number of ticks a test is allowed to take if [#maxTicks()] does not specify anything.
    ///
    int DEFAULT_MAX_TICKS = 100;
    
    ///
    /// The default number of ticks to wait for the setup to complete before the test runs if [#setupTicks()] does not specify anything.
    ///
    int DEFAULT_SETUP_TICKS = 0;
    
    ///
    /// The structure of the test.
    ///
    /// Defaults to {@value #DEFAULT_STRUCTURE}.
    ///
    /// @return The structure of the test.
    ///
    String structure() default ServerTest.DEFAULT_STRUCTURE;
    
    ///
    /// The maximum number of ticks the test is allowed to take before it fails.
    ///
    /// Defaults to {@value #DEFAULT_MAX_TICKS}.
    ///
    /// @return The maximum number of ticks the test is allowed to take before it fails.
    ///
    @Range(from = 1, to = Integer.MAX_VALUE) int maxTicks() default ServerTest.DEFAULT_MAX_TICKS;
    
    ///
    /// The number of ticks we wait after placing the structure before the actual test runs.
    ///
    /// Defaults to {@value #DEFAULT_SETUP_TICKS}.
    ///
    /// @return The number of ticks we wait after placing the structure before the actual test runs.
    ///
    @Range(from = 0, to = Integer.MAX_VALUE) int setupTicks() default ServerTest.DEFAULT_SETUP_TICKS;
    
    ///
    /// If this test failing causes the entire test-run to fail.
    ///
    /// @return `true` if this test failing causes the entire test-run to fail.
    ///
    boolean required() default true;
    
    ///
    /// If the structure needs access to the sky above it.
    ///
    /// @return `true` if the structure needs access to the sky above it.
    ///
    boolean skyAccess() default false;
}
