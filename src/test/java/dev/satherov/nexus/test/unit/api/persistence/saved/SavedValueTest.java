package dev.satherov.nexus.test.unit.api.persistence.saved;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.persistence.Persistence;
import dev.satherov.nexus.api.persistence.format.Format;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

///
/// Checks the saved values that a mod declares on its persistence.
///
public class SavedValueTest {

    private static final Format<Integer> FORMAT = Format.of(NexusCodec.INT);

    private static @Nullable Throwable firstRefusal;
    private static @Nullable Throwable duplicateRefusal;

    @Test
    public void refusesASecondValueOfOneName() {
        Assertions.assertThat(SavedValueTest.firstRefusal).isNull();
        Assertions.assertThat(SavedValueTest.duplicateRefusal).isNotNull();
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Persistence persistence = Nexus.getMod().getPersistence();
            SavedValueTest.firstRefusal = Assertions.catchThrowable(() -> persistence.serverValue("saved_value_test_duplicate", SavedValueTest.FORMAT, () -> 0).register());
            SavedValueTest.duplicateRefusal = Assertions.catchThrowable(() -> persistence.serverValue("saved_value_test_duplicate", SavedValueTest.FORMAT, () -> 0).register());
        }
    }
}
