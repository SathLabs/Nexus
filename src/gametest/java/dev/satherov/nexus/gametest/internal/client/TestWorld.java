package dev.satherov.nexus.gametest.internal.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.internal.level.VoidLevel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FileUtil;
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
    /// The save name a join asks for; vanilla numbers it where a save of that name is already there.
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
    /// Creates the level and starts joining it; the player is only in it once [#awaitSpawn()] returns.
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
    private static String freeName(LevelStorageSource source) {
        try {
            return FileUtil.findAvailableName(source.getBaseDir(), TestWorld.SAVE_NAME, "");
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to name a test world under " + source.getBaseDir(), failure);
        }
    }

    ///
    /// Pumps frames until the player floats at the level's spawn with the chunk around it loaded.
    ///
    public void awaitSpawn() {
        this.client.until("the player to log in", () -> !this.overworld().players().isEmpty());
        this.floatAtSpawn();
        this.client.until("the player to float at the spawn", () -> TestWorld.isFloating(this.client.minecraft()));
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
    private static boolean isFloating(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return player != null && player.getAbilities().flying && player.level().isLoaded(player.blockPosition());
    }

    ///
    /// The overworld of the integrated server the level runs on.
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
    private void deleteSave() {
        try (LevelStorageSource.LevelStorageAccess access = this.client.minecraft().getLevelSource().createAccess(this.name)) {
            access.deleteLevel();
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to delete the test world '" + this.name + "'", failure);
        }
    }
}
