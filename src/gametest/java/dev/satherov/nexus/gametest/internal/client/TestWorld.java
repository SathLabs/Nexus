package dev.satherov.nexus.gametest.internal.client;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.internal.level.VoidLevel;
import dev.satherov.nexus.gametest.mixin.ClientCommonPacketListenerImplMixin;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
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
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicReference;

///
/// A reference to a world the player is in and what the player can do within it.
///
/// @see VoidLevel
/// @see ClientCommonPacketListenerImplMixin
///
@ApiStatus.Internal
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EventBusSubscriber(modid = "nexus_gametest", value = Dist.CLIENT)
public final class TestWorld {
    
    ///
    /// The name of the save file the level should be created in.
    ///
    /// If the name is already taken, vanilla will just add a number to it.
    ///
    private static final String SAVE_NAME = "nexus-gametest";
    
    ///
    /// The maximum distance between the camera and the eyes of the player for the camera to count as being at the eyes.
    ///
    /// Vanilla smooths the eye height of the camera, and it never fully reaches the eye height of the player.
    ///
    private static final double CAMERA_TOLERANCE = 1.0E-4D;
    
    ///
    /// The ping that [#settle()] asked for and the server has not sent yet, or `null` if there is none.
    ///
    /// Set on the render thread and taken on the server thread.
    ///
    private static final AtomicReference<@Nullable Ping> PENDING_PING = new AtomicReference<>();
    
    ///
    /// How many pings [#settle()] has asked for so far, which is also the identifier of the last one.
    ///
    private static int pings;
    
    ///
    /// The identifier of the last ping of [#settle()] that the client handled, or `0` if it handled none.
    ///
    private static int handledPing;
    
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
    /// Marks the given ping as handled by the client, if it is the last one that [#settle()] sent.
    ///
    /// Runs on the render thread, like [#settle()].
    ///
    /// @param id The identifier of the ping the client handled.
    ///
    public static void onPingHandled(int id) {
        if (id == TestWorld.pings) {
            TestWorld.handledPing = id;
        }
    }
    
    ///
    /// Sends the pending ping of [#settle()] to every player, once the server has finished a tick that started after the ping was asked for.
    ///
    /// The server sends the changes of its levels before the end of its tick, so the ping follows every change made before [#settle()] was called.
    ///
    /// @param event The event of the tick the server finished.
    ///
    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Ping ping = TestWorld.PENDING_PING.get();
        if (ping != null && server.getTickCount() > ping.tick() && TestWorld.PENDING_PING.compareAndSet(ping, null)) {
            server.getPlayerList().broadcastAll(new ClientboundPingPacket(ping.id()));
        }
    }
    
    ///
    /// Waits until the player is floating at the level's spawnpoint and the level's clocks are stopped.
    ///
    /// Then waits until the loading screen is closed.
    ///
    public void awaitSpawn() {
        this.freeze();
        this.client.until("the player to log in", () -> !this.overworld().players().isEmpty());
        this.floatAtSpawn();
        this.client.until("the player to float at the spawn", () -> TestWorld.isFloating(this.client.minecraft()));
        this.client.until("the loading screen to close", () -> this.client.screen() == null);
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
    /// Teleports the player to the given position and rotation on the server and then waits until the camera of the client is there.
    ///
    /// @param position The position to put the player at.
    /// @param yaw      The horizontal rotation of the player, in degrees.
    /// @param pitch    The vertical rotation of the player, in degrees.
    ///
    public void pose(Vec3 position, float yaw, float pitch) {
        ServerLevel level = this.overworld();
        ServerPlayer player = level.players().getFirst();
        level.getServer().execute(() -> player.connection.teleport(position.x, position.y, position.z, yaw, pitch));
        
        Vec3 eyes = position.add(0.0D, player.getEyeHeight(), 0.0D);
        this.client.until("the camera to be at " + eyes + " looking along " + yaw + ", " + pitch, () -> this.isCameraAt(eyes, yaw, pitch));
    }
    
    ///
    /// Checks if the camera of the client is at the given position and looks along the given yaw and pitch.
    ///
    /// @param eyes  The position of the eyes of the player.
    /// @param yaw   The horizontal rotation of the player, in degrees.
    /// @param pitch The vertical rotation of the player, in degrees.
    ///
    /// @return `true` if the camera is at the given position and looks along the given yaw and pitch.
    ///
    private boolean isCameraAt(Vec3 eyes, float yaw, float pitch) {
        Camera camera = this.client.minecraft().gameRenderer.getMainCamera();
        return camera.position().closerThan(eyes, TestWorld.CAMERA_TOLERANCE) && camera.yRot() == yaw && camera.xRot() == pitch;
    }
    
    ///
    /// Asks the server for a ping at the end of its next tick and then waits until the client handled it.
    ///
    /// The client handles packets in the order the server sent them, so every packet sent before the ping is handled once the ping is.
    ///
    public void settle() {
        int id = ++TestWorld.pings;
        TestWorld.PENDING_PING.set(new Ping(id, this.overworld().getServer().getTickCount()));
        this.client.until("the client to handle the ping '" + id + "'", () -> TestWorld.handledPing == id);
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
    
    ///
    /// A ping that [#settle()] asked for.
    ///
    /// @param id   The identifier of the ping.
    /// @param tick The tick count of the server when the ping was asked for.
    ///
    private record Ping(int id, int tick) { }
}
