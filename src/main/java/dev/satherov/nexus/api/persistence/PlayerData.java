package dev.satherov.nexus.api.persistence;

import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.internal.persistence.player.DetachedPlayer;

import net.neoforged.neoforge.attachment.AttachmentSync;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

import org.jetbrains.annotations.ApiStatus;

import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

///
/// Allows editing the saved data of every player of a server, whether the player is online or offline.
///
/// An offline player is edited through a detached copy that never joins a level or the player list, and is never a [FakePlayer].
/// [PlayerEvent.LoadFromFile] and [PlayerEvent.SaveToFile] will fire whenever a copy is loaded or saved.
///
/// Should only be called on the server thread.
///
/// Can be accessed through [Persistence#getPlayerData()].
///
@Slf4j
public final class PlayerData {
    
    ///
    /// The respawn flags that keep no data of the old player on the client.
    ///
    private static final byte KEEP_NOTHING = 0;
    
    ///
    /// Should only ever be called from [Persistence#getPlayerData()].
    ///
    @ApiStatus.Internal
    public PlayerData() { }
    
    ///
    /// Looks up the name of the given player in the name cache of the server,
    /// or uses the UUID as the name if the cache doesn't hold it.
    ///
    /// @param server The server that holds the name cache.
    /// @param player The UUID of the player.
    ///
    /// @return The UUID and the name of the player.
    ///
    private static NameAndId nameAndId(MinecraftServer server, UUID player) {
        return server.services()
                .nameToIdCache()
                .get(player)
                .orElseGet(() -> new NameAndId(player, player.toString()));
    }
    
    ///
    /// Runs the given action on the given player, or on a detached copy of it if the player is offline.
    ///
    /// If the player is offline, the copy will be loaded from the file of the player and saved back before this returns.
    /// The stats and advancements of the copy will not be saved.
    ///
    /// The copy will be built in the saved dimension of the player, or in the overworld if that level is gone.
    /// Moving the copy will not move the player.
    ///
    /// If the action throws, the file will be left as it was and the exception will be rethrown.
    ///
    /// @param server The server the player belongs to.
    /// @param player The UUID of the player.
    /// @param action The action to run, called with the online player or the detached copy.
    ///
    /// @return `true` if the action ran, or `false` if the player is offline and has no file.
    ///
    /// @throws UncheckedIOException If the file could not be written.
    ///
    public boolean edit(MinecraftServer server, UUID player, Consumer<ServerPlayer> action) {
        ServerPlayer live = server.getPlayerList().getPlayer(player);
        if (live != null) {
            action.accept(live);
            return true;
        }
        
        Optional<CompoundTag> tag = this.read(server, player);
        if (tag.isEmpty()) {
            return false;
        }
        
        try (DetachedPlayer copy = DetachedPlayer.from(server, PlayerData.nameAndId(server, player), tag.get())) {
            action.accept(copy);
            copy.save(this.snapshot(copy));
        }
        
        return true;
    }
    
    ///
    /// Runs the given function on a detached copy loaded from the given player tag.
    ///
    /// The copy will never be saved, and the tag may belong to a player who is online.
    ///
    /// @param server The server the player belongs to.
    /// @param tag    The player tag to load the copy from.
    /// @param action The function to run, called with the detached copy.
    ///
    /// @return The result of the function.
    ///
    public <RESULT> RESULT inspect(MinecraftServer server, CompoundTag tag, Function<ServerPlayer, RESULT> action) {
        UUID player = tag.read(Entity.TAG_UUID, UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        try (DetachedPlayer copy = DetachedPlayer.from(server, PlayerData.nameAndId(server, player), tag)) {
            return action.apply(copy);
        }
    }
    
    ///
    /// Creates the player tag that a save of the given player would write.
    ///
    /// Meant to be used for backups.
    ///
    /// @param player The player to take the snapshot of.
    ///
    /// @return The player tag.
    ///
    public CompoundTag snapshot(ServerPlayer player) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(player.problemPath(), PlayerData.log)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, player.registryAccess());
            player.saveWithoutId(output);
            return output.buildResult();
        }
    }
    
    ///
    /// Reads the player tag of the given player from its file, or creates a snapshot of the player if it is online.
    ///
    /// @param server The server the player belongs to.
    /// @param player The UUID of the player.
    ///
    /// @return The player tag, or empty if the player is offline and has no file.
    ///
    public Optional<CompoundTag> read(MinecraftServer server, UUID player) {
        PlayerList players = server.getPlayerList();
        ServerPlayer live = players.getPlayer(player);
        if (live != null) {
            return Optional.of(this.snapshot(live));
        }
        
        return players.getPlayerIo().load(PlayerData.nameAndId(server, player));
    }
    
    ///
    /// Replaces the saved data of the given player with the given player tag.
    ///
    /// If the player is offline, the file of the player will be swapped for the tag.
    /// [PlayerEvent.LoadFromFile] and [PlayerEvent.SaveToFile] will fire for a detached copy loaded from the tag.
    ///
    /// If the player is online, it will be removed without being saved,
    /// and a new player will be loaded from the tag with the connection and the entity id of the old one.
    /// The client will be moved to the dimension and the position of the tag.
    ///
    /// [PlayerEvent.PlayerLoggedOutEvent] will fire for the old player, and [PlayerEvent.LoadFromFile] and [PlayerEvent.PlayerLoggedInEvent] for the new one.
    /// [PlayerEvent.Clone] and [PlayerEvent.PlayerRespawnEvent] will not fire.
    ///
    /// @param server The server the player belongs to.
    /// @param player The UUID of the player.
    /// @param tag    The player tag to load.
    ///
    /// @throws UncheckedIOException If the file could not be written.
    ///
    public void replace(MinecraftServer server, UUID player, CompoundTag tag) {
        ServerPlayer live = server.getPlayerList().getPlayer(player);
        if (live != null) {
            this.swap(server, live, tag);
            return;
        }
        
        try (DetachedPlayer copy = DetachedPlayer.from(server, PlayerData.nameAndId(server, player), tag)) {
            copy.save(tag);
        }
    }
    
    ///
    /// Swaps the given online player for a new player loaded from the given player tag on the same connection.
    ///
    /// The old player will be removed as on a logout but without being saved, and the client will be sent the packets of a respawn.
    ///
    /// @param server The server the player belongs to.
    /// @param old    The online player to remove.
    /// @param tag    The player tag to load the new player from.
    ///
    private void swap(MinecraftServer server, ServerPlayer old, CompoundTag tag) {
        PlayerList players = server.getPlayerList();
        EventHooks.firePlayerLoggedOut(old);
        if (old.isPassenger()) {
            Entity vehicle = old.getRootVehicle();
            if (vehicle.hasExactlyOnePlayerPassenger()) {
                old.stopRiding();
                vehicle.getPassengersAndSelf().forEach(entity -> entity.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER));
            }
        }
        
        old.unRide();
        for (ThrownEnderpearl pearl : old.getEnderPearls()) {
            pearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }
        
        old.level().removePlayerImmediately(old, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        players.players.remove(old);
        
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(old.problemPath(), PlayerData.log)) {
            ValueInput input = TagValueInput.create(reporter, server.registryAccess(), tag);
            ServerLevel level = DetachedPlayer.savedLevel(server, input);
            ServerGamePacketListenerImpl connection = old.connection;
            ServerPlayer player = new ServerPlayer(server, level, old.getGameProfile(), old.clientInformation());
            player.connection = connection;
            connection.player = player;
            player.setId(old.getId());
            player.load(input);
            EventHooks.firePlayerLoadingEvent(player, players, player.getStringUUID());
            player.snapTo(player.position(), player.getYRot(), player.getXRot());
            
            LevelData levelData = level.getLevelData();
            connection.send(new ClientboundRespawnPacket(player.createCommonSpawnInfo(level), PlayerData.KEEP_NOTHING));
            connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            connection.resetPosition();
            connection.send(new ClientboundChangeDifficultyPacket(levelData.getDifficulty(), levelData.isDifficultyLocked()));
            player.onUpdateAbilities();
            connection.send(new ClientboundSetExperiencePacket(player.experienceProgress, player.totalExperience, player.experienceLevel));
            players.sendActivePlayerEffects(player);
            players.sendLevelInfo(player, level);
            players.sendPlayerPermissionLevel(player);
            connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
            
            level.addRespawnedPlayer(player);
            players.players.add(player);
            players.playersByUUID.put(old.getUUID(), player);
            player.initInventoryMenu();
            player.setHealth(player.getHealth());
            player.getRecipeBook().sendInitialRecipeBook(player);
            players.broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, player));
            AttachmentSync.syncInitialPlayerAttachments(player);
            EventHooks.firePlayerLoggedIn(player);
            player.loadAndSpawnEnderPearls(input);
            player.loadAndSpawnParentVehicle(input);
        }
    }
}
