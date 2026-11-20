package dev.satherov.nexus.internal.network.client;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.network.PayloadBuilder;
import dev.satherov.nexus.api.network.context.ClientHandlerContext;
import dev.satherov.nexus.api.network.payload.Payload;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.codec.path.Encoding;
import dev.satherov.nexus.internal.component.LookupGeneration;
import dev.satherov.nexus.internal.network.Envelope;

import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.RegistryAccess;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

///
/// Utility for the play connection of the client, which reads the listener and registries of the connection.
///
/// Should only ever be loaded on the client, through [Payload#sendToServer(Object)], [Payload#canSendToServer()], [PayloadBuilder#toClient(ClientOnly)],
/// [PayloadBuilder#bidirectional(ClientOnly, BiConsumer)], [Encoding], and [LookupGeneration].
///
@UtilityClass
@ApiStatus.Internal
public class ClientConnection {
    
    ///
    /// The listener of the play connection of the client.
    ///
    /// @return The listener of the connection.
    ///
    /// @throws IllegalStateException If the client has no play connection open.
    ///
    public static ICommonPacketListener getListener() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            throw new IllegalStateException("The client has no play connection open");
        }
        
        return connection;
    }
    
    ///
    /// The listener of the play connection of the client, or `null` if the client has no play connection open.
    ///
    /// @return The listener of the connection, or `null` if there is none.
    ///
    public static @Nullable ICommonPacketListener tryGetListener() {
        return Minecraft.getInstance().getConnection();
    }
    
    ///
    /// The registries of the client's play connection, or `null` if the client has no play connection open.
    ///
    /// @return The registry access of the connection, or `null` if there is none.
    ///
    public static @Nullable RegistryAccess tryGetRegistryAccess() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null ? connection.registryAccess() : null;
    }
    
    ///
    /// Opens the given client handler and wraps it into a neoforge handler of envelopes.
    ///
    /// @param handler The handler of the client, called with every value the client receives.
    ///
    /// @return The neoforge handler of the envelopes of the payload.
    ///
    public static <VALUE> IPayloadHandler<Envelope<VALUE>> adaptHandler(ClientOnly<BiConsumer<VALUE, ClientHandlerContext>> handler) {
        return Envelope.handler(ClientHandlerContext::new, handler.get());
    }
}
