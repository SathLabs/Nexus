package dev.satherov.nexus.api.network;

import dev.satherov.nexus.api.network.context.ServerHandlerContext;
import dev.satherov.nexus.api.network.payload.ClientboundPayload;

import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.function.Predicate;

///
/// A group of connections that the packet of a clientbound payload is delivered to.
///
@FunctionalInterface
public interface Recipients {
    
    ///
    /// Delivers the given packet to every recipient whose listener the given test accepts.
    ///
    /// Called once by every [ClientboundPayload#send(Object, Recipients)], with the one packet it built.
    ///
    /// @param packet  The packet to deliver.
    /// @param accepts The test of the listener of every recipient, `true` to deliver the packet to it.
    ///
    void send(Packet<?> packet, Predicate<? super ICommonPacketListener> accepts);
    
    ///
    /// Creates the recipients of the given player.
    ///
    /// @param player The player to deliver to.
    ///
    /// @return The recipients of the player.
    ///
    static Recipients player(ServerPlayer player) {
        return (packet, accepts) -> Recipients.deliver(player.connection, packet, accepts);
    }
    
    ///
    /// Creates the recipients of the given players.
    ///
    /// @param players The players to deliver to.
    ///
    /// @return The recipients of the players.
    ///
    static Recipients players(Collection<ServerPlayer> players) {
        return (packet, accepts) -> Recipients.deliver(players, packet, accepts);
    }
    
    ///
    /// Creates the recipients of every player on the given server.
    ///
    /// @param server The server whose players to deliver to.
    ///
    /// @return The recipients of every player on the server.
    ///
    static Recipients all(MinecraftServer server) {
        return (packet, accepts) -> Recipients.deliver(server.getPlayerList().getPlayers(), packet, accepts);
    }
    
    ///
    /// Creates the recipients of every player in the given level.
    ///
    /// @param level The level whose players to deliver to.
    ///
    /// @return The recipients of every player in the level.
    ///
    static Recipients dimension(ServerLevel level) {
        return (packet, accepts) -> Recipients.deliver(level.players(), packet, accepts);
    }
    
    ///
    /// Creates the recipients of every player in the given level within the given radius of the given center.
    ///
    /// @param level  The level whose players to deliver to.
    /// @param center The position to measure the distance from.
    /// @param radius The maximum distance from the center, in blocks.
    ///
    /// @return The recipients of every player near the center.
    ///
    static Recipients near(ServerLevel level, Vec3 center, double radius) {
        return (packet, accepts) -> Recipients.deliver(level.getPlayers(player -> player.distanceToSqr(center) <= radius * radius), packet, accepts);
    }
    
    ///
    /// Creates the recipients of every player tracking the given entity.
    ///
    /// If the entity is a player, it will not be among them.
    ///
    /// @param entity The entity whose tracking players to deliver to.
    ///
    /// @return The recipients of every player tracking the entity.
    ///
    static Recipients trackingEntity(Entity entity) {
        return (packet, accepts) -> {
            ServerLevel level = (ServerLevel) entity.level();
            //noinspection unchecked The chunk map only passes the packet on to connections, which take any packet.
            level.getChunkSource().chunkMap.sendToTrackingPlayersFiltered(entity, (Packet<? super ClientGamePacketListener>) packet, player -> accepts.test(player.connection));
        };
    }
    
    ///
    /// Creates the recipients of every player tracking the given entity, and the entity itself if it is a player.
    ///
    /// @param entity The entity whose tracking players to deliver to.
    ///
    /// @return The recipients of every player tracking the entity and the entity itself.
    ///
    static Recipients trackingEntityAndSelf(Entity entity) {
        return (packet, accepts) -> {
            Recipients.trackingEntity(entity).send(packet, accepts);
            if (entity instanceof ServerPlayer player) {
                Recipients.deliver(player.connection, packet, accepts);
            }
        };
    }
    
    ///
    /// Creates the recipients of every player that has the given chunk loaded.
    ///
    /// @param level The level of the chunk.
    /// @param chunk The position of the chunk.
    ///
    /// @return The recipients of every player that has the chunk loaded.
    ///
    static Recipients trackingChunk(ServerLevel level, ChunkPos chunk) {
        return (packet, accepts) -> Recipients.deliver(level.getChunkSource().chunkMap.getPlayers(chunk, false), packet, accepts);
    }
    
    ///
    /// Creates the recipients of every player that has the chunk of the given position loaded.
    ///
    /// @param level The level of the position.
    /// @param pos   The position inside the chunk.
    ///
    /// @return The recipients of every player that has the chunk of the position loaded.
    ///
    static Recipients trackingPosition(ServerLevel level, BlockPos pos) {
        return Recipients.trackingChunk(level, ChunkPos.containing(pos));
    }
    
    ///
    /// Creates the recipients of the other side of the connection of the given listener, in play or configuration.
    ///
    /// Meant to be used in a configuration task, with the listener of [RegisterConfigurationTasksEvent#getListener()].
    ///
    /// @param listener The listener of the connection.
    ///
    /// @return The recipients of the other side of the connection.
    ///
    static Recipients listener(ICommonPacketListener listener) {
        return (packet, accepts) -> Recipients.deliver(listener, packet, accepts);
    }
    
    ///
    /// Creates the recipients of the connection that the server handler of the given context received on, in play or configuration.
    ///
    /// Meant to be used for a reply.
    ///
    /// @param context The context of the server handler.
    ///
    /// @return The recipients of the client that sent the payload.
    ///
    static Recipients sender(ServerHandlerContext context) {
        return Recipients.listener(context.getPlatformContext().listener());
    }
    
    ///
    /// Delivers the given packet to every one of the given players whose listener the given test accepts.
    ///
    private static void deliver(Collection<ServerPlayer> players, Packet<?> packet, Predicate<? super ICommonPacketListener> accepts) {
        for (ServerPlayer player : players) {
            Recipients.deliver(player.connection, packet, accepts);
        }
    }
    
    ///
    /// Delivers the given packet over the given listener if the given test accepts it.
    ///
    private static void deliver(ICommonPacketListener listener, Packet<?> packet, Predicate<? super ICommonPacketListener> accepts) {
        if (accepts.test(listener)) {
            listener.send(packet);
        }
    }
}
