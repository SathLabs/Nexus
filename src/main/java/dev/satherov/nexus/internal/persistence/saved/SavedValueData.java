package dev.satherov.nexus.internal.persistence.saved;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import dev.satherov.nexus.api.persistence.format.Format;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.mojang.serialization.Codec;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

///
/// The saved data that holds the value of one saved value in one scope.
///
/// @param <VALUE> The type of the value.
///
@ApiStatus.Internal
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class SavedValueData<VALUE> extends SavedData {
    
    ///
    /// The current value.
    ///
    @Getter
    private VALUE value;
    
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
        Codec<SavedValueData<VALUE>> codec = format.codec().asDfu().xmap(SavedValueData::new, SavedValueData::getValue);
        return new SavedDataType<>(id, () -> new SavedValueData<>(initial.get()), codec);
    }
    
    ///
    /// Replaces the value and marks this data dirty.
    ///
    /// @param value The new value.
    ///
    public void replace(VALUE value) {
        this.value = value;
        this.setDirty();
    }
}
