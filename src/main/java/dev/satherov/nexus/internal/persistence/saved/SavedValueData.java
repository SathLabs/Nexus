package dev.satherov.nexus.internal.persistence.saved;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.internal.codec.CodecErrors;
import dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// The saved data that holds the value of one saved value in one scope, next to the tag the value is saved as.
///
/// Its saved value encodes every value before the value is stored here, so saving this data only writes the tag and can't fail.
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
    /// The tag the current value is saved as, or the tag of the file if it could not be read.
    ///
    private Tag encoded;

    ///
    /// The failure the file could not be read with, or `null` if it was read or there was no file.
    ///
    private final @Nullable NexusCodecException refusal;

    ///
    /// Creates the type of the saved data of one saved value, written in the versioned form of the given format.
    ///
    /// The data storage never creates the data, since the data of a scope without a file is created by its saved value through [#of(Object, Tag)].
    ///
    /// @param id     The identifier of the saved value, which is also the path of its file.
    /// @param format The format the value is saved in.
    ///
    /// @return The saved data type.
    ///
    public static <VALUE> SavedDataType<SavedValueData<VALUE>> type(Identifier id, Format<VALUE> format) {
        Codec<SavedValueData<VALUE>> codec = Codec.of(
                Codec.PASSTHROUGH.comap(data -> new Dynamic<>(NbtOps.INSTANCE, data.encoded)),
                Codec.PASSTHROUGH.map(input -> SavedValueData.read(id, format, input))
        );
        return new SavedDataType<>(id, () -> {
            throw new IllegalStateException("Could not create the saved data of '" + id + "', it is only created by its saved value");
        }, codec);
    }

    ///
    /// Creates the data of a scope that doesn't have a file yet.
    ///
    /// @param value   The initial value.
    /// @param encoded The tag the initial value is saved as.
    ///
    /// @return The data of the saved value.
    ///
    public static <VALUE> SavedValueData<VALUE> of(VALUE value, Tag encoded) {
        return new SavedValueData<>(value, encoded, null);
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
        Tag encoded = input.convert(NbtOps.INSTANCE).getValue();
        try {
            return new SavedValueData<>(VanillaAdapters.decode(format.codec(), input.getOps(), input.getValue()), encoded, null);
        } catch (NexusCodecException failure) {
            return new SavedValueData<>(null, encoded, CodecErrors.decodeFailure(id.toString(), CodecFormat.NBT.name(), failure));
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
    /// Replaces the value and the tag it is saved as, and marks this data dirty.
    ///
    /// @param value   The new value.
    /// @param encoded The tag the new value is saved as.
    ///
    /// @throws NexusCodecException If the file could not be read.
    ///
    public void replace(VALUE value, Tag encoded) {
        this.requireRead();
        this.value = value;
        this.encoded = encoded;
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
