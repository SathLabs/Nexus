package dev.satherov.nexus.gametest.internal.measurement;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

///
/// The per-tick or per-frame durations recorded over one test's window.
///
/// @param test    The id of the measured test.
/// @param nanos   The duration of each tick or frame of the window, in nanoseconds, in the order they were recorded.
/// @param profile The saved profiler breakdown, or `null` if none was written.
///
@ApiStatus.Internal
public record Measurement(Identifier test, long[] nanos, @Nullable Path profile) { }
