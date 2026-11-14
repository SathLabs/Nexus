package dev.satherov.nexus.internal.persistence.saved;

import lombok.Getter;
import lombok.experimental.Accessors;

import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.network.ClientRecipients;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.SavedDataStorage;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

///
/// The scope of a saved value, which is the server or a level.
///
/// Sends every synced value of the scope to a player in one bundle whenever the player joins the scope.
///
/// @param <SCOPE> The type of the scope.
///
@ApiStatus.Internal
@Accessors(fluent = true)
public final class SavedScope<SCOPE> {

    ///
    /// The scope of a server value, which a player joins when it logs in.
    ///
    public static final SavedScope<MinecraftServer> SERVER = new SavedScope<>(
            MinecraftServer::getDataStorage,
            ClientRecipients::all,
            PlayerEvent.PlayerLoggedInEvent.class,
            player -> player.level().getServer()
    );

    ///
    /// The scope of a level value, which a player joins whenever it enters the level.
    ///
    public static final SavedScope<ServerLevel> LEVEL = new SavedScope<>(
            ServerLevel::getDataStorage,
            ClientRecipients::dimension,
            EntityJoinLevelEvent.class,
            ServerPlayer::level
    );

    ///
    /// The function that gets the data storage of a scope.
    ///
    @Getter
    private final Function<SCOPE, SavedDataStorage> storage;

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
    /// The functions that wrap the value of every synced value of a player's scope into its packet, in the order the values were registered.
    ///
    /// Values may be registered while mods are constructed in parallel.
    ///
    private final List<Function<ServerPlayer, Packet<? super ClientGamePacketListener>>> synced = new CopyOnWriteArrayList<>();

    ///
    /// Subscribes the listener that sends the synced values to a player that joins a scope.
    ///
    /// @param storage     The function that gets the data storage of a scope.
    /// @param recipients  The function that creates the recipients of every player of a scope.
    /// @param joinEvent   The class of the event that is fired when a player joins a scope.
    /// @param playerScope The function that gets the scope a player is in.
    ///
    private SavedScope(
            Function<SCOPE, SavedDataStorage> storage,
            Function<SCOPE, ClientRecipients> recipients,
            Class<? extends EntityEvent> joinEvent,
            Function<ServerPlayer, SCOPE> playerScope
    ) {
        this.storage = storage;
        this.recipients = recipients;
        this.playerScope = playerScope;

        NeoForge.EVENT_BUS.addListener(joinEvent, this::onJoin);
    }

    ///
    /// Adds a synced value to the values that a player gets when it joins a scope.
    ///
    /// @param packet The function that wraps the value of the scope of a player into the packet of its payload.
    ///
    public void sync(Function<ServerPlayer, Packet<? super ClientGamePacketListener>> packet) {
        this.synced.add(packet);
    }

    ///
    /// Sends the synced values of the scope a player joined to that player in one bundle.
    ///
    /// @param event The event that is fired when a player joins the scope.
    ///
    /// @throws NexusCodecException If the scope the player joined has a saved value that could not be read.
    ///
    private void onJoin(EntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || this.synced.isEmpty()) {
            return;
        }

        List<Packet<? super ClientGamePacketListener>> packets = this.synced.stream()
                .<Packet<? super ClientGamePacketListener>>map(packet -> packet.apply(player))
                .toList();
        player.connection.send(new ClientboundBundlePacket(packets));
    }
}
