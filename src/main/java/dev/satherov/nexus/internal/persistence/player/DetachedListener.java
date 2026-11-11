package dev.satherov.nexus.internal.persistence.player;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import io.netty.channel.ChannelFutureListener;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// A network handler that sends nothing and reports every payload channel as missing.
///
/// @see DetachedPlayer
///
@ApiStatus.Internal
public final class DetachedListener extends ServerGamePacketListenerImpl {
    
    ///
    /// Should only ever be called from the constructor of [DetachedPlayer].
    ///
    /// @param server The server the player belongs to.
    /// @param player The detached player.
    ///
    public DetachedListener(MinecraftServer server, ServerPlayer player) {
        super(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
    }
    
    ///
    /// Does nothing.
    ///
    /// @param packet   The packet to send.
    /// @param listener The listener to call once the packet is sent, or `null` if there is none.
    ///
    @Override
    public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) { }
    
    ///
    /// Will always return `false`.
    ///
    /// @param payloadId The identifier of the payload channel.
    ///
    /// @return `false`.
    ///
    @Override
    public boolean hasChannel(Identifier payloadId) {
        return false;
    }
}
