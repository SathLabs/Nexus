package dev.satherov.nexus.internal.network.client;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.network.Channel;
import dev.satherov.nexus.api.network.payload.Payload;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import org.jetbrains.annotations.ApiStatus;

///
/// The play connection of the client, for the sending of payloads to the server.
///
/// The only class that references the connection of the client and [ClientPacketDistributor].
///
/// Should only ever be loaded on the client, behind the dist checks of [Payload#sendToServer(Object)] and [Channel#canSendToServer()].
///
@UtilityClass
@ApiStatus.Internal
public class ClientConnection {
    
    ///
    /// Sends the given payload over the play connection of the client.
    ///
    /// @param payload The payload to send.
    ///
    /// @throws IllegalStateException If the client has no play connection open.
    ///
    public static void sendToServer(CustomPacketPayload payload) {
        if (Minecraft.getInstance().getConnection() == null) {
            throw new IllegalStateException("Could not send the payload '" + payload.type().id() + "', the client has no play connection open");
        }
        
        ClientPacketDistributor.sendToServer(payload);
    }
    
    ///
    /// The listener of the play connection of the client.
    ///
    /// @return The listener of the connection.
    ///
    /// @throws IllegalStateException If the client has no play connection open.
    ///
    public static ICommonPacketListener getListener() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) throw new IllegalStateException("The client has no play connection open");
        return connection;
    }
}
