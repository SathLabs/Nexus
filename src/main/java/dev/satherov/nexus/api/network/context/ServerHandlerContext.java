package dev.satherov.nexus.api.network.context;

import dev.satherov.nexus.api.network.PayloadBuilder;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.BiConsumer;

///
/// The context that a payload handler on the server receives together with the payload itself.
///
public final class ServerHandlerContext extends HandlerContext {

    ///
    /// Should only ever be called from [PayloadBuilder#adaptServer(BiConsumer)].
    ///
    /// @param platform The neoforge context of the payload.
    ///
    @ApiStatus.Internal
    public ServerHandlerContext(IPayloadContext platform) {
        super(platform);
    }

    ///
    /// @return The packet listener of the connection.
    ///
    @Override
    public ServerCommonPacketListener getListener() {
        return (ServerCommonPacketListener) super.getListener();
    }

    ///
    /// The player that sent the payload.
    ///
    /// @return The player that sent the payload.
    ///
    /// @throws IllegalStateException If the connection is in the configuration phase, where no player exists yet.
    ///
    @Override
    public ServerPlayer getPlayer() {
        return (ServerPlayer) super.getPlayer();
    }
}
