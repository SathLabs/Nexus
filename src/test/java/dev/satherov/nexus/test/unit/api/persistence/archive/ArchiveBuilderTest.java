package dev.satherov.nexus.test.unit.api.persistence.archive;

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

import java.util.Optional;
import java.util.function.UnaryOperator;

///
/// Checks that an archive refuses to import an older layout unless its format reads the bare tags of version `0`.
///
public class ArchiveBuilderTest {

    private static final String NAME = "archive_builder_test_legacy";
    private static final String OLDER_LAYOUT = "data/archive_builder_test_older";

    private static @Nullable Throwable withoutStepRefusal;
    private static @Nullable Throwable withStepRefusal;

    @Test
    public void refusesALegacyLayoutWithoutAStepFromVersionZero() {
        Assertions.assertThat(ArchiveBuilderTest.withoutStepRefusal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Nexus.MOD_ID + ":" + ArchiveBuilderTest.NAME);
        Assertions.assertThat(ArchiveBuilderTest.withStepRefusal).isNull();
    }

    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Declared {

        @SubscribeEvent
        public static void onConstruct(FMLConstructModEvent event) {
            Persistence persistence = Nexus.getMod().getPersistence();
            Format<Integer> current = Format.of(NexusCodec.INT);
            ArchiveBuilderTest.withoutStepRefusal = Assertions.catchThrowable(() -> persistence.archive(ArchiveBuilderTest.NAME, NameKey::parse, current)
                    .legacy(ArchiveBuilderTest.OLDER_LAYOUT, path -> Optional.empty())
                    .register());
            ArchiveBuilderTest.withStepRefusal = Assertions.catchThrowable(() -> persistence.archive(ArchiveBuilderTest.NAME, NameKey::parse, current.upgradeFrom(0, UnaryOperator.identity()))
                    .legacy(ArchiveBuilderTest.OLDER_LAYOUT, path -> Optional.empty())
                    .register());
        }
    }
}
