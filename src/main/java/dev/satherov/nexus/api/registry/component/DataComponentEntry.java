package dev.satherov.nexus.api.registry.component;

import dev.satherov.nexus.api.component.ComponentFilter;
import dev.satherov.nexus.api.component.ComponentPath;
import dev.satherov.nexus.api.registry.RegistryEntry;

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
/// An entry of one data component type that a [DataComponentBuilder] registered.
///
/// @param <VALUE> The type of the values of the component.
///
public final class DataComponentEntry<VALUE> extends RegistryEntry<DataComponentType<?>, DataComponentType<VALUE>> {
    
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
    public @Nullable VALUE set(MutableDataComponentHolder holder, @Nullable VALUE value) {
        return holder.set(this.value(), value);
    }
    
    ///
    /// Updates this component on the given holder with the given operator.
    ///
    /// @param holder   The holder of the attachment.
    /// @param operator The operator to apply to the current value.
    ///
    /// @return The previous value of the attachment or `null` if the value did not change.
    ///
    public @Nullable VALUE update(MutableDataComponentHolder holder, UnaryOperator<@Nullable VALUE> operator) {
        VALUE old = holder.get(this.value());
        VALUE updated = operator.apply(old);
        if (Objects.equals(old, updated)) {
            return null;
        }
        
        return holder.set(this.value(), updated);
    }
    
    ///
    /// Updates this component on the given holder by replacing it with the given value.
    ///
    /// @param holder   The holder of the attachment.
    /// @param supplier The supplier to replace the value with.
    ///
    /// @return The previous value of the attachment or `null` if the value did not change.
    ///
    public @Nullable VALUE update(MutableDataComponentHolder holder, Supplier<@Nullable VALUE> supplier) {
        VALUE old = holder.get(this.value());
        VALUE updated = supplier.get();
        if (Objects.equals(old, updated)) {
            return null;
        }
        
        return holder.set(this.value(), updated);
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
    /// Creates a filter that passes every holder with this component, an item's default included.
    ///
    /// @return The filter.
    ///
    /// @see ComponentFilter#has(DataComponentType)
    ///
    public ComponentFilter.Has present() {
        return ComponentFilter.has(this.value());
    }

    ///
    /// Creates a filter that passes every holder whose value of this component equals the given value.
    ///
    /// A holder without the component fails.
    ///
    /// @param value The value the component of the holder must equal.
    ///
    /// @return The filter.
    ///
    /// @see ComponentFilter#exact(DataComponentType, Object)
    ///
    public ComponentFilter.Exact<VALUE> exactly(VALUE value) {
        return ComponentFilter.exact(this.value(), value);
    }
    
    ///
    /// Creates the path of the whole value of this component.
    ///
    /// @return The path of the whole value.
    ///
    /// @see ComponentPath#of(DataComponentType)
    ///
    public ComponentPath path() {
        return ComponentPath.of(this.value());
    }
    
    ///
    /// Creates the path of the value below this component at the given path.
    ///
    /// @param path The keys and indices down to the value, in the form of [ComponentPath#of(DataComponentType, String)].
    ///
    /// @return The path of the value.
    ///
    /// @throws IllegalArgumentException If the path is malformed, the struct of this component doesn't have one of its keys or indices,
    /// or the path continues below a number, a string, or a boolean.
    ///
    public ComponentPath path(String path) {
        return ComponentPath.of(this.value(), path);
    }
}
