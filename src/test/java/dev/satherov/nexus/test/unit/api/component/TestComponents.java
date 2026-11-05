package dev.satherov.nexus.test.unit.api.component;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.registry.component.DataComponentEntry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

import com.mojang.serialization.Codec;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

///
/// The two components the tests register, the Nexus struct `nexus:test_tier` and the foreign `nexus:test_counted` that counts how often it encodes.
///
public final class TestComponents {
    
    private static final StructCodec<Tier, Access.Plain> TIER_CODEC = NexusCodec.struct(
            "tier",
            NexusCodec.INT.field("level", Tier::level),
            NexusCodec.STRING.field("tag", Tier::tag),
            Tier::new
    );
    
    private static final AtomicInteger ENCODES = new AtomicInteger();
    
    private static final Codec<Counted> COUNTED_CODEC = Codec.INT.xmap(Counted::new, counted -> {
        TestComponents.ENCODES.incrementAndGet();
        return counted.value();
    });
    
    private static @Nullable DataComponentEntry<Tier> tier;
    private static @Nullable RegistryEntry<DataComponentType<?>, DataComponentType<Counted>> counted;
    
    public static DataComponentEntry<Tier> tier() {
        return Objects.requireNonNull(TestComponents.tier);
    }
    
    public static DataComponentType<Counted> counted() {
        return Objects.requireNonNull(TestComponents.counted).value();
    }
    
    public static int encodes() {
        return TestComponents.ENCODES.get();
    }
    
    public record Tier(int level, String tag) { }
    
    public record Counted(int value) { }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            TestComponents.tier = registrations.component("test_tier", TestComponents.TIER_CODEC).register();
            TestComponents.counted = registrations.register(
                    Registries.DATA_COMPONENT_TYPE,
                    "test_counted",
                    () -> DataComponentType.<Counted>builder().persistent(TestComponents.COUNTED_CODEC).build()
            ).register();
        }
    }
}
