///
/// Budgets of time per pass that the jobs of a scope share.
/// - [Budget]: The time per pass of a group of jobs, created through [Scheduler#budget(String, Duration, Duration)].
///
@NullMarked
@CheckReturnValue
package dev.satherov.nexus.api.tick.budget;

import dev.satherov.nexus.api.tick.Scheduler;

import com.google.errorprone.annotations.CheckReturnValue;

import org.jspecify.annotations.NullMarked;

import java.time.Duration;
