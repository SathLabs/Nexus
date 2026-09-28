package dev.satherov.nexus.api.codec.key;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.internal.codec.CollectionCodecs;

import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;
import java.util.function.Function;

///
/// A key of the map codec of [NexusCodec#mapOf(MapKey, NexusCodec)].
///
/// In JSON and NBT, a key is written as its string form, which is the key of its value in the object.
/// On the network, a key is written with its codec.
///
/// @param <K> The type of the key.
/// @param <A> The access a format has to offer to be used with this key.
///
@ApiStatus.NonExtendable
public interface MapKey<K, A extends Access.Plain> {
    
    ///
    /// The key of a string of at most vanilla's 32,767 characters, which is a string itself in JSON and NBT.
    ///
    /// On the network, it's written the same as [NexusCodec#STRING].
    ///
    MapKey<String, Access.Plain> STRING = CollectionCodecs.STRING_KEY;
    
    ///
    /// The key of an [Identifier], as its string form.
    ///
    /// Fails if the string is not a valid identifier.
    ///
    MapKey<Identifier, Access.Plain> IDENTIFIER = CollectionCodecs.IDENTIFIER_KEY;
    
    ///
    /// The key of a [UUID], as its string form in JSON and NBT.
    ///
    /// On the network, it's written the same as [NexusCodec#UUID].
    ///
    MapKey<UUID, Access.Plain> UUID = CollectionCodecs.UUID_KEY;
    
    ///
    /// The key of an `int`, as its decimal string form in JSON and NBT.
    ///
    /// On the network, it's written the same as [NexusCodec#INT].
    ///
    MapKey<Integer, Access.Plain> INT = CollectionCodecs.INT_KEY;
    
    ///
    /// The key of a `long`, as its decimal string form in JSON and NBT.
    ///
    /// On the network, it's written the same as [NexusCodec#LONG].
    ///
    MapKey<Long, Access.Plain> LONG = CollectionCodecs.LONG_KEY;
    
    ///
    /// Creates the key of the constants of the given enum.
    ///
    /// In JSON and NBT, a constant is written as:
    /// - Its [StringRepresentable#getSerializedName()] if the enum implements [StringRepresentable].
    /// - Its name in lower case otherwise.
    ///
    /// On the network, a constant is written as its ordinal, as a VarInt, the same as [NexusCodec#enumOf(Class)].
    ///
    /// Fails if a name or an ordinal belongs to none of the constants.
    ///
    /// @param type The class of the enum.
    ///
    /// @return The key of the enum's constants.
    ///
    static <E extends Enum<E>> MapKey<E, Access.Plain> enumOf(Class<E> type) {
        return CollectionCodecs.enumKey(type);
    }
    
    ///
    /// Creates the key of the values of the given codec, with the given string form.
    ///
    /// `fromString` should read back every string that `toString` writes.
    /// If either of them throws anything other than a [NexusCodecException], the key will fail with the message of what it threw.
    ///
    /// @param codec      The codec of the key on the network.
    /// @param toString   The writer of the string form of a key.
    /// @param fromString The reader of a key from its string form, which throws a [NexusCodecException] if it can't read the string.
    ///
    /// @return The key.
    ///
    static <K, A extends Access.Plain> MapKey<K, A> of(NexusCodec<K, A> codec, Function<? super K, String> toString, Function<String, ? extends K> fromString) {
        return CollectionCodecs.key(codec, toString, fromString);
    }
}
