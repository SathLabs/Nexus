package dev.satherov.nexus.test.unit.internal.persistence.saved;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.internal.persistence.saved.SavedValueData;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.mojang.serialization.Codec;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

///
/// Checks that the data of a saved value is marked dirty when its value is replaced, saves the tag of its value, and reads its value back from that tag.
///
public class SavedValueDataTest {

    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final RegistryOps<Tag> OPS = SavedValueDataTest.REGISTRIES.createSerializationContext(NbtOps.INSTANCE);
    private static final Format<Integer> FORMAT = Format.of(NexusCodec.INT);
    private static final SavedDataType<SavedValueData<Integer>> TYPE = SavedValueData.type(Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "saved_value_data_test"), SavedValueDataTest.FORMAT);

    @Test
    public void replaceMarksTheDataDirty() {
        SavedValueData<Integer> data = SavedValueData.of(1, SavedValueDataTest.FORMAT.write(1, SavedValueDataTest.REGISTRIES));
        data.setDirty(false);

        data.replace(2, SavedValueDataTest.FORMAT.write(2, SavedValueDataTest.REGISTRIES));

        Assertions.assertThat(data.isDirty()).isTrue();
        Assertions.assertThat(data.getValue()).isEqualTo(2);
    }

    @Test
    public void savesTheTagOfTheReplacedValue() {
        SavedValueData<Integer> data = SavedValueData.of(1, SavedValueDataTest.FORMAT.write(1, SavedValueDataTest.REGISTRIES));
        Tag written = SavedValueDataTest.FORMAT.write(2, SavedValueDataTest.REGISTRIES);
        data.replace(2, written);

        Assertions.assertThat(SavedValueDataTest.codec().encodeStart(SavedValueDataTest.OPS, data).getOrThrow()).isEqualTo(written);
    }

    @Test
    public void readsTheValueBackFromItsSavedTag() {
        Tag saved = SavedValueDataTest.FORMAT.write(5, SavedValueDataTest.REGISTRIES);

        Assertions.assertThat(SavedValueDataTest.codec().parse(SavedValueDataTest.OPS, saved).getOrThrow().getValue()).isEqualTo(5);
    }

    private static Codec<SavedValueData<Integer>> codec() {
        return SavedValueDataTest.TYPE.codecFactory().create(null);
    }
}
