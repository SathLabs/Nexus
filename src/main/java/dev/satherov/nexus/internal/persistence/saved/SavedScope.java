package dev.satherov.nexus.internal.persistence.saved;

import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.network.ClientRecipients;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.SavedDataStorage;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

///
/// The scope of a saved value, which is the server or a level.
///
/// Sends every synced value of the scope to a player in one bundle whenever the player joins the scope.
/// A value that could not be read or encoded will be logged and left out of the bundle.
///
/// @param <SCOPE> The type of the scope.
///
@Slf4j
@ApiStatus.Internal
@Accessors(fluent = true)
public final class SavedScope<SCOPE> {

    ///
    /// The scope of a server value, which a player joins when it logs in.
    ///
    /// A player gets the values again when it respawns, since it isn't a recipient of the server while it respawns.
    ///
    public static final SavedScope<MinecraftServer> SERVER = new SavedScope<>(
            MinecraftServer::getDataStorage,
            MinecraftServer::registryAccess,
            ClientRecipients::all,
            List.of(PlayerEvent.PlayerLoggedInEvent.class, PlayerEvent.PlayerRespawnEvent.class),
            player -> player.level().getServer()
    );

    ///
    /// The scope of a level value, which a player joins whenever it logs in, changes to the level, or respawns in it.
    ///
    public static final SavedScope<ServerLevel> LEVEL = new SavedScope<>(
            ServerLevel::getDataStorage,
            ServerLevel::registryAccess,
            ClientRecipients::dimension,
            List.of(PlayerEvent.PlayerLoggedInEvent.class, PlayerEvent.PlayerChangedDimensionEvent.class, PlayerEvent.PlayerRespawnEvent.class),
            ServerPlayer::level
    );

    ///
    /// The function that gets the data storage of a scope.
    ///
    @Getter
    private final Function<SCOPE, SavedDataStorage> storage;

    ///
    /// The function that gets the registry access of a scope.
    ///
    @Getter
    private final Function<SCOPE, RegistryAccess> registries;

    ///
    /// The function that creates the recipients of every player of a scope.
    ///
    @Getter
    private final Function<SCOPE, ClientRecipients> recipients;

    ///
    /// The function that gets the scope a player is in.
    ///
    @Getter
    private final Function<ServerPlayer, SCOPE> playerScope;

    ///
    /// Every synced value of the scope, in the order the values were registered.
    ///
    /// Values may be registered while mods are constructed in parallel.
    ///
    private final List<Synced> synced = new CopyOnWriteArrayList<>();

    ///
    /// Subscribes the listener that sends the synced values to a player that joins a scope.
    ///
    /// @param storage     The function that gets the data storage of a scope.
    /// @param registries  The function that gets the registry access of a scope.
    /// @param recipients  The function that creates the recipients of every player of a scope.
    /// @param joinEvents  The classes of the events that are fired once a player joined a scope and is one of its recipients.
    /// @param playerScope The function that gets the scope a player is in.
    ///
    private SavedScope(
            Function<SCOPE, SavedDataStorage> storage,
            Function<SCOPE, RegistryAccess> registries,
            Function<SCOPE, ClientRecipients> recipients,
            List<Class<? extends PlayerEvent>> joinEvents,
            Function<ServerPlayer, SCOPE> playerScope
    ) {
        this.storage = storage;
        this.registries = registries;
        this.recipients = recipients;
        this.playerScope = playerScope;

        for (Class<? extends PlayerEvent> joinEvent : joinEvents) {
            NeoForge.EVENT_BUS.addListener(joinEvent, this::onJoin);
        }
    }

    ///
    /// Adds a synced value to the values that a player gets when it joins a scope.
    ///
    /// @param id     The identifier of the value, used in the log if the value could not be sent.
    /// @param packet The function that wraps the value of the scope of a player into the packet of its payload.
    ///
    public void sync(Identifier id, Function<ServerPlayer, Packet<? super ClientGamePacketListener>> packet) {
        this.synced.add(new Synced(id, packet));
    }

    ///
    /// Sends the synced values of the scope a player joined to that player in one bundle.
    ///
    /// A value that could not be read or encoded will be logged and left out.
    ///
    /// @param event The event that is fired once a player joined the scope.
    ///
    private void onJoin(PlayerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || this.synced.isEmpty()) {
            return;
        }

        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        for (Synced value : this.synced) {
            try {
                packets.add(value.packet().apply(player));
            } catch (NexusCodecException failure) {
                SavedScope.log.error("Could not send the saved value '{}' to '{}'", value.id(), player.getPlainTextName(), failure);
            }
        }

        if (!packets.isEmpty()) {
            player.connection.send(new ClientboundBundlePacket(packets));
        }
    }

    ///
    /// One synced value of a scope.
    ///
    /// @param id     The identifier of the value.
    /// @param packet The function that wraps the value of the scope of a player into the packet of its payload.
    ///
    private record Synced(Identifier id, Function<ServerPlayer, Packet<? super ClientGamePacketListener>> packet) { }
}
