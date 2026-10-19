package dev.satherov.nexus.test.unit.api.network;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.network.Channel;
import dev.satherov.nexus.api.network.Network;
import dev.satherov.nexus.api.network.PayloadBuilder;
import dev.satherov.nexus.api.network.payload.ClientboundPayload;
import dev.satherov.nexus.api.network.payload.Payload;
import dev.satherov.nexus.api.network.payload.ServerboundPayload;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;

///
/// Checks the channels and payloads that a mod declares on its network.
///
public class NetworkTest {
    
    private static @Nullable Channel channel;
    private static @Nullable Throwable duplicateChannelRefusal;
    private static @Nullable Throwable collisionRefusal;
    private static @Nullable Identifier defaultChannelId;
    private static @Nullable Identifier namedChannelId;
    private static @Nullable ServerboundPayload<Integer> serverbound;
    private static @Nullable ClientboundPayload<String> clientbound;
    private static @Nullable Payload<Integer> bidirectional;
    private static @Nullable PayloadBuilder<Integer> unfinished;
    
    @Test
    public void namesTheDefaultChannelAfterTheMod() {
        Channel channel = Nexus.getMod().getNetwork().getDefaultChannel();
        Assertions.assertThat(channel.getName()).isEqualTo(Nexus.MOD_ID);
        Assertions.assertThat(channel.getVersion()).isEqualTo("1");
        Assertions.assertThat(channel.isOptional()).isFalse();
    }
    
    @Test
    public void holdsTheVersionAndOptionalOfACreatedChannel() {
        Channel channel = Objects.requireNonNull(NetworkTest.channel);
        Assertions.assertThat(channel.getName()).isEqualTo("network_test");
        Assertions.assertThat(channel.getVersion()).isEqualTo("2");
        Assertions.assertThat(channel.isOptional()).isTrue();
    }
    
    @Test
    public void refusesASecondChannelOfOneName() {
        Assertions.assertThat(NetworkTest.duplicateChannelRefusal).isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    public void namesAPayloadOnTheDefaultChannelAfterTheModAndTheName() {
        Assertions.assertThat(NetworkTest.defaultChannelId).isEqualTo(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "network_test_serverbound"));
    }
    
    @Test
    public void namesAPayloadOnANamedChannelAfterTheModTheChannelAndTheName() {
        Assertions.assertThat(NetworkTest.namedChannelId).isEqualTo(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "network_test/network_test_clientbound"));
    }
    
    @Test
    public void refusesAPayloadWhoseIdAnotherChannelAlreadyDeclared() {
        Assertions.assertThat(NetworkTest.collisionRefusal).isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    public void returnsHandlesOfTheIdAndTheDirectionOfTheirTerminal() {
        Payload<Integer> serverbound = (Payload<Integer>) Objects.requireNonNull(NetworkTest.serverbound);
        Payload<String> clientbound = (Payload<String>) Objects.requireNonNull(NetworkTest.clientbound);
        Payload<Integer> bidirectional = Objects.requireNonNull(NetworkTest.bidirectional);
        
        Assertions.assertThat(serverbound.getId()).isEqualTo(NetworkTest.defaultChannelId);
        Assertions.assertThat(serverbound.getDirection()).isEqualTo(Payload.Direction.SERVERBOUND);
        Assertions.assertThat(clientbound.getId()).isEqualTo(NetworkTest.namedChannelId);
        Assertions.assertThat(clientbound.getDirection()).isEqualTo(Payload.Direction.CLIENTBOUND);
        Assertions.assertThat(bidirectional.getId()).isEqualTo(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "network_test_bidirectional"));
        Assertions.assertThat(bidirectional.getDirection()).isEqualTo(Payload.Direction.BIDIRECTIONAL);
    }
    
    @Test
    public void refusesAChannelAfterTheEvent() {
        Network network = Nexus.getMod().getNetwork();
        Assertions.assertThatThrownBy(() -> network.channel("network_test_late")).isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    public void refusesATerminalAfterTheEvent() {
        PayloadBuilder<Integer> builder = Objects.requireNonNull(NetworkTest.unfinished);
        Assertions.assertThatThrownBy(() -> builder.toServer((_, _) -> { })).isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    public void refusesANewNetworkAfterTheEvent() {
        Assertions.assertThatThrownBy(() -> new Network(Nexus.getMod())).isInstanceOf(IllegalStateException.class);
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Network network = Nexus.getMod().getNetwork();
            Channel channel = network.channel("network_test").version("2").optional();
            PayloadBuilder<Integer> serverbound = network.play("network_test_serverbound", NexusCodec.INT);
            PayloadBuilder<String> clientbound = channel.configuration("network_test_clientbound", NexusCodec.STRING);
            
            NetworkTest.channel = channel;
            NetworkTest.duplicateChannelRefusal = Assertions.catchThrowable(() -> network.channel("network_test"));
            NetworkTest.defaultChannelId = serverbound.getId();
            NetworkTest.namedChannelId = clientbound.getId();
            NetworkTest.serverbound = serverbound.toServer((_, _) -> { });
            NetworkTest.clientbound = clientbound.toClient(() -> (_, _) -> { });
            NetworkTest.bidirectional = network.play("network_test_bidirectional", ByteBufCodecs.VAR_INT)
                    .onNetworkThread()
                    .bidirectional(() -> (_, _) -> { }, (_, _) -> { });
            NetworkTest.unfinished = network.play("network_test_unfinished", NexusCodec.INT);
            
            network.play("network_test/collision", NexusCodec.INT).toServer((_, _) -> { });
            NetworkTest.collisionRefusal = Assertions.catchThrowable(() -> channel.play("collision", NexusCodec.INT).toServer((_, _) -> { }));
        }
    }
}
