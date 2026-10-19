package dev.satherov.nexus.api.network;

import lombok.Getter;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.mod.NexusMod;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

///
/// A table that holds the channels of one mod and opens payloads on its default channel.
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
    /// Every channel of the mod by its name, the default channel included.
    ///
    private final Map<String, Channel> channelsByName = new HashMap<>();
    
    ///
    /// The identifiers of every payload declared on any channel of the mod.
    ///
    private final Set<Identifier> payloadIds = new HashSet<>();
    
    ///
    /// The channel named after the mod, which the openers of this type declare on.
    ///
    @Getter
    private final Channel defaultChannel;
    
    ///
    /// Creates the empty network of the given mod with its default channel and subscribes it to the payload handlers event on the event bus of the mod.
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
        
        this.defaultChannel = this.channel(mod.getModId());
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
    /// Creates a channel of the given name at version `1`.
    ///
    /// @param name The name of the channel.
    ///
    /// @return The created channel.
    ///
    /// @throws IllegalStateException If the name is already taken, or if the payload handlers event already ran.
    ///
    public Channel channel(String name) {
        Network.checkBeforeEvent("create the channel '" + name + "' of '" + this.mod.getModId() + "'");
        Channel channel = new Channel(this.mod, name, this.payloadIds);
        if (this.channelsByName.putIfAbsent(name, channel) != null) {
            throw new IllegalStateException("The channel '" + name + "' of '" + this.mod.getModId() + "' already exists");
        }
        
        return channel;
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
    /// Opens a play payload of the given name on the default channel, encoded by the registry-aware stream codec of the given codec.
    ///
    /// @param name  The name of the payload.
    /// @param codec The codec of the values of the payload.
    ///
    /// @return The builder of the payload.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    /// @see Channel#play(String, NexusCodec)
    ///
    public <VALUE> PayloadBuilder<VALUE> play(String name, NexusCodec<VALUE, ?> codec) {
        return this.defaultChannel.play(name, codec);
    }
    
    ///
    /// Opens a play payload of the given name on the default channel, encoded by the given stream codec.
    ///
    /// @param name  The name of the payload.
    /// @param codec The stream codec of the values of the payload.
    ///
    /// @return The builder of the payload.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    /// @see Channel#play(String, StreamCodec)
    ///
    public <VALUE> PayloadBuilder<VALUE> play(String name, StreamCodec<? super RegistryFriendlyByteBuf, VALUE> codec) {
        return this.defaultChannel.play(name, codec);
    }
    
    ///
    /// Opens a configuration payload of the given name on the default channel, encoded by the plain stream codec of the given codec.
    ///
    /// @param name  The name of the payload.
    /// @param codec The codec of the values of the payload, which needs no registries.
    ///
    /// @return The builder of the payload.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    /// @see Channel#configuration(String, NexusCodec)
    ///
    public <VALUE> PayloadBuilder<VALUE> configuration(String name, NexusCodec<VALUE, Access.Plain> codec) {
        return this.defaultChannel.configuration(name, codec);
    }
    
    ///
    /// Opens a configuration payload of the given name on the default channel, encoded by the given stream codec.
    ///
    /// @param name  The name of the payload.
    /// @param codec The stream codec of the values of the payload.
    ///
    /// @return The builder of the payload.
    ///
    /// @throws IllegalStateException If the payload handlers event already ran.
    ///
    /// @see Channel#configuration(String, StreamCodec)
    ///
    public <VALUE> PayloadBuilder<VALUE> configuration(String name, StreamCodec<? super FriendlyByteBuf, VALUE> codec) {
        return this.defaultChannel.configuration(name, codec);
    }
    
    ///
    /// Registers the payloads of every channel of the mod.
    ///
    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        this.channelsByName.values().forEach(channel -> channel.register(event));
    }
}
