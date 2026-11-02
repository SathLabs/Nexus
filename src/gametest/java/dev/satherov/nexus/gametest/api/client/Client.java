package dev.satherov.nexus.gametest.api.client;

import lombok.Getter;
import lombok.experimental.Accessors;

import dev.satherov.nexus.gametest.internal.client.Pump;
import dev.satherov.nexus.gametest.internal.client.TestWorld;

import net.neoforged.fml.ModList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import net.irisshaders.iris.api.v0.IrisApi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Range;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    /// Checks if the given captures have the same size and the same color in every pixel.
    ///
    /// @param first  The first capture.
    /// @param second The second capture.
    ///
    /// @return `true` if both captures are identical.
    ///
    private static boolean isIdentical(Capture first, Capture second) {
        if (first.width() != second.width() || first.height() != second.height()) {
            return false;
        }
        
        for (int y = 0; y < first.height(); y++) {
            for (int x = 0; x < first.width(); x++) {
                if (first.pixel(x, y) != second.pixel(x, y)) {
                    return false;
                }
            }
        }
        
        return true;
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
    /// Runs the game until the given number of consecutive frames are identical, pixel for pixel.
    /// The last frame rendered before this call counts as the first.
    ///
    /// Fails the test if we are not allowed to run any more frames.
    ///
    /// @param frames The number of consecutive frames that have to be identical.
    ///
    /// @throws IllegalArgumentException If the frames are below `2`.
    /// @throws AssertionError           If we are not allowed to run any more frames, before the frames are identical.
    ///
    public void awaitStill(@Range(from = 2, to = Integer.MAX_VALUE) int frames) {
        //noinspection ConstantValue Nothing enforces the range at runtime.
        if (frames < 2) {
            throw new IllegalArgumentException("Frame count '" + frames + "' is below 2");
        }
        
        AtomicReference<@Nullable Capture> previous = new AtomicReference<>();
        AtomicInteger identical = new AtomicInteger();
        this.until("'" + frames + "' identical frames in a row", () -> {
            Capture current = this.capture();
            Capture before = previous.getAndSet(current);
            identical.set(before != null && Client.isIdentical(before, current) ? identical.get() + 1 : 1);
            return identical.get() >= frames;
        });
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
    /// No screen will be open.
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
        return this.world().overworld();
    }
    
    ///
    /// Hides or shows the vanilla HUD for the rest of the test.
    ///
    /// @param hidden If the HUD should be hidden.
    ///
    public void hideHud(boolean hidden) {
        this.minecraft.options.hideGui = hidden;
    }
    
    ///
    /// @return `true` if iris is loaded and a shader pack is in use through it.
    ///
    public boolean hasShaderPack() {
        return ModList.get().isLoaded("iris") && IrisApi.getInstance().isShaderPackInUse();
    }
    
    ///
    /// Puts the player at the given position looking along the given yaw and pitch, on the server and the client,
    /// and then runs the game until the camera of the client is there.
    ///
    /// Fails the test if we are not allowed to run any more frames.
    ///
    /// @param position The position to put the player at.
    /// @param yaw      The horizontal rotation of the player, in degrees.
    /// @param pitch    The vertical rotation of the player, in degrees.
    ///
    /// @throws IllegalStateException If we are not currently in a world.
    /// @throws AssertionError        If we are not allowed to run any more frames, before the camera is there.
    ///
    public void pose(Vec3 position, float yaw, float pitch) {
        this.world().pose(position, yaw, pitch);
    }
    
    ///
    /// Runs the game until the client has handled everything the server sent up to the end of its next tick,
    /// and then until the sections near the camera are rebuilt.
    ///
    /// Every change made on the server before this call will be visible on the client.
    /// A change to a block within 10 blocks of the camera will also be visible in the next capture.
    ///
    /// If sodium is loaded, one more frame will run,
    /// in which sodium rebuilds every section whose center is within 16 blocks of the camera.
    ///
    /// Fails the test if we are not allowed to run any more frames.
    ///
    /// @throws IllegalStateException If we are not currently in a world.
    /// @throws AssertionError        If we are not allowed to run any more frames, before the client has handled the packets and
    ///                               rebuilt the sections.
    ///
    public void settle() {
        this.world().settle();
    }
    
    ///
    /// The world the client is currently in.
    ///
    /// @return The world the client is currently in.
    ///
    /// @throws IllegalStateException If we are not currently in a world.
    ///
    private TestWorld world() {
        if (this.world == null) {
            throw new IllegalStateException("The client is not currently in any world");
        }
        
        return this.world;
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
