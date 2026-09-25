package dev.satherov.nexus.test.unit.api.codec;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Unit;

import com.mojang.serialization.Codec;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

///
/// The parity fixture, every Nexus codec next to its DFU codec, its vanilla stream codec, and the values to compare them on.
///
@UtilityClass
public class ParityCases {
    
    public static final List<Case<?>> ALL = List.of(
            new Case<>("BOOL", NexusCodec.BOOL, Codec.BOOL, ByteBufCodecs.BOOL, List.of(true, false)),
            new Case<>("BYTE", NexusCodec.BYTE, Codec.BYTE, ByteBufCodecs.BYTE, List.of((byte) 0, (byte) 1, (byte) -1, Byte.MIN_VALUE, Byte.MAX_VALUE)),
            new Case<>("SHORT", NexusCodec.SHORT, Codec.SHORT, ByteBufCodecs.SHORT, List.of((short) 0, (short) 1234, (short) -1, Short.MIN_VALUE, Short.MAX_VALUE)),
            new Case<>("INT", NexusCodec.INT, Codec.INT, ByteBufCodecs.INT, List.of(0, 300, -1, Integer.MIN_VALUE, Integer.MAX_VALUE)),
            new Case<>("VAR_INT", NexusCodec.VAR_INT, Codec.INT, ByteBufCodecs.VAR_INT, List.of(0, 127, 128, 300, -1, Integer.MIN_VALUE, Integer.MAX_VALUE)),
            new Case<>("LONG", NexusCodec.LONG, Codec.LONG, ByteBufCodecs.LONG, List.of(0L, 1L << 40, -1L, Long.MIN_VALUE, Long.MAX_VALUE)),
            new Case<>("VAR_LONG", NexusCodec.VAR_LONG, Codec.LONG, ByteBufCodecs.VAR_LONG, List.of(0L, 127L, 128L, 1L << 40, -1L, Long.MIN_VALUE, Long.MAX_VALUE)),
            new Case<>("FLOAT", NexusCodec.FLOAT, Codec.FLOAT, ByteBufCodecs.FLOAT, List.of(0.0F, -0.0F, 0.1F, 1.5F, -3.25F, Float.MIN_VALUE, Float.MAX_VALUE)),
            new Case<>("DOUBLE", NexusCodec.DOUBLE, Codec.DOUBLE, ByteBufCodecs.DOUBLE, List.of(0.0D, -0.0D, 0.1D, 1.5D, -3.25D, Double.MIN_VALUE, Double.MAX_VALUE)),
            new Case<>("STRING", NexusCodec.STRING, Codec.STRING, ByteBufCodecs.STRING_UTF8, List.of("", "stone", "café", "𝄞", "a".repeat(32767))),
            new Case<>("string(16)", NexusCodec.string(16), Codec.sizeLimitedString(16), ByteBufCodecs.stringUtf8(16), List.of("", "sixteen chars ok")),
            new Case<>(
                    "IDENTIFIER",
                    NexusCodec.IDENTIFIER,
                    Identifier.CODEC,
                    Identifier.STREAM_CODEC,
                    List.of(Identifier.withDefaultNamespace("stone"), Identifier.fromNamespaceAndPath("nexus", "path/to/thing"))
            ),
            new Case<>(
                    "UUID",
                    NexusCodec.UUID,
                    UUIDUtil.LENIENT_CODEC,
                    UUIDUtil.STREAM_CODEC,
                    List.of(new UUID(0L, 0L), new UUID(-1L, Long.MIN_VALUE), UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6"))
            ),
            new Case<>(
                    "enumOf(Weight)",
                    NexusCodec.enumOf(Weight.class),
                    StringRepresentable.fromEnum(Weight::values),
                    ByteBufCodecs.idMapper(ordinal -> Weight.values()[ordinal], Weight::ordinal),
                    List.of(Weight.values())
            ),
            new Case<>(
                    "enumOf(Alias)",
                    NexusCodec.enumOf(Alias.class),
                    StringRepresentable.fromEnum(Alias::values),
                    ByteBufCodecs.idMapper(ordinal -> Alias.values()[ordinal], Alias::ordinal),
                    List.of(Alias.values())
            ),
            new Case<>(
                    "enumOf(Phase)",
                    NexusCodec.enumOf(Phase.class),
                    Codec.STRING.xmap(name -> Phase.valueOf(name.toUpperCase(Locale.ROOT)), phase -> phase.name().toLowerCase(Locale.ROOT)),
                    ByteBufCodecs.idMapper(ordinal -> Phase.values()[ordinal], Phase::ordinal),
                    List.of(Phase.values())
            ),
            new Case<>("unit", NexusCodec.unit(Unit.INSTANCE), Unit.CODEC, Unit.STREAM_CODEC, List.of(Unit.INSTANCE))
    );
    
    public record Case<T>(String name, NexusCodec<T, ? super Access.Registries> codec, Codec<T> dfu, StreamCodec<? super RegistryFriendlyByteBuf, T> stream, List<T> values) { }
    
    @RequiredArgsConstructor
    public enum Weight implements StringRepresentable {
        FEATHER("light"),
        ANVIL("heavy");
        
        private final String serializedName;
        
        @Override
        public String getSerializedName() {
            return this.serializedName;
        }
    }
    
    @RequiredArgsConstructor
    public enum Alias implements StringRepresentable {
        FIRST("shared"),
        SECOND("shared");
        
        private final String serializedName;
        
        @Override
        public String getSerializedName() {
            return this.serializedName;
        }
    }
    
    public enum Phase {
        NEW_MOON,
        FULL_MOON
    }
}
