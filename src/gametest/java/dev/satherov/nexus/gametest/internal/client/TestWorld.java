package dev.satherov.nexus.gametest.internal.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.internal.level.VoidLevel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
import net.minecraft.world.clock.ClockTimeMarkers;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.io.UncheckedIOException;

///
/// A joined void level: created under a unique name, deleted on leave.
///
@ApiStatus.Internal
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestWorld {

    ///
    /// The save name a join asks for.
    /// Vanilla numbers it where a save of that name is already there.
    ///
    private static final String SAVE_NAME = "nexus-gametest";

    ///
    /// The client the level was joined on.
    ///
    private final Client client;

    ///
    /// The name of the save the level lives in.
    ///
    private final String name;

    ///
    /// Creates the level and starts joining it.
    /// The player is only in it once [#awaitSpawn()] returns.
    ///
    /// @param client The client the level is joined on.
    ///
    /// @return The level the client is joining.
    ///
    public static TestWorld create(Client client) {
        Minecraft minecraft = client.minecraft();
        String name = TestWorld.freeName(minecraft.getLevelSource());

        // Vanilla spins the render thread inside this call until the server is ready, so the load costs the test no frames.
        minecraft.createWorldOpenFlows().createFreshLevel(
                name,
                VoidLevel.settings(name),
                VoidLevel.OPTIONS,
                VoidLevel::dimensions,
                new TitleScreen()
        );

        return new TestWorld(client, name);
    }

    ///
    /// A save name no directory under the level source uses yet.
    ///
    /// @param source The level source the save goes under.
    ///
    /// @return A save name no directory under the level source uses yet.
    ///
    /// @throws UncheckedIOException If the level source can't be read.
    ///
    private static String freeName(LevelStorageSource source) {
        try {
            return FileUtil.findAvailableName(source.getBaseDir(), TestWorld.SAVE_NAME, "");
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to name a test world under " + source.getBaseDir(), failure);
        }
    }

    ///
    /// Pumps frames until the player floats at the level's spawn with the chunk around it loaded, in a level that no longer advances by itself.
    ///
    public void awaitSpawn() {
        this.freeze();
        this.client.until("the player to log in", () -> !this.overworld().players().isEmpty());
        this.floatAtSpawn();
        this.client.until("the player to float at the spawn", () -> TestWorld.isFloating(this.client.minecraft()));
    }

    ///
    /// Stops the level's time and its weather and moves its clock to noon, so the same frame of a test looks the same on every run of one machine.
    ///
    /// Setting a rule broadcasts the clock to everyone, so the server thread takes the write, and the pumping of the next frames is what gives it the task.
    /// The wait is what has the level frozen before the join goes on, and not in a frame a test has already looked at.
    ///
    private void freeze() {
        ServerLevel level = this.overworld();
        MinecraftServer server = level.getServer();
        Holder<WorldClock> clock = server.registryAccess().getOrThrow(WorldClocks.OVERWORLD);

        server.execute(() -> {
            GameRules rules = level.getGameRules();
            rules.set(GameRules.ADVANCE_TIME, false, server);
            rules.set(GameRules.ADVANCE_WEATHER, false, server);
            level.clockManager().moveToTimeMarker(clock, ClockTimeMarkers.NOON);
        });

        this.client.until("the level to freeze at noon", () -> this.isFrozen(clock));
    }

    ///
    /// If the level advances neither its time nor its weather, and its clock stands at noon.
    ///
    /// @param clock The level's clock.
    ///
    /// @return `true` if the level advances neither its time nor its weather, and its clock stands at noon.
    ///
    private boolean isFrozen(Holder<WorldClock> clock) {
        ServerLevel level = this.overworld();
        GameRules rules = level.getGameRules();
        return !rules.get(GameRules.ADVANCE_TIME) && !rules.get(GameRules.ADVANCE_WEATHER) && level.clockManager().isAtTimeMarker(clock, ClockTimeMarkers.NOON);
    }

    ///
    /// Puts the player at the level's spawn and has it fly, since a void level has nothing to stand on.
    ///
    private void floatAtSpawn() {
        ServerLevel level = this.overworld();
        ServerPlayer player = level.players().getFirst();
        Vec3 spawn = level.getRespawnData().pos().getBottomCenter();

        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(spawn.x, spawn.y, spawn.z);
    }

    ///
    /// If the player flies in the level and the chunk it floats in has arrived.
    ///
    /// @param minecraft The client the player is on.
    ///
    /// @return `true` if the player flies in the level and the chunk it floats in has arrived.
    ///
    private static boolean isFloating(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return player != null && player.getAbilities().flying && player.level().isLoaded(player.blockPosition());
    }

    ///
    /// The overworld of the integrated server the level runs on.
    ///
    /// @return The overworld of the integrated server the level runs on.
    ///
    /// @throws IllegalStateException If the integrated server is gone.
    ///
    public ServerLevel overworld() {
        IntegratedServer server = this.client.minecraft().getSingleplayerServer();
        if (server == null) throw new IllegalStateException("the test world's server is gone");
        return server.overworld();
    }

    ///
    /// Disconnects, pumps until the title screen is back, deletes the save.
    ///
    public void leave() {
        try {
            // Vanilla spins the render thread inside the disconnect until the server has saved, stopped and unlocked the save.
            this.client.minecraft().disconnect(new TitleScreen(), false);
            this.client.until("the title screen", () -> this.client.screen() instanceof TitleScreen);
        } finally {
            this.deleteSave();
        }
    }

    ///
    /// Deletes the save the level was created in.
    ///
    /// @throws UncheckedIOException If the save can't be deleted.
    ///
    private void deleteSave() {
        try (LevelStorageSource.LevelStorageAccess access = this.client.minecraft().getLevelSource().createAccess(this.name)) {
            access.deleteLevel();
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to delete the test world '" + this.name + "'", failure);
        }
    }
}
