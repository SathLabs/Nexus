package dev.satherov.nexus.api.registry.attachment;

import dev.satherov.nexus.api.registry.RegistryEntry;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

import net.minecraft.resources.ResourceKey;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

///
/// An entry of one attachment type that an [AttachmentBuilder] registered.
///
/// @param <VALUE> The type of the values of the attachment.
///
public final class AttachmentEntry<VALUE> extends RegistryEntry<AttachmentType<?>, AttachmentType<VALUE>> {
    
    ///
    /// Binds to the given key.
    ///
    /// Should only ever be called from [AttachmentBuilder#createEntry(ResourceKey)].
    ///
    /// @param key     The key of the attachment type.
    /// @param builder The builder that registers the attachment type.
    ///
    @ApiStatus.Internal
    public AttachmentEntry(ResourceKey<AttachmentType<?>> key, AttachmentBuilder<VALUE, ?> builder) {
        super(key, builder);
    }
    
    ///
    /// The value of this attachment on the given holder.
    ///
    /// If the holder doesn't have one, a value will be created from the initial value and set on the holder first.
    ///
    /// @param holder The holder of the attachment.
    ///
    /// @return The value on the holder.
    ///
    public VALUE get(IAttachmentHolder holder) {
        return holder.getData(this.value());
    }
    
    ///
    /// Sets this attachment on the given holder to the given value.
    ///
    /// @param holder The holder of the attachment.
    /// @param value  The value to set.
    ///
    public void set(IAttachmentHolder holder, VALUE value) {
        holder.setData(this.value(), value);
    }
    
    ///
    /// @param holder The holder of the attachment.
    ///
    /// @return `true` if the given holder has a value of this attachment.
    ///
    public boolean has(IAttachmentHolder holder) {
        return holder.hasData(this.value());
    }
    
    ///
    /// Removes this attachment from the given holder.
    ///
    /// @param holder The holder of the attachment.
    ///
    /// @return The removed value, or `null` if there was none.
    ///
    @CanIgnoreReturnValue
    public @Nullable VALUE remove(IAttachmentHolder holder) {
        return holder.removeData(this.value());
    }
}
