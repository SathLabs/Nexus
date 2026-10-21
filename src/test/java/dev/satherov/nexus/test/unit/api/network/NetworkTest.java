package dev.satherov.nexus.test.unit.api.network;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
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
/// Checks the payloads that a mod declares on its network.
///
public class NetworkTest {

    private static @Nullable Throwable duplicateRefusal;
    private static @Nullable Throwable blankVersionRefusal;
    private static @Nullable ServerboundPayload<Integer> serverbound;
    private static @Nullable ClientboundPayload<String> clientbound;
    private static @Nullable Payload<Integer> bidirectional;
    private static @Nullable PayloadBuilder<Integer> unfinished;

    @Test
    public void namesAPayloadAfterTheModAndTheName() {
        Assertions.assertThat(Objects.requireNonNull(NetworkTest.serverbound).getId()).isEqualTo(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "network_test_serverbound"));
    }

    @Test
    public void defaultsToVersionOneAndRequired() {
        Payload<Integer> serverbound = (Payload<Integer>) Objects.requireNonNull(NetworkTest.serverbound);
        Assertions.assertThat(serverbound.getVersion()).isEqualTo("1");
        Assertions.assertThat(serverbound.isOptional()).isFalse();
    }

    @Test
    public void holdsTheVersionAndOptionalOfItsBuilder() {
        Payload<String> clientbound = (Payload<String>) Objects.requireNonNull(NetworkTest.clientbound);
        Assertions.assertThat(clientbound.getVersion()).isEqualTo("2");
        Assertions.assertThat(clientbound.isOptional()).isTrue();
    }

    @Test
    public void refusesABlankVersion() {
        Assertions.assertThat(NetworkTest.blankVersionRefusal).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void refusesASecondPayloadOfOneName() {
        Assertions.assertThat(NetworkTest.duplicateRefusal).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void returnsHandlesOfTheDirectionOfTheirTerminal() {
        Payload<Integer> serverbound = (Payload<Integer>) Objects.requireNonNull(NetworkTest.serverbound);
        Payload<String> clientbound = (Payload<String>) Objects.requireNonNull(NetworkTest.clientbound);
        Payload<Integer> bidirectional = Objects.requireNonNull(NetworkTest.bidirectional);

        Assertions.assertThat(serverbound.getDirection()).isEqualTo(Payload.Direction.SERVERBOUND);
        Assertions.assertThat(clientbound.getDirection()).isEqualTo(Payload.Direction.CLIENTBOUND);
        Assertions.assertThat(bidirectional.getId()).isEqualTo(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "network_test_bidirectional"));
        Assertions.assertThat(bidirectional.getDirection()).isEqualTo(Payload.Direction.BIDIRECTIONAL);
    }

    @Test
    public void refusesAnOpenerAfterTheEvent() {
        Network network = Nexus.getMod().getNetwork();
        Assertions.assertThatThrownBy(() -> network.play("network_test_late", NexusCodec.INT)).isInstanceOf(IllegalStateException.class);
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
            NetworkTest.serverbound = network.play("network_test_serverbound", NexusCodec.INT).toServer((_, _) -> { });
            NetworkTest.clientbound = network.configuration("network_test_clientbound", NexusCodec.STRING)
                    .version("2")
                    .optional()
                    .toClient(() -> (_, _) -> { });
            NetworkTest.bidirectional = network.play("network_test_bidirectional", ByteBufCodecs.VAR_INT)
                    .onNetworkThread()
                    .bidirectional(() -> (_, _) -> { }, (_, _) -> { });
            NetworkTest.unfinished = network.play("network_test_unfinished", NexusCodec.INT);
            NetworkTest.blankVersionRefusal = Assertions.catchThrowable(() -> network.play("network_test_blank", NexusCodec.INT).version(" "));
            NetworkTest.duplicateRefusal = Assertions.catchThrowable(() -> network.play("network_test_serverbound", NexusCodec.INT).toServer((_, _) -> { }));
        }
    }
}
