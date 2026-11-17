package dev.satherov.nexus.api.network.payload;

import lombok.Getter;

import dev.satherov.nexus.api.network.ClientRecipients;
import dev.satherov.nexus.api.network.PayloadBuilder;
import dev.satherov.nexus.api.network.context.ClientHandlerContext;
import dev.satherov.nexus.internal.network.Envelope;
import dev.satherov.nexus.internal.network.client.ClientConnection;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.ApiStatus;

///
/// A registered payload that sends its values in the direction it was declared with.
///
/// @param <VALUE> The type of the values that the payload sends.
///
public final class Payload<VALUE> implements ClientboundPayload<VALUE>, ServerboundPayload<VALUE> {
    
    ///
    /// The direction the payload was declared with.
    ///
    @Getter
    private final Payload.Direction direction;
    
    ///
    /// The protocol the payload was declared for.
    ///
    @Getter
    private final ConnectionProtocol protocol;
    
    ///
    /// The type of the envelope that wraps the values, built from the identifier of the payload.
    ///
    private final CustomPacketPayload.Type<Envelope<VALUE>> type;
    
    ///
    /// The version neoforge negotiates for the payload.
    ///
    @Getter
    private final String version;
    
    ///
    /// If a client or server lacking the payload still connects.
    ///
    @Getter
    private final boolean optional;
    
    ///
    /// Should only ever be called from the terminals of [PayloadBuilder].
    ///
    /// @param id        The identifier the payload is registered under.
    /// @param direction The direction the payload was declared with.
    /// @param protocol  The protocol the payload was declared for.
    /// @param version   The version neoforge negotiates for the payload.
    /// @param optional  If a client or server lacking the payload still connects.
    ///
    @ApiStatus.Internal
    public Payload(Identifier id, Payload.Direction direction, ConnectionProtocol protocol, String version, boolean optional) {
        this.direction = direction;
        this.protocol = protocol;
        this.version = version;
        this.optional = optional;
        
        this.type = new CustomPacketPayload.Type<>(id);
    }
    
    ///
    /// The identifier the payload is registered under.
    ///
    /// @return The identifier of the payload.
    ///
    @Override
    public Identifier getId() {
        return this.type.id();
    }
    
    ///
    /// Wraps the given value, builds the packet once, and delivers it to the given recipients.
    ///
    /// @param value      The value to send.
    /// @param recipients The recipients to deliver the value to.
    ///
    /// @throws IllegalStateException If the payload is not client-bound, or if a recipient is connected in another protocol than the payload.
    ///
    @Override
    public void send(VALUE value, ClientRecipients recipients) {
        recipients.send(this.toPacket(value), this::accepts);
    }

    ///
    /// Wraps the given value into an [Envelope] inside a custom payload packet.
    ///
    /// @param value The value to wrap.
    ///
    /// @return The packet that holds the value.
    ///
    /// @throws IllegalStateException If the payload is not client-bound.
    ///
    @Override
    public Packet<ClientCommonPacketListener> toPacket(VALUE value) {
        this.checkClientbound();
        return new ClientboundCustomPayloadPacket(new Envelope<>(this.type, value));
    }

    ///
    /// If the client of the given player has registered the payload for the play protocol.
    ///
    /// @param player The player to check the client of.
    ///
    /// @return `true` if the client of the player has registered the payload for the play protocol.
    ///
    /// @throws IllegalStateException If the payload is not client-bound.
    ///
    @Override
    public boolean canSend(ServerPlayer player) {
        return this.canSend(player.connection);
    }
    
    ///
    /// If the other side of the given listener has registered the payload for the current protocol of the connection.
    ///
    /// @param listener The listener of the connection to check.
    ///
    /// @return `true` if the other side of the listener has registered the payload.
    ///
    /// @throws IllegalStateException If the payload is not client-bound.
    ///
    @Override
    public boolean canSend(ICommonPacketListener listener) {
        this.checkClientbound();
        return this.canReach(listener);
    }
    
    ///
    /// Wraps the given value and sends it over the game connection of this client.
    ///
    /// @param value The value to send.
    ///
    /// @throws IllegalStateException If the payload is not server-bound, this is a dedicated server, the client is not in play,
    /// or the payload is not a play payload.
    ///
    @Override
    public void sendToServer(VALUE value) {
        this.checkServerbound();
        ICommonPacketListener listener = ClientConnection.getListener();
        if (this.accepts(listener)) {
            listener.send(new Envelope<>(this.type, value));
        }
    }
    
    ///
    /// If the server that this client is connected to has registered the payload for the play protocol.
    ///
    /// @return `true` if the server registered the payload.
    ///
    /// @throws IllegalStateException If the payload is not server-bound, this is a dedicated server, or the client is not in play.
    ///
    @Override
    public boolean canSendToServer() {
        this.checkServerbound();
        return this.canReach(ClientConnection.getListener());
    }
    
    ///
    /// Wraps the given value into an [Envelope] and sends it back over the connection of the given context.
    ///
    /// @param value   The value to send.
    /// @param context The context of the client handler.
    ///
    /// @throws IllegalStateException If the payload is not server-bound, this is a dedicated server,
    /// or the connection is in another protocol than the payload.
    ///
    @Override
    public void reply(VALUE value, ClientHandlerContext context) {
        this.checkServerbound();
        if (this.accepts(context.getListener())) {
            context.reply(new Envelope<>(this.type, value));
        }
    }
    
    ///
    /// If the payload can reach the other side of the given listener, throwing an [IllegalStateException] if the listener is in another protocol.
    ///
    private boolean accepts(ICommonPacketListener listener) {
        if (listener.protocol() != this.protocol) {
            throw new IllegalStateException("Could not send the payload '" + this.type.id() + "' of the protocol '" + this.protocol.id() + "' over a connection in '" + listener.protocol().id() + "'");
        }
        
        return this.canReach(listener);
    }
    
    ///
    /// If the given listener is in the protocol of the payload, and the payload is required or the other side registered it.
    ///
    private boolean canReach(ICommonPacketListener listener) {
        return listener.protocol() == this.protocol && (!this.optional || listener.hasChannel(this.type));
    }
    
    ///
    /// Throws an [IllegalStateException] if the payload is not client-bound.
    ///
    private void checkClientbound() {
        if (this.direction == Payload.Direction.SERVERBOUND) {
            throw new IllegalStateException("The payload '" + this.type.id() + "' is not client-bound");
        }
    }
    
    ///
    /// Throws an [IllegalStateException] if the payload is not server-bound or this is a dedicated server.
    ///
    private void checkServerbound() {
        if (this.direction == Payload.Direction.CLIENTBOUND) {
            throw new IllegalStateException("The payload '" + this.type.id() + "' is not server-bound");
        }
        
        if (!FMLEnvironment.getDist().isClient()) {
            throw new IllegalStateException("The payload '" + this.type.id() + "' cannot be sent from a dedicated server");
        }
    }
    
    ///
    /// The sides a payload travels to.
    ///
    public enum Direction {
        ///
        /// From the server to the client.
        ///
        CLIENTBOUND,
        
        ///
        /// From the client to the server.
        ///
        SERVERBOUND,
        
        ///
        /// Both from the server to the client and from the client to the server.
        ///
        BIDIRECTIONAL
    }
}
