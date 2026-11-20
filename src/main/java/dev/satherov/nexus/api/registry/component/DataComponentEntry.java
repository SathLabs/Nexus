package dev.satherov.nexus.api.registry.component;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.registry.RegistryEntry;
import dev.satherov.nexus.internal.codec.path.Encoding;

import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.MutableDataComponentHolder;

import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

///
/// An entry of one [DataComponentType] that a [DataComponentBuilder] registered.
///
/// @param <VALUE> The type of the values of the component.
///
public final class DataComponentEntry<VALUE> extends RegistryEntry<DataComponentType<?>, DataComponentType<VALUE>> {
    
    ///
    /// The codec of the values of the component.
    ///
    private final NexusCodec<VALUE, ?> codec;
    
    ///
    /// Should only ever be called from [DataComponentBuilder#createEntry(ResourceKey)].
    ///
    /// @param key     The key of the data component type.
    /// @param builder The builder that registers the data component type.
    /// @param codec   The codec of the values of the component.
    ///
    @ApiStatus.Internal
    public DataComponentEntry(ResourceKey<DataComponentType<?>> key, DataComponentBuilder<VALUE, ?> builder, NexusCodec<VALUE, ?> codec) {
        super(key, builder);
        this.codec = codec;
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
    /// @throws IllegalArgumentException If the codec of this component could not encode the given value.
    ///
    @CanIgnoreReturnValue
    public @Nullable VALUE set(MutableDataComponentHolder holder, @Nullable VALUE value) {
        this.requireEncodable(value);
        return holder.set(this.value(), value);
    }
    
    ///
    /// Updates this component on the given holder with the given operator.
    ///
    /// @param holder   The holder of the component.
    /// @param operator The operator to apply to the current value.
    ///
    /// @return The previous value of the component, or `null` if the value did not change or the component was absent.
    ///
    /// @throws IllegalArgumentException If the codec of this component could not encode the updated value.
    ///
    public @Nullable VALUE update(MutableDataComponentHolder holder, UnaryOperator<@Nullable VALUE> operator) {
        VALUE old = holder.get(this.value());
        VALUE updated = operator.apply(old);
        if (Objects.equals(old, updated)) {
            return null;
        }
        
        this.requireEncodable(updated);
        return holder.set(this.value(), updated);
    }
    
    ///
    /// Updates this component on the given holder by replacing it with the given value.
    ///
    /// @param holder   The holder of the component.
    /// @param supplier The supplier to replace the value with.
    ///
    /// @return The previous value of the component, or `null` if the value did not change or the component was absent.
    ///
    /// @throws IllegalArgumentException If the codec of this component could not encode the updated value.
    ///
    public @Nullable VALUE update(MutableDataComponentHolder holder, Supplier<@Nullable VALUE> supplier) {
        VALUE old = holder.get(this.value());
        VALUE updated = supplier.get();
        if (Objects.equals(old, updated)) {
            return null;
        }
        
        this.requireEncodable(updated);
        return holder.set(this.value(), updated);
    }
    
    ///
    /// Checks that the codec of this component can encode the given value in NBT, with the registries of the running server or of the client's play connection.
    ///
    /// Does nothing for `null`, which removes the component, or if neither the server nor the play connection exists.
    ///
    private void requireEncodable(@Nullable VALUE value) {
        if (value == null) {
            return;
        }
        
        try {
            Encoding.requireEncodable(this.codec, value);
        } catch (NexusCodecException failure) {
            throw new IllegalArgumentException("Could not set the component '" + this.getId() + "' to the value, " + failure.getMessage(), failure);
        }
    }
    
    ///
    /// @param holder The holder of the component.
    ///
    /// @return `true` if the given holder has this component.
    ///
    public boolean has(DataComponentGetter holder) {
        return holder.has(this.value());
    }

    ///
    /// Creates the path with the given steps on this component.
    ///
    /// @param steps The steps from the value of the component to the part.
    ///
    /// @return The path.
    ///
    /// @see ComponentPath#of(DataComponentType, String)
    ///
    public ComponentPath path(String steps) {
        return ComponentPath.of(this.value(), steps);
    }
}
