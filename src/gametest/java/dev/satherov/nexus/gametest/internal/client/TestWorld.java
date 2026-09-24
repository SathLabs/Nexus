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
/// A reference to a world the player is in and what the player can do within it.
///
/// @see VoidLevel
///
@ApiStatus.Internal
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestWorld {
    
    ///
    /// The name of the save file the level should be created in.
    ///
    /// If the name is already taken, vanilla will just add a number to it.
    ///
    private static final String SAVE_NAME = "nexus-gametest";
    
    ///
    /// The client that joined the level.
    ///
    private final Client client;
    
    ///
    /// The actual name of the save file.
    ///
    private final String name;
    
    ///
    /// Creates a new test world and then joins it.
    ///
    /// The player will only be in the world after [#awaitSpawn()] finishes.
    ///
    /// @param client The client that joined the level.
    ///
    /// @return The level the client joined.
    ///
    public static TestWorld create(Client client) {
        Minecraft minecraft = client.minecraft();
        String name = TestWorld.freeName(minecraft.getLevelSource());
        
        // Vanilla traps the render thread here until the server is ready,
        // so waiting for a world to load doesn't impact the available frames for a test case.
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
    /// Returns a save directory name that has not yet been used.
    ///
    /// @param source The level source for the save file.
    ///
    /// @return A save directory name that has not yet been used.
    ///
    /// @throws UncheckedIOException If the level source could not be read.
    ///
    private static String freeName(LevelStorageSource source) {
        try {
            return FileUtil.findAvailableName(source.getBaseDir(), TestWorld.SAVE_NAME, "");
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to name a test world under " + source.getBaseDir(), failure);
        }
    }
    
    ///
    /// Checks if the player is floating and the chunk it's floating is loaded.
    ///
    /// @param minecraft The client the player is on.
    ///
    /// @return `true` if the player is floating and the chunk it's floating in is loaded.
    ///
    private static boolean isFloating(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return player != null && player.getAbilities().flying && player.level().isLoaded(player.blockPosition());
    }
    
    ///
    /// Waits until the player is floating at the level's spawnpoint and the level's clocks are stopped.
    ///
    public void awaitSpawn() {
        this.freeze();
        this.client.until("the player to log in", () -> !this.overworld().players().isEmpty());
        this.floatAtSpawn();
        this.client.until("the player to float at the spawn", () -> TestWorld.isFloating(this.client.minecraft()));
    }
    
    ///
    /// Freezes the level's time and weather before moving its clock to noon as to ensure that every frame looks the same across every test.
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
    /// Checks if the level's time and weather are frozen and the clock is at noon.
    ///
    /// @param clock The level's clock.
    ///
    /// @return `true` if the level's time and weather are frozen and the clock is at noon.
    ///
    private boolean isFrozen(Holder<WorldClock> clock) {
        ServerLevel level = this.overworld();
        GameRules rules = level.getGameRules();
        return !rules.get(GameRules.ADVANCE_TIME) && !rules.get(GameRules.ADVANCE_WEATHER) && level.clockManager().isAtTimeMarker(clock, ClockTimeMarkers.NOON);
    }
    
    ///
    /// Moves the player to the level's spawn and makes it fly.
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
    /// Gets the server level of the test worlds overworld, which must exist.
    ///
    /// @return The overworld of the integrated server.
    ///
    /// @throws IllegalStateException If the integrated server does not exist anymore.
    ///
    public ServerLevel overworld() {
        IntegratedServer server = this.client.minecraft().getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("the test world's server is gone");
        }
        return server.overworld();
    }
    
    ///
    /// Disconnects from the test world, waits until the title screen shows up and then deletes the save file.
    ///
    public void leave() {
        try {
            // Vanilla does the same thing here as when creating the world, trapping the render thread until the server has stopped.
            this.client.minecraft().disconnect(new TitleScreen(), false);
            this.client.until("the title screen", () -> this.client.screen() instanceof TitleScreen);
        } finally {
            this.deleteSave();
        }
    }
    
    ///
    /// Deletes the save file of the test world.
    ///
    /// @throws UncheckedIOException If the save couldn't be deleted.
    ///
    private void deleteSave() {
        try (LevelStorageSource.LevelStorageAccess access = this.client.minecraft().getLevelSource().createAccess(this.name)) {
            access.deleteLevel();
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to delete the test world '" + this.name + "'", failure);
        }
    }
}
