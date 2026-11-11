package dev.satherov.nexus.api.persistence.format;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.internal.codec.CodecErrors;
import dev.satherov.nexus.internal.codec.Traversal;
import dev.satherov.nexus.internal.codec.format.NbtOperations;
import dev.satherov.nexus.internal.codec.format.Operations;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

///
/// A codec with a version and the steps that upgrade the tags of older versions to the current one.
///
/// @param <VALUE> The type of value this format reads and writes.
///
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Format<VALUE> {

    ///
    /// The key of the version in the versioned form.
    ///
    private static final String VERSION = "version";

    ///
    /// The key of the encoded value in the versioned form.
    ///
    private static final String DATA = "data";

    ///
    /// The keys of the versioned form.
    ///
    private static final Set<String> KEYS = Set.of(Format.VERSION, Format.DATA);

    ///
    /// The codec of the value.
    ///
    private final Traversal<VALUE, ? super Access.Registries> codec;

    ///
    /// The oldest version this format reads, which is the version of the first step or the current version if there are none.
    ///
    private final int oldest;

    ///
    /// The upgrade steps, in order, oldest version first.
    ///
    private final List<UnaryOperator<Tag>> steps;

    ///
    /// The codec of the versioned form.
    ///
    private final Versioned versioned = new Versioned();

    ///
    /// The codec of the flat form.
    ///
    private final Flat flat = new Flat();

    ///
    /// Creates a new format of the given codec at version `1`.
    ///
    /// @param codec The codec of the value.
    ///
    /// @return The new format.
    ///
    public static <VALUE> Format<VALUE> of(NexusCodec<VALUE, ? super Access.Registries> codec) {
        return new Format<>((Traversal<VALUE, ? super Access.Registries>) codec, 1, List.of());
    }

    ///
    /// Refuses every key of the given object other than the version and the data if the format is strict.
    ///
    private static <V> void refuseUnknown(Operations<V> operations, V object) {
        if (!operations.isStrict()) {
            return;
        }

        List<CodecError> unknown = operations.keys(object).stream()
                .filter(key -> !Format.KEYS.contains(key))
                .map(CodecErrors::unknownKey)
                .toList();

        if (!unknown.isEmpty()) {
            throw new NexusCodecException(unknown);
        }
    }

    ///
    /// Copies every entry of the given object other than the version into a new object.
    ///
    private static <V> V copyWithoutVersion(Operations<V> operations, V object) {
        V copy = operations.emptyObject();
        for (String key : operations.keys(object)) {
            if (!key.equals(Format.VERSION)) {
                operations.put(copy, key, Objects.requireNonNull(operations.get(object, key)));
            }
        }

        return copy;
    }

    ///
    /// Creates a new format with the steps of this format and the given step.
    ///
    /// If the version is the current one, the new format will be one version higher.
    /// If the version is one below the oldest version this format reads, the new format will read that version too.
    ///
    /// This format will be left as it was.
    ///
    /// @param version The version the step upgrades from.
    /// @param step    The step that upgrades the data of the given version to the next version.
    ///
    /// @return The new format.
    ///
    /// @throws IllegalArgumentException If the version is negative, or if the step would leave a gap or double a step of this format.
    ///
    public Format<VALUE> upgradeFrom(int version, UnaryOperator<Tag> step) {
        if (version < 0) {
            throw new IllegalArgumentException("Could not add an upgrade step from the negative version '" + version + "'");
        }

        if (version == this.getVersion()) {
            return new Format<>(this.codec, this.oldest, Stream.concat(this.steps.stream(), Stream.of(step)).toList());
        }

        if (version == this.oldest - 1) {
            return new Format<>(this.codec, version, Stream.concat(Stream.of(step), this.steps.stream()).toList());
        }

        throw new IllegalArgumentException("Could not add an upgrade step from version '" + version + "', the format reads versions '" + this.oldest + "' to '" + this.getVersion() + "'");
    }

    ///
    /// The current version, which is one above the version of the highest upgrade step, or `1` if there are none.
    ///
    /// @return The current version.
    ///
    public int getVersion() {
        return this.oldest + this.steps.size();
    }

    ///
    /// Upgrades the data of the given tag from its version to the current one and then decodes the value.
    ///
    /// If the tag is not a compound with a number under `version` and a tag under `data`,
    /// the whole tag will be read as data of version `0`, which is data written before Nexus.
    ///
    /// The given tag will be left as it was.
    ///
    /// @param tag        The tag to read.
    /// @param registries The registry lookup provider for the codec.
    ///
    /// @return The value.
    ///
    /// @throws NexusCodecException - If the version of the tag is newer than the current one.
    /// - If this format has no step from the version of the tag.
    /// - If an upgrade step throws.
    /// - If the data could not be decoded.
    ///
    public VALUE read(Tag tag, HolderLookup.Provider registries) {
        return this.versioned.decode(CodecFormat.NBT.withRegistries(registries), tag);
    }

    ///
    /// Writes a compound with the current version under `version` and the given value encoded under `data`.
    ///
    /// @param value      The value to write.
    /// @param registries The registry lookup provider for the codec.
    ///
    /// @return The compound.
    ///
    /// @throws NexusCodecException If the value could not be encoded.
    ///
    public Tag write(VALUE value, HolderLookup.Provider registries) {
        return this.versioned.encode(CodecFormat.NBT.withRegistries(registries), value);
    }

    ///
    /// The codec of the versioned form, for nesting the value inside the codec of another value.
    ///
    /// In JSON and NBT, the current version will be written under `version` and the encoded value under `data`.
    /// On the network, the version will be written as a VarInt in front of the value.
    ///
    /// Older versions can only be read from NBT,
    /// in which case they will be upgraded the same way as with [#read(Tag, HolderLookup.Provider)].
    ///
    /// @return The codec of the versioned form.
    ///
    public NexusCodec<VALUE, Access.Registries> codec() {
        return this.versioned;
    }

    ///
    /// The codec of the flat form, for storing the value as an attachment.
    ///
    /// In JSON and NBT, the current version will be added under `version` to the object the value encodes to,
    /// and on the network the version will be written as a VarInt in front of the value.
    /// Encoding will fail if the value does not encode to an object or if that object already holds `version`.
    ///
    /// Older versions can only be read from NBT,
    /// in which case they will be upgraded the same way as with [#read(Tag, HolderLookup.Provider)].
    /// In NBT, an object without `version` will be read as version `0`.
    ///
    /// @return The codec of the flat form.
    ///
    public NexusCodec<VALUE, Access.Registries> flatCodec() {
        return this.flat;
    }

    ///
    /// Runs every step from the given version up to the current one on a copy of the given data.
    ///
    private Tag upgrade(int version, Tag data) {
        int current = this.getVersion();
        if (version > current) throw CodecErrors.tooNew(current, version);
        if (version < this.oldest) throw CodecErrors.missingStep(version);
        Tag upgraded = version == current ? data : data.copy();
        for (int from = version; from < current; from++) {
            try {
                upgraded = this.steps.get(from - this.oldest).apply(upgraded);
            } catch (RuntimeException failure) {
                throw CodecErrors.failedStep(from, failure);
            }
        }

        return upgraded;
    }

    ///
    /// Fails if the given version is not the current one.
    ///
    private void requireCurrent(int version) {
        if (version != this.getVersion()) {
            throw CodecErrors.mismatch("version " + this.getVersion(), version);
        }
    }

    ///
    /// Reads the given version, with a failure at its key.
    ///
    private <V> int readVersion(Operations<V> operations, V version) {
        try {
            return operations.asVarInt(version);
        } catch (NexusCodecException failure) {
            throw CodecErrors.prefixKey(failure, Format.VERSION);
        }
    }

    ///
    /// The codec of the versioned form of this format.
    ///
    private final class Versioned extends Traversal<VALUE, Access.Registries> {

        ///
        /// Creates the codec of the versioned form.
        ///
        private Versioned() {
            super("format");
        }

        ///
        /// Appends the version as a VarInt and then the value on the network, and writes both under their keys into a new object otherwise.
        ///
        @Override
        public <V> V write(Operations<V> operations, VALUE value) {
            V object = operations.emptyObject();
            V version = operations.ofVarInt(Format.this.getVersion());
            if (operations.isPositional()) {
                Format.this.codec.write(operations, value);
                return object;
            }

            operations.put(object, Format.VERSION, version);
            try {
                operations.put(object, Format.DATA, Format.this.codec.write(operations, value));
            } catch (NexusCodecException failure) {
                throw CodecErrors.prefixKey(failure, Format.DATA);
            }

            return object;
        }

        ///
        /// Reads the current version only in JSON and on the network, and upgrades the data of older versions in NBT.
        ///
        @Override
        public <V> VALUE read(Operations<V> operations, V input) {
            if (operations instanceof NbtOperations<?> nbt) {
                return this.readTag(nbt, (Tag) input);
            }

            if (operations.isPositional()) {
                Format.this.requireCurrent(operations.asVarInt(input));
                return Format.this.codec.read(operations, input);
            }

            V version = operations.get(input, Format.VERSION);
            V data = operations.get(input, Format.DATA);
            if (version == null) throw CodecErrors.missing(Format.VERSION);
            if (data == null) throw CodecErrors.missing(Format.DATA);
            Format.refuseUnknown(operations, input);
            Format.this.requireCurrent(Format.this.readVersion(operations, version));
            return this.readData(operations, data);
        }

        ///
        /// Reads the versioned form from the given tag, or the whole tag as the data of version `0` if it is not in that form.
        ///
        private VALUE readTag(Operations<Tag> nbt, Tag input) {
            if (input instanceof CompoundTag compound && compound.get(Format.VERSION) instanceof NumericTag version && compound.get(Format.DATA) instanceof Tag data) {
                Format.refuseUnknown(nbt, input);
                return this.readData(nbt, Format.this.upgrade(Format.this.readVersion(nbt, version), data));
            }

            return Format.this.codec.read(nbt, Format.this.upgrade(0, input));
        }

        ///
        /// Reads the value from the given data, with every failure below its key.
        ///
        private <V> VALUE readData(Operations<V> operations, V data) {
            try {
                return Format.this.codec.read(operations, data);
            } catch (NexusCodecException failure) {
                throw CodecErrors.prefixKey(failure, Format.DATA);
            }
        }
    }

    ///
    /// The codec of the flat form of this format.
    ///
    private final class Flat extends Traversal<VALUE, Access.Registries> {

        ///
        /// Creates the codec of the flat form.
        ///
        private Flat() {
            super("format");
        }

        ///
        /// Writes the versioned form on the network, and adds the current version to a copy of the encoded object otherwise.
        ///
        /// Fails if the encoded value is not an object or if it already holds the version.
        ///
        @Override
        public <V> V write(Operations<V> operations, VALUE value) {
            if (operations.isPositional()) {
                return Format.this.versioned.write(operations, value);
            }

            V encoded = Format.this.codec.write(operations, value);
            if (operations.get(encoded, Format.VERSION) != null) {
                throw CodecErrors.reservedKey(Format.VERSION);
            }

            return operations.put(Format.copyWithoutVersion(operations, encoded), Format.VERSION, operations.ofVarInt(Format.this.getVersion()));
        }

        ///
        /// Reads the versioned form on the network, upgrades older versions in NBT, and reads the current version only in JSON.
        ///
        /// In NBT, an object without the version will be read as version `0`.
        ///
        @Override
        public <V> VALUE read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                return Format.this.versioned.read(operations, input);
            }

            V version = operations.get(input, Format.VERSION);
            V data = Format.copyWithoutVersion(operations, input);
            if (operations instanceof NbtOperations<?> nbt) {
                int from = version == null ? 0 : Format.this.readVersion(operations, version);
                return Format.this.codec.read(nbt, Format.this.upgrade(from, (Tag) data));
            }

            if (version == null) throw CodecErrors.missing(Format.VERSION);
            Format.this.requireCurrent(Format.this.readVersion(operations, version));
            return Format.this.codec.read(operations, data);
        }
    }
}
