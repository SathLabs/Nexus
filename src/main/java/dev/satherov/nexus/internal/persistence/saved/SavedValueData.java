package dev.satherov.nexus.internal.persistence.saved;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.internal.codec.CodecErrors;
import dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

///
/// The saved data that holds the value of one saved value in one scope.
///
/// If its file could not be read, this data will never be marked dirty, so the file will be left as it was.
///
/// @param <VALUE> The type of the value.
///
@ApiStatus.Internal
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SavedValueData<VALUE> extends SavedData {

    ///
    /// The current value, or `null` if the file could not be read.
    ///
    private @Nullable VALUE value;

    ///
    /// The failure the file could not be read with, or `null` if it was read or there was no file.
    ///
    private final @Nullable NexusCodecException refusal;

    ///
    /// Creates the type of the saved data of one saved value, written in the versioned form of the given format.
    ///
    /// @param id      The identifier of the saved value, which is also the path of its file.
    /// @param format  The format the value is saved in.
    /// @param initial The supplier of the value to start with if the data storage doesn't have a saved one.
    ///
    /// @return The saved data type.
    ///
    public static <VALUE> SavedDataType<SavedValueData<VALUE>> type(Identifier id, Format<VALUE> format, Supplier<VALUE> initial) {
        Codec<SavedValueData<VALUE>> codec = Codec.of(
                format.codec().asDfu().comap(SavedValueData::getValue),
                Codec.PASSTHROUGH.map(input -> SavedValueData.read(id, format, input))
        );
        return new SavedDataType<>(id, () -> new SavedValueData<>(initial.get(), null), codec);
    }

    ///
    /// Reads the data from the given input, or creates data that holds the failure if the format refuses the input.
    ///
    /// @param id     The identifier of the saved value.
    /// @param format The format the value is saved in.
    /// @param input  The input from the file.
    ///
    /// @return The data of the saved value.
    ///
    private static <VALUE, V> SavedValueData<VALUE> read(Identifier id, Format<VALUE> format, Dynamic<V> input) {
        try {
            return new SavedValueData<>(VanillaAdapters.decode(format.codec(), input.getOps(), input.getValue()), null);
        } catch (NexusCodecException failure) {
            return new SavedValueData<>(null, CodecErrors.decodeFailure(id.toString(), CodecFormat.NBT.name(), failure));
        }
    }

    ///
    /// The current value.
    ///
    /// @return The current value.
    ///
    /// @throws NexusCodecException If the file could not be read.
    ///
    public VALUE getValue() {
        this.requireRead();
        //noinspection DataFlowIssue The value is only missing if the file could not be read.
        return this.value;
    }

    ///
    /// Replaces the value and marks this data dirty.
    ///
    /// @param value The new value.
    ///
    /// @throws NexusCodecException If the file could not be read.
    ///
    public void replace(VALUE value) {
        this.requireRead();
        this.value = value;
        this.setDirty();
    }

    ///
    /// Throws the failure the file could not be read with, if there is one.
    ///
    /// @throws NexusCodecException If the file could not be read.
    ///
    private void requireRead() {
        if (this.refusal != null) {
            throw this.refusal;
        }
    }
}
