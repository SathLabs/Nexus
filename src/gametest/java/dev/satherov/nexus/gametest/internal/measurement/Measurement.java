package dev.satherov.nexus.gametest.internal.measurement;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

///
/// The per-tick or per-frame durations recorded over one test's window; `profile` is the saved profiler breakdown or `null`.
///
@ApiStatus.Internal
public record Measurement(Identifier test, long[] nanos, @Nullable Path profile) { }
