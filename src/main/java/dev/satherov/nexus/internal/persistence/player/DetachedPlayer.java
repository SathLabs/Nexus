package dev.satherov.nexus.internal.persistence.player;

import lombok.extern.slf4j.Slf4j;

import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Util;
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
import java.util.UUID;

///
/// A server player that is loaded from a player tag and never joins a level or the player list.
///
@Slf4j
@ApiStatus.Internal
public final class DetachedPlayer extends ServerPlayer implements AutoCloseable {
    
    ///
    /// The keys of the loaded player tag that a save writes back unchanged.
    ///
    private static final List<String> CARRIED_KEYS = List.of(ServerPlayer.ENDER_PEARLS_TAG, "RootVehicle", "Dimension", "Pos");
    
    ///
    /// The player tag the player was loaded from.
    ///
    private final CompoundTag tag;
    
    ///
    /// If the construction of this player added the stats-entry of its UUID to the player list.
    ///
    private final boolean addedStats;

    ///
    /// If the construction of this player added the advancements-entry of its UUID to the player list.
    ///
    private final boolean addedAdvancements;
    
    ///
    /// Should only ever be called from [#from(MinecraftServer, NameAndId, CompoundTag)].
    ///
    /// If a player with the same UUID is online, its advancements will be pointed back at it after the construction.
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
        this.addedAdvancements = !players.advancements.containsKey(profile.id());
        super(server, level, profile, ClientInformation.createDefault());
        this.connection = new DetachedListener(server, this);
        
        ServerPlayer live = players.getPlayer(profile.id());
        if (live != null) {
            live.getAdvancements().setPlayer(live);
        }
    }
    
    ///
    /// Creates a new detached player from the given player tag and fires [PlayerEvent.LoadFromFile] for it.
    ///
    /// The player will be built in the saved dimension of the tag, or in the overworld if that level is gone.
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
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(DetachedPlayer.log)) {
            ValueInput input = TagValueInput.create(reporter, server.registryAccess(), tag);
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
    /// Writes the given player tag into the file of this player and fires [PlayerEvent.SaveToFile] for it.
    ///
    /// The ender pearls, the root vehicle, and the dimension of the loaded player tag will be put into the given tag first.
    /// The old file will be kept as `<uuid>.dat_old`.
    ///
    /// @param saved The player tag to write.
    ///
    /// @throws UncheckedIOException If the file could not be written.
    ///
    public void save(CompoundTag saved) {
        for (String key : DetachedPlayer.CARRIED_KEYS) {
            Tag carried = this.tag.get(key);
            if (carried != null) {
                saved.put(key, carried);
            }
        }
        
        String id = this.getGameProfile().id().toString();
        File folder = this.level().getServer().getPlayerList().getPlayerIo().getPlayerDir();
        Path directory = folder.toPath();
        try {
            Path temp = Files.createTempFile(directory, id + "-", ".dat");
            NbtIo.writeCompressed(saved, temp);
            Util.safeReplaceFile(directory.resolve(id + ".dat"), temp, directory.resolve(id + ".dat_old"));
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not save the player data of '" + id + "'", failure);
        }
        
        EventHooks.firePlayerSavingEvent(this, folder, id);
    }
    
    ///
    /// Removes the stats and advancements entries that the construction of this player added to the player list.
    ///
    @Override
    public void close() {
        UUID id = this.getGameProfile().id();
        PlayerList players = this.level().getServer().getPlayerList();
        if (this.addedAdvancements) {
            this.getAdvancements().stopListening();
            players.advancements.remove(id);
        }
        
        if (this.addedStats) {
            players.stats.remove(id);
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
