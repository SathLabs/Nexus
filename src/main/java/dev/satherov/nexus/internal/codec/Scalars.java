package dev.satherov.nexus.internal.codec;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.internal.codec.format.Operations;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

///
/// The scalar codecs behind the constants and the scalar factories of [NexusCodec].
///
@UtilityClass
@ApiStatus.Internal
public class Scalars {
    
    ///
    /// The codec behind [NexusCodec#BOOL].
    ///
    public static final Traversal<Boolean, Access.Plain> BOOL = new Traversal<>("BOOL") {
        
        ///
        /// Writes the `boolean`.
        ///
        @Override
        public <V> V write(Operations<V> operations, Boolean value) {
            return operations.ofBoolean(value);
        }
        
        ///
        /// Reads a `boolean`.
        ///
        @Override
        public <V> Boolean read(Operations<V> operations, V input) {
            return operations.asBoolean(input);
        }
    };
    
    ///
    /// The codec behind [NexusCodec#BYTE].
    ///
    public static final Traversal<Byte, Access.Plain> BYTE = new Traversal<>("BYTE") {
        
        ///
        /// Writes the `byte`.
        ///
        @Override
        public <V> V write(Operations<V> operations, Byte value) {
            return operations.ofByte(value);
        }
        
        ///
        /// Reads a `byte`.
        ///
        @Override
        public <V> Byte read(Operations<V> operations, V input) {
            return operations.asByte(input);
        }
    };
    
    ///
    /// The codec behind [NexusCodec#SHORT].
    ///
    public static final Traversal<Short, Access.Plain> SHORT = new Traversal<>("SHORT") {
        
        ///
        /// Writes the `short`.
        ///
        @Override
        public <V> V write(Operations<V> operations, Short value) {
            return operations.ofShort(value);
        }
        
        ///
        /// Reads a `short`.
        ///
        @Override
        public <V> Short read(Operations<V> operations, V input) {
            return operations.asShort(input);
        }
    };
    
    ///
    /// The codec behind [NexusCodec#INT].
    ///
    public static final Traversal<Integer, Access.Plain> INT = new Traversal<>("INT") {
        
        ///
        /// Writes the `int`.
        ///
        @Override
        public <V> V write(Operations<V> operations, Integer value) {
            return operations.ofInt(value);
        }
        
        ///
        /// Reads an `int`.
        ///
        @Override
        public <V> Integer read(Operations<V> operations, V input) {
            return operations.asInt(input);
        }
    };
    
    ///
    /// The codec behind [NexusCodec#VAR_INT].
    ///
    public static final Traversal<Integer, Access.Plain> VAR_INT = new Traversal<>("VAR_INT") {
        
        ///
        /// Writes the `int`, as a VarInt on the network.
        ///
        @Override
        public <V> V write(Operations<V> operations, Integer value) {
            return operations.ofVarInt(value);
        }
        
        ///
        /// Reads an `int`, as a VarInt on the network.
        ///
        @Override
        public <V> Integer read(Operations<V> operations, V input) {
            return operations.asVarInt(input);
        }
    };
    
    ///
    /// The codec behind [NexusCodec#LONG].
    ///
    public static final Traversal<Long, Access.Plain> LONG = new Traversal<>("LONG") {
        
        ///
        /// Writes the `long`.
        ///
        @Override
        public <V> V write(Operations<V> operations, Long value) {
            return operations.ofLong(value);
        }
        
        ///
        /// Reads a `long`.
        ///
        @Override
        public <V> Long read(Operations<V> operations, V input) {
            return operations.asLong(input);
        }
    };
    
    ///
    /// The codec behind [NexusCodec#VAR_LONG].
    ///
    public static final Traversal<Long, Access.Plain> VAR_LONG = new Traversal<>("VAR_LONG") {
        
        ///
        /// Writes the `long`, as a VarLong on the network.
        ///
        @Override
        public <V> V write(Operations<V> operations, Long value) {
            return operations.ofVarLong(value);
        }
        
        ///
        /// Reads a `long`, as a VarLong on the network.
        ///
        @Override
        public <V> Long read(Operations<V> operations, V input) {
            return operations.asVarLong(input);
        }
    };
    
    ///
    /// The codec behind [NexusCodec#FLOAT].
    ///
    public static final Traversal<Float, Access.Plain> FLOAT = new Traversal<>("FLOAT") {
        
        ///
        /// Writes the `float`.
        ///
        @Override
        public <V> V write(Operations<V> operations, Float value) {
            return operations.ofFloat(value);
        }
        
        ///
        /// Reads a `float`.
        ///
        @Override
        public <V> Float read(Operations<V> operations, V input) {
            return operations.asFloat(input);
        }
    };
    
    ///
    /// The codec behind [NexusCodec#DOUBLE].
    ///
    public static final Traversal<Double, Access.Plain> DOUBLE = new Traversal<>("DOUBLE") {
        
        ///
        /// Writes the `double`.
        ///
        @Override
        public <V> V write(Operations<V> operations, Double value) {
            return operations.ofDouble(value);
        }
        
        ///
        /// Reads a `double`.
        ///
        @Override
        public <V> Double read(Operations<V> operations, V input) {
            return operations.asDouble(input);
        }
    };
    
    ///
    /// The codec behind [NexusCodec#STRING].
    ///
    public static final Traversal<String, Access.Plain> STRING = Scalars.string("STRING", FriendlyByteBuf.MAX_STRING_LENGTH);
    
    ///
    /// The codec behind [NexusCodec#IDENTIFIER].
    ///
    public static final Traversal<Identifier, Access.Plain> IDENTIFIER = new Traversal<>("IDENTIFIER") {
        
        ///
        /// Writes the string form of the `identifier`.
        ///
        @Override
        public <V> V write(Operations<V> operations, Identifier value) {
            return operations.ofString(value.toString(), FriendlyByteBuf.MAX_STRING_LENGTH);
        }
        
        ///
        /// Reads a string and parses it as an `identifier`.
        ///
        @Override
        public <V> Identifier read(Operations<V> operations, V input) {
            String text = operations.asString(input, FriendlyByteBuf.MAX_STRING_LENGTH);
            Identifier identifier = Identifier.tryParse(text);
            if (identifier == null) {
                throw CodecErrors.mismatch("an identifier", text);
            }
            
            return identifier;
        }
    };
    
    ///
    /// The codec behind [NexusCodec#UUID].
    ///
    /// It isn't called `UUID` because that field would hide the type [UUID] in this file.
    ///
    public static final Traversal<UUID, Access.Plain> UNIQUE_ID = new Traversal<>("UUID") {
        
        ///
        /// Writes the `UUID` as two `long`s on the network, and as an `int` array otherwise.
        ///
        @Override
        public <V> V write(Operations<V> operations, UUID value) {
            if (operations.isPositional()) {
                operations.ofLong(value.getMostSignificantBits());
                return operations.ofLong(value.getLeastSignificantBits());
            }
            
            return operations.ofIntArray(UUIDUtil.uuidToIntArray(value));
        }
        
        ///
        /// Reads two `long`s on the network, and an `int` array or the string form of a `UUID` otherwise.
        ///
        @Override
        public <V> UUID read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                return new UUID(operations.asLong(input), operations.asLong(input));
            }
            
            int[] parts;
            try {
                parts = operations.asIntArray(input);
            } catch (NexusCodecException _) {
                return Scalars.parseUuid(operations, input);
            }
            
            if (parts.length != 4) throw CodecErrors.mismatch("a list of 4 `int`s", parts.length);
            return UUIDUtil.uuidFromIntArray(parts);
        }
    };
    
    ///
    /// Creates the codec behind [NexusCodec#string(int)].
    ///
    /// @param limit The maximum number of characters of the string.
    ///
    /// @return The codec of the string.
    ///
    public static Traversal<String, Access.Plain> string(int limit) {
        return Scalars.string("string(" + limit + ")", limit);
    }
    
    ///
    /// Creates the codec of a string of at most the given number of characters, with the given name.
    ///
    private static Traversal<String, Access.Plain> string(String name, int limit) {
        return new Traversal<>(name) {
            
            ///
            /// Writes the string.
            ///
            @Override
            public <V> V write(Operations<V> operations, String value) {
                return operations.ofString(value, limit);
            }
            
            ///
            /// Reads a string.
            ///
            @Override
            public <V> String read(Operations<V> operations, V input) {
                return operations.asString(input, limit);
            }
        };
    }
    
    ///
    /// Creates the codec behind [NexusCodec#enumOf(Class)].
    ///
    /// @param type The class of the enum.
    ///
    /// @return The codec of the enum's constants.
    ///
    public static <E extends Enum<E>> Traversal<E, Access.Plain> enumOf(Class<E> type) {
        return new EnumScalar<>(type);
    }
    
    ///
    /// Creates the codec behind [NexusCodec#unit(Object)].
    ///
    /// @param value The value to decode to.
    ///
    /// @return The codec of the value.
    ///
    public static <T> Traversal<T, Access.Plain> unit(T value) {
        return new Traversal<>("unit") {
            
            ///
            /// Writes an empty object, which is nothing on the network.
            ///
            @Override
            public <V> V write(Operations<V> operations, T value) {
                return operations.emptyObject();
            }
            
            ///
            /// Reads nothing on the network and checks that the input is an object otherwise.
            ///
            @Override
            public <V> T read(Operations<V> operations, V input) {
                if (!operations.isPositional()) {
                    // Called only to fail on anything that isn't an object, the same as DFU does.
                    operations.keys(input);
                }
                
                return value;
            }
        };
    }
    
    ///
    /// Reads the string form of a `UUID` from the given input.
    ///
    private static <V> UUID parseUuid(Operations<V> operations, V input) {
        String text;
        try {
            text = operations.asString(input, FriendlyByteBuf.MAX_STRING_LENGTH);
        } catch (NexusCodecException _) {
            throw CodecErrors.mismatch("a list of 4 `int`s or a UUID string", input);
        }
        
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException _) {
            throw CodecErrors.mismatch("a UUID string", text);
        }
    }
    
    ///
    /// The codec of the constants of one enum, with the names of the constants computed once.
    ///
    private static final class EnumScalar<E extends Enum<E>> extends Traversal<E, Access.Plain> {
        
        ///
        /// The constants of the enum, indexed by their ordinal.
        ///
        private final E[] constants;
        
        ///
        /// The names of the constants in JSON and NBT, indexed by their ordinal.
        ///
        private final List<String> names;
        
        ///
        /// The constants of the enum by their name in JSON and NBT, where the first constant takes a name that several share.
        ///
        private final Map<String, E> constantsByName;
        
        ///
        /// Creates the codec of the constants of the given enum.
        ///
        private EnumScalar(Class<E> type) {
            super(type.getSimpleName());
            
            this.constants = type.getEnumConstants();
            this.names = Arrays.stream(this.constants).map(EnumScalar::nameOf).toList();
            this.constantsByName = Arrays.stream(this.constants)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toMap(
                                    val -> EnumScalar.nameOf(val), // Why the hell does a short form lambda not work here???
                                    Function.identity(),
                                    (first, _) -> first
                            ),
                            Map::copyOf
                    ));
        }
        
        ///
        /// Gets the name of the given constant in JSON and NBT.
        ///
        private static String nameOf(Enum<?> constant) {
            return constant instanceof StringRepresentable representable ?
                    representable.getSerializedName() :
                    constant.name().toLowerCase(Locale.ROOT);
        }
        
        ///
        /// Writes the ordinal of the constant as a VarInt on the network, and its name otherwise.
        ///
        @Override
        public <V> V write(Operations<V> operations, E value) {
            if (operations.isPositional()) {
                return operations.ofVarInt(value.ordinal());
            }
            
            return operations.ofString(this.names.get(value.ordinal()), FriendlyByteBuf.MAX_STRING_LENGTH);
        }
        
        ///
        /// Reads the ordinal of a constant as a VarInt on the network, and its name otherwise.
        ///
        @Override
        public <V> E read(Operations<V> operations, V input) {
            if (operations.isPositional()) {
                int ordinal = operations.asVarInt(input);
                if (ordinal < 0 || ordinal >= this.constants.length) {
                    throw CodecErrors.outOfRange(0, this.constants.length - 1, ordinal);
                }
                
                return this.constants[ordinal];
            }
            
            String name = operations.asString(input, FriendlyByteBuf.MAX_STRING_LENGTH);
            E constant = this.constantsByName.get(name);
            if (constant == null) {
                throw CodecErrors.unknownName(this.names, name);
            }
            
            return constant;
        }
    }
}
