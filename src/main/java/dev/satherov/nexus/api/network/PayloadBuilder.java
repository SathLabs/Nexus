package dev.satherov.nexus.api.network;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.network.context.HandlerContext;
import dev.satherov.nexus.api.network.context.ServerHandlerContext;
import dev.satherov.nexus.api.network.payload.ClientboundPayload;
import dev.satherov.nexus.api.network.payload.Payload;
import dev.satherov.nexus.api.network.payload.ServerboundPayload;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.network.Envelope;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

///
/// A builder that declares one payload on a channel once one of its terminals is called.
///
/// @param <VALUE> The type of the values of the payload.
///
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class PayloadBuilder<VALUE> {
    
    ///
    /// The channel the payload is declared on.
    ///
    private final Channel channel;
    
    ///
    /// The type of the envelopes of the payload, built from its identifier.
    ///
    private final CustomPacketPayload.Type<Envelope<VALUE>> type;
    
    ///
    /// The protocol of the payload, with the stream codec of its envelopes.
    ///
    private final PayloadBuilder.Protocol<VALUE> protocol;
    
    ///
    /// The thread the handlers of the payload run on.
    ///
    private HandlerThread thread = HandlerThread.MAIN;
    
    ///
    /// The identifier the payload registers under.
    ///
    /// It's `<modid>:<name>` on the default channel and `<modid>:<channel>/<name>` on any other.
    ///
    /// @return The identifier of the payload.
    ///
    public Identifier getId() {
        return this.type.id();
    }
    
    ///
    /// Makes the handlers run on the network thread instead of the main thread of the receiving side.
    ///
    /// @return The builder for chaining.
    ///
    /// @see HandlerContext#enqueue(Runnable)
    ///
    @CanIgnoreReturnValue
    public PayloadBuilder<VALUE> onNetworkThread() {
        this.thread = HandlerThread.NETWORK;
        return this;
    }
    
    ///
    /// Declares the payload as sent from the server to the client, with the handler the client runs.
    ///
    /// The handler will only be opened on the client, once inside the payload handlers event.
    ///
    /// @param handler The handler of the client, called with every value the client receives.
    ///
    /// @return The handle of the payload.
    ///
    /// @throws IllegalStateException If the identifier is already taken, or if the payload handlers event already ran.
    ///
    public ClientboundPayload<VALUE> toClient(ClientOnly<BiConsumer<VALUE, HandlerContext>> handler) {
        return this.declare(Payload.Direction.CLIENTBOUND, registrar -> switch (this.protocol) {
            case PayloadBuilder.Play(StreamCodec<RegistryFriendlyByteBuf, Envelope<VALUE>> codec) -> FMLEnvironment.getDist().isClient() ?
                    registrar.playToClient(this.type, codec, PayloadBuilder.adaptClient(handler)) :
                    registrar.playToClient(this.type, codec);
            case PayloadBuilder.Configuration(StreamCodec<FriendlyByteBuf, Envelope<VALUE>> codec) -> FMLEnvironment.getDist().isClient() ?
                    registrar.configurationToClient(this.type, codec, PayloadBuilder.adaptClient(handler)) :
                    registrar.configurationToClient(this.type, codec);
        });
    }
    
    ///
    /// Declares the payload as sent from the client to the server, with the handler the server runs.
    ///
    /// @param handler The handler of the server, called with every value the server receives.
    ///
    /// @return The handle of the payload.
    ///
    /// @throws IllegalStateException If the identifier is already taken, or if the payload handlers event already ran.
    ///
    public ServerboundPayload<VALUE> toServer(BiConsumer<VALUE, ServerHandlerContext> handler) {
        return this.declare(Payload.Direction.SERVERBOUND, registrar -> switch (this.protocol) {
            case PayloadBuilder.Play(StreamCodec<RegistryFriendlyByteBuf, Envelope<VALUE>> codec) -> registrar.playToServer(
                    this.type,
                    codec,
                    PayloadBuilder.adaptServer(handler)
            );
            case PayloadBuilder.Configuration(StreamCodec<FriendlyByteBuf, Envelope<VALUE>> codec) -> registrar.configurationToServer(
                    this.type,
                    codec,
                    PayloadBuilder.adaptServer(handler)
            );
        });
    }
    
    ///
    /// Declares the payload as sent in both directions, with one handler for each receiving side.
    ///
    /// The client handler will only be opened on the client, once inside the payload handlers event.
    ///
    /// @param clientHandler The handler of the client, called with every value the client receives.
    /// @param serverHandler The handler of the server, called with every value the server receives.
    ///
    /// @return The handle of the payload.
    ///
    /// @throws IllegalStateException If the identifier is already taken, or if the payload handlers event already ran.
    ///
    public Payload<VALUE> bidirectional(ClientOnly<BiConsumer<VALUE, HandlerContext>> clientHandler, BiConsumer<VALUE, ServerHandlerContext> serverHandler) {
        return this.declare(Payload.Direction.BIDIRECTIONAL, registrar -> switch (this.protocol) {
            case PayloadBuilder.Play(StreamCodec<RegistryFriendlyByteBuf, Envelope<VALUE>> codec) -> registrar.playBidirectional(
                    this.type,
                    codec,
                    PayloadBuilder.adaptServer(serverHandler),
                    FMLEnvironment.getDist().isClient() ? PayloadBuilder.adaptClient(clientHandler) : null
            );
            case PayloadBuilder.Configuration(StreamCodec<FriendlyByteBuf, Envelope<VALUE>> codec) -> registrar.configurationBidirectional(
                    this.type,
                    codec,
                    PayloadBuilder.adaptServer(serverHandler),
                    FMLEnvironment.getDist().isClient() ? PayloadBuilder.adaptClient(clientHandler) : null
            );
        });
    }
    
    ///
    /// Declares the payload on the channel with the given registration and creates its handle.
    ///
    private Payload<VALUE> declare(Payload.Direction direction, UnaryOperator<PayloadRegistrar> registration) {
        Identifier id = this.getId();
        this.channel.declare(id, new PayloadBuilder.Declaration(this.protocol, this.thread, registration));
        return new Payload<>(this.channel, id, direction);
    }
    
    ///
    /// Opens the given client handler and wraps it into a neoforge handler of envelopes.
    ///
    private static <VALUE> IPayloadHandler<Envelope<VALUE>> adaptClient(ClientOnly<BiConsumer<VALUE, HandlerContext>> handler) {
        BiConsumer<VALUE, HandlerContext> opened = handler.get();
        return (envelope, context) -> opened.accept(envelope.value(), new HandlerContext(context));
    }
    
    ///
    /// Wraps the given server handler into a neoforge handler of envelopes.
    ///
    private static <VALUE> IPayloadHandler<Envelope<VALUE>> adaptServer(BiConsumer<VALUE, ServerHandlerContext> handler) {
        return (envelope, context) -> handler.accept(envelope.value(), new ServerHandlerContext(context));
    }
    
    ///
    /// The protocol of a payload, with the stream codec of its envelopes.
    ///
    /// @param <VALUE> The type of the values of the payload.
    ///
    sealed interface Protocol<VALUE> { }
    
    ///
    /// The play protocol, whose stream codec may read registries.
    ///
    /// @param codec   The stream codec of the envelopes.
    /// @param <VALUE> The type of the values of the payload.
    ///
    record Play<VALUE>(StreamCodec<RegistryFriendlyByteBuf, Envelope<VALUE>> codec) implements PayloadBuilder.Protocol<VALUE> { }
    
    ///
    /// The configuration protocol.
    ///
    /// @param codec   The stream codec of the envelopes.
    /// @param <VALUE> The type of the values of the payload.
    ///
    record Configuration<VALUE>(StreamCodec<FriendlyByteBuf, Envelope<VALUE>> codec) implements PayloadBuilder.Protocol<VALUE> { }
    
    ///
    /// The declaration of one payload, which its channel registers inside the payload handlers event.
    ///
    /// @param protocol     The protocol of the payload.
    /// @param thread       The thread the handlers of the payload run on.
    /// @param registration The registration of the payload, called with the registrar of its channel.
    ///
    record Declaration(PayloadBuilder.Protocol<?> protocol, HandlerThread thread, UnaryOperator<PayloadRegistrar> registration) {
        
        ///
        /// Registers the payload with the given registrar, on the thread of the declaration.
        ///
        /// @param registrar The registrar of the channel.
        ///
        void register(PayloadRegistrar registrar) {
            this.registration.apply(registrar.executesOn(this.thread));
        }
    }
}
