package dev.satherov.nexus.api.network.context;

import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.network.client.ClientConnection;

import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;

import org.jetbrains.annotations.ApiStatus;

///
/// The context that a payload handler on the client receives together with the payload itself.
///
/// Should only ever be loaded on the client, through the handler adapter of [ClientConnection].
///
public final class ClientHandlerContext extends HandlerContext {
    
    ///
    /// Should only ever be called from [ClientConnection#adaptHandler(ClientOnly)].
    ///
    /// @param platform The neoforge context of the payload.
    ///
    @ApiStatus.Internal
    public ClientHandlerContext(IPayloadContext platform) {
        super(platform);
    }
    
    ///
    /// @return The packet listener of the connection.
    ///
    @Override
    public ClientCommonPacketListener getListener() {
        return (ClientCommonPacketListener) super.getListener();
    }
    
    ///
    /// The player of this client.
    ///
    /// @return The player of this client.
    ///
    /// @throws IllegalStateException If the connection is in the configuration phase, where no player exists yet.
    ///
    @Override
    public LocalPlayer getPlayer() {
        return (LocalPlayer) super.getPlayer();
    }
}
