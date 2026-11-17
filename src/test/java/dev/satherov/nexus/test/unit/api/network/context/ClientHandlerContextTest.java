package dev.satherov.nexus.test.unit.api.network.context;

import dev.satherov.nexus.api.network.context.ClientHandlerContext;

import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

///
/// Checks the context of payload handlers on the client.
///
public class ClientHandlerContextTest {

    @Test
    public void refusesThePlayerInPlayBeforeTheClientHasOne() {
        ClientHandlerContext context = new ClientHandlerContext(new ClientHandlerContextTest.PlayerlessContext(ConnectionProtocol.PLAY));
        Assertions.assertThatIllegalStateException().isThrownBy(context::getPlayer);
    }

    private record PlayerlessContext(ConnectionProtocol protocol) implements IPayloadContext {

        @Override
        public Player player() {
            throw new UnsupportedOperationException("Cannot retrieve the client player during the configuration phase.");
        }

        @Override
        public ICommonPacketListener listener() {
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
}
