package dev.satherov.nexus.gametest.api.client;

import lombok.Getter;
import lombok.experimental.Accessors;

import dev.satherov.nexus.gametest.internal.client.TestWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;

///
/// A client under the control of one test script.
/// Every method runs on the render thread and returns when its work is done.
///
@Accessors(fluent = true)
public final class Client {

    ///
    /// The client itself.
    ///
    @Getter
    private final Minecraft minecraft;

    ///
    /// The keyboard of the client.
    ///
    @Getter
    private final Keyboard keyboard;

    ///
    /// The mouse of the client.
    ///
    @Getter
    private final Mouse mouse;

    ///
    /// The id of the test the client runs.
    ///
    private final Identifier test;

    ///
    /// The frames the script may pump before the test fails.
    ///
    private final int maxFrames;

    ///
    /// The running of one frame.
    ///
    private final Runnable frame;

    ///
    /// The frames the script has pumped so far.
    ///
    private int frames;

    ///
    /// The world the script joined, or `null` if it is in none.
    ///
    private @Nullable TestWorld world;

    ///
    /// Creates the client a [ClientTest] method is called with.
    ///
    /// @param minecraft The client the script runs on.
    /// @param test      The id of the test the client runs.
    /// @param maxFrames The frames the script may pump before the test fails.
    /// @param frame     The running of one frame, called once per [#tick()].
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
    /// Runs one frame.
    /// The game advances exactly one tick in it unless the run is at normal rate.
    ///
    /// Fails the test if the frames it may pump are already spent.
    ///
    public void tick() {
        this.pump();
    }

    ///
    /// Runs the given number of frames.
    ///
    /// Fails the test on the frame past the ones it may pump.
    ///
    /// @param count The frames to run.
    ///
    public void ticks(int count) {
        for (int i = 0; i < count; i++) {
            this.pump();
        }
    }

    ///
    /// Runs frames until the condition holds.
    ///
    /// Fails the test if the frames it may pump run out first, with what was awaited in the failure message.
    ///
    /// @param what      The thing that is waited for.
    /// @param condition The condition, checked before every frame.
    ///
    /// @throws AssertionError If the frames the script may pump run out before the condition holds.
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
    /// @return The screen currently shown, or `null` if there is none.
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
    /// Starts an integrated server on a fresh void level and returns once the player floats in creative, flying, at its spawn with the chunk loaded.
    ///
    public void joinWorld() {
        this.leaveWorld();

        // The field is set before the wait, so a join that gives up is still a world [#leaveWorld()] leaves and deletes.
        this.world = TestWorld.create(this);
        this.world.awaitSpawn();
    }

    ///
    /// Leaves the world and deletes its save.
    /// Does nothing if not in one.
    ///
    public void leaveWorld() {
        if (this.world == null) {
            return;
        }

        this.world.leave();
        this.world = null;
    }

    ///
    /// The overworld of the integrated server.
    ///
    /// @return The overworld of the integrated server.
    ///
    /// @throws IllegalStateException If not in a world.
    ///
    public ServerLevel serverLevel() {
        if (this.world == null) throw new IllegalStateException("the client is not in a world");
        return this.world.overworld();
    }

    ///
    /// Runs one frame against the frames the test may pump.
    ///
    /// @throws AssertionError If the frames the test may pump are already spent.
    ///
    private void pump() {
        if (this.frames >= this.maxFrames) {
            throw new AssertionError("the test ran out of its " + this.maxFrames + " frames");
        }

        this.frames++;
        this.frame.run();
    }
}
