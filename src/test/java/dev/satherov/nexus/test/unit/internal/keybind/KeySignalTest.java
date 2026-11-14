package dev.satherov.nexus.test.unit.internal.keybind;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.vanilla.VanillaCodecs;
import dev.satherov.nexus.internal.keybind.KeySignal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Items;

import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

///
/// Checks that the stream codec of key signals reads back the signals it wrote.
///
public class KeySignalTest {
    
    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    
    @Test
    public void firingWithOneValueRoundTrips() {
        StreamCodec<RegistryFriendlyByteBuf, KeySignal> codec = KeySignal.codec(List.of(NexusCodec.INT.asStream()));
        KeySignalTest.assertRoundTrips(codec, new KeySignal.Fired(List.of(7)));
    }
    
    @Test
    public void firingWithTwoValuesRoundTrips() {
        StreamCodec<RegistryFriendlyByteBuf, KeySignal> codec = KeySignal.codec(List.of(NexusCodec.INT.asStream(), NexusCodec.STRING.asStream()));
        KeySignalTest.assertRoundTrips(codec, new KeySignal.Fired(List.of(7, "seven")));
    }
    
    @Test
    public void firingWithEightValuesRoundTrips() {
        StreamCodec<RegistryFriendlyByteBuf, KeySignal> codec = KeySignal.codec(List.of(
                NexusCodec.INT.asStream(),
                NexusCodec.STRING.asStream(),
                NexusCodec.BOOL.asStream(),
                NexusCodec.LONG.asStream(),
                NexusCodec.DOUBLE.asStream(),
                VanillaCodecs.BLOCK_POS.asStream(),
                VanillaCodecs.DIRECTION.asStream(),
                NexusCodec.holder(Registries.ITEM).asStream()
        ));
        KeySignal.Fired fired = new KeySignal.Fired(List.of(
                1,
                "two",
                true,
                4L,
                5.0D,
                new BlockPos(6, 6, 6),
                Direction.SOUTH,
                Items.DIAMOND.builtInRegistryHolder()
        ));
        KeySignalTest.assertRoundTrips(codec, fired);
    }
    
    @Test
    public void downAndUpRoundTrip() {
        StreamCodec<RegistryFriendlyByteBuf, KeySignal> codec = KeySignal.codec(List.of(NexusCodec.IDENTIFIER.asStream()));
        KeySignalTest.assertRoundTrips(codec, KeySignal.Down.INSTANCE);
        KeySignalTest.assertRoundTrips(codec, KeySignal.Up.INSTANCE);
    }
    
    private static void assertRoundTrips(StreamCodec<RegistryFriendlyByteBuf, KeySignal> codec, KeySignal signal) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), KeySignalTest.REGISTRIES);
        codec.encode(buffer, signal);
        Assertions.assertThat(codec.decode(buffer)).isEqualTo(signal);
        Assertions.assertThat(buffer.readableBytes()).isZero();
    }
}
