package dev.satherov.nexus.api.persistence;

import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.player.PlayerNames;
import dev.satherov.nexus.internal.persistence.player.DetachedPlayer;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentSync;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.RemoteChatSession;
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
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

///
/// Allows editing the saved data of every player of a server, whether the player is online or offline.
///
/// An offline player is edited through a detached copy that never joins a level or the player list.
/// [PlayerEvent.LoadFromFile] and [PlayerEvent.SaveToFile] will fire whenever a copy is loaded or saved.
///
/// A player tag from an older version will be upgraded before it is loaded, the same way vanilla upgrades the file of a player.
///
/// Should only be called on the server thread.
///
/// Can be accessed through [Persistence#getPlayerData()].
///
@Slf4j
@EventBusSubscriber(modid = Nexus.MOD_ID)
public final class PlayerData {
    
    ///
    /// The respawn flags that keep no data of the old player on the client.
    ///
    private static final byte KEEP_NOTHING = 0;
    
    ///
    /// The detached copies that an edit is currently running on, by the UUID of their player.
    ///
    /// Shared by the player data of every mod, so a nested edit through another mod runs on the same copy.
    ///
    private static final Map<UUID, DetachedPlayer> COPIES_BY_PLAYER = new HashMap<>();
    
    ///
    /// The old player that a swap is currently removing, or `null` if no swap is.
    ///
    private static @Nullable ServerPlayer removing;
    
    ///
    /// Should only ever be called from [Persistence#getPlayerData()].
    ///
    @ApiStatus.Internal
    public PlayerData() { }
    
    ///
    /// Forgets the open copies when the server stops, so none of them keeps the stopped server alive.
    ///
    /// @param event The event of the stopped server.
    ///
    @SubscribeEvent
    private static void onServerStopped(ServerStoppedEvent event) {
        PlayerData.COPIES_BY_PLAYER.clear();
    }
    
    ///
    /// Cancels the toss of every item that the old player of a swap would drop into the world while it is removed.
    ///
    /// Runs before every other listener of the toss, so none of them sees the item.
    ///
    /// @param event The toss event.
    ///
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    private static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() == PlayerData.removing) {
            event.setCanceled(true);
        }
    }
    
    ///
    /// Looks up the last known name of the given player,
    /// or uses the UUID as the name if no cache holds the player.
    ///
    /// @param server The server that holds the name cache.
    /// @param player The UUID of the player.
    ///
    /// @return The UUID and the name of the player.
    ///
    private static NameAndId nameAndId(MinecraftServer server, UUID player) {
        return PlayerNames.find(server, player).orElseGet(() -> new NameAndId(player, player.toString()));
    }
    
    ///
    /// Upgrades a copy of the given player tag to the current data version, the same way vanilla upgrades the file of a player.
    ///
    /// @param server The server that holds the data fixer.
    /// @param tag    The player tag to upgrade.
    ///
    /// @return The upgraded copy, stamped with the current data version.
    ///
    private static CompoundTag upgrade(MinecraftServer server, CompoundTag tag) {
        return NbtUtils.addCurrentDataVersion(DataFixTypes.PLAYER.updateToCurrentVersion(server.getFixerUpper(), tag.copy(), NbtUtils.getDataVersion(tag)));
    }
    
    ///
    /// Runs the given action on the given player, or on a detached copy of it if the player is offline.
    ///
    /// If the player is offline, the copy will be loaded from the file of the player and saved back before this returns.
    /// The stats and advancements of the copy will not be saved.
    /// An edit of the same offline player inside the action, through this or the player data of another mod,
    /// will run on the same copy, and only the outer edit will save it.
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
        
        DetachedPlayer open = PlayerData.COPIES_BY_PLAYER.get(player);
        if (open != null) {
            action.accept(open);
            return true;
        }
        
        Optional<CompoundTag> tag = this.read(server, player);
        if (tag.isEmpty()) {
            return false;
        }
        
        try (DetachedPlayer copy = DetachedPlayer.from(server, PlayerData.nameAndId(server, player), tag.get())) {
            PlayerData.COPIES_BY_PLAYER.put(player, copy);
            try {
                action.accept(copy);
            } finally {
                PlayerData.COPIES_BY_PLAYER.remove(player);
            }
            
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
        CompoundTag upgraded = PlayerData.upgrade(server, tag);
        UUID player = upgraded.read(Entity.TAG_UUID, UUIDUtil.CODEC).orElseGet(UUID::randomUUID);
        try (DetachedPlayer copy = DetachedPlayer.from(server, PlayerData.nameAndId(server, player), upgraded)) {
            return action.apply(copy);
        }
    }
    
    ///
    /// Creates the player tag that a save of the given player would write.
    ///
    /// The root vehicle and the ender pearls of the player will be left out.
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
            CompoundTag tag = output.buildResult();
            tag.remove(DetachedPlayer.ROOT_VEHICLE_TAG);
            tag.remove(ServerPlayer.ENDER_PEARLS_TAG);
            return tag;
        }
    }
    
    ///
    /// Reads the player tag of the given player from its file or creates a snapshot of the player if it is online.
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
        
        return players.getPlayerIo().load(PlayerData.nameAndId(server, player)).map(NbtUtils::addCurrentDataVersion);
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
    /// The vehicle of the old player will stay in the world.
    /// If the tag holds that same vehicle, the vehicle of the tag will take its place.
    /// The cursor and the menus of the old player will not toss their items into the world.
    ///
    /// If loading the new player throws, the old player will be left as it was and the exception will be rethrown.
    ///
    /// [PlayerEvent.LoadFromFile] will fire for the new player, then [PlayerEvent.PlayerLoggedOutEvent] for the old one,
    /// and then [PlayerEvent.PlayerLoggedInEvent] for the new one.
    /// [PlayerEvent.Clone] and [PlayerEvent.PlayerRespawnEvent] will not fire.
    ///
    /// @param server The server the player belongs to.
    /// @param player The UUID of the player.
    /// @param tag    The player tag to load.
    ///
    /// @throws UncheckedIOException If the file could not be written.
    ///
    public void replace(MinecraftServer server, UUID player, CompoundTag tag) {
        CompoundTag upgraded = PlayerData.upgrade(server, tag);
        ServerPlayer live = server.getPlayerList().getPlayer(player);
        if (live != null) {
            this.swap(server, live, upgraded);
            return;
        }
        
        try (DetachedPlayer copy = DetachedPlayer.from(server, PlayerData.nameAndId(server, player), upgraded)) {
            copy.save(upgraded);
        }
    }
    
    ///
    /// Swaps the given online player for a new player loaded from the given player tag on the same connection.
    ///
    /// The old player will be removed as on a logout but without being saved, and the client will be sent the packets of a respawn.
    /// If loading the new player throws, the old player will be left as it was.
    ///
    /// @param server The server the player belongs to.
    /// @param old    The online player to remove.
    /// @param tag    The player tag to load the new player from.
    ///
    private void swap(MinecraftServer server, ServerPlayer old, CompoundTag tag) {
        PlayerList players = server.getPlayerList();
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(old.problemPath(), PlayerData.log)) {
            ValueInput input = TagValueInput.create(reporter, server.registryAccess(), tag);
            ServerLevel level = DetachedPlayer.savedLevel(server, input);
            ServerPlayer player = new ServerPlayer(server, level, old.getGameProfile(), old.clientInformation());
            player.setId(old.getId());
            try {
                player.load(input);
                EventHooks.firePlayerLoadingEvent(player, players, player.getStringUUID());
            } catch (RuntimeException failure) {
                // The constructor of the new player took over the advancements of the old one, so we hand them back.
                old.getAdvancements().setPlayer(old);
                throw failure;
            }
            
            player.snapTo(player.position(), player.getYRot(), player.getXRot());
            
            old.ejectPassengers();
            if (old.isSleeping()) {
                old.stopSleepInBed(true, false);
            }
            
            EventHooks.firePlayerLoggedOut(old);
            if (old.isPassenger()) {
                Entity vehicle = old.getRootVehicle();
                Optional<UUID> restored = input.childOrEmpty(DetachedPlayer.ROOT_VEHICLE_TAG)
                        .childOrEmpty("Entity")
                        .read(Entity.TAG_UUID, UUIDUtil.CODEC);
                
                // Vanilla only unloads the vehicle after saving it with the player, so we keep it in the world unless the tag brings it back.
                boolean replaced = vehicle.hasExactlyOnePlayerPassenger() && restored.filter(vehicle.getUUID()::equals).isPresent();
                old.stopRiding();
                if (replaced) {
                    vehicle.getPassengersAndSelf().forEach(entity -> entity.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER));
                }
            }
            
            old.unRide();
            for (ThrownEnderpearl pearl : old.getEnderPearls()) {
                pearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            }
            
            // Removing the old player closes its menus, and they would toss what they hold into the world.
            // The tag holds the items the player keeps, so we cancel those tosses.
            PlayerData.removing = old;
            try {
                old.level().removePlayerImmediately(old, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
            } finally {
                PlayerData.removing = null;
            }
            
            players.players.remove(old);
            
            ServerGamePacketListenerImpl connection = old.connection;
            player.connection = connection;
            connection.player = player;
            player.setTabListHeaderFooter(old.getTabListHeader(), old.getTabListFooter());
            RemoteChatSession session = old.getChatSession();
            if (session != null) {
                player.setChatSession(session);
            }
            
            LevelData levelData = level.getLevelData();
            connection.send(new ClientboundRespawnPacket(player.createCommonSpawnInfo(level), PlayerData.KEEP_NOTHING));
            connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            connection.resetPosition();
            connection.restartClientLoadTimerAfterRespawn();
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
            //noinspection UnstableApiUsage We gotta sync that stuff somehow ig.
            AttachmentSync.syncInitialPlayerAttachments(player);
            EventHooks.firePlayerLoggedIn(player);
            player.loadAndSpawnEnderPearls(input);
            player.loadAndSpawnParentVehicle(input);
        }
    }
}
