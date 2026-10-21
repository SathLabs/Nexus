package dev.satherov.nexus.api.network;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.network.context.ClientHandlerContext;
import dev.satherov.nexus.api.network.context.HandlerContext;
import dev.satherov.nexus.api.network.context.ServerHandlerContext;
import dev.satherov.nexus.api.network.payload.ClientboundPayload;
import dev.satherov.nexus.api.network.payload.Payload;
import dev.satherov.nexus.api.network.payload.ServerboundPayload;
import dev.satherov.nexus.api.registry.client.ClientOnly;
import dev.satherov.nexus.internal.network.Envelope;
import dev.satherov.nexus.internal.network.client.ClientConnection;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
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
/// A builder that declares one payload on the network.
///
/// @param <VALUE> The type of the values of the payload.
///
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class PayloadBuilder<VALUE> {
    
    ///
    /// The network the payload is declared on.
    ///
    private final Network network;
    
    ///
    /// The type of the envelopes of the payload, built from its identifier.
    ///
    private final CustomPacketPayload.Type<Envelope<VALUE>> type;
    
    ///
    /// The protocol of the payload, with the stream codec of its envelopes.
    ///
    private final PayloadBuilder.Protocol<VALUE> protocol;
    
    ///
    /// The version neoforge negotiates for the payload.
    ///
    /// Defaults to `1`.
    ///
    private String version = "1";
    
    ///
    /// If a client or server lacking the payload should still be allowed to connect.
    ///
    private boolean optional;
    
    ///
    /// The thread the handlers of the payload run on.
    ///
    /// Defaults to [HandlerThread#MAIN].
    ///
    private HandlerThread thread = HandlerThread.MAIN;
    
    ///
    /// Wraps the given server handler into a neoforge handler of envelopes.
    ///
    private static <VALUE> IPayloadHandler<Envelope<VALUE>> adaptServer(BiConsumer<VALUE, ServerHandlerContext> handler) {
        return (envelope, context) -> handler.accept(envelope.value(), new ServerHandlerContext(context));
    }
    
    ///
    /// The identifier the payload will be registered under.
    ///
    /// @return The identifier of the payload.
    ///
    public Identifier getId() {
        return this.type.id();
    }
    
    ///
    /// Sets the version of the payload.
    ///
    /// A client and a server only connect if both registered the payload with the same version.
    ///
    /// @param version The version neoforge negotiates for the payload.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalArgumentException If the version is blank.
    ///
    @CanIgnoreReturnValue
    public PayloadBuilder<VALUE> version(String version) {
        if (version.isBlank()) {
            throw new IllegalArgumentException("The version of the payload '" + this.type.id() + "' is blank");
        }
        
        this.version = version;
        return this;
    }
    
    ///
    /// Lets a client or server lacking the payload connect.
    ///
    /// Sending the payload will skip every recipient that doesn't have it registered.
    ///
    /// @return The builder for chaining.
    ///
    @CanIgnoreReturnValue
    public PayloadBuilder<VALUE> optional() {
        this.optional = true;
        return this;
    }
    
    ///
    /// Makes the handlers on the receiving run on the network thread instead of the main thread.
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
    public ClientboundPayload<VALUE> toClient(ClientOnly<BiConsumer<VALUE, ClientHandlerContext>> handler) {
        return this.declare(Payload.Direction.CLIENTBOUND, registrar -> switch (this.protocol) {
            case PayloadBuilder.Play(StreamCodec<RegistryFriendlyByteBuf, Envelope<VALUE>> codec) -> FMLEnvironment.getDist().isClient() ?
                    registrar.playToClient(this.type, codec, ClientConnection.adaptHandler(handler)) :
                    registrar.playToClient(this.type, codec);
            case PayloadBuilder.Configuration(StreamCodec<FriendlyByteBuf, Envelope<VALUE>> codec) -> FMLEnvironment.getDist().isClient() ?
                    registrar.configurationToClient(this.type, codec, ClientConnection.adaptHandler(handler)) :
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
    public Payload<VALUE> bidirectional(ClientOnly<BiConsumer<VALUE, ClientHandlerContext>> clientHandler, BiConsumer<VALUE, ServerHandlerContext> serverHandler) {
        return this.declare(Payload.Direction.BIDIRECTIONAL, registrar -> switch (this.protocol) {
            case PayloadBuilder.Play(StreamCodec<RegistryFriendlyByteBuf, Envelope<VALUE>> codec) -> registrar.playBidirectional(
                    this.type,
                    codec,
                    PayloadBuilder.adaptServer(serverHandler),
                    FMLEnvironment.getDist().isClient() ? ClientConnection.adaptHandler(clientHandler) : null
            );
            case PayloadBuilder.Configuration(StreamCodec<FriendlyByteBuf, Envelope<VALUE>> codec) -> registrar.configurationBidirectional(
                    this.type,
                    codec,
                    PayloadBuilder.adaptServer(serverHandler),
                    FMLEnvironment.getDist().isClient() ? ClientConnection.adaptHandler(clientHandler) : null
            );
        });
    }
    
    ///
    /// Declares the payload on the network with the given registration and creates its handle.
    ///
    private Payload<VALUE> declare(Payload.Direction direction, UnaryOperator<PayloadRegistrar> registration) {
        Identifier id = this.getId();
        this.network.declare(id, new PayloadBuilder.Declaration(this.version, this.optional, this.thread, registration));
        return new Payload<>(id, direction, this.version, this.optional);
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
    /// The declaration of one payload.
    ///
    /// @param version      The version neoforge negotiates for the payload.
    /// @param optional     If a client or server lacking the payload should still connect.
    /// @param thread       The thread the handlers of the payload run on.
    /// @param registration The registration of the payload, called with the registrar of the payload.
    ///
    record Declaration(String version, boolean optional, HandlerThread thread, UnaryOperator<PayloadRegistrar> registration) {
        
        ///
        /// Registers the payload with a registrar of its version, optional flag and thread.
        ///
        /// @param event The payload handlers event.
        ///
        void register(RegisterPayloadHandlersEvent event) {
            PayloadRegistrar registrar = event.registrar(this.version).executesOn(this.thread);
            this.registration.apply(this.optional ? registrar.optional() : registrar);
        }
    }
}
