package dev.satherov.nexus.test.unit.internal.codec.path;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.internal.codec.path.Encoding;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

///
/// Checks that encoding keeps the registries of one source, falls back to the built-in registries without a source, and reads a failure as no tag.
///
public class EncodingTest {
    
    @Test
    public void ownerWithoutAServerIsTheBuiltInRegistry() {
        Assertions.assertThat(Encoding.owner(Registries.ITEM)).isSameAs(BuiltInRegistries.ITEM);
        Assertions.assertThat(Encoding.owner(Registries.BLOCK)).isSameAs(BuiltInRegistries.BLOCK);
    }
    
    @Test
    public void ownerOfARegistryThatIsNotBuiltInIsNull() {
        Assertions.assertThat(Encoding.owner(Registries.ENCHANTMENT)).isNull();
    }
    
    @Test
    public void holderEncodesWithTheBuiltInRegistries() {
        Holder<Item> stone = BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE);
        
        Assertions.assertThat(stone.canSerializeIn(Encoding.owner(Registries.ITEM))).isTrue();
        Assertions.assertThat(Encoding.encode(NexusCodec.holder(Registries.ITEM), stone)).isEqualTo(StringTag.valueOf("minecraft:stone"));
    }
    
    @Test
    public void refusedValueEncodesToNull() {
        NexusCodec<Integer, Access.Plain> positive = NexusCodec.INT.validate(value -> value > 0 ? null : "Not positive");
        
        Assertions.assertThat(Encoding.encode(positive, 1)).isEqualTo(IntTag.valueOf(1));
        Assertions.assertThat(Encoding.encode(positive, -1)).isNull();
    }
    
    @Test
    public void exceptionWhileEncodingEncodesToNull() {
        NexusCodec<Integer, Access.Plain> throwing = NexusCodec.ofDfu(Codec.INT.xmap(Function.identity(), value -> {
            throw new IllegalStateException("Could not map the value");
        }));
        
        Assertions.assertThat(Encoding.encode(throwing, 1)).isNull();
    }
    
    @Test
    public void repeatedEncodingUsesEqualRegistryOps() {
        List<DynamicOps<?>> seen = new ArrayList<>();
        NexusCodec<Integer, Access.Registries> codec = EncodingTest.recording(seen);
        
        Assertions.assertThat(Encoding.encode(codec, 1)).isEqualTo(IntTag.valueOf(1));
        Assertions.assertThat(Encoding.encode(codec, 2)).isEqualTo(IntTag.valueOf(2));
        Assertions.assertThat(seen).hasSize(2);
        Assertions.assertThat(seen.getFirst()).isInstanceOf(RegistryOps.class).isEqualTo(seen.getLast());
    }
    
    @Test
    public void encodingWithTheSameSourceUsesEqualRegistryOps() {
        RegistryAccess source = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        DynamicOps<?> first = EncodingTest.opsOf(Encoding.format(source));
        
        Assertions.assertThat(first).isInstanceOf(RegistryOps.class).isEqualTo(EncodingTest.opsOf(Encoding.format(source)));
    }
    
    @Test
    public void encodingWithAnotherSourceUsesOtherRegistryOps() {
        DynamicOps<?> first = EncodingTest.opsOf(Encoding.format(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)));
        DynamicOps<?> second = EncodingTest.opsOf(Encoding.format(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)));
        
        Assertions.assertThat(second).isNotEqualTo(first);
    }
    
    @Test
    public void encodingWithASourceFindsTheBuiltInRegistries() {
        RegistryOps<?> ops = (RegistryOps<?>) EncodingTest.opsOf(Encoding.format(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)));
        
        Assertions.assertThat(ops.owner(Registries.ITEM)).containsSame(BuiltInRegistries.ITEM);
        Assertions.assertThat(ops.owner(Registries.ENCHANTMENT)).isEmpty();
    }
    
    @Test
    public void encodingWithoutASourceDropsTheRegistriesOfTheLastSource() {
        RegistryAccess source = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        DynamicOps<?> connected = EncodingTest.opsOf(Encoding.format(source));
        DynamicOps<?> unconnected = EncodingTest.opsOf(Encoding.format(null));
        
        Assertions.assertThat(unconnected).isNotEqualTo(connected);
        Assertions.assertThat(EncodingTest.opsOf(Encoding.format(source))).isNotEqualTo(connected);
    }
    
    private static NexusCodec<Integer, Access.Registries> recording(List<DynamicOps<?>> seen) {
        Encoder<Integer> recording = new Encoder<>() {
            
            @Override
            public <V> DataResult<V> encode(Integer input, DynamicOps<V> ops, V prefix) {
                seen.add(ops);
                return DataResult.success(ops.createInt(input));
            }
        };
        
        return NexusCodec.ofDfu(Codec.of(recording, Codec.INT), Access.Registries.class);
    }
    
    private static DynamicOps<?> opsOf(CodecFormat<Tag, Access.Registries> format) {
        List<DynamicOps<?>> seen = new ArrayList<>();
        
        Assertions.assertThat(EncodingTest.recording(seen).encode(format, 1)).isEqualTo(IntTag.valueOf(1));
        return seen.getFirst();
    }
}
