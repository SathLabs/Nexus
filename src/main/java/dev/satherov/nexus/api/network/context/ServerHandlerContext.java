package dev.satherov.nexus.api.network.context;

import dev.satherov.nexus.api.network.PayloadBuilder;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.ApiStatus;

///
/// A context that a payload handler receives along with a payload the server received.
///
public final class ServerHandlerContext extends HandlerContext {

    ///
    /// Wraps the given neoforge context of a payload the server received.
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
