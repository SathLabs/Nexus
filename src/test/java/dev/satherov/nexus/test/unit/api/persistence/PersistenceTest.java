package dev.satherov.nexus.test.unit.api.persistence;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.persistence.Persistence;
import dev.satherov.nexus.api.persistence.archive.NameKey;
import dev.satherov.nexus.api.persistence.format.Format;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;

import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

///
/// Checks that a mod can't declare a saved value or an archive whose files would lie inside the folder of another of its names.
///
public class PersistenceTest {

    private static final Format<Integer> FORMAT = Format.of(NexusCodec.INT);

    private static @Nullable Throwable archiveRefusal;
    private static @Nullable Throwable besideRefusal;
    private static @Nullable Throwable innerRefusal;
    private static @Nullable Throwable nestedArchiveRefusal;
    private static @Nullable Throwable nestedValueRefusal;
    private static @Nullable Throwable outerRefusal;

    @Test
    public void refusesANameInsideAnArchive() {
        Assertions.assertThat(PersistenceTest.archiveRefusal).isNull();
        Assertions.assertThat(PersistenceTest.nestedValueRefusal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Nexus.MOD_ID + ":persistence_test_folder/index");
        Assertions.assertThat(PersistenceTest.nestedArchiveRefusal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Nexus.MOD_ID + ":persistence_test_folder/nested");
        Assertions.assertThat(PersistenceTest.besideRefusal).isNull();
    }

    @Test
    public void refusesAnArchiveAroundADeclaredName() {
        Assertions.assertThat(PersistenceTest.innerRefusal).isNull();
        Assertions.assertThat(PersistenceTest.outerRefusal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Nexus.MOD_ID + ":persistence_test_inner/index");
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Persistence persistence = Nexus.getMod().getPersistence();
            PersistenceTest.archiveRefusal = Assertions.catchThrowable(() -> persistence.archive("persistence_test_folder", NameKey::parse, PersistenceTest.FORMAT).register());
            PersistenceTest.nestedValueRefusal = Assertions.catchThrowable(() -> persistence.serverValue("persistence_test_folder/index", PersistenceTest.FORMAT, () -> 0).register());
            PersistenceTest.nestedArchiveRefusal = Assertions.catchThrowable(() -> persistence.archive("persistence_test_folder/nested", NameKey::parse, PersistenceTest.FORMAT).register());
            PersistenceTest.besideRefusal = Assertions.catchThrowable(() -> persistence.serverValue("persistence_test_folders", PersistenceTest.FORMAT, () -> 0).register());
            PersistenceTest.innerRefusal = Assertions.catchThrowable(() -> persistence.levelValue("persistence_test_inner/index", PersistenceTest.FORMAT, () -> 0).register());
            PersistenceTest.outerRefusal = Assertions.catchThrowable(() -> persistence.archive("persistence_test_inner", NameKey::parse, PersistenceTest.FORMAT).register());
        }
    }
}
