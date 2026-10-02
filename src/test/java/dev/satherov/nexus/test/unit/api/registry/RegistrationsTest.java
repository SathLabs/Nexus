package dev.satherov.nexus.test.unit.api.registry;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.PlainBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.api.registry.datagen.ProviderKind;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.gameevent.GameEvent;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

///
/// Checks the entries that builders register through the registrations of the library.
///
public class RegistrationsTest {
    
    private static final TagKey<SoundEvent> FIRST_TAG = TagKey.create(Registries.SOUND_EVENT, Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "first"));
    private static final TagKey<SoundEvent> SECOND_TAG = TagKey.create(Registries.SOUND_EVENT, Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "second"));
    private static final ProviderKind<String> NOTE = new ProviderKind<>(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "note"));
    private static final ProviderKind<String> OTHER = new ProviderKind<>(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "other"));
    private static final List<Map.Entry<String, SoundEvent>> CALLBACKS = new ArrayList<>();
    
    private static @Nullable PlainBuilder<SoundEvent, SoundEvent, Registrations> soundBuilder;
    private static @Nullable RegistryEntry<SoundEvent, SoundEvent> sound;
    private static @Nullable RegistryEntry<GameEvent, GameEvent> gameEvent;
    private static boolean clientCodeRan;
    
    @Test
    public void bindsTheEntryToTheObjectInTheRegistry() {
        RegistryEntry<SoundEvent, SoundEvent> sound = RegistrationsTest.sound();
        Assertions.assertThat(sound.isBound()).isTrue();
        Assertions.assertThat(sound.value()).isSameAs(BuiltInRegistries.SOUND_EVENT.getValue(sound.getId()));
    }
    
    @Test
    public void isTheHolderOfItsRegister() {
        RegistryEntry<SoundEvent, SoundEvent> sound = RegistrationsTest.sound();
        Assertions.assertThat(Nexus.getMod().getRegistrations().getRegister(Registries.SOUND_EVENT).getEntries()).anySatisfy(holder -> Assertions.assertThat(holder).isSameAs(sound));
    }
    
    @Test
    public void namesTheEntryAfterTheModAndTheName() {
        RegistryEntry<SoundEvent, SoundEvent> sound = RegistrationsTest.sound();
        Assertions.assertThat(sound.getId()).isEqualTo(Nexus.getMod().id("registrations_test_sound"));
        Assertions.assertThat(sound.getName()).isEqualTo("registrations_test_sound");
        Assertions.assertThat(sound.getMod()).isSameAs(Nexus.getMod());
    }
    
    @Test
    public void holdsTheTagsInCallOrder() {
        Assertions.assertThat(RegistrationsTest.sound().getTags()).containsExactly(RegistrationsTest.SECOND_TAG, RegistrationsTest.FIRST_TAG);
    }
    
    @Test
    public void holdsTheLastFactOfAKind() {
        Assertions.assertThat(RegistrationsTest.sound().getDatagen(RegistrationsTest.NOTE)).isEqualTo("second");
    }
    
    @Test
    public void holdsNoFactForAKindThatWasNotRecorded() {
        Assertions.assertThat(RegistrationsTest.sound().getDatagen(RegistrationsTest.OTHER)).isNull();
    }
    
    @Test
    public void runsTheCallbacksWithTheCreatedObjectInCallOrder() {
        SoundEvent value = RegistrationsTest.sound().value();
        Assertions.assertThat(RegistrationsTest.CALLBACKS).containsExactly(Map.entry("first", value), Map.entry("second", value));
    }
    
    @Test
    public void refusesASecondRegister() {
        PlainBuilder<SoundEvent, SoundEvent, Registrations> builder = Objects.requireNonNull(RegistrationsTest.soundBuilder);
        Assertions.assertThatThrownBy(builder::register)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("'nexus:registrations_test_sound' is already registered");
    }
    
    @Test
    public void refusesChangesAfterRegister() {
        PlainBuilder<SoundEvent, SoundEvent, Registrations> builder = Objects.requireNonNull(RegistrationsTest.soundBuilder);
        Assertions.assertThatThrownBy(() -> builder.tag(RegistrationsTest.FIRST_TAG)).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> builder.onRegister(_ -> { })).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> builder.datagen(RegistrationsTest.NOTE, "third")).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> builder.client(() -> (_, _) -> { })).isInstanceOf(IllegalStateException.class);
        Assertions.assertThat(RegistrationsTest.sound().getTags()).containsExactly(RegistrationsTest.SECOND_TAG, RegistrationsTest.FIRST_TAG);
        Assertions.assertThat(RegistrationsTest.sound().getDatagen(RegistrationsTest.NOTE)).isEqualTo("second");
    }
    
    @Test
    public void refusesAnObjectOfARegistryWithoutRegisterAfterItsEvent() {
        Registrations registrations = Nexus.getMod().getRegistrations();
        PlainBuilder<Identifier, Identifier, Registrations> builder = registrations.register(
                Registries.CUSTOM_STAT,
                "registrations_test_late_stat",
                () -> Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "registrations_test_late_stat")
        );
        
        Assertions.assertThatThrownBy(builder::register)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not register 'nexus:registrations_test_late_stat' because the registry event of 'minecraft:custom_stat' already ran");
        Assertions.assertThat(registrations.getAll(Registries.CUSTOM_STAT)).isEmpty();
    }
    
    @Test
    public void refusesAnObjectOfARegistryWithRegisterAfterItsEvent() {
        Registrations registrations = Nexus.getMod().getRegistrations();
        PlainBuilder<SoundEvent, SoundEvent, Registrations> builder = registrations.register(
                Registries.SOUND_EVENT,
                "registrations_test_late_sound",
                () -> SoundEvent.createVariableRangeEvent(Nexus.getMod().id("registrations_test_late_sound"))
        );
        
        Assertions.assertThatThrownBy(builder::register)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not register 'nexus:registrations_test_late_sound' because the registry event of 'minecraft:sound_event' already ran");
        Assertions.assertThat(registrations.getAll(Registries.SOUND_EVENT)).noneMatch(entry -> entry.getName().equals("registrations_test_late_sound"));
    }
    
    @Test
    public void neverRunsClientCodeOnADedicatedServer() {
        Assertions.assertThat(RegistrationsTest.sound().isBound()).isTrue();
        Assertions.assertThat(RegistrationsTest.clientCodeRan).isFalse();
    }
    
    @Test
    public void listsEveryEntryInRegistrationOrder() {
        Assertions.assertThat(Nexus.getMod().getRegistrations().getAll()).containsSubsequence(RegistrationsTest.sound(), RegistrationsTest.gameEvent());
    }
    
    @Test
    public void listsTheEntriesOfOneRegistryOnly() {
        Registrations registrations = Nexus.getMod().getRegistrations();
        Assertions.assertThat(registrations.getAll(Registries.SOUND_EVENT))
                .contains(RegistrationsTest.sound())
                .allSatisfy(entry -> Assertions.assertThat(entry.getKey().isFor(Registries.SOUND_EVENT)).isTrue());
        Assertions.assertThat(registrations.getAll(Registries.GAME_EVENT)).contains(RegistrationsTest.gameEvent());
    }
    
    private static RegistryEntry<SoundEvent, SoundEvent> sound() {
        return Objects.requireNonNull(RegistrationsTest.sound);
    }
    
    private static RegistryEntry<GameEvent, GameEvent> gameEvent() {
        return Objects.requireNonNull(RegistrationsTest.gameEvent);
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            PlainBuilder<SoundEvent, SoundEvent, Registrations> builder = registrations
                    .register(Registries.SOUND_EVENT, "registrations_test_sound", () -> SoundEvent.createVariableRangeEvent(Nexus.getMod().id("registrations_test_sound")))
                    .tag(RegistrationsTest.SECOND_TAG)
                    .tag(RegistrationsTest.FIRST_TAG, RegistrationsTest.SECOND_TAG)
                    .datagen(RegistrationsTest.NOTE, "first")
                    .datagen(RegistrationsTest.NOTE, "second")
                    .onRegister(value -> RegistrationsTest.CALLBACKS.add(Map.entry("first", value)))
                    .onRegister(value -> RegistrationsTest.CALLBACKS.add(Map.entry("second", value)))
                    .client(() -> {
                        RegistrationsTest.clientCodeRan = true;
                        return (_, _) -> RegistrationsTest.clientCodeRan = true;
                    });
            
            RegistrationsTest.soundBuilder = builder;
            RegistrationsTest.sound = builder.register();
            RegistrationsTest.gameEvent = registrations.register(Registries.GAME_EVENT, "registrations_test_game_event", () -> new GameEvent(16)).register();
        }
    }
}
