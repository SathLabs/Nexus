package dev.satherov.nexus.test.unit.api.codec;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.MapKey;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.StructCodec;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Unit;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

///
/// The parity fixture, every Nexus codec next to its DFU codec, its vanilla stream codec, and the values to compare them on.
///
@UtilityClass
public class ParityCases {
    
    private static final StructCodec<Listing, Access.Plain> LISTING = NexusCodec.struct(
            "listing",
            NexusCodec.STRING.field("item", Listing::item),
            NexusCodec.INT.optionalField("count", 1, Listing::count),
            NexusCodec.STRING.optionalField("label", Listing::label),
            Listing::new
    );
    
    private static final MapCodec<Listing> LISTING_DFU = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("item").forGetter(Listing::item),
            Codec.INT.optionalFieldOf("count", 1).forGetter(Listing::count),
            Codec.STRING.optionalFieldOf("label").forGetter(Listing::label)
    ).apply(instance, Listing::new));
    
    private static final StreamCodec<ByteBuf, Listing> LISTING_STREAM = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            Listing::item,
            ByteBufCodecs.INT,
            Listing::count,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8),
            Listing::label,
            Listing::new
    );
    
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
            new Case<>("unit", NexusCodec.unit(Unit.INSTANCE), Unit.CODEC, Unit.STREAM_CODEC, List.of(Unit.INSTANCE)),
            new Case<>(
                    "struct(listing)",
                    ParityCases.LISTING,
                    ParityCases.LISTING_DFU.codec(),
                    ParityCases.LISTING_STREAM,
                    List.of(
                            new Listing("stone", 1, Optional.empty()),
                            new Listing("dirt", 64, Optional.of("cheap")),
                            new Listing("", 0, Optional.of(""))
                    )
            ),
            new Case<>(
                    "struct(offer)",
                    NexusCodec.struct(
                            "offer",
                            ParityCases.LISTING.inline(Offer::listing),
                            NexusCodec.INT.field("price", Offer::price),
                            Offer::new
                    ),
                    RecordCodecBuilder.create(instance -> instance.group(
                            ParityCases.LISTING_DFU.forGetter(Offer::listing),
                            Codec.INT.fieldOf("price").forGetter(Offer::price)
                    ).apply(instance, Offer::new)),
                    StreamCodec.composite(ParityCases.LISTING_STREAM, Offer::listing, ByteBufCodecs.INT, Offer::price, Offer::new),
                    List.of(new Offer(new Listing("stone", 1, Optional.empty()), 5), new Offer(new Listing("gold", 3, Optional.of("shiny")), 100))
            ),
            new Case<>("INT.list()", NexusCodec.INT.list(), Codec.INT.listOf(), ByteBufCodecs.INT.apply(ByteBufCodecs.list()), List.of(List.of(), List.of(1, -1, 300, 1))),
            new Case<>(
                    "STRING.list(3)",
                    NexusCodec.STRING.list(3),
                    Codec.STRING.sizeLimitedListOf(3),
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(3)),
                    List.of(List.of(), List.of("stone", "dirt", "café"))
            ),
            new Case<>(
                    "listing.list()",
                    ParityCases.LISTING.list(),
                    ParityCases.LISTING_DFU.codec().listOf(),
                    ParityCases.LISTING_STREAM.apply(ByteBufCodecs.list()),
                    List.of(List.of(new Listing("stone", 1, Optional.empty()), new Listing("dirt", 64, Optional.of("cheap"))))
            ),
            new Case<>(
                    "UUID.set()",
                    NexusCodec.UUID.set(),
                    UUIDUtil.CODEC_LINKED_SET,
                    ByteBufCodecs.collection(LinkedHashSet::new, UUIDUtil.STREAM_CODEC),
                    List.of(Set.of(), ImmutableSet.of(new UUID(0L, 1L), UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6")))
            ),
            new Case<>(
                    "mapOf(STRING, INT)",
                    NexusCodec.mapOf(MapKey.STRING, NexusCodec.INT),
                    Codec.unboundedMap(Codec.STRING, Codec.INT),
                    ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.INT),
                    List.of(Map.of(), ImmutableMap.of("stone", 1, "dirt", 64, "", 0))
            ),
            new Case<>(
                    "mapOf(IDENTIFIER, STRING)",
                    NexusCodec.mapOf(MapKey.IDENTIFIER, NexusCodec.STRING),
                    Codec.unboundedMap(Identifier.CODEC, Codec.STRING),
                    ByteBufCodecs.map(LinkedHashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.STRING_UTF8),
                    List.of(ImmutableMap.of(Identifier.withDefaultNamespace("stone"), "rock", Identifier.fromNamespaceAndPath("nexus", "path/to/thing"), "thing"))
            ),
            new Case<>(
                    "mapOf(enumOf(Weight), INT)",
                    NexusCodec.mapOf(MapKey.enumOf(Weight.class), NexusCodec.INT),
                    Codec.unboundedMap(StringRepresentable.fromEnum(Weight::values), Codec.INT),
                    ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.idMapper(ordinal -> Weight.values()[ordinal], Weight::ordinal), ByteBufCodecs.INT),
                    List.of(ImmutableMap.of(Weight.ANVIL, 100, Weight.FEATHER, 1))
            ),
            new Case<>(
                    "struct(bundle)",
                    NexusCodec.struct(
                            "bundle",
                            NexusCodec.STRING.field("name", Bundle::name),
                            NexusCodec.INT.list().field("counts", Bundle::counts),
                            Bundle::new
                    ),
                    RecordCodecBuilder.create(instance -> instance.group(
                            Codec.STRING.fieldOf("name").forGetter(Bundle::name),
                            Codec.INT.listOf().fieldOf("counts").forGetter(Bundle::counts)
                    ).apply(instance, Bundle::new)),
                    StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Bundle::name, ByteBufCodecs.INT.apply(ByteBufCodecs.list()), Bundle::counts, Bundle::new),
                    List.of(new Bundle("empty", List.of()), new Bundle("full", List.of(1, 2, 3)))
            )
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
    
    public record Listing(String item, int count, Optional<String> label) { }
    
    public record Offer(Listing listing, int price) { }
    
    public record Bundle(String name, List<Integer> counts) { }
}
