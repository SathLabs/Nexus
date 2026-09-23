package dev.satherov.nexus.gametest.internal.measurement;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

///
/// The result of one test's performance measurement.
///
/// @param test    The identifier of the test we measured.
/// @param nanos   The duration of each frame or tick in nanoseconds, where the index corresponds to the respective tick or frame.
/// @param profile The path to the file that the profiler wrote or `null` if we didn't write anything.
///
@ApiStatus.Internal
public record Measurement(Identifier test, long[] nanos, @Nullable Path profile) { }
