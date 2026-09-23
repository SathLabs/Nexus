package dev.satherov.nexus.gametest.api.client;

import lombok.Getter;
import lombok.experimental.Accessors;

import dev.satherov.nexus.gametest.internal.client.Pump;
import dev.satherov.nexus.gametest.internal.client.TestWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

///
/// The client abstraction that a [ClientTest] can control.
///
/// Every method in here runs on the render thread.
///
@Accessors(fluent = true)
public final class Client {
    
    ///
    /// The minecraft client instance.
    ///
    @Getter
    private final Minecraft minecraft;
    
    ///
    /// The fake keyboard of the client.
    ///
    @Getter
    private final Keyboard keyboard;
    
    ///
    /// The fake mouse of the client.
    ///
    @Getter
    private final Mouse mouse;
    
    ///
    /// The identifier of the test the client runs.
    ///
    private final Identifier test;
    
    ///
    /// The maximum number of frames the client may run for before the test fails.
    ///
    private final int maxFrames;
    
    ///
    /// The executor of a single frame.
    ///
    /// @see Pump#frame()
    ///
    private final Runnable frame;
    
    ///
    /// How many frames have been rendered so far.
    ///
    private int frames;
    
    ///
    /// The world that the client joined, or `null` if it is not in one.
    ///
    private @Nullable TestWorld world;
    
    ///
    /// Creates the client a [ClientTest] method is called with.
    ///
    /// @param minecraft The minecraft client instance the test runs on.
    /// @param test      The id of the test the client runs.
    /// @param maxFrames The maximum number of frames the client may run for before the test fails.
    /// @param frame     The executor of a single frame, called once per [#tick()].
    ///
    @ApiStatus.Internal
    public Client(Minecraft minecraft, Identifier test, int maxFrames, Runnable frame) {
        this.minecraft = minecraft;
        this.test = test;
        this.maxFrames = maxFrames;
        this.frame = frame;
        
        this.keyboard = new Keyboard(this);
        this.mouse = new Mouse(this);
    }
    
    ///
    /// Runs the game for one frame.
    ///
    /// Fails the test if we are not allowed to run any more frames.
    ///
    public void tick() {
        this.pump();
    }
    
    ///
    /// Runs the game for the given number of frames.
    ///
    /// Fails the test if we are not allowed to run any more frames.
    ///
    /// @param count The frames to run.
    ///
    public void ticks(int count) {
        for (int i = 0; i < count; i++) {
            this.pump();
        }
    }
    
    ///
    /// Runs the game until the given condition returns `true`.
    ///
    /// Fails the test if we are not allowed to run any more frames.
    ///
    /// @param what      The thing that is waited for, used in the failure message.
    /// @param condition The condition, checked before every frame.
    ///
    /// @throws AssertionError If we are not allowed to run any more frames, before the condition is `true`.
    ///
    public void until(String what, BooleanSupplier condition) {
        while (!condition.getAsBoolean()) {
            if (this.frames >= this.maxFrames) {
                throw new AssertionError("gave up waiting for " + what + " after " + this.maxFrames + " frames");
            }
            
            this.pump();
        }
    }
    
    ///
    /// The screen currently shown.
    ///
    /// @return The screen currently shown, or `null` if there isn't one.
    ///
    public @Nullable Screen screen() {
        return this.minecraft.screen;
    }
    
    ///
    /// The last rendered frame.
    ///
    /// @return The last rendered frame.
    ///
    public Capture capture() {
        return Capture.from(this.test, this.minecraft.getMainRenderTarget());
    }
    
    ///
    /// Creates a new fresh void world running on an integrated server and then enters it.
    ///
    /// The player will be in creative mode, flying at its spawnpoint.
    ///
    public void joinWorld() {
        this.leaveWorld();
        this.world = TestWorld.create(this);
        this.world.awaitSpawn();
    }
    
    ///
    /// Leaves the world we are currently in and deletes any save files.
    ///
    /// Does nothing if we are not in a world already.
    ///
    public void leaveWorld() {
        if (this.world == null) {
            return;
        }
        
        this.world.leave();
        this.world = null;
    }
    
    ///
    /// The overworld level of the player world.
    ///
    /// @return The overworld level of the player world.
    ///
    /// @throws IllegalStateException If we are not currently in a world.
    ///
    public ServerLevel serverLevel() {
        if (this.world == null) {
            throw new IllegalStateException("The client is not currently in any world");
        }
        
        return this.world.overworld();
    }
    
    ///
    /// Runs one frame if we are allowed to.
    ///
    /// @throws AssertionError If we are not allowed to run any more frames.
    ///
    private void pump() {
        if (this.frames >= this.maxFrames) {
            throw new AssertionError("The test '" + this.test + "' ran out of frames, it's only allowed to run for '" + this.maxFrames + "' frames");
        }
        
        this.frames++;
        this.frame.run();
    }
}
