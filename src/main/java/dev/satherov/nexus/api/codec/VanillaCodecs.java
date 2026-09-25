package dev.satherov.nexus.api.codec;

import lombok.experimental.UtilityClass;

import dev.satherov.nexus.internal.codec.Errors;
import dev.satherov.nexus.internal.codec.VanillaTypes;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

///
/// Utility for the codecs of vanilla and neoforge types, which write the same JSON, NBT, and bytes as the codecs and stream codecs of those types.
///
@UtilityClass
public class VanillaCodecs {
    
    ///
    /// The codec of a [BlockPos], in the same form as vanilla's [BlockPos#CODEC] and [BlockPos#STREAM_CODEC].
    ///
    /// In JSON and NBT, it writes the three coordinates as an int array.
    /// On the network, it writes the position packed into a `long`, the same as [BlockPos#asLong()].
    ///
    /// Fails if the array doesn't hold exactly three ints.
    ///
    public static final NexusCodec<BlockPos, Access.Plain> BLOCK_POS = VanillaTypes.split(
            "BLOCK_POS",
            VanillaTypes.ints(3).xmap(ints -> new BlockPos(ints[0], ints[1], ints[2]), pos -> new int[]{ pos.getX(), pos.getY(), pos.getZ() }),
            NexusCodec.LONG.xmap(BlockPos::of, BlockPos::asLong)
    );
    
    ///
    /// The codec of a [ChunkPos], in the same form as vanilla's [ChunkPos#CODEC] and [ChunkPos#STREAM_CODEC].
    ///
    /// In JSON and NBT, it writes the two coordinates as an int array.
    /// On the network, it writes the position packed into a `long`, the same as [ChunkPos#pack()].
    ///
    /// Fails if the array doesn't hold exactly two ints.
    ///
    public static final NexusCodec<ChunkPos, Access.Plain> CHUNK_POS = VanillaTypes.split(
            "CHUNK_POS",
            VanillaTypes.ints(2).xmap(ints -> new ChunkPos(ints[0], ints[1]), pos -> new int[]{ pos.x(), pos.z() }),
            NexusCodec.LONG.xmap(ChunkPos::unpack, ChunkPos::pack)
    );
    
    ///
    /// The codec of a [GlobalPos], in the same form as vanilla's [GlobalPos#CODEC] and [GlobalPos#STREAM_CODEC].
    ///
    /// In JSON and NBT, it writes a struct with the identifier of the dimension under `dimension` and the position under `pos`.
    /// On the network, it writes the identifier of the dimension and then the position.
    /// The position is written the same as [#BLOCK_POS].
    ///
    public static final StructCodec<GlobalPos, Access.Plain> GLOBAL_POS = NexusCodec.struct(
            "GLOBAL_POS",
            VanillaCodecs.resourceKey(Registries.DIMENSION).field("dimension", GlobalPos::dimension),
            VanillaCodecs.BLOCK_POS.field("pos", GlobalPos::pos),
            GlobalPos::of
    );
    
    ///
    /// The codec of a [Vec3], in the same form as vanilla's [Vec3#CODEC] and [Vec3#STREAM_CODEC].
    ///
    /// In JSON and NBT, it writes the three coordinates as a list of doubles.
    /// On the network, it writes the three coordinates as doubles.
    ///
    /// Fails if the list doesn't hold exactly three doubles.
    ///
    public static final NexusCodec<Vec3, Access.Plain> VEC3 = VanillaTypes.split(
            "VEC3",
            NexusCodec.DOUBLE
                    .list()
                    .validate(coordinates -> coordinates.size() == 3 ? null : "expected a list of 3 doubles, found " + coordinates.size())
                    .xmap(coordinates -> new Vec3(coordinates.get(0), coordinates.get(1), coordinates.get(2)), vec -> List.of(vec.x(), vec.y(), vec.z())),
            NexusCodec.struct(
                    "VEC3",
                    NexusCodec.DOUBLE.field("x", Vec3::x),
                    NexusCodec.DOUBLE.field("y", Vec3::y),
                    NexusCodec.DOUBLE.field("z", Vec3::z),
                    Vec3::new
            )
    );
    
    ///
    /// The codec of a [Vec3i], in the same form as vanilla's [Vec3i#CODEC] and [Vec3i#STREAM_CODEC].
    ///
    /// In JSON and NBT, it writes the three coordinates as an int array.
    /// On the network, it writes the three coordinates as VarInts.
    ///
    /// Fails if the array doesn't hold exactly three ints.
    ///
    public static final NexusCodec<Vec3i, Access.Plain> VEC3I = VanillaTypes.split(
            "VEC3I",
            VanillaTypes.ints(3).xmap(ints -> new Vec3i(ints[0], ints[1], ints[2]), vec -> new int[]{ vec.getX(), vec.getY(), vec.getZ() }),
            NexusCodec.struct(
                    "VEC3I",
                    NexusCodec.VAR_INT.field("x", Vec3i::getX),
                    NexusCodec.VAR_INT.field("y", Vec3i::getY),
                    NexusCodec.VAR_INT.field("z", Vec3i::getZ),
                    Vec3i::new
            )
    );
    
    ///
    /// The codec of a [Direction], in the same form as vanilla's [Direction#CODEC] and [Direction#STREAM_CODEC].
    ///
    /// In JSON and NBT, it writes the name of the direction in lower case, such as `north`.
    /// On the network, it writes the ordinal of the direction as a VarInt.
    ///
    /// Fails if a name or an ordinal belongs to none of the directions.
    ///
    public static final NexusCodec<Direction, Access.Plain> DIRECTION = NexusCodec.enumOf(Direction.class);
    
    ///
    /// The codec of a [CompoundTag], in the same form as vanilla's [CompoundTag#CODEC] and [ByteBufCodecs#COMPOUND_TAG].
    ///
    /// In JSON, it writes the tag as a json object.
    /// In NBT, it writes a copy of the tag.
    /// On the network, it writes the tag with its type, in vanilla's NBT bytes.
    ///
    /// In JSON, the types of the tags are lost, and a number is read back as the smallest type that holds it.
    ///
    /// Fails if the input is not an object, or on the network if the tag is larger than vanilla's default quota.
    ///
    public static final NexusCodec<CompoundTag, Access.Plain> COMPOUND_TAG = VanillaTypes.COMPOUND_TAG;
    
    ///
    /// The codec of a [BlockState], in the same form as vanilla's [BlockState#CODEC] and the stream codec of [Block#BLOCK_STATE_REGISTRY].
    ///
    /// In JSON and NBT, it writes the identifier of the block under `Name` and the value of every property under `Properties`.
    /// A block without properties has no `Properties`, and a property that `Properties` doesn't have reads as its value in the default state.
    /// On the network, it writes the id of the state in [Block#BLOCK_STATE_REGISTRY] as a VarInt.
    ///
    /// Fails if the block, the value of a property, or an id is unknown.
    /// A strict format also refuses a property that the block doesn't have.
    ///
    public static final NexusCodec<BlockState, Access.Plain> BLOCK_STATE = VanillaTypes.BLOCK_STATE;
    
    ///
    /// The codec of an [ItemStack] that may be empty, in the same form as vanilla's [ItemStack#OPTIONAL_CODEC] and [ItemStack#OPTIONAL_STREAM_CODEC].
    ///
    /// In JSON and NBT, it writes the identifier of the item under `id`, the count under `count`, and the patch of the components under `components`.
    /// The patch is left out if it's empty, and a missing `count` reads as `1`.
    /// On the network, it writes the count as a VarInt, and then the id of the item and the patch.
    ///
    /// Every component is written and read with the codec and the stream codec of its [DataComponentType], and a removed one is written under `!` and its identifier.
    /// An empty stack is an empty object in JSON and NBT, and a count of `0` on the network.
    ///
    /// Fails if the item is unknown or air, or in JSON and NBT if the count is not in `[1, 99]`.
    /// In JSON and NBT, a failure holds the errors of every field that failed, and a failure of a component is at its identifier inside `components`, such as `components['minecraft:damage']`.
    ///
    public static final StructCodec<ItemStack, Access.Registries> ITEM_STACK = VanillaTypes.ITEM_STACK;
    
    ///
    /// The codec of a [FluidStack] that may be empty, in the same form as neoforge's [FluidStack#OPTIONAL_CODEC] and [FluidStack#OPTIONAL_STREAM_CODEC].
    ///
    /// In JSON and NBT, it writes the identifier of the fluid under `id`, the amount under `amount`, and the patch of the components under `components`.
    /// The patch is left out if it's empty.
    /// On the network, it writes the amount as a VarInt, and then the id of the fluid and the patch.
    ///
    /// Every component is written and read with the codec and the stream codec of its [DataComponentType], and a removed one is written under `!` and its identifier.
    /// An empty stack is an empty object in JSON and NBT, and an amount of `0` on the network.
    ///
    /// Fails if the fluid is unknown or empty, or in JSON and NBT if the amount is missing or not positive.
    /// In JSON and NBT, a failure holds the errors of every field that failed, and a failure of a component is at its identifier inside `components`, such as `components['minecraft:custom_name']`.
    ///
    public static final StructCodec<FluidStack, Access.Registries> FLUID_STACK = VanillaTypes.FLUID_STACK;
    
    ///
    /// The codec of a text [Component], which runs vanilla's [ComponentSerialization#CODEC] and [ComponentSerialization#STREAM_CODEC].
    ///
    /// In JSON and NBT, it runs the codec over registry ops with the registries of the format.
    ///
    /// If vanilla's codec returns an error or the stream codec throws, it will fail with the message of that error or exception.
    ///
    public static final NexusCodec<Component, Access.Registries> COMPONENT = NexusCodec.ofVanilla(
            ComponentSerialization.CODEC,
            ComponentSerialization.STREAM_CODEC,
            Access.Registries.class
    );
    
    ///
    /// Creates the codec of a key of the given registry, in the same form as vanilla's [ResourceKey#codec(ResourceKey)] and [ResourceKey#streamCodec(ResourceKey)].
    ///
    /// It writes the identifier of the key, the same as [NexusCodec#IDENTIFIER].
    ///
    /// @param registry The key of the registry.
    ///
    /// @return The codec of the key.
    ///
    public static <T> NexusCodec<ResourceKey<T>, Access.Plain> resourceKey(ResourceKey<? extends Registry<T>> registry) {
        return NexusCodec.IDENTIFIER.xmap(id -> ResourceKey.create(registry, id), ResourceKey::identifier);
    }
    
    ///
    /// Creates the codec of a tag of the given registry, in the same form as vanilla's [TagKey#hashedCodec(ResourceKey)] and [TagKey#streamCodec(ResourceKey)].
    ///
    /// In JSON and NBT, it writes `#` and the identifier of the tag, such as `#minecraft:logs`.
    /// On the network, it writes the identifier of the tag without the `#`, the same as [NexusCodec#IDENTIFIER].
    ///
    /// Fails if the string doesn't start with `#` or the rest of it is not a valid identifier.
    ///
    /// @param registry The key of the registry.
    ///
    /// @return The codec of the tag.
    ///
    public static <T> NexusCodec<TagKey<T>, Access.Plain> tagKey(ResourceKey<? extends Registry<T>> registry) {
        return VanillaTypes.split(
                "tagKey",
                NexusCodec.STRING.flatXmap(text -> VanillaCodecs.tagOf(registry, text), tag -> "#" + tag.location()),
                NexusCodec.IDENTIFIER.xmap(id -> TagKey.create(registry, id), TagKey::location)
        );
    }
    
    ///
    /// Gets the tag of the given registry that the given string holds after its `#`.
    ///
    private static <T> TagKey<T> tagOf(ResourceKey<? extends Registry<T>> registry, String text) {
        Identifier id = text.startsWith("#") ? Identifier.tryParse(text.substring(1)) : null;
        if (id == null) throw Errors.mismatch("'#' and an identifier", text);
        return TagKey.create(registry, id);
    }
}
