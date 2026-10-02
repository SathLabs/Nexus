package dev.satherov.nexus.test.unit.api.registry.component;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.component.DataComponentEntry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.common.MutableDataComponentHolder;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;

///
/// Checks the values that data component entries read from and write to a holder, and the forms their types save and sync them in.
///
public class DataComponentBuilderTest {
    
    private static final StructCodec<Counter, Access.Plain> CODEC = NexusCodec.struct("counter", NexusCodec.INT.field("count", Counter::count), Counter::new);
    
    private static @Nullable DataComponentEntry<Counter> counter;
    private static @Nullable DataComponentEntry<Counter> cached;
    
    @Test
    public void readsNothingFromAHolderWithoutTheComponent() {
        DataComponentEntry<Counter> counter = DataComponentBuilderTest.counter();
        PatchedHolder holder = new PatchedHolder();
        Assertions.assertThat(counter.has(holder)).isFalse();
        Assertions.assertThat(counter.get(holder)).isNull();
        Assertions.assertThat(counter.getOrElse(holder, new Counter(7))).isEqualTo(new Counter(7));
    }
    
    @Test
    public void readsTheValueThatWasSet() {
        DataComponentEntry<Counter> counter = DataComponentBuilderTest.counter();
        PatchedHolder holder = new PatchedHolder();
        Assertions.assertThat(counter.set(holder, new Counter(3))).isNull();
        Assertions.assertThat(counter.has(holder)).isTrue();
        Assertions.assertThat(counter.get(holder)).isEqualTo(new Counter(3));
        Assertions.assertThat(counter.getOrElse(holder, new Counter(7))).isEqualTo(new Counter(3));
    }
    
    @Test
    public void returnsTheReplacedValue() {
        DataComponentEntry<Counter> counter = DataComponentBuilderTest.counter();
        PatchedHolder holder = new PatchedHolder();
        counter.set(holder, new Counter(3));
        Assertions.assertThat(counter.set(holder, new Counter(4))).isEqualTo(new Counter(3));
        Assertions.assertThat(counter.get(holder)).isEqualTo(new Counter(4));
    }
    
    @Test
    public void savesTheValueWithTheCodec() {
        DataComponentType<Counter> type = DataComponentBuilderTest.counter().value();
        JsonElement json = type.codecOrThrow().encodeStart(JsonOps.INSTANCE, new Counter(3)).getOrThrow();
        Assertions.assertThat(json).isEqualTo(JsonParser.parseString("{\"count\":3}"));
        Assertions.assertThat(type.codecOrThrow().parse(JsonOps.INSTANCE, json).getOrThrow()).isEqualTo(new Counter(3));
    }
    
    @Test
    public void syncsTheValueWithTheStreamCodec() {
        DataComponentType<Counter> type = DataComponentBuilderTest.counter().value();
        RegistryFriendlyByteBuf fromType = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        RegistryFriendlyByteBuf fromCodec = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        type.streamCodec().encode(fromType, new Counter(3));
        DataComponentBuilderTest.CODEC.asStream().encode(fromCodec, new Counter(3));
        Assertions.assertThat(ByteBufUtil.getBytes(fromType)).isEqualTo(ByteBufUtil.getBytes(fromCodec));
        Assertions.assertThat(type.streamCodec().decode(fromType)).isEqualTo(new Counter(3));
        Assertions.assertThat(fromType.readableBytes()).isZero();
    }
    
    @Test
    public void savesTheSameFormWithACachedEncoding() {
        JsonElement json = DataComponentBuilderTest.cached().value().codecOrThrow().encodeStart(JsonOps.INSTANCE, new Counter(3)).getOrThrow();
        Assertions.assertThat(json).isEqualTo(JsonParser.parseString("{\"count\":3}"));
    }
    
    @Test
    public void ignoresTheSwapAnimationOnlyWhereSet() {
        Assertions.assertThat(DataComponentBuilderTest.counter().value().ignoreSwapAnimation()).isFalse();
        Assertions.assertThat(DataComponentBuilderTest.cached().value().ignoreSwapAnimation()).isTrue();
    }
    
    private static DataComponentEntry<Counter> counter() {
        return Objects.requireNonNull(DataComponentBuilderTest.counter);
    }
    
    private static DataComponentEntry<Counter> cached() {
        return Objects.requireNonNull(DataComponentBuilderTest.cached);
    }
    
    private record Counter(int count) { }
    
    private static final class PatchedHolder implements MutableDataComponentHolder {
        
        private final PatchedDataComponentMap components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
        
        @Override
        public DataComponentMap getComponents() {
            return this.components;
        }
        
        @Override
        public <T> @Nullable T set(DataComponentType<T> type, @Nullable T value) {
            return this.components.set(type, value);
        }
        
        @Override
        public <T> @Nullable T remove(DataComponentType<? extends T> type) {
            return this.components.remove(type);
        }
        
        @Override
        public void applyComponents(DataComponentPatch patch) {
            this.components.applyPatch(patch);
        }
        
        @Override
        public void applyComponents(DataComponentMap components) {
            this.components.setAll(components);
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            DataComponentBuilderTest.counter = registrations.component("data_component_builder_test_counter", DataComponentBuilderTest.CODEC).register();
            DataComponentBuilderTest.cached = registrations.component("data_component_builder_test_cached", DataComponentBuilderTest.CODEC)
                    .cacheEncoding()
                    .ignoreSwapAnimation()
                    .register();
        }
    }
}
