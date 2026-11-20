package dev.satherov.nexus.internal.persistence.player;

import lombok.extern.slf4j.Slf4j;

import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;

import com.mojang.authlib.GameProfile;

import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

///
/// A server player that is loaded from a player tag and never joins a level or the player list.
///
@Slf4j
@ApiStatus.Internal
public final class DetachedPlayer extends ServerPlayer implements AutoCloseable {
    
    ///
    /// The key of the root vehicle in a player tag.
    ///
    public static final String ROOT_VEHICLE_TAG = "RootVehicle";
    
    ///
    /// The keys of the loaded player tag that a save writes back unchanged.
    ///
    private static final List<String> CARRIED_KEYS = List.of(ServerPlayer.ENDER_PEARLS_TAG, DetachedPlayer.ROOT_VEHICLE_TAG, "Dimension", "Pos");
    
    ///
    /// The player tag the player was loaded from.
    ///
    private final CompoundTag tag;
    
    ///
    /// If the construction of this player added the stats-entry of its UUID to the player list.
    ///
    private final boolean addedStats;
    
    ///
    /// Should only ever be called from [#from(MinecraftServer, NameAndId, CompoundTag)].
    ///
    /// The player will get advancements of its own, and the player list will keep the advancements entry it held for the UUID.
    ///
    /// @param server  The server the player belongs to.
    /// @param level   The level the player is built in.
    /// @param profile The game profile of the player.
    /// @param tag     The player tag the player is loaded from.
    ///
    private DetachedPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, CompoundTag tag) {
        PlayerList players = server.getPlayerList();
        this.tag = tag;
        this.addedStats = !players.stats.containsKey(profile.id());
        PlayerAdvancements existing = players.advancements.remove(profile.id());
        super(server, level, profile, ClientInformation.createDefault());
        this.connection = new DetachedListener(server, this);
        
        // The construction put the advancements of this player into the player list, so we put back what was there before.
        if (existing != null) {
            players.advancements.put(profile.id(), existing);
        } else {
            players.advancements.remove(profile.id());
        }
    }
    
    ///
    /// Creates a new detached player from the given player tag and fires [PlayerEvent.LoadFromFile] for it.
    ///
    /// The player will be built in the saved dimension of the tag, or in the overworld if that level is gone.
    /// The sleeping position of the tag will be ignored.
    ///
    /// If loading throws, the player will be closed before the exception is rethrown.
    ///
    /// @param server The server the player belongs to.
    /// @param owner  The UUID and the name of the player.
    /// @param tag    The player tag to load.
    ///
    /// @return The new detached player.
    ///
    public static DetachedPlayer from(MinecraftServer server, NameAndId owner, CompoundTag tag) {
        // If the tag held a sleeping position, the player would wake up on load and free the real bed.
        CompoundTag awake = tag.copy();
        awake.remove(LivingEntity.TAG_SLEEPING_POS);
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(DetachedPlayer.log)) {
            ValueInput input = TagValueInput.create(reporter, server.registryAccess(), awake);
            DetachedPlayer player = new DetachedPlayer(server, DetachedPlayer.savedLevel(server, input), new GameProfile(owner.id(), owner.name()), tag);
            try {
                player.load(input);
                EventHooks.firePlayerLoadingEvent(player, server.getPlayerList(), player.getStringUUID());
            } catch (RuntimeException failure) {
                player.close();
                throw failure;
            }
            
            return player;
        }
    }
    
    ///
    /// Gets the level of the dimension that the given value input holds, or the overworld if that level is gone.
    ///
    /// @param server The server that holds the levels.
    /// @param input  The value input of the player tag.
    ///
    /// @return The level to build the player in.
    ///
    public static ServerLevel savedLevel(MinecraftServer server, ValueInput input) {
        return input.read("Dimension", Level.RESOURCE_KEY_CODEC)
                .map(server::getLevel)
                .orElseGet(server::overworld);
    }
    
    ///
    /// Copies the ender pearls, the root vehicle, the dimension, and the position of the loaded player tag into the given tag.
    ///
    /// @param saved The player tag to copy them into.
    ///
    public void putCarriedKeys(CompoundTag saved) {
        for (String key : DetachedPlayer.CARRIED_KEYS) {
            Tag carried = this.tag.get(key);
            if (carried != null) {
                saved.put(key, carried.copy());
            }
        }
    }
    
    ///
    /// Writes the given player tag into the file of this player and fires [PlayerEvent.SaveToFile] for it.
    ///
    /// The ender pearls, the root vehicle, and the dimension of the loaded player tag will be put into the given tag first.
    /// The old file will be kept as `<uuid>.dat_old`.
    ///
    /// If a listener of the event throws, the exception will be logged and the file will stay written.
    ///
    /// @param saved The player tag to write.
    ///
    /// @throws UncheckedIOException If the file could not be written.
    ///
    public void save(CompoundTag saved) {
        this.putCarriedKeys(saved);
        String id = this.getGameProfile().id().toString();
        File folder = this.level().getServer().getPlayerList().getPlayerIo().getPlayerDir();
        Path directory = folder.toPath();
        try {
            Path temp = Files.createTempFile(directory, id + "-", ".dat");
            NbtIo.writeCompressed(saved, temp);
            if (!Util.safeReplaceOrMoveFile(directory.resolve(id + ".dat"), temp, directory.resolve(id + ".dat_old"), false)) {
                Files.deleteIfExists(temp);
                throw new IOException("Could not replace '" + id + ".dat' with '" + temp.getFileName() + "'");
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not save the player data of '" + id + "'", failure);
        }
        
        try {
            EventHooks.firePlayerSavingEvent(this, folder, id);
        } catch (RuntimeException failure) {
            DetachedPlayer.log.error("Could not fire 'PlayerEvent.SaveToFile' for the player data of '{}'", id, failure);
        }
    }
    
    ///
    /// Unregisters the advancement triggers of this player and removes the stats entry that its construction added to the player list.
    ///
    @Override
    public void close() {
        this.getAdvancements().stopListening();
        if (this.addedStats) {
            this.level().getServer().getPlayerList().stats.remove(this.getGameProfile().id());
        }
    }
    
    ///
    /// Will always return [TextFilter#DUMMY].
    ///
    /// @return The dummy text filter.
    ///
    @Override
    public TextFilter getTextFilter() {
        return TextFilter.DUMMY;
    }
}
