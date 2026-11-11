package dev.satherov.nexus.internal.persistence.saved;

import dev.satherov.nexus.api.network.ClientRecipients;

import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.SavedDataStorage;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

///
/// The scope of a saved value, which is the server or a level.
///
/// @param storage     The function that gets the data storage of a scope.
/// @param recipients  The function that creates the recipients of every player of a scope.
/// @param joinEvent   The class of the event that is fired when a player joins a scope.
/// @param playerScope The function that gets the scope a player is in.
/// @param <SCOPE>     The type of the scope.
///
@ApiStatus.Internal
public record SavedScope<SCOPE>(Function<SCOPE, SavedDataStorage> storage, Function<SCOPE, ClientRecipients> recipients, Class<? extends EntityEvent> joinEvent, Function<ServerPlayer, SCOPE> playerScope) {
    
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
}
