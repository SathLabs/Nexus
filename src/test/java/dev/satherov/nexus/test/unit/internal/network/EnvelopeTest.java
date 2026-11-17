package dev.satherov.nexus.test.unit.internal.network;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.internal.network.Envelope;

import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;

import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

///
/// Checks the stream codec and the handler of envelopes.
///
public class EnvelopeTest {

    private static final CustomPacketPayload.Type<Envelope<String>> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "envelope_test"));

    @Test
    public void readsTheEnvelopeItWrote() {
        StreamCodec<RegistryFriendlyByteBuf, Envelope<String>> codec = Envelope.codec(EnvelopeTest.TYPE, NexusCodec.STRING.asStream());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        Envelope<String> written = new Envelope<>(EnvelopeTest.TYPE, "envelope");

        codec.encode(buffer, written);
        Assertions.assertThat(codec.decode(buffer)).isEqualTo(written);
        Assertions.assertThat(buffer.readableBytes()).isZero();
    }

    @Test
    public void handlesTheValueOnAConnectionThatAcceptsMessages() {
        List<String> handled = new ArrayList<>();
        IPayloadHandler<Envelope<String>> handler = Envelope.handler(Function.identity(), (value, _) -> handled.add(value));

        handler.handle(new Envelope<>(EnvelopeTest.TYPE, "accepted"), new EnvelopeTest.FakeContext(new EnvelopeTest.FakeListener(true)));
        Assertions.assertThat(handled).containsExactly("accepted");
    }

    @Test
    public void skipsTheValueOnAConnectionThatStoppedAcceptingMessages() {
        List<String> handled = new ArrayList<>();
        IPayloadHandler<Envelope<String>> handler = Envelope.handler(Function.identity(), (value, _) -> handled.add(value));

        handler.handle(new Envelope<>(EnvelopeTest.TYPE, "closed"), new EnvelopeTest.FakeContext(new EnvelopeTest.FakeListener(false)));
        Assertions.assertThat(handled).isEmpty();
    }

    private record FakeContext(ICommonPacketListener listener) implements IPayloadContext {

        @Override
        public Player player() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> enqueueWork(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <RESULT> CompletableFuture<RESULT> enqueueWork(Supplier<RESULT> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PacketFlow flow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void handle(CustomPacketPayload payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void finishCurrentTask(ConfigurationTask.Type type) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeListener(boolean accepting) implements ICommonPacketListener {

        @Override
        public boolean isAcceptingMessages() {
            return this.accepting;
        }

        @Override
        public void send(Packet<?> packet) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(CustomPacketPayload payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void disconnect(Component reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Connection getConnection() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PacketProcessor getPacketProcessor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConnectionType getConnectionType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PacketFlow flow() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConnectionProtocol protocol() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onDisconnect(DisconnectionDetails details) {
            throw new UnsupportedOperationException();
        }
    }
}
