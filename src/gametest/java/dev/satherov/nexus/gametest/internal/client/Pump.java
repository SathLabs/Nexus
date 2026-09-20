package dev.satherov.nexus.gametest.internal.client;

import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.gametest.mixin.MinecraftAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.ActiveProfiler;
import net.minecraft.util.profiling.Profiler;

import com.mojang.blaze3d.systems.RenderSystem;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

///
/// Drives frames on the render thread: one tick per frame in lockstep with the integrated server, or vanilla's timing at normal rate.
///
@Slf4j
@ApiStatus.Internal
public final class Pump {

    ///
    /// How long the render thread waits for the server before it looks at the server's state again, in milliseconds.
    ///
    private static final long POLL_MS = 1L;

    ///
    /// The pump of the run that owns the clock, while a run does.
    ///
    private static volatile @Nullable Pump accelerated;

    ///
    /// The client the pump drives.
    ///
    private final Minecraft minecraft;

    ///
    /// If vanilla's timing runs, so the pump drives neither the frames nor the server.
    ///
    private final boolean realtime;

    ///
    /// Guards the two flags below and carries the handover between the render thread and the server thread.
    ///
    private final Object lock = new Object();

    ///
    /// A tick the render thread released and the server has not taken yet.
    ///
    private boolean released;

    ///
    /// If the server thread sits in [#awaitTick()].
    ///
    private boolean parked;

    ///
    /// The frames the pump has measured, which a profiled window counts its ticks by.
    ///
    private int frames;

    ///
    /// Takes the clock for an accelerated run: vsync goes off, and the mixins find the pump through the static.
    ///
    public Pump(Minecraft minecraft, boolean realtime) {
        this.minecraft = minecraft;
        this.realtime = realtime;

        if (!realtime) {
            // Vsync is the display's own clock: it holds every frame to the refresh rate however fast the pump drives them.
            minecraft.getWindow().updateVsync(false);

            // The server thread reads the static, so the pump is published once its fields are set and not before.
            Pump.accelerated = this;
        }
    }

    ///
    /// The pump of the run that owns the clock, or `null` if the run goes at the normal rate or no run is active.
    ///
    public static @Nullable Pump getAccelerated() {
        return Pump.accelerated;
    }

    ///
    /// If the pump drives this server's ticks right now; it drives none of another server's, and none of this one's while it starts up or stops.
    ///
    public boolean isLockstepped(MinecraftServer server) {
        return server == this.minecraft.getSingleplayerServer() && server.isReady() && server.isRunning() && !server.isShutdown();
    }

    ///
    /// Runs one frame: releases one server tick, waits until the server waits again, then runs the client tick and renders.
    ///
    /// The wait covers the server's tick and not its packets: the local connection carries them on netty's own threads, so a packet
    /// written late in that tick is handled in one of the frames after it.
    ///
    public void frame() {
        if (!this.realtime) {
            this.releaseTick();
        }

        // The run owns the loop, so nothing else polls the window's events.
        RenderSystem.pollEvents();
        ((MinecraftAccess) this.minecraft).invokeRunTick(true);
    }

    ///
    /// Lets the server run one tick and returns once it has run it and waits again.
    ///
    private void releaseTick() {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server == null || !this.isLockstepped(server)) {
            return;
        }

        synchronized (this.lock) {
            try {
                this.released = true;
                this.lock.notifyAll();

                while (this.released || !this.parked) {
                    // A server that halts or dies inside its tick never parks again, so every round looks at it instead of waiting for a wakeup.
                    if (!this.isLockstepped(server)) {
                        return;
                    }

                    this.lock.wait(Pump.POLL_MS);
                }
            } catch (InterruptedException interrupted) {
                // The flag is left cleared, the way vanilla's own frame limiter leaves it, so one interrupt does not throw out of every later wait.
                Pump.log.warn("the render thread was interrupted, so this frame ran without the lockstep");
            } finally {
                // A frame that gives up takes its release back, so no later server starts on a tick no frame asked for.
                this.released = false;
            }
        }
    }

    ///
    /// Runs the given number of frames recording each one's duration.
    ///
    /// With a file, vanilla's profiler covers the window and its breakdown goes into that file, one tick per frame; the
    /// durations of such a window carry the profiler's own cost.
    ///
    /// A breakdown that could not be written leaves no file behind.
    ///
    /// @param frames  The frames the window records.
    /// @param profile The file the profiler breakdown goes into, or `null` to record the durations alone.
    ///
    /// @return The duration of every frame of the window, in nanoseconds, in order, oldest first.
    ///
    public long[] measure(int frames, @Nullable Path profile) {
        if (profile == null) {
            return this.window(frames, null);
        }

        ActiveProfiler profiler = new ActiveProfiler(Util.timeSource, () -> this.frames, () -> true);
        long[] nanos = this.window(frames, profiler);
        if (!profiler.getResults().saveResults(profile)) {
            Pump.log.warn("Can't write the profiler breakdown of the window to {}", profile);
            Pump.discardBreakdown(profile);
        }

        return nanos;
    }

    ///
    /// Deletes the file of a breakdown that was not written, so an earlier run's is never left behind to be read as this window's.
    ///
    private static void discardBreakdown(Path profile) {
        try {
            Files.deleteIfExists(profile);
        } catch (IOException failure) {
            Pump.log.warn("Can't delete the profiler breakdown at {}", profile, failure);
        }
    }

    ///
    /// Runs the frames of one window and returns each one's duration, in nanoseconds; with a profiler, every frame is one of its ticks.
    ///
    private long[] window(int frames, @Nullable ActiveProfiler profiler) {
        long[] nanos = new long[frames];
        for (int frame = 0; frame < frames; frame++) {
            this.frames++;

            long started = Util.getNanos();
            if (profiler == null) {
                this.frame();
            } else {
                try (Profiler.Scope _ = Profiler.use(profiler)) {
                    this.frame();
                }
            }

            nanos[frame] = Util.getNanos() - started;
        }

        return nanos;
    }

    ///
    /// Called by the integrated server's thread before each tick; returns once the client has released a tick, or at once while the server is not ready or is stopping.
    ///
    public void awaitTick() {
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (server == null || !this.isLockstepped(server)) {
            return;
        }

        synchronized (this.lock) {
            this.parked = true;
            this.lock.notifyAll();
        }

        // Vanilla's own wait runs the server's tasks, and the render thread blocks on one of them to disconnect, so this wait runs them too.
        server.managedBlock(() -> this.takeTick(server));
    }

    ///
    /// Takes the released tick if there is one; `true` once the server may tick, whether the render thread released it or the lockstep ended.
    ///
    private boolean takeTick(IntegratedServer server) {
        synchronized (this.lock) {
            if (!this.released && this.isLockstepped(server)) {
                return false;
            }

            this.released = false;
            this.parked = false;
            this.lock.notifyAll();
            return true;
        }
    }
}
