package dev.satherov.nexus.api.network;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.internal.network.Envelope;
import dev.satherov.nexus.internal.network.client.ClientConnection;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

///
/// A group of payloads of one mod that neoforge negotiates together, under one version.
///
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class Channel {
    
    ///
    /// The handle of the mod.
    ///
    private final NexusMod mod;
    
    ///
    /// The name of the channel, which is the mod id for the default channel.
    ///
    @Getter
    private final String name;
    
    ///
    /// A reference to the identifiers of every payload of the mod, which this channel adds the identifiers of its payloads to.
    ///
    /// Two channels can produce one identifier, such as the payload `b/c` on the default channel and `c` on the channel `b`.
    ///
    private final Set<Identifier> payloadIds;
    
    ///
    /// The declared payloads of the channel by their identifiers, in order, oldest first.
    ///
    private final Map<Identifier, PayloadBuilder.Declaration> declarationsById = new LinkedHashMap<>();
    
    ///
    /// The version neoforge negotiates for every payload of the channel.
    ///
    /// Defaults to `1`.
    ///
    @Getter
    private String version = "1";
    
    ///
    /// If a client or server lacking the channel should still connect.
    ///
    @Getter
    private boolean optional;
    
    ///
    /// Sets the version of the channel.
    ///
    /// @param version The version neoforge negotiates for every payload of the channel.
    ///
    /// @return The channel for chaining.
    ///
    /// @throws IllegalArgumentException If the version is blank.
    /// @throws IllegalStateException    If the payload handlers event already ran.
    ///
    @CanIgnoreReturnValue
    public Channel version(String version) {
        Network.checkBeforeEvent("change the version of the channel '" + this.name + "' of '" + this.mod.getModId() + "'");
        if (version.isBlank()) {
            throw new IllegalArgumentException("The version of the channel '" + this.name + "' of '" + this.mod.getModId() + "' is blank");
        }
        
        this.version = version;
        return this;
    }
    
    ///
    /// Lets a client or server lacking the channel connect.
    ///
    /// Sends will skip the players lacking it.
    ///
    /// @return The channel for chaining.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    @CanIgnoreReturnValue
    public Channel optional() {
        Network.checkBeforeEvent("make the channel '" + this.name + "' of '" + this.mod.getModId() + "' optional");
        this.optional = true;
        return this;
    }
    
    ///
    /// If the client of the given player registered every play payload of the channel.
    ///
    /// Will always be `true` for a required channel, and for a channel with only configuration payloads.
    ///
    /// @param player The player to check the client of.
    ///
    /// @return `true` if the client of the player registered every play payload of the channel.
    ///
    public boolean canSend(ServerPlayer player) {
        return this.hasEveryPlayPayload(player.connection);
    }
    
    ///
    /// If the server this client is connected to registered every play payload of the channel.
    ///
    /// Will always be `true` for a required channel, and for a channel with only configuration payloads.
    ///
    /// @return `true` if the server registered every play payload of the channel.
    ///
    /// @throws IllegalStateException If this runs on a dedicated server, or if the client is not in play.
    ///
    public boolean canSendToServer() {
        if (!FMLEnvironment.getDist().isClient()) {
            throw new IllegalStateException("Could not check the channel '" + this.name + "' of '" + this.mod.getModId() + "' against the server on a dedicated server");
        }
        
        return this.hasEveryPlayPayload(ClientConnection.getListener());
    }
    
    ///
    /// If the given packet listener has every play payload of the channel, or if the channel is required.
    ///
    private boolean hasEveryPlayPayload(ICommonPacketListener listener) {
        if (!this.optional) {
            return true;
        }
        
        return this.declarationsById.entrySet()
                .stream()
                .filter(entry -> entry.getValue().protocol() instanceof PayloadBuilder.Play<?>)
                .allMatch(entry -> listener.hasChannel(entry.getKey()));
    }
    
    ///
    /// Opens a play payload of the given name, encoded by the registry-aware stream codec of the given codec.
    ///
    /// @param name  The name of the payload.
    /// @param codec The codec of the values of the payload.
    ///
    /// @return The builder of the payload.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    /// @see NexusCodec#asStream()
    ///
    public <VALUE> PayloadBuilder<VALUE> play(String name, NexusCodec<VALUE, ?> codec) {
        return this.play(name, codec.asStream());
    }
    
    ///
    /// Opens a play payload of the given name, encoded by the given stream codec.
    ///
    /// @param name  The name of the payload.
    /// @param codec The stream codec of the values of the payload.
    ///
    /// @return The builder of the payload.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    public <VALUE> PayloadBuilder<VALUE> play(String name, StreamCodec<? super RegistryFriendlyByteBuf, VALUE> codec) {
        CustomPacketPayload.Type<Envelope<VALUE>> type = this.createType(name);
        return new PayloadBuilder<>(this, type, new PayloadBuilder.Play<>(Envelope.codec(type, codec)));
    }
    
    ///
    /// Opens a configuration payload of the given name, encoded by the plain stream codec of the given codec.
    ///
    /// @param name  The name of the payload.
    /// @param codec The codec of the values of the payload, which needs no registries.
    ///
    /// @return The builder of the payload.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    /// @see NexusCodec#plainStream(NexusCodec)
    ///
    public <VALUE> PayloadBuilder<VALUE> configuration(String name, NexusCodec<VALUE, Access.Plain> codec) {
        return this.configuration(name, NexusCodec.plainStream(codec));
    }
    
    ///
    /// Opens a configuration payload of the given name, encoded by the given stream codec.
    ///
    /// @param name  The name of the payload.
    /// @param codec The stream codec of the values of the payload.
    ///
    /// @return The builder of the payload.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    public <VALUE> PayloadBuilder<VALUE> configuration(String name, StreamCodec<? super FriendlyByteBuf, VALUE> codec) {
        CustomPacketPayload.Type<Envelope<VALUE>> type = this.createType(name);
        return new PayloadBuilder<>(this, type, new PayloadBuilder.Configuration<>(Envelope.codec(type, codec)));
    }
    
    ///
    /// Creates the type of the payload of the given name on this channel, and throws if the payload handlers event already ran.
    ///
    private <VALUE> CustomPacketPayload.Type<Envelope<VALUE>> createType(String name) {
        Identifier id = this.mod.id(this.name.equals(this.mod.getModId()) ? name : this.name + "/" + name);
        Network.checkBeforeEvent("open the payload '" + id + "'");
        return new CustomPacketPayload.Type<>(id);
    }
    
    ///
    /// Adds the given declaration of the payload with the given identifier.
    ///
    /// Should only ever be called from the terminals of [PayloadBuilder].
    ///
    /// @param id          The identifier of the payload.
    /// @param declaration The declaration of the payload.
    ///
    /// @throws IllegalStateException If the identifier is already declared on any channel of the mod, or if the payload handlers event already ran.
    ///
    void declare(Identifier id, PayloadBuilder.Declaration declaration) {
        Network.checkBeforeEvent("declare the payload '" + id + "'");
        if (!this.payloadIds.add(id)) {
            throw new IllegalStateException("'" + id + "' is already declared");
        }
        
        this.declarationsById.put(id, declaration);
    }
    
    ///
    /// Registers every payload of the channel with a registrar of its version, which is optional if the channel is.
    ///
    /// Should only ever be called from the payload handlers event of [Network].
    ///
    /// @param event The payload handlers event.
    ///
    void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(this.version);
        PayloadRegistrar target = this.optional ? registrar.optional() : registrar;
        this.declarationsById.values().forEach(declaration -> declaration.register(target));
    }
}
