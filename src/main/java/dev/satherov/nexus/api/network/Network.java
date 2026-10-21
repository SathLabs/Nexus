package dev.satherov.nexus.api.network;

import lombok.Getter;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.mod.NexusMod;
import dev.satherov.nexus.internal.network.Envelope;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashMap;
import java.util.Map;

///
/// A table that holds the payloads of one mod and starts their builders.
///
@EventBusSubscriber(modid = Nexus.MOD_ID)
public final class Network {
    
    ///
    /// If the payload handlers event already ran.
    ///
    private static boolean fired;
    
    ///
    /// The handle of the mod.
    ///
    @Getter
    private final NexusMod mod;
    
    ///
    /// The declared payloads of the mod by their identifiers.
    ///
    private final Map<Identifier, PayloadBuilder.Declaration> declarationsById = new LinkedHashMap<>();
    
    ///
    /// Should only ever be called from [NexusMod#getNetwork()].
    ///
    /// @param mod The handle of the mod.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    @ApiStatus.Internal
    public Network(NexusMod mod) {
        Network.checkBeforeEvent("create the network of '" + mod.getModId() + "'");
        this.mod = mod;
        
        mod.getEventBus().addListener(RegisterPayloadHandlersEvent.class, this::onRegisterPayloads);
    }
    
    ///
    /// Records that the payload handlers event ran.
    ///
    /// Runs on the event bus of Nexus, which gets the event before every mod that depends on it.
    ///
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        Network.fired = true;
    }
    
    ///
    /// Throws an [IllegalStateException] if the payload handlers event already ran.
    ///
    /// @param action The action to refuse, used in the failure message.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    static void checkBeforeEvent(String action) {
        if (Network.fired) {
            throw new IllegalStateException("Could not " + action + " because the payload handlers event already ran");
        }
    }
    
    ///
    /// Starts a new play payload builder with the given codec.
    ///
    /// The identifier will be `<modid>:<name>`.
    ///
    /// @param name  The name of the payload.
    /// @param codec The codec of the values of the payload.
    ///
    /// @return The builder of the payload.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    public <VALUE> PayloadBuilder<VALUE> play(String name, NexusCodec<VALUE, ?> codec) {
        return this.play(name, codec.asStream());
    }
    
    ///
    /// Starts a new play payload builder with the given stream codec.
    ///
    /// The identifier will be `<modid>:<name>`.
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
    /// Starts a new configuration payload builder with the given codec.
    ///
    /// The identifier will be `<modid>:<name>`.
    ///
    /// @param name  The name of the payload.
    /// @param codec The codec of the values of the payload, which does not require registries.
    ///
    /// @return The builder of the payload.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    public <VALUE> PayloadBuilder<VALUE> configuration(String name, NexusCodec<VALUE, Access.Plain> codec) {
        return this.configuration(name, NexusCodec.plainStream(codec));
    }
    
    ///
    /// Starts a new configuration payload builder with the given stream codec.
    ///
    /// The identifier will be `<modid>:<name>`.
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
    /// Creates the type of the payload of the given name and throws if the payload handlers event already ran.
    ///
    private <VALUE> CustomPacketPayload.Type<Envelope<VALUE>> createType(String name) {
        Identifier id = this.mod.id(name);
        Network.checkBeforeEvent("start the payload '" + id + "'");
        return new CustomPacketPayload.Type<>(id);
    }
    
    ///
    /// Adds the given declaration of the payload with the given identifier.
    ///
    /// Should only ever be called from the declaration methods in [PayloadBuilder].
    ///
    /// @param id          The identifier of the payload.
    /// @param declaration The declaration of the payload.
    ///
    /// @throws IllegalStateException If the identifier is already declared, or if the payload handlers event already ran.
    ///
    void declare(Identifier id, PayloadBuilder.Declaration declaration) {
        Network.checkBeforeEvent("declare the payload '" + id + "'");
        if (this.declarationsById.putIfAbsent(id, declaration) != null) {
            throw new IllegalStateException("'" + id + "' is already declared");
        }
    }
    
    ///
    /// Registers every payload of the mod.
    ///
    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        this.declarationsById.values().forEach(declaration -> declaration.register(event));
    }
}
