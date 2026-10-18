package dev.satherov.nexus.api.registry.attachment;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

///
/// A builder of one attachment type and the initial value of its attachments.
///
/// @param <VALUE>  The type of the values of the attachment.
/// @param <PARENT> The type of what [#end()] returns.
///
public final class AttachmentBuilder<VALUE, PARENT>
        // @formatter:off
        extends EntryBuilder
        <
            AttachmentType<?>,
            AttachmentType<VALUE>,
            AttachmentEntry<VALUE>,
            PARENT,
            AttachmentBuilder<VALUE, PARENT>
        >
        // @formatter:on
{
    
    ///
    /// The supplier of the initial value, called each time the attachment is read from a holder that doesn't have a value.
    ///
    private final Supplier<VALUE> initial;
    
    ///
    /// The map codec the attachment is saved with, or `null` if [#codec(StructCodec)] was not called.
    ///
    private @Nullable MapCodec<VALUE> codec;
    
    ///
    /// The stream codec the attachment is synced with, or `null` if [#sync(NexusCodec)] was not called.
    ///
    private @Nullable StreamCodec<RegistryFriendlyByteBuf, VALUE> stream;
    
    ///
    /// If the attachment should be kept when a player dies.
    ///
    private boolean copyOnDeath;
    
    ///
    /// Starts the attachment type `<modid>:<name>` with the given initial value.
    ///
    /// The type has no translation.
    ///
    /// @param registrations The registrations to register through.
    /// @param name          The path of the identifier.
    /// @param parent        What [#end()] returns.
    /// @param initial       The supplier of the initial value, called each time the attachment is read from a holder that doesn't have it.
    ///
    public AttachmentBuilder(Registrations registrations, String name, PARENT parent, Supplier<VALUE> initial) {
        super(registrations, NeoForgeRegistries.Keys.ATTACHMENT_TYPES, name, parent);
        this.initial = initial;
    }
    
    ///
    /// Saves the attachment with the map codec of the given codec.
    ///
    /// @param codec The codec of the values of the attachment.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public AttachmentBuilder<VALUE, PARENT> codec(StructCodec<VALUE, ?> codec) {
        this.checkUnregistered();
        this.codec = codec.asMapCodec();
        return this;
    }
    
    ///
    /// Syncs the attachment to the clients tracking its holder, with the stream codec of the given codec.
    ///
    /// @param codec The codec of the values of the attachment.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public AttachmentBuilder<VALUE, PARENT> sync(NexusCodec<VALUE, ?> codec) {
        this.checkUnregistered();
        this.stream = codec.asStream();
        return this;
    }
    
    ///
    /// Keeps the attachment when a player dies or a living entity converts into another.
    ///
    /// If [#codec(StructCodec)] is not called as well, creating the type will throw an [IllegalStateException].
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public AttachmentBuilder<VALUE, PARENT> copyOnDeath() {
        this.checkUnregistered();
        this.copyOnDeath = true;
        return this;
    }
    
    ///
    /// Creates the type with the initial value and the options of this builder.
    ///
    /// @return The created type.
    ///
    @Override
    protected AttachmentType<VALUE> create() {
        AttachmentType.Builder<VALUE> type = AttachmentType.builder(this.initial);
        if (this.codec != null) {
            type.serialize(this.codec);
        }
        
        if (this.stream != null) {
            type.sync(this.stream);
        }
        
        if (this.copyOnDeath) {
            type.copyOnDeath();
        }
        
        return type.build();
    }
    
    ///
    /// Creates the entry for the given key.
    ///
    /// @param key The key of the attachment type.
    ///
    /// @return The created entry.
    ///
    @Override
    protected AttachmentEntry<VALUE> createEntry(ResourceKey<AttachmentType<?>> key) {
        return new AttachmentEntry<>(key, this);
    }
}
