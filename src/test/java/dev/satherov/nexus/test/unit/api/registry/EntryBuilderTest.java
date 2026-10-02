package dev.satherov.nexus.test.unit.api.registry;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.registry.EntryBuilder;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.gameevent.GameEvent;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

///
/// Checks that a builder taking over another one keeps everything recorded on it and closes it.
///
public class EntryBuilderTest {
    
    private static final TagKey<GameEvent> FIRST_TAG = TagKey.create(Registries.GAME_EVENT, Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "entry_builder_first"));
    private static final TagKey<GameEvent> SECOND_TAG = TagKey.create(Registries.GAME_EVENT, Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "entry_builder_second"));
    private static final ProviderKind<String> NOTE = new ProviderKind<>(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "entry_builder_note"));
    private static final List<String> CALLBACKS = new ArrayList<>();
    
    private static @Nullable PlainBuilder<GameEvent, GameEvent, Registrations> taken;
    private static @Nullable PlainBuilder<GameEvent, GameEvent, Registrations> registered;
    private static @Nullable RegistryEntry<GameEvent, GameEvent> event;
    private static @Nullable ParentBuilder parent;
    private static @Nullable Throwable subRefusal;
    private static boolean clientCodeRan;
    
    @Test
    public void registersUnderTheKeyOfTheTakenBuilder() {
        RegistryEntry<GameEvent, GameEvent> event = EntryBuilderTest.event();
        Assertions.assertThat(event.getId()).isEqualTo(Nexus.getMod().id("entry_builder_test_event"));
        Assertions.assertThat(event.value()).isSameAs(BuiltInRegistries.GAME_EVENT.getValue(event.getId()));
    }
    
    @Test
    public void keepsTheTagsOfBothInCallOrder() {
        Assertions.assertThat(EntryBuilderTest.event().getTags()).containsExactly(EntryBuilderTest.FIRST_TAG, EntryBuilderTest.SECOND_TAG);
    }
    
    @Test
    public void keepsTheFactsOfTheTakenBuilder() {
        Assertions.assertThat(EntryBuilderTest.event().getDatagen(EntryBuilderTest.NOTE)).isEqualTo("taken");
    }
    
    @Test
    public void runsTheCallbacksOfBothInCallOrder() {
        Assertions.assertThat(EntryBuilderTest.event().isBound()).isTrue();
        Assertions.assertThat(EntryBuilderTest.CALLBACKS).containsExactly("taken", "taking");
    }
    
    @Test
    public void neverRunsTheTakenClientCodeOnADedicatedServer() {
        Assertions.assertThat(EntryBuilderTest.event().isBound()).isTrue();
        Assertions.assertThat(EntryBuilderTest.clientCodeRan).isFalse();
    }
    
    @Test
    public void refusesChangesToTheTakenBuilder() {
        PlainBuilder<GameEvent, GameEvent, Registrations> taken = Objects.requireNonNull(EntryBuilderTest.taken);
        Assertions.assertThatThrownBy(() -> taken.tag(EntryBuilderTest.SECOND_TAG)).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> taken.onRegister(_ -> { })).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> taken.datagen(EntryBuilderTest.NOTE, "late")).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(() -> taken.client(() -> (_, _) -> { })).isInstanceOf(IllegalStateException.class);
        Assertions.assertThatThrownBy(taken::register)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("'nexus:entry_builder_test_event' is already registered");
        Assertions.assertThat(EntryBuilderTest.event().getDatagen(EntryBuilderTest.NOTE)).isEqualTo("taken");
    }
    
    @Test
    public void refusesToTakeOverATakenBuilder() {
        PlainBuilder<GameEvent, GameEvent, Registrations> taken = Objects.requireNonNull(EntryBuilderTest.taken);
        Assertions.assertThatThrownBy(() -> new Takeover(taken)).isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    public void refusesToTakeOverARegisteredBuilder() {
        PlainBuilder<GameEvent, GameEvent, Registrations> registered = Objects.requireNonNull(EntryBuilderTest.registered);
        Assertions.assertThatThrownBy(() -> new Takeover(registered)).isInstanceOf(IllegalStateException.class);
    }
    
    @Test
    public void refusesToRegisterASubObjectOnItsOwn() {
        Assertions.assertThat(EntryBuilderTest.subRefusal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("'nexus:entry_builder_test_sub' is registered by its parent through end().register()");
    }
    
    @Test
    public void registersASubObjectThroughItsParent() {
        RegistryEntry<GameEvent, GameEvent> sub = Objects.requireNonNull(Objects.requireNonNull(EntryBuilderTest.parent).subEntry);
        Assertions.assertThat(sub.getId()).isEqualTo(Nexus.getMod().id("entry_builder_test_sub"));
        Assertions.assertThat(sub.value()).isSameAs(BuiltInRegistries.GAME_EVENT.getValue(sub.getId()));
    }
    
    @Test
    public void refusesALateRegisterBeforeCreatingTheEntry() {
        LateBuilder late = new LateBuilder(Nexus.getMod().getRegistrations());
        Assertions.assertThatThrownBy(late::register)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not register 'nexus:entry_builder_test_late' because the registry event of 'minecraft:game_event' already ran");
        Assertions.assertThat(late.entryCreated).isFalse();
    }
    
    private static RegistryEntry<GameEvent, GameEvent> event() {
        return Objects.requireNonNull(EntryBuilderTest.event);
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
            return new TestEntry(key, this);
        }
    }
    
    private static final class ParentBuilder extends EntryBuilder<GameEvent, GameEvent, RegistryEntry<GameEvent, GameEvent>, Registrations, ParentBuilder> {
        
        private final PlainBuilder<GameEvent, GameEvent, ParentBuilder> sub;
        private @Nullable RegistryEntry<GameEvent, GameEvent> subEntry;
        
        private ParentBuilder(Registrations registrations) {
            super(registrations, Registries.GAME_EVENT, "entry_builder_test_parent", registrations);
            this.sub = new PlainBuilder<>(registrations, Registries.GAME_EVENT, "entry_builder_test_sub", this, () -> new GameEvent(16));
        }
        
        @Override
        protected GameEvent create() {
            return new GameEvent(16);
        }
        
        @Override
        protected RegistryEntry<GameEvent, GameEvent> createEntry(ResourceKey<GameEvent> key) {
            this.subEntry = EntryBuilder.registerSubObject(this.sub);
            return new TestEntry(key, this);
        }
    }
    
    private static final class LateBuilder extends EntryBuilder<GameEvent, GameEvent, RegistryEntry<GameEvent, GameEvent>, Registrations, LateBuilder> {
        
        private boolean entryCreated;
        
        private LateBuilder(Registrations registrations) {
            super(registrations, Registries.GAME_EVENT, "entry_builder_test_late", registrations);
        }
        
        @Override
        protected GameEvent create() {
            return new GameEvent(16);
        }
        
        @Override
        protected RegistryEntry<GameEvent, GameEvent> createEntry(ResourceKey<GameEvent> key) {
            this.entryCreated = true;
            return new TestEntry(key, this);
        }
    }
    
    private static final class TestEntry extends RegistryEntry<GameEvent, GameEvent> {
        
        private TestEntry(ResourceKey<GameEvent> key, EntryBuilder<GameEvent, GameEvent, ?, ?, ?> builder) {
            super(key, builder);
        }
    }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Registered {
        
        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Registrations registrations = Nexus.getMod().getRegistrations();
            PlainBuilder<GameEvent, GameEvent, Registrations> taken = registrations
                    .register(Registries.GAME_EVENT, "entry_builder_test_event", () -> new GameEvent(16))
                    .tag(EntryBuilderTest.FIRST_TAG)
                    .datagen(EntryBuilderTest.NOTE, "taken")
                    .onRegister(_ -> EntryBuilderTest.CALLBACKS.add("taken"))
                    .client(() -> {
                        EntryBuilderTest.clientCodeRan = true;
                        return (_, _) -> EntryBuilderTest.clientCodeRan = true;
                    });
            
            EntryBuilderTest.taken = taken;
            EntryBuilderTest.event = new Takeover(taken)
                    .tag(EntryBuilderTest.SECOND_TAG)
                    .onRegister(_ -> EntryBuilderTest.CALLBACKS.add("taking"))
                    .register();
            
            PlainBuilder<GameEvent, GameEvent, Registrations> registered = registrations.register(Registries.GAME_EVENT, "entry_builder_test_registered", () -> new GameEvent(16));
            registered.register();
            EntryBuilderTest.registered = registered;
            
            ParentBuilder parent = new ParentBuilder(registrations);
            EntryBuilderTest.subRefusal = Assertions.catchThrowable(parent.sub::register);
            parent.register();
            EntryBuilderTest.parent = parent;
        }
    }
}
