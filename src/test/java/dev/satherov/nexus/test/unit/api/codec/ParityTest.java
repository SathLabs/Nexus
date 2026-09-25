package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.CodecFormat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

///
/// Checks that every parity case writes the same JSON, NBT, and bytes as its DFU and vanilla twins, and that each side decodes the other's output.
///
public class ParityTest {
    
    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final CodecFormat<JsonElement, Access.Registries> JSON = CodecFormat.JSON.withRegistries(ParityTest.REGISTRIES);
    private static final CodecFormat<Tag, Access.Registries> NBT = CodecFormat.NBT.withRegistries(ParityTest.REGISTRIES);
    private static final RegistryOps<JsonElement> JSON_OPS = RegistryOps.create(JsonOps.INSTANCE, ParityTest.REGISTRIES);
    private static final RegistryOps<Tag> NBT_OPS = RegistryOps.create(NbtOps.INSTANCE, ParityTest.REGISTRIES);
    
    public static Stream<Arguments> samples() {
        return ParityCases.ALL.stream().flatMap(parity -> parity.values().stream().map(value -> Arguments.of(Named.of(parity.name(), parity), value)));
    }
    
    @ParameterizedTest(name = "{0} [{index}]")
    @MethodSource("samples")
    public <T> void jsonMatchesDfu(ParityCases.Case<T> parity, T value) {
        JsonElement nexus = parity.codec().encode(ParityTest.JSON, value);
        JsonElement dfu = parity.dfu().encodeStart(ParityTest.JSON_OPS, value).getOrThrow();
        T dfuRoundTrip = parity.dfu().parse(ParityTest.JSON_OPS, dfu).getOrThrow();
        
        Assertions.assertThat(nexus.toString()).isEqualTo(dfu.toString());
        Assertions.assertThat(parity.dfu().parse(ParityTest.JSON_OPS, nexus).getOrThrow()).isEqualTo(dfuRoundTrip);
        Assertions.assertThat(parity.codec().decode(ParityTest.JSON, dfu)).isEqualTo(dfuRoundTrip);
    }
    
    @ParameterizedTest(name = "{0} [{index}]")
    @MethodSource("samples")
    public <T> void nbtMatchesDfu(ParityCases.Case<T> parity, T value) {
        Tag nexus = parity.codec().encode(ParityTest.NBT, value);
        Tag dfu = parity.dfu().encodeStart(ParityTest.NBT_OPS, value).getOrThrow();
        T dfuRoundTrip = parity.dfu().parse(ParityTest.NBT_OPS, dfu).getOrThrow();
        
        Assertions.assertThat(nexus).isEqualTo(dfu);
        Assertions.assertThat(parity.dfu().parse(ParityTest.NBT_OPS, nexus).getOrThrow()).isEqualTo(dfuRoundTrip);
        Assertions.assertThat(parity.codec().decode(ParityTest.NBT, dfu)).isEqualTo(dfuRoundTrip);
    }
    
    @ParameterizedTest(name = "{0} [{index}]")
    @MethodSource("samples")
    public <T> void nettyMatchesStreamCodec(ParityCases.Case<T> parity, T value) {
        RegistryFriendlyByteBuf nexus = new RegistryFriendlyByteBuf(Unpooled.buffer(), ParityTest.REGISTRIES);
        RegistryFriendlyByteBuf vanilla = new RegistryFriendlyByteBuf(Unpooled.buffer(), ParityTest.REGISTRIES);
        parity.codec().encode(CodecFormat.netty(nexus), value);
        parity.stream().encode(vanilla, value);
        
        Assertions.assertThat(ByteBufUtil.getBytes(nexus)).isEqualTo(ByteBufUtil.getBytes(vanilla));
        Assertions.assertThat(parity.stream().decode(nexus)).isEqualTo(value);
        Assertions.assertThat(nexus.readableBytes()).isZero();
        Assertions.assertThat(parity.codec().decode(CodecFormat.netty(vanilla))).isEqualTo(value);
        Assertions.assertThat(vanilla.readableBytes()).isZero();
    }
}
