package dev.satherov.nexus.api.registry.component;

import dev.satherov.nexus.api.registry.RegistryEntry;

import net.neoforged.neoforge.common.MutableDataComponentHolder;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// An entry of one data component type that a [DataComponentBuilder] registered.
///
/// @param <VALUE> The type of the values of the component.
///
public final class DataComponentEntry<VALUE> extends RegistryEntry<DataComponentType<?>, DataComponentType<VALUE>> {
    
    ///
    /// Binds to the given key.
    ///
    /// Should only ever be called from [DataComponentBuilder#createEntry(ResourceKey)].
    ///
    /// @param key     The key of the data component type.
    /// @param builder The builder that registers the data component type.
    ///
    @ApiStatus.Internal
    public DataComponentEntry(ResourceKey<DataComponentType<?>> key, DataComponentBuilder<VALUE, ?> builder) {
        super(key, builder);
    }
    
    ///
    /// The value of this component on the given holder, or `null` if the holder doesn't have one.
    ///
    /// @param holder The holder of the component.
    ///
    /// @return The value on the holder, or `null` if the holder doesn't have one.
    ///
    public @Nullable VALUE get(DataComponentGetter holder) {
        return holder.get(this.value());
    }
    
    ///
    /// The value of this component on the given holder, or the given fallback if the holder doesn't have one.
    ///
    /// @param holder   The holder of the component.
    /// @param fallback The value to return if the holder doesn't have one.
    ///
    /// @return The value on the holder, or the fallback if the holder doesn't have one.
    ///
    public VALUE getOrElse(DataComponentGetter holder, VALUE fallback) {
        return holder.getOrDefault(this.value(), fallback);
    }
    
    ///
    /// Sets this component on the given holder to the given value.
    ///
    /// @param holder The holder of the component.
    /// @param value  The value to set.
    ///
    /// @return The value it replaced, or `null` if there was none.
    ///
    @CanIgnoreReturnValue
    public @Nullable VALUE set(MutableDataComponentHolder holder, VALUE value) {
        return holder.set(this.value(), value);
    }
    
    ///
    /// @param holder The holder of the component.
    ///
    /// @return `true` if the given holder has this component.
    ///
    public boolean has(DataComponentGetter holder) {
        return holder.has(this.value());
    }
}
