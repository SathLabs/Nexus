package dev.satherov.nexus.api.network.payload;

import lombok.Getter;

import dev.satherov.nexus.api.network.Channel;
import dev.satherov.nexus.api.network.PayloadBuilder;
import dev.satherov.nexus.api.network.Recipients;
import dev.satherov.nexus.api.network.context.HandlerContext;
import dev.satherov.nexus.internal.network.Envelope;
import dev.satherov.nexus.internal.network.client.ClientConnection;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

///
/// A registered payload that sends its values in the direction it was declared with.
///
/// @param <VALUE> The type of the values that the payload sends.
///
public final class Payload<VALUE> implements ClientboundPayload<VALUE>, ServerboundPayload<VALUE> {
    
    ///
    /// The channel the payload registered through.
    ///
    @Getter
    private final Channel channel;
    
    ///
    /// The direction the payload was declared with.
    ///
    @Getter
    private final Payload.Direction direction;
    
    ///
    /// The type of the envelope that wraps the values, built from the identifier of the payload.
    ///
    private final CustomPacketPayload.Type<Envelope<VALUE>> type;
    
    ///
    /// Creates the handle of a payload on the given channel.
    ///
    /// Should only ever be called from the terminals of [PayloadBuilder].
    ///
    /// @param channel   The channel the payload registered through.
    /// @param id        The identifier the payload is registered under.
    /// @param direction The direction the payload was declared with.
    ///
    @ApiStatus.Internal
    public Payload(Channel channel, Identifier id, Payload.Direction direction) {
        this.channel = channel;
        this.direction = direction;
        
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
    /// @throws IllegalStateException If the payload is not clientbound.
    ///
    @Override
    public void send(VALUE value, Recipients recipients) {
        if (this.direction == Payload.Direction.SERVERBOUND) {
            throw new IllegalStateException("The payload '" + this.type.id() + "' is not clientbound");
        }
        
        Packet<?> packet = new ClientboundCustomPayloadPacket(new Envelope<>(this.type, value));
        recipients.send(packet, this.channel.isOptional() ? listener -> listener.hasChannel(this.type) : _ -> true);
    }
    
    ///
    /// Wraps the given value and sends it over the game connection of this client.
    ///
    /// @param value The value to send.
    ///
    /// @throws IllegalStateException If the payload is not serverbound or this is a dedicated server.
    ///
    @Override
    public void sendToServer(VALUE value) {
        this.checkServerbound();
        ClientConnection.sendToServer(new Envelope<>(this.type, value));
    }
    
    ///
    /// Wraps the given value and sends it back over the connection of the neoforge context of the given context.
    ///
    /// @param value   The value to send.
    /// @param context The context of the client handler.
    ///
    /// @throws IllegalStateException If the payload is not serverbound, this is a dedicated server, or the context is not of a client handler.
    ///
    @Override
    public void reply(VALUE value, HandlerContext context) {
        this.checkServerbound();
        IPayloadContext platform = context.getPlatformContext();
        if (platform.flow() != PacketFlow.CLIENTBOUND) {
            throw new IllegalStateException("Could not reply with the payload '" + this.type.id() + "', the context is not of a client handler");
        }
        
        platform.reply(new Envelope<>(this.type, value));
    }
    
    ///
    /// Throws an [IllegalStateException] if the payload is not serverbound or this is a dedicated server.
    ///
    private void checkServerbound() {
        if (this.direction == Payload.Direction.CLIENTBOUND) {
            throw new IllegalStateException("The payload '" + this.type.id() + "' is not serverbound");
        }
        
        if (!FMLEnvironment.getDist().isClient()) {
            throw new IllegalStateException("The payload '" + this.type.id() + "' can't be sent from a dedicated server");
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
