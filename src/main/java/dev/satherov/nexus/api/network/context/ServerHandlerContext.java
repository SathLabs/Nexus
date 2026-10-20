package dev.satherov.nexus.api.network.context;

import dev.satherov.nexus.api.network.PayloadBuilder;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.ApiStatus;

///
/// The context that a payload handler on the server receives together with the payload itself.
///
public final class ServerHandlerContext extends HandlerContext {

    ///
    /// Should only ever be called from the handler adapters of [PayloadBuilder].
    ///
    /// @param platform The neoforge context of the payload.
    ///
    @ApiStatus.Internal
    public ServerHandlerContext(IPayloadContext platform) {
        super(platform);
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
