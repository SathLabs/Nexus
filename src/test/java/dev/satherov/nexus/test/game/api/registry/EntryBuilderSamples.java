package dev.satherov.nexus.test.game.api.registry;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.PlainBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.gametest.api.client.Client;
import dev.satherov.nexus.gametest.api.client.ClientTest;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.gameevent.GameEvent;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

///
/// Checks that a builder taking over another one runs the client code of the taken builder on the client.
///
public class EntryBuilderSamples {
    
    private static final List<Map.Entry<RegistryEntry<GameEvent, GameEvent>, Boolean>> CLIENT_CALLS = new ArrayList<>();
    
    private static @Nullable RegistryEntry<GameEvent, GameEvent> taking;
    
    @ClientTest
    public static void runsTheClientCodeOfTheTakenBuilderOnce(Client client) {
        RegistryEntry<GameEvent, GameEvent> taking = Objects.requireNonNull(EntryBuilderSamples.taking);
        Assertions.assertThat(EntryBuilderSamples.CLIENT_CALLS).as("the entries the client code ran with, and if each was bound").containsExactly(Map.entry(taking, true));
    }
    
    private static final class Takeover extends EntryBuilder<GameEvent, GameEvent, RegistryEntry<GameEvent, GameEvent>, Registrations, Takeover> {
        
        private Takeover(PlainBuilder<GameEvent, GameEvent, Registrations> from) {
            super(from, from.end());
        }
        
        @Override
        protected GameEvent create() {
            return new GameEvent(16);
        }
        
        @Override
        protected RegistryEntry<GameEvent, GameEvent> createEntry(ResourceKey<GameEvent> key) {
            return new SampleEntry(key, this);
        }
    }
    
    private static final class SampleEntry extends RegistryEntry<GameEvent, GameEvent> {
        
        private SampleEntry(ResourceKey<GameEvent> key, EntryBuilder<GameEvent, GameEvent, ?, ?, ?> builder) {
            super(key, builder);
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            PlainBuilder<GameEvent, GameEvent, Registrations> taken = Nexus.getMod().getRegistrations()
                    .register(Registries.GAME_EVENT, "entry_builder_samples_taken", () -> new GameEvent(16))
                    .client(() -> (_, entry) -> EntryBuilderSamples.CLIENT_CALLS.add(Map.entry(entry, entry.isBound())));
            
            EntryBuilderSamples.taking = new Takeover(taken).register();
        }
    }
}
