package dev.satherov.nexus.test.unit.api.codec;

import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.MapKey;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.NexusCodecException;
import dev.satherov.nexus.api.codec.StructCodec;
import dev.satherov.nexus.api.codec.VanillaCodecs;

import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

///
/// The parity fixture, every Nexus codec next to its DFU codec, its vanilla stream codec, and the values to compare them on.
///
@UtilityClass
public class ParityCases {
    
    public static final Identifier CIRCLE = Identifier.fromNamespaceAndPath("nexus", "circle");
    public static final Identifier SQUARE = Identifier.fromNamespaceAndPath("nexus", "square");
    
    private static final Holder<Item> STONE = BuiltInRegistries.ITEM.wrapAsHolder(Items.STONE);
    private static final Holder<Item> DIRT = BuiltInRegistries.ITEM.wrapAsHolder(Items.DIRT);
    
    private static final StructCodec<Listing, Access.Plain> LISTING = NexusCodec.struct(
            "listing",
            NexusCodec.STRING.field("item", Listing::item),
            NexusCodec.INT.optionalField("count", 1, Listing::count),
            NexusCodec.STRING.optionalField("label", Listing::label),
            Listing::new
    );
    
    public static final MapCodec<Listing> LISTING_DFU = RecordCodecBuilder.mapCodec(instance -> instance.group(
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
    
    private static final StructCodec<Shape, Access.Plain> SHAPE = NexusCodec.dispatch(
            "type",
            ParityCases::typeOf,
            Map.of(
                    ParityCases.CIRCLE,
                    NexusCodec.struct("circle", NexusCodec.INT.field("radius", Circle::radius), Circle::new),
                    ParityCases.SQUARE,
                    NexusCodec.struct("square", NexusCodec.INT.field("side", Square::side), Square::new)
            )
    );
    
    private static final Map<Identifier, MapCodec<? extends Shape>> SHAPES_DFU = Map.of(
            ParityCases.CIRCLE,
            Codec.INT.fieldOf("radius").xmap(Circle::new, Circle::radius),
            ParityCases.SQUARE,
            Codec.INT.fieldOf("side").xmap(Square::new, Square::side)
    );
    
    private static final StreamCodec<ByteBuf, Shape> SHAPE_STREAM = Identifier.STREAM_CODEC.dispatch(
            ParityCases::typeOf,
            Map.<Identifier, StreamCodec<ByteBuf, ? extends Shape>>of(
                    ParityCases.CIRCLE,
                    ByteBufCodecs.INT.map(Circle::new, Circle::radius),
                    ParityCases.SQUARE,
                    ByteBufCodecs.INT.map(Square::new, Square::side)
            )::get
    );
    
    private static final NexusCodec<Node, Access.Plain> TREE = NexusCodec.recursive(
            "node",
            self -> NexusCodec.struct("node", NexusCodec.INT.field("value", Node::value), self.list().field("children", Node::children), Node::new)
    );
    
    private static final Codec<Node> TREE_DFU = Codec.recursive(
            "node",
            self -> RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("value").forGetter(Node::value),
                    self.listOf().fieldOf("children").forGetter(Node::children)
            ).apply(instance, Node::new))
    );
    
    private static final StreamCodec<ByteBuf, Node> TREE_STREAM = StreamCodec.recursive(
            self -> StreamCodec.composite(ByteBufCodecs.INT, Node::value, self.apply(ByteBufCodecs.list()), Node::children, Node::new)
    );
    
    private static final StructCodec<SoundEvent, Access.Plain> SOUND_EVENT = NexusCodec.struct(
            "sound_event",
            NexusCodec.IDENTIFIER.field("sound_id", SoundEvent::location),
            NexusCodec.FLOAT.optionalField("range", SoundEvent::fixedRange),
            SoundEvent::new
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
            ),
            new Case<>(
                    "either(INT, STRING)",
                    NexusCodec.either(NexusCodec.INT, NexusCodec.STRING),
                    Codec.either(Codec.INT, Codec.STRING),
                    ByteBufCodecs.either(ByteBufCodecs.INT, ByteBufCodecs.STRING_UTF8),
                    List.of(Either.left(5), Either.left(-1), Either.right("five"), Either.right(""))
            ),
            new Case<>(
                    "STRING.oneOrMany()",
                    NexusCodec.STRING.oneOrMany(),
                    ExtraCodecs.compactListCodec(Codec.STRING),
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    List.of(List.of(), List.of("stone"), List.of("stone", "dirt"))
            ),
            new Case<>(
                    "listing.orShort(STRING)",
                    ParityCases.LISTING.orShort(NexusCodec.STRING, ParityCases::shortListing, ParityCases::shortFormOf),
                    Codec.either(Codec.STRING, ParityCases.LISTING_DFU.codec()).xmap(ParityCases::fromEither, ParityCases::toEither),
                    ByteBufCodecs.either(ByteBufCodecs.STRING_UTF8, ParityCases.LISTING_STREAM).map(ParityCases::fromEither, ParityCases::toEither),
                    List.of(new Listing("stone", 1, Optional.empty()), new Listing("dirt", 64, Optional.of("cheap")), new Listing("clay", 1, Optional.of("")))
            ),
            new Case<>(
                    "dispatch(type)",
                    ParityCases.SHAPE,
                    Identifier.CODEC.dispatch("type", ParityCases::typeOf, ParityCases.SHAPES_DFU::get),
                    ParityCases.SHAPE_STREAM,
                    List.of(new Circle(3), new Square(4))
            ),
            new Case<>(
                    "struct(named)",
                    NexusCodec.struct("named", NexusCodec.STRING.field("name", Named::name), ParityCases.SHAPE.inline(Named::shape), Named::new),
                    RecordCodecBuilder.create(instance -> instance.group(
                            Codec.STRING.fieldOf("name").forGetter(Named::name),
                            Identifier.CODEC.<Shape>dispatchMap("type", ParityCases::typeOf, ParityCases.SHAPES_DFU::get).forGetter(Named::shape)
                    ).apply(instance, Named::new)),
                    StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Named::name, ParityCases.SHAPE_STREAM, Named::shape, Named::new),
                    List.of(new Named("wheel", new Circle(3)), new Named("tile", new Square(4)))
            ),
            new Case<>(
                    "recursive(node)",
                    ParityCases.TREE,
                    ParityCases.TREE_DFU,
                    ParityCases.TREE_STREAM,
                    List.of(new Node(1, List.of()), new Node(1, List.of(new Node(2, List.of()), new Node(3, List.of(new Node(4, List.of()))))))
            ),
            new Case<>(
                    "INT.xmap(Amount)",
                    NexusCodec.INT.xmap(Amount::new, Amount::value),
                    Codec.INT.xmap(Amount::new, Amount::value),
                    ByteBufCodecs.INT.map(Amount::new, Amount::value),
                    List.of(new Amount(0), new Amount(-7), new Amount(300))
            ),
            new Case<>(
                    "INT.flatXmap(Amount)",
                    NexusCodec.INT.flatXmap(ParityCases::positiveAmount, Amount::value),
                    Codec.INT.flatXmap(value -> value > 0 ? DataResult.success(new Amount(value)) : DataResult.error(() -> "negative"), amount -> DataResult.success(amount.value())),
                    ByteBufCodecs.INT.map(Amount::new, Amount::value),
                    List.of(new Amount(1), new Amount(300))
            ),
            new Case<>(
                    "INT.validate()",
                    NexusCodec.INT.validate(value -> value < 0 ? "negative" : null),
                    Codec.INT.validate(value -> value < 0 ? DataResult.error(() -> "negative") : DataResult.success(value)),
                    ByteBufCodecs.INT,
                    List.of(0, 5, Integer.MAX_VALUE)
            ),
            new Case<>(
                    "holder(ITEM)",
                    NexusCodec.holder(Registries.ITEM),
                    RegistryFixedCodec.create(Registries.ITEM),
                    ByteBufCodecs.holderRegistry(Registries.ITEM),
                    List.of(ParityCases.STONE, ParityCases.DIRT)
            ),
            new Case<>(
                    "holderOrInline(SOUND_EVENT)",
                    NexusCodec.holderOrInline(Registries.SOUND_EVENT, ParityCases.SOUND_EVENT),
                    RegistryFileCodec.create(Registries.SOUND_EVENT, SoundEvent.DIRECT_CODEC),
                    ByteBufCodecs.holder(Registries.SOUND_EVENT, SoundEvent.DIRECT_STREAM_CODEC),
                    List.of(
                            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ANVIL_LAND),
                            Holder.direct(new SoundEvent(Identifier.fromNamespaceAndPath("nexus", "boom"), Optional.empty())),
                            Holder.direct(new SoundEvent(Identifier.fromNamespaceAndPath("nexus", "boom"), Optional.of(16.0F)))
                    )
            ),
            new Case<>(
                    "holderSet(ITEM)",
                    NexusCodec.holderSet(Registries.ITEM),
                    RegistryCodecs.homogeneousList(Registries.ITEM),
                    ByteBufCodecs.holderSet(Registries.ITEM),
                    List.of(
                            HolderSet.direct(List.of()),
                            HolderSet.direct(List.of(ParityCases.STONE)),
                            HolderSet.direct(List.of(ParityCases.STONE, ParityCases.DIRT)),
                            BuiltInRegistries.ITEM.getOrThrow(ItemTags.WOODEN_TOOL_MATERIALS)
                    )
            ),
            new Case<>(
                    "ofDfu(listing)",
                    NexusCodec.ofDfu(ParityCases.LISTING_DFU.codec()),
                    ParityCases.LISTING_DFU.codec(),
                    ByteBufCodecs.fromCodec(ParityCases.LISTING_DFU.codec()),
                    List.of(new Listing("stone", 1, Optional.empty()), new Listing("dirt", 64, Optional.of("cheap")))
            ),
            new Case<>(
                    "ofDfu(RegistryFixedCodec(ITEM))",
                    NexusCodec.ofDfu(RegistryFixedCodec.create(Registries.ITEM), Access.Registries.class),
                    RegistryFixedCodec.create(Registries.ITEM),
                    ByteBufCodecs.fromCodecWithRegistries(RegistryFixedCodec.create(Registries.ITEM)),
                    List.of(ParityCases.STONE, ParityCases.DIRT)
            ),
            new Case<>(
                    "ofVanilla(listing)",
                    NexusCodec.ofVanilla(ParityCases.LISTING_DFU.codec(), ParityCases.LISTING_STREAM),
                    ParityCases.LISTING_DFU.codec(),
                    ParityCases.LISTING_STREAM,
                    List.of(new Listing("stone", 1, Optional.empty()), new Listing("dirt", 64, Optional.of("cheap")))
            ),
            new Case<>(
                    "BLOCK_POS",
                    VanillaCodecs.BLOCK_POS,
                    BlockPos.CODEC,
                    BlockPos.STREAM_CODEC,
                    List.of(BlockPos.ZERO, new BlockPos(1, -64, -1), new BlockPos(29_999_999, 2047, -30_000_000))
            ),
            new Case<>(
                    "CHUNK_POS",
                    VanillaCodecs.CHUNK_POS,
                    ChunkPos.CODEC,
                    ChunkPos.STREAM_CODEC,
                    List.of(ChunkPos.ZERO, new ChunkPos(-1, 5), new ChunkPos(Integer.MIN_VALUE, Integer.MAX_VALUE))
            ),
            new Case<>(
                    "GLOBAL_POS",
                    VanillaCodecs.GLOBAL_POS,
                    GlobalPos.CODEC,
                    GlobalPos.STREAM_CODEC,
                    List.of(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), GlobalPos.of(Level.NETHER, new BlockPos(1, 2, 3)))
            ),
            new Case<>("VEC3", VanillaCodecs.VEC3, Vec3.CODEC, Vec3.STREAM_CODEC, List.of(Vec3.ZERO, new Vec3(1.5D, -2.0D, 0.1D))),
            new Case<>("VEC3I", VanillaCodecs.VEC3I, Vec3i.CODEC, Vec3i.STREAM_CODEC, List.of(Vec3i.ZERO, new Vec3i(1, -2, 300))),
            new Case<>("DIRECTION", VanillaCodecs.DIRECTION, Direction.CODEC, Direction.STREAM_CODEC, List.of(Direction.values())),
            new Case<>("COMPOUND_TAG", VanillaCodecs.COMPOUND_TAG, CompoundTag.CODEC, ByteBufCodecs.COMPOUND_TAG, List.of(new CompoundTag(), ParityCases.compoundTag())),
            new Case<>(
                    "BLOCK_STATE",
                    VanillaCodecs.BLOCK_STATE,
                    BlockState.CODEC,
                    ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY),
                    List.of(
                            Blocks.STONE.defaultBlockState(),
                            Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X),
                            Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP)
                    )
            ),
            new Case<>(
                    "ITEM_STACK",
                    VanillaCodecs.ITEM_STACK.xmap(MatchedItem::new, MatchedItem::stack),
                    ItemStack.OPTIONAL_CODEC.xmap(MatchedItem::new, MatchedItem::stack),
                    ItemStack.OPTIONAL_STREAM_CODEC.map(MatchedItem::new, MatchedItem::stack),
                    ParityCases.itemStacks().stream().map(MatchedItem::new).toList()
            ),
            new Case<>(
                    "FLUID_STACK",
                    VanillaCodecs.FLUID_STACK.xmap(MatchedFluid::new, MatchedFluid::stack),
                    FluidStack.OPTIONAL_CODEC.xmap(MatchedFluid::new, MatchedFluid::stack),
                    FluidStack.OPTIONAL_STREAM_CODEC.map(MatchedFluid::new, MatchedFluid::stack),
                    ParityCases.fluidStacks().stream().map(MatchedFluid::new).toList()
            ),
            new Case<>(
                    "COMPONENT",
                    VanillaCodecs.COMPONENT,
                    ComponentSerialization.CODEC,
                    ComponentSerialization.STREAM_CODEC,
                    List.of(Component.literal("hello"), Component.translatable("block.minecraft.stone").withStyle(ChatFormatting.RED))
            ),
            new Case<>(
                    "resourceKey(ITEM)",
                    VanillaCodecs.resourceKey(Registries.ITEM),
                    ResourceKey.codec(Registries.ITEM),
                    ResourceKey.streamCodec(Registries.ITEM),
                    List.of(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("stone")), ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("nexus", "path/to/thing")))
            ),
            new Case<>(
                    "tagKey(ITEM)",
                    VanillaCodecs.tagKey(Registries.ITEM),
                    TagKey.hashedCodec(Registries.ITEM),
                    TagKey.streamCodec(Registries.ITEM),
                    List.of(ItemTags.LOGS, TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("nexus", "path/to/tag")))
            )
    );
    
    public static Identifier typeOf(Shape shape) {
        return switch (shape) {
            case Circle _ -> ParityCases.CIRCLE;
            case Square _ -> ParityCases.SQUARE;
        };
    }
    
    public static Amount positiveAmount(int value) {
        if (value <= 0) throw new NexusCodecException("expected a positive amount, found " + value);
        return new Amount(value);
    }
    
    public static CompoundTag compoundTag() {
        CompoundTag nested = new CompoundTag();
        nested.putBoolean("flag", true);
        ListTag list = new ListTag();
        list.add(StringTag.valueOf("a"));
        list.add(StringTag.valueOf("b"));
        CompoundTag tag = new CompoundTag();
        tag.putByte("byte", (byte) 1);
        tag.putShort("short", (short) 300);
        tag.putInt("int", 100_000);
        tag.putLong("long", 1L << 40);
        tag.putFloat("float", 1.5F);
        tag.putDouble("double", 0.1D);
        tag.putString("string", "stone");
        tag.putByteArray("bytes", new byte[]{ 1, 2 });
        tag.putIntArray("ints", new int[]{ 1, 2 });
        tag.putLongArray("longs", new long[]{ 1L, 2L });
        tag.put("list", list);
        tag.put("nested", nested);
        return tag;
    }
    
    public static void bindDefaultComponents() {
        if (BuiltInRegistries.ITEM.wrapAsHolder(Items.AIR).areComponentsBound()) {
            return;
        }
        
        CommonHooks.markComponentClassAsValid(HolderSet.emptyNamed(BuiltInRegistries.ITEM, ItemTags.LOGS).getClass());
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup()).forEach(DataComponentInitializers.PendingComponents::apply);
    }
    
    public static List<ItemStack> itemStacks() {
        ParityCases.bindDefaultComponents();
        return List.of(ItemStack.EMPTY, new ItemStack(Items.DIRT, 64), ParityCases.namedSword(), ParityCases.inedibleApple());
    }
    
    public static List<FluidStack> fluidStacks() {
        ParityCases.bindDefaultComponents();
        return List.of(FluidStack.EMPTY, new FluidStack(Fluids.WATER, 1000), ParityCases.namedLava());
    }
    
    public static ItemStack namedSword() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.DAMAGE, 5);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Edge"));
        return sword;
    }
    
    public static ItemStack inedibleApple() {
        ItemStack apple = new ItemStack(Items.APPLE);
        apple.remove(DataComponents.FOOD);
        return apple;
    }
    
    public static FluidStack namedLava() {
        FluidStack lava = new FluidStack(Fluids.LAVA, 250);
        lava.set(DataComponents.CUSTOM_NAME, Component.literal("Hot"));
        return lava;
    }
    
    private static Listing shortListing(String item) {
        return new Listing(item, 1, Optional.empty());
    }
    
    private static Optional<String> shortFormOf(Listing listing) {
        return listing.count() == 1 && listing.label().isEmpty() ? Optional.of(listing.item()) : Optional.empty();
    }
    
    private static Listing fromEither(Either<String, Listing> either) {
        return either.map(ParityCases::shortListing, Function.identity());
    }
    
    private static Either<String, Listing> toEither(Listing listing) {
        return ParityCases.shortFormOf(listing).<Either<String, Listing>>map(Either::left).orElseGet(() -> Either.right(listing));
    }
    
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
    
    public sealed interface Shape permits Circle, Square { }
    
    public record Circle(int radius) implements Shape { }
    
    public record Square(int side) implements Shape { }
    
    public record Named(String name, Shape shape) { }
    
    public record Node(int value, List<Node> children) { }
    
    public record Amount(int value) { }
    
    public record MatchedItem(ItemStack stack) {
        
        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof MatchedItem(ItemStack that) && ItemStack.matches(this.stack, that);
        }
        
        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(this.stack);
        }
    }
    
    public record MatchedFluid(FluidStack stack) {
        
        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof MatchedFluid(FluidStack that) && FluidStack.matches(this.stack, that);
        }
        
        @Override
        public int hashCode() {
            return FluidStack.hashFluidAndComponents(this.stack);
        }
    }
}
