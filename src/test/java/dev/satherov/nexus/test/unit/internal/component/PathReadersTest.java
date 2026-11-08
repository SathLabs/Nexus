package dev.satherov.nexus.test.unit.internal.component;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.component.Comparison;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.component.PathReader;
import dev.satherov.nexus.api.event.component.RegisterPathReadersEvent;
import dev.satherov.nexus.extension.component.ComponentTypeCodec;
import dev.satherov.nexus.internal.component.PathReaders;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;

///
/// Checks the order in which registered readers are asked, the codec that a type was built with, and the encoding of the whole value through the codec of the type.
///
public class PathReadersTest {
    
    private static final List<ComponentPath.Step> STEPS = List.of(new ComponentPath.Step.Key("level"));
    
    private static final PathReader<Integer> FIRST = new FixedReader();
    private static final PathReader<Integer> SECOND = new FixedReader();
    private static final PathReader<Integer> BY_CODEC = new FixedReader();
    
    private static final Codec<Integer> ORDERED_CODEC = new CountingCodec();
    private static final Codec<Integer> SHARED_CODEC = new CountingCodec();
    
    private static final DataComponentType<Integer> ORDERED = DataComponentType.<Integer>builder().persistent(PathReadersTest.ORDERED_CODEC).build();
    private static final DataComponentType<Integer> SHARED = DataComponentType.<Integer>builder().persistent(PathReadersTest.SHARED_CODEC).build();
    private static final DataComponentType<Integer> SHARED_CACHED = DataComponentType.<Integer>builder().persistent(PathReadersTest.SHARED_CODEC).cacheEncoding().build();
    
    @Test
    public void asksTheReadersOfAComponentInTheOrderTheyWereRegistered() {
        Assertions.assertThat(PathReaders.resolve(PathReadersTest.ORDERED, PathReadersTest.STEPS)).isSameAs(PathReadersTest.FIRST);
    }
    
    @Test
    public void asksTheReadersOfACodecAfterEveryReaderOfTheComponentDeclined() {
        Assertions.assertThat(PathReaders.resolve(PathReadersTest.ORDERED, List.of())).isSameAs(PathReadersTest.BY_CODEC);
    }
    
    @Test
    public void matchesTheCodecThatATypeWasBuiltWith() {
        Assertions.assertThat(PathReaders.resolve(PathReadersTest.SHARED, PathReadersTest.STEPS)).isSameAs(PathReadersTest.BY_CODEC);
        Assertions.assertThat(PathReaders.resolve(PathReadersTest.SHARED_CACHED, PathReadersTest.STEPS)).isSameAs(PathReadersTest.BY_CODEC);
    }
    
    @Test
    public void matchesTheCodecByIdentity() {
        DataComponentType<Integer> type = DataComponentType.<Integer>builder().persistent(new CountingCodec()).build();
        Assertions.assertThat(PathReaders.resolve(type, PathReadersTest.STEPS)).isNotSameAs(PathReadersTest.BY_CODEC);
    }
    
    @Test
    public void walksTheCodecIfEveryReaderDeclined() {
        PathReader<Integer> reader = PathReaders.resolve(PathReadersTest.SHARED, List.of());
        Assertions.assertThat(reader).isNotSameAs(PathReadersTest.BY_CODEC);
        Assertions.assertThat(reader.getCost()).isEqualTo(PathReader.Cost.ENCODE);
    }
    
    @Test
    public void keepsTheCodecThatACachedTypeWasBuiltWith() {
        Assertions.assertThat(PathReadersTest.SHARED_CACHED.codec()).isNotSameAs(PathReadersTest.SHARED_CODEC);
        Assertions.assertThat(((ComponentTypeCodec) PathReadersTest.SHARED_CACHED).nexus$getCodec()).isSameAs(PathReadersTest.SHARED_CODEC);
        Assertions.assertThat(((ComponentTypeCodec) PathReadersTest.SHARED).nexus$getCodec()).isSameAs(PathReadersTest.SHARED_CODEC);
    }
    
    @Test
    public void keepsNoCodecOnATransientType() {
        DataComponentType<Integer> type = DataComponentType.<Integer>builder().networkSynchronized(ByteBufCodecs.VAR_INT).build();
        Assertions.assertThat(((ComponentTypeCodec) type).nexus$getCodec()).isNull();
    }
    
    @Test
    public void encodesTheWholeValueOfACachedTypeThroughItsEncoderCache() {
        CountingCodec codec = new CountingCodec();
        DataComponentType<Integer> type = DataComponentType.<Integer>builder().persistent(codec).cacheEncoding().build();
        Predicate<Integer> test = PathReaders.resolve(type, List.of()).bind(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(3.0D)));
        Assertions.assertThat(test.test(3)).isTrue();
        Assertions.assertThat(test.test(3)).isTrue();
        Assertions.assertThat(test.test(4)).isFalse();
        Assertions.assertThat(codec.encodes).isEqualTo(2);
    }
    
    @Test
    public void encodesTheWholeValueOfAnUncachedTypeOnEveryRead() {
        CountingCodec codec = new CountingCodec();
        DataComponentType<Integer> type = DataComponentType.<Integer>builder().persistent(codec).build();
        Predicate<Integer> test = PathReaders.resolve(type, List.of()).bind(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(3.0D)));
        Assertions.assertThat(test.test(3)).isTrue();
        Assertions.assertThat(test.test(3)).isTrue();
        Assertions.assertThat(test.test(4)).isFalse();
        Assertions.assertThat(codec.encodes).isEqualTo(3);
    }
    
    private static final class FixedReader implements PathReader<Integer> {
        
        @Override
        public Cost getCost() {
            return Cost.DIRECT;
        }
        
        @Override
        public boolean isOrdered() {
            return true;
        }
        
        @Override
        public Predicate<Integer> bind(Comparison comparison) {
            return _ -> true;
        }
    }
    
    private static final class CountingCodec implements Codec<Integer> {
        
        private int encodes;
        
        @Override
        public <T> DataResult<Pair<Integer, T>> decode(DynamicOps<T> ops, T input) {
            return Codec.INT.decode(ops, input);
        }
        
        @Override
        public <T> DataResult<T> encode(Integer input, DynamicOps<T> ops, T prefix) {
            this.encodes++;
            return Codec.INT.encode(input, ops, prefix);
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onRegisterPathReaders(RegisterPathReadersEvent event) {
            event.register(PathReadersTest.ORDERED, _ -> null);
            event.register(PathReadersTest.ORDERED, steps -> steps.equals(PathReadersTest.STEPS) ? PathReadersTest.FIRST : null);
            event.register(PathReadersTest.ORDERED, steps -> steps.equals(PathReadersTest.STEPS) ? PathReadersTest.SECOND : null);
            event.register(PathReadersTest.ORDERED_CODEC, _ -> PathReadersTest.BY_CODEC);
            event.register(PathReadersTest.SHARED_CODEC, steps -> steps.equals(PathReadersTest.STEPS) ? PathReadersTest.BY_CODEC : null);
        }
    }
}
