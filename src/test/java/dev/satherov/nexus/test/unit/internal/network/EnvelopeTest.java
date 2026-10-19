package dev.satherov.nexus.test.unit.internal.network;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.internal.network.Envelope;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

///
/// Checks the stream codec of envelopes.
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
}
