package dev.satherov.nexus.test.unit.api.mod;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.mod.NexusMod;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

///
/// Checks the handle of the loaded library against its mod container.
///
public class NexusModTest {
    
    private static final ModContainer CONTAINER = ModList.get().getModContainerById(Nexus.MOD_ID).orElseThrow();
    
    @Test
    public void holdsTheModId() {
        Assertions.assertThat(Nexus.getMod().getModId()).isEqualTo(Nexus.MOD_ID);
    }
    
    @Test
    public void holdsTheEventBusOfTheContainer() {
        Assertions.assertThat(Nexus.getMod().getEventBus()).isSameAs(NexusModTest.CONTAINER.getEventBus());
    }
    
    @Test
    public void holdsTheContainer() {
        Assertions.assertThat(Nexus.getMod().getContainer()).isSameAs(NexusModTest.CONTAINER);
    }
    
    @Test
    public void refusesASecondHandleForTheSameMod() {
        Assertions.assertThatThrownBy(() -> NexusMod.of(NexusModTest.CONTAINER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mod 'nexus' already has a handle");
    }
}
