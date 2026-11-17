package dev.satherov.nexus.api.registry.attachment;

import lombok.extern.slf4j.Slf4j;

import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;
import dev.satherov.nexus.api.persistence.format.Format;
import dev.satherov.nexus.api.registry.EntryBuilder;
import dev.satherov.nexus.api.registry.Registrations;
import dev.satherov.nexus.internal.codec.vanilla.VanillaAdapters;

import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentCopyHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

///
/// A builder of one attachment type and the initial value of its attachments.
///
/// @param <VALUE>  The type of the values of the attachment.
/// @param <PARENT> The type of what [#end()] returns.
///
@Slf4j
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
    /// The codec the attachment is saved with, or `null` if neither [#codec(StructCodec)] nor [#format(Format)] was called.
    ///
    private @Nullable NexusCodec<VALUE, ?> codec;
    
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
    /// If a value could not be encoded or decoded, the failure will be logged and the holder will be saved, loaded, or copied without it.
    ///
    /// Replaces previous calls of this and of [#format(Format)].
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
        this.codec = codec;
        return this;
    }

    ///
    /// Saves the attachment with the flat codec of the given format.
    ///
    /// If a saved attachment has no `version` key, it will be read as version `0` and upgraded to the current one.
    /// If a value could not be encoded or decoded, the failure will be logged and the holder will be saved, loaded, or copied without it.
    ///
    /// Replaces previous calls of this and of [#codec(StructCodec)].
    ///
    /// @param format The format of the values of the attachment.
    ///
    /// @return The builder for chaining.
    ///
    /// @throws IllegalStateException If [#register()] was already called or another builder took over this one.
    ///
    @CanIgnoreReturnValue
    public AttachmentBuilder<VALUE, PARENT> format(Format<VALUE> format) {
        this.checkUnregistered();
        this.codec = format.flatCodec();
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
    /// If neither [#codec(StructCodec)] nor [#format(Format)] is called as well, creating the type will throw an [IllegalStateException].
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
            Serializer<VALUE> serializer = new Serializer<>(this.getKey().identifier(), this.codec);
            type.serialize(serializer).copyHandler(serializer);
        }
        
        if (this.stream != null) {
            type.sync(new SyncHandler<>(this.stream));
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
    
    ///
    /// The serializer and the copy handler of a saved attachment.
    ///
    /// As a DFU codec, it throws a failure of the codec instead of returning it, so the failure never reaches the problem reporter of the holder.
    /// NeoForge throws a chunk away and generates it again if one of its attachments reported a problem.
    ///
    /// @param id      The identifier of the attachment type, used in the failure messages.
    /// @param codec   The codec of the values of the attachment.
    /// @param <VALUE> The type of the values of the attachment.
    ///
    // @formatter:off
    private record Serializer<VALUE>(Identifier id, NexusCodec<VALUE, ?> codec)
            implements
                Codec<VALUE>,
                IAttachmentSerializer<VALUE>,
                IAttachmentCopyHandler<VALUE>
    {
    // @formatter:on
        
        ///
        /// Decodes the value from the given input with the codec, and throws the failure if it could not be decoded.
        ///
        @Override
        public <V> DataResult<Pair<VALUE, V>> decode(DynamicOps<V> ops, @Nullable V input) {
            return DataResult.success(Pair.of(VanillaAdapters.decode(this.codec, ops, input), ops.empty()));
        }
        
        ///
        /// Encodes the given value with the codec into the prefix, and throws the failure if it could not be encoded.
        ///
        @Override
        public <V> DataResult<V> encode(VALUE input, DynamicOps<V> ops, V prefix) {
            return ops.mergeToPrimitive(prefix, VanillaAdapters.toOps(ops, VanillaAdapters.encodeFor(this.codec, ops, input)));
        }
        
        ///
        /// Reads the value from the given input, and throws the failure for the holder to log if it could not be decoded.
        ///
        @Override
        public VALUE read(IAttachmentHolder holder, ValueInput input) {
            //noinspection deprecation The struct and the flat format both read a map.
            return input.read(MapCodec.assumeMapUnsafe(this)).orElseThrow();
        }
        
        ///
        /// Writes the given value into the given output, or logs the failure and leaves the value out if it could not be encoded.
        ///
        @Override
        public boolean write(VALUE value, ValueOutput output) {
            try {
                //noinspection deprecation The struct and the flat format both write a map.
                output.store(MapCodec.assumeMapUnsafe(this), value);
                return true;
            } catch (NexusCodecException failure) {
                AttachmentBuilder.log.error("Could not save a value of the attachment '{}'", this.id, failure);
                return false;
            }
        }
        
        ///
        /// Encodes and decodes the given value in NBT, or logs the failure and leaves the value out if it could not be encoded or decoded.
        ///
        @Override
        public @Nullable VALUE copy(VALUE value, IAttachmentHolder holder, HolderLookup.Provider registries) {
            RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            try {
                return VanillaAdapters.decode(this.codec, ops, VanillaAdapters.toOps(ops, VanillaAdapters.encodeFor(this.codec, ops, value)));
            } catch (NexusCodecException failure) {
                AttachmentBuilder.log.error("Could not copy a value of the attachment '{}'", this.id, failure);
                return null;
            }
        }
    }
    
    ///
    /// The sync handler of a synced attachment, which writes a zero byte in front of every value.
    ///
    /// NeoForge leaves an attachment out of the initial sync of a holder if its handler writes nothing,
    /// so a value whose codec writes no bytes would never reach a client that starts tracking the holder later.
    ///
    /// @param stream  The stream codec of the values of the attachment.
    /// @param <VALUE> The type of the values of the attachment.
    ///
    private record SyncHandler<VALUE>(StreamCodec<RegistryFriendlyByteBuf, VALUE> stream) implements AttachmentSyncHandler<VALUE> {
        
        ///
        /// Writes a zero byte and then the given value with the stream codec.
        ///
        @Override
        public void write(RegistryFriendlyByteBuf buffer, VALUE value, boolean initialSync) {
            buffer.writeByte(0);
            this.stream.encode(buffer, value);
        }
        
        ///
        /// Skips the zero byte and then reads the value with the stream codec.
        ///
        @Override
        public VALUE read(IAttachmentHolder holder, RegistryFriendlyByteBuf buffer, @Nullable VALUE previousValue) {
            buffer.skipBytes(1);
            return this.stream.decode(buffer);
        }
    }
}
