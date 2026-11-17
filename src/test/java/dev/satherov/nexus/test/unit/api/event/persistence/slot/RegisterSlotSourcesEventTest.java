package dev.satherov.nexus.test.unit.api.event.persistence.slot;

import dev.satherov.nexus.api.event.persistence.slot.RegisterSlotSourcesEvent;
import dev.satherov.nexus.api.persistence.slot.SlotSource;

import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

///
/// Checks which source an event keeps when two listeners register one identifier, in either order.
///
public class RegisterSlotSourcesEventTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("owner", "slots");

    private final Map<Identifier, SlotSource<?>> sources = new LinkedHashMap<>();

    private final RegisterSlotSourcesEvent event = new RegisterSlotSourcesEvent(this.sources);

    @Test
    public void tryRegisterAfterRegisterKeepsTheOwnersSource() {
        SlotSource<String> owner = RegisterSlotSourcesEventTest.source();
        this.event.register(RegisterSlotSourcesEventTest.ID, owner);
        Assertions.assertThat(this.event.tryRegister(RegisterSlotSourcesEventTest.ID, RegisterSlotSourcesEventTest.source())).isFalse();
        Assertions.assertThat(this.sources.get(RegisterSlotSourcesEventTest.ID)).isSameAs(owner);
    }

    @Test
    public void registerAfterTryRegisterReplacesTheSource() {
        SlotSource<String> owner = RegisterSlotSourcesEventTest.source();
        Assertions.assertThat(this.event.tryRegister(RegisterSlotSourcesEventTest.ID, RegisterSlotSourcesEventTest.source())).isTrue();
        this.event.register(RegisterSlotSourcesEventTest.ID, owner);
        Assertions.assertThat(this.sources.get(RegisterSlotSourcesEventTest.ID)).isSameAs(owner);
    }

    @Test
    public void secondTryRegisterKeepsTheFirstSource() {
        SlotSource<String> first = RegisterSlotSourcesEventTest.source();
        Assertions.assertThat(this.event.tryRegister(RegisterSlotSourcesEventTest.ID, first)).isTrue();
        Assertions.assertThat(this.event.tryRegister(RegisterSlotSourcesEventTest.ID, RegisterSlotSourcesEventTest.source())).isFalse();
        Assertions.assertThat(this.sources.get(RegisterSlotSourcesEventTest.ID)).isSameAs(first);
    }

    @Test
    public void secondRegisterThrowsAndKeepsTheFirstSource() {
        SlotSource<String> first = RegisterSlotSourcesEventTest.source();
        this.event.register(RegisterSlotSourcesEventTest.ID, first);
        Assertions.assertThatThrownBy(() -> this.event.register(RegisterSlotSourcesEventTest.ID, RegisterSlotSourcesEventTest.source()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The slot source 'owner:slots' is registered twice");
        Assertions.assertThat(this.sources.get(RegisterSlotSourcesEventTest.ID)).isSameAs(first);
    }

    @Test
    public void registerAfterAReplacementThrows() {
        SlotSource<String> owner = RegisterSlotSourcesEventTest.source();
        this.event.tryRegister(RegisterSlotSourcesEventTest.ID, RegisterSlotSourcesEventTest.source());
        this.event.register(RegisterSlotSourcesEventTest.ID, owner);
        Assertions.assertThatThrownBy(() -> this.event.register(RegisterSlotSourcesEventTest.ID, RegisterSlotSourcesEventTest.source()))
                .isInstanceOf(IllegalStateException.class);
        Assertions.assertThat(this.sources.get(RegisterSlotSourcesEventTest.ID)).isSameAs(owner);
    }

    private static SlotSource<String> source() {
        return SlotSource.of(String.class, _ -> {
            throw new UnsupportedOperationException();
        });
    }
}
