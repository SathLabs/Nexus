package dev.satherov.nexus.internal.dev;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.Nexus;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;

import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

///
/// Ops the players that a dev run lists in the `nexus.dev.ops` system property when they log in and then deops them again when they log out.
///
/// Ops nobody in production or if the property is absent.
///
/// The players we opped are recorded in a file in the server directory.
/// If the server stops without deopping them, they will be deopped on its next start.
///
@Slf4j
@UtilityClass
@ApiStatus.Internal
@EventBusSubscriber(modid = Nexus.MOD_ID)
public class DevOperators {
    
    ///
    /// The system property holding the names, comma separated.
    ///
    public static final String PROPERTY = "nexus.dev.ops";
    
    ///
    /// The name of the file in the server directory that holds the players we opped.
    ///
    private static final String FILE = "nexus_dev_ops.json";
    
    ///
    /// The codec of the players in the file.
    ///
    private static final Codec<List<NameAndId>> CODEC = NameAndId.CODEC.listOf();
    
    ///
    /// Ops the player if the property lists them and they are not an operator yet.
    ///
    /// @param event The login event.
    ///
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (FMLEnvironment.isProduction()) {
            return;
        }
        
        String names = System.getProperty(DevOperators.PROPERTY);
        if (names == null || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        NameAndId profile = player.nameAndId();
        boolean listed = Arrays.stream(names.split(","))
                .map(String::trim)
                .anyMatch(profile.name()::equalsIgnoreCase);
        
        MinecraftServer server = player.level().getServer();
        PlayerList players = server.getPlayerList();
        if (!listed || players.isOp(profile)) {
            return;
        }
        
        Set<NameAndId> opped = DevOperators.read(server);
        opped.add(profile);
        DevOperators.write(server, opped);
        players.op(profile);
        DevOperators.log.info("Opped dev player '{}'", profile.name());
    }
    
    ///
    /// Deops the player if we opped them on login.
    ///
    /// @param event The logout event.
    ///
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        MinecraftServer server = player.level().getServer();
        NameAndId profile = player.nameAndId();
        Set<NameAndId> opped = DevOperators.read(server);
        if (!opped.remove(profile)) {
            return;
        }
        
        server.getPlayerList().deop(profile);
        DevOperators.write(server, opped);
        DevOperators.log.info("Deopped dev player '{}'", profile.name());
    }
    
    ///
    /// Deops every player we opped in an earlier run that stopped without deopping them.
    ///
    /// @param event The server starting event.
    ///
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        DevOperators.deopAll(event.getServer());
    }
    
    ///
    /// Deops every player we opped that is still logged in, since a server may stop without logging its players out.
    ///
    /// @param event The server stopping event.
    ///
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DevOperators.deopAll(event.getServer());
    }
    
    ///
    /// Deops every player in the file and then deletes it.
    ///
    /// @param server The server the file belongs to.
    ///
    private static void deopAll(MinecraftServer server) {
        PlayerList players = server.getPlayerList();
        DevOperators.read(server).forEach(players::deop);
        DevOperators.write(server, Set.of());
    }
    
    ///
    /// Reads the players we opped from the file, or an empty set if the file is missing or could not be read.
    ///
    /// @param server The server the file belongs to.
    ///
    /// @return A mutable copy of the players in the file.
    ///
    private static Set<NameAndId> read(MinecraftServer server) {
        Path file = server.getFile(DevOperators.FILE);
        if (!Files.exists(file)) {
            return new LinkedHashSet<>();
        }
        
        try {
            return new LinkedHashSet<>(
                    DevOperators.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                            .resultOrPartial(error -> DevOperators.log.warn("Could not read the dev operators from '{}': {}", file, error))
                            .orElse(List.of())
            );
        } catch (IOException | JsonParseException exception) {
            DevOperators.log.warn("Could not read the dev operators from '{}'", file, exception);
            return new LinkedHashSet<>();
        }
    }
    
    ///
    /// Writes the given players to the file, or deletes it if there are none.
    ///
    /// @param server The server the file belongs to.
    /// @param opped  The players we opped.
    ///
    private static void write(MinecraftServer server, Set<NameAndId> opped) {
        Path file = server.getFile(DevOperators.FILE);
        try {
            if (opped.isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                Files.writeString(file, DevOperators.CODEC.encodeStart(JsonOps.INSTANCE, List.copyOf(opped)).getOrThrow().toString());
            }
        } catch (IOException exception) {
            DevOperators.log.warn("Could not write the dev operators to '{}'", file, exception);
        }
    }
}
