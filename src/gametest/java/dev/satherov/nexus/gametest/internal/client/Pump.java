package dev.satherov.nexus.gametest.internal.client;

import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.gametest.api.measurement.Measured;
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
/// Runs the frames on the render thread, in lockstep while on an integrated server to control exactly when to continue to the next frame.
///
@Slf4j
@ApiStatus.Internal
public final class Pump {
    
    ///
    /// How long the render thread waits for the server before it polls the server state again, in milliseconds.
    ///
    private static final long POLL_MS = 1L;
    
    ///
    /// The pump used for controlling the frame rate.
    ///
    private static volatile @Nullable Pump instance;
    
    ///
    /// The minecraft client whose frames the pump controls.
    ///
    private final Minecraft minecraft;
    
    ///
    /// If vanilla's ticking system is active and the pump is out of effect.
    ///
    private final boolean realtime;
    
    ///
    /// Guards the two flags below to ensure we don't blow stuff up when switching between the render and server thread.
    ///
    private final Object lock = new Object();
    
    ///
    /// `true` if a tick has been released but not yet taken by the server.
    ///
    private boolean released;
    
    ///
    /// `true` if the server thread is currently stuck in purgatory (in [#awaitTick()]).
    ///
    private boolean parked;
    
    ///
    /// The number of frames that the pump has measured already, checked by [Measured#value()]
    ///
    private int frames;
    
    ///
    /// Creates the pump for an accelerated run and ensures that vsync doesn't limit the frame rate.
    ///
    /// @param minecraft The client whose frames the pump runs.
    /// @param realtime  If vanilla's timing runs, so neither the frames nor the server's ticks wait on the pump.
    ///
    public Pump(Minecraft minecraft, boolean realtime) {
        this.minecraft = minecraft;
        this.realtime = realtime;
        
        if (!realtime) {
            // Vsync is the display's own clock: it holds every frame to the refresh rate however fast the pump runs them.
            minecraft.getWindow().updateVsync(false);
            
            // The server thread reads the static, so the pump is published once its fields are set and not before.
            Pump.instance = this;
        }
    }
    
    ///
    /// Gets the pump of the current run or `null` if either no run is active or the run is running in realtime.
    ///
    /// @return The pump of the current run or `null` if either no run is active or the run is running in realtime.
    ///
    public static @Nullable Pump getInstance() {
        return Pump.instance;
    }
    
    ///
    /// Deletes the file of a report that was never saved to ensure that a previous run is not mistakenly interpreted as this run's report.
    ///
    /// @param profile The file of the report that was not written.
    ///
    private static void discardReport(Path profile) {
        try {
            Files.deleteIfExists(profile);
        } catch (IOException failure) {
            Pump.log.warn("Could not delete the profiler report at {}", profile, failure);
        }
    }
    
    ///
    /// If the pump is in control of the server's tick time right now.
    ///
    /// Returns `true` if all the following is true:
    /// - The server is an integrated server.
    /// - The server is running.
    /// - The server is ready.
    /// - The server is not shutting down.
    ///
    /// @param server The server the pump may run the ticks of.
    ///
    /// @return `true` if the pump runs this server's ticks right now.
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
        
        // Gotta do that here manually because we aren't allowing minecraft to poll the events through its usual loop.
        RenderSystem.pollEvents();
        ((MinecraftAccess) this.minecraft).invokeRunTick(true);
    }
    
    ///
    /// Allows the server to run one tick and then waits again.
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
                    // If the server dies while inside a tick, it'll be parked again, so we check on it each time.
                    if (!this.isLockstepped(server)) {
                        return;
                    }
                    
                    this.lock.wait(Pump.POLL_MS);
                }
            } catch (InterruptedException interrupted) {
                Pump.log.warn("The render thread was interrupted. The current frame ran without the lockstep watching over it: ", interrupted.getCause());
            } finally {
                // The frame has to be released here, or the server would end up on a tick that it never asked for.
                this.released = false;
            }
        }
    }
    
    ///
    /// Runs the given number of frames and records how long each frame took.
    ///
    /// If a path was given, vanilla's profiler will take care of the window and its report will be written into the file.
    ///
    /// If the report could not be written, no file will be created.
    ///
    /// @param frames  The number of frames to record for.
    /// @param profile The file the profiler report will be written into, or `null` to just record and write nothing.
    ///
    /// @return An array with every frame's duration in nanoseconds, where the index corresponds to the frame's order.
    ///
    public long[] measure(int frames, @Nullable Path profile) {
        if (profile == null) {
            return this.recordWindow(frames, null);
        }
        
        ActiveProfiler profiler = new ActiveProfiler(Util.timeSource, () -> this.frames, () -> true);
        long[] nanos = this.recordWindow(frames, profiler);
        if (!profiler.getResults().saveResults(profile)) {
            Pump.log.warn("Could not write the profiler report to {}", profile);
            Pump.discardReport(profile);
        }
        
        return nanos;
    }
    
    ///
    /// Runs the given number of frames and collects each ticks duration in nanoseconds.
    ///
    /// If a profiler is given, it will also collect the tick duration.
    /// Its overhead has to be considered when analyzing the results.
    ///
    /// @param frames   The number of frames to run.
    /// @param profiler The profiler the window runs under, or `null` to just record the durations alone.
    ///
    /// @return An array with every frame's duration in nanoseconds, where the index corresponds to the frame's order.
    ///
    private long[] recordWindow(int frames, @Nullable ActiveProfiler profiler) {
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
    /// Called by the integrated server's thread before each tick.
    ///
    /// Stops once the client has released a tick, or immediately if the server is not ready or is stopping.
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
    /// Takes the released tick if there is one.
    /// `true` once the server may tick, whether the render thread released it or the lockstep ended.
    ///
    /// @param server The server that wants to tick.
    ///
    /// @return `true` once the server may tick, whether the render thread released it or the lockstep ended.
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
