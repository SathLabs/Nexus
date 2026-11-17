package dev.satherov.nexus.test.unit.internal.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.key.MapKey;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.CodecResult;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.test.unit.api.codec.ParityCases;

import net.minecraft.network.FriendlyByteBuf;

import com.mojang.datafixers.util.Either;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

///
/// Checks that a codec encodes only NBT tags that vanilla can read back, and that its entry points that return a failure leave the buffer of the format where it was before.
///
public class TraversalTest {
    
    private static final StructCodec<Triple, Access.Plain> TRIPLE = NexusCodec.struct(
            "triple",
            NexusCodec.INT.field("first", Triple::first),
            NexusCodec.INT.field("second", Triple::second),
            NexusCodec.INT.validate(value -> value < 0 ? "negative" : null).field("third", Triple::third),
            Triple::new
    );
    
    private static final NexusCodec<ParityCases.Node, Access.Plain> TREE = NexusCodec.recursive(
            "node",
            self -> NexusCodec.struct(
                    "node",
                    NexusCodec.INT.field("value", ParityCases.Node::value),
                    self.list().field("children", ParityCases.Node::children),
                    ParityCases.Node::new
            )
    );
    
    private static final NexusCodec<Nest, Access.Plain> NEST = NexusCodec.recursive(
            "nest",
            self -> NexusCodec.either(NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT.list()).list(), self.list()).xmap(Nest::new, Nest::content)
    );
    
    @Test
    public void nbtEncodingAcceptsATagOf512Levels() {
        ParityCases.Node tree = TraversalTest.chain(256);
        
        Assertions.assertThat(TraversalTest.TREE.decode(CodecFormat.NBT, TraversalTest.TREE.encode(CodecFormat.NBT, tree))).isEqualTo(tree);
    }
    
    @Test
    public void nbtEncodingRefusesATagOfMoreThan512Levels() {
        List<ParityCases.Node> trees = List.of(TraversalTest.chain(256));
        List<Either<Integer, List<ParityCases.Node>>> mixed = List.of(Either.left(1), Either.right(List.of(TraversalTest.chain(255))));
        
        Assertions.assertThat(TraversalTest.errors(() -> TraversalTest.TREE.list().encode(CodecFormat.NBT, trees)))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.message()).contains("512"));
        Assertions.assertThat(TraversalTest.errors(() -> NexusCodec.either(NexusCodec.INT, TraversalTest.TREE.list()).list().encode(CodecFormat.NBT, mixed)))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.message()).contains("512"));
        Assertions.assertThatCode(() -> TraversalTest.TREE.list().encode(CodecFormat.JSON, trees)).doesNotThrowAnyException();
    }
    
    @Test
    public void nbtEncodingRefusesAWrapperElementOfAListAt512Levels() {
        Nest nest = new Nest(Either.left(List.of(Map.of("", List.of()))));
        for (int level = 1; level < 512; level++) {
            nest = new Nest(Either.right(List.of(nest)));
        }
        
        Nest boundary = nest;
        Assertions.assertThat(TraversalTest.errors(() -> TraversalTest.NEST.encode(CodecFormat.NBT, boundary)))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.message()).contains("512"));
        Assertions.assertThatCode(() -> TraversalTest.NEST.encode(CodecFormat.JSON, boundary)).doesNotThrowAnyException();
    }
    
    @Test
    public void tryDecodeMovesTheReaderIndexBackIfTheThirdFieldFails() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeByte(0x7F);
        buffer.writeInt(1);
        buffer.writeInt(2);
        buffer.writeInt(-1);
        buffer.readerIndex(1);
        
        Assertions.assertThat(TraversalTest.TRIPLE.tryDecode(CodecFormat.netty(buffer))).isInstanceOf(CodecResult.Failure.class);
        Assertions.assertThat(buffer.readerIndex()).isEqualTo(1);
        Assertions.assertThat(TraversalTest.TRIPLE.tryDecode(CodecFormat.netty(buffer), buffer)).isInstanceOf(CodecResult.Failure.class);
        Assertions.assertThat(buffer.readerIndex()).isEqualTo(1);
    }
    
    @Test
    public void tryEncodeMovesTheWriterIndexBackIfTheThirdFieldFails() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeByte(0x7F);
        
        Assertions.assertThat(TraversalTest.TRIPLE.tryEncode(CodecFormat.netty(buffer), new Triple(1, 2, -1))).isInstanceOf(CodecResult.Failure.class);
        Assertions.assertThat(buffer.writerIndex()).isEqualTo(1);
    }
    
    private static List<CodecError> errors(ThrowableAssert.ThrowingCallable call) {
        return Assertions.catchThrowableOfType(NexusCodecException.class, call).errors();
    }
    
    private static ParityCases.Node chain(int levels) {
        ParityCases.Node node = new ParityCases.Node(levels, List.of());
        for (int level = levels - 1; level > 0; level--) {
            node = new ParityCases.Node(level, List.of(node));
        }
        
        return node;
    }
    
    public record Triple(int first, int second, int third) { }
    
    public record Nest(Either<List<Map<String, List<Integer>>>, List<Nest>> content) { }
}
