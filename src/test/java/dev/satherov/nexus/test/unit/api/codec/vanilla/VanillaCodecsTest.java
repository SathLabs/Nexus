package dev.satherov.nexus.test.unit.api.codec.vanilla;

import dev.satherov.nexus.Nexus;
import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.vanilla.VanillaCodecs;
import dev.satherov.nexus.test.unit.api.codec.ParityCases;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

///
/// Checks that the vanilla codecs write what the codecs and stream codecs of vanilla write, and how an item stack writes its components.
///
public class VanillaCodecsTest {
    
    private static final RegistryAccess.Frozen REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final CodecFormat<JsonElement, Access.Registries> JSON = CodecFormat.JSON.withRegistries(VanillaCodecsTest.REGISTRIES);
    private static final CodecFormat<Tag, Access.Registries> NBT = CodecFormat.NBT.withRegistries(VanillaCodecsTest.REGISTRIES);
    private static final RegistryOps<JsonElement> JSON_OPS = RegistryOps.create(JsonOps.INSTANCE, VanillaCodecsTest.REGISTRIES);
    private static final RegistryOps<Tag> NBT_OPS = RegistryOps.create(NbtOps.INSTANCE, VanillaCodecsTest.REGISTRIES);
    private static final NexusCodec<ParityCases.MatchedItem, Access.Registries> ITEMS = VanillaCodecs.ITEM_STACK.xmap(ParityCases.MatchedItem::new, ParityCases.MatchedItem::stack);
    private static final NexusCodec<ParityCases.MatchedFluid, Access.Registries> FLUIDS = VanillaCodecs.FLUID_STACK.xmap(ParityCases.MatchedFluid::new, ParityCases.MatchedFluid::stack);
    
    @BeforeAll
    public static void bindDefaultComponents() {
        ParityCases.bindDefaultComponents();
    }
    
    @Test
    public void blockPosIsAnIntArrayAndAPackedLong() {
        BlockPos pos = new BlockPos(1, 2, 3);
        
        Assertions.assertThat(VanillaCodecs.BLOCK_POS.encode(VanillaCodecsTest.JSON, pos)).hasToString("[1,2,3]");
        Assertions.assertThat(VanillaCodecs.BLOCK_POS.encode(VanillaCodecsTest.NBT, pos)).isEqualTo(new IntArrayTag(new int[]{ 1, 2, 3 }));
        Assertions.assertThat(VanillaCodecsTest.written(VanillaCodecs.BLOCK_POS, pos)).isEqualTo(VanillaCodecsTest.bytes(0x00, 0x00, 0x00, 0x40, 0x00, 0x00, 0x30, 0x02));
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.BLOCK_POS, BlockPos.CODEC, BlockPos.STREAM_CODEC, pos);
        VanillaCodecsTest.assertRoundTrips(VanillaCodecs.BLOCK_POS, pos);
    }
    
    @Test
    public void chunkPosIsAnIntArrayAndAPackedLong() {
        ChunkPos pos = new ChunkPos(1, 2);
        
        Assertions.assertThat(VanillaCodecs.CHUNK_POS.encode(VanillaCodecsTest.JSON, pos)).hasToString("[1,2]");
        Assertions.assertThat(VanillaCodecs.CHUNK_POS.encode(VanillaCodecsTest.NBT, pos)).isEqualTo(new IntArrayTag(new int[]{ 1, 2 }));
        Assertions.assertThat(VanillaCodecsTest.written(VanillaCodecs.CHUNK_POS, pos)).isEqualTo(VanillaCodecsTest.bytes(0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x01));
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.CHUNK_POS, ChunkPos.CODEC, ChunkPos.STREAM_CODEC, pos);
        VanillaCodecsTest.assertRoundTrips(VanillaCodecs.CHUNK_POS, pos);
    }
    
    @Test
    public void globalPosIsTheDimensionAndThePos() {
        GlobalPos pos = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 2, 3));
        
        Assertions.assertThat(VanillaCodecs.GLOBAL_POS.encode(VanillaCodecsTest.JSON, pos)).hasToString("{\"dimension\":\"minecraft:overworld\",\"pos\":[1,2,3]}");
        Assertions.assertThat(VanillaCodecsTest.written(VanillaCodecs.GLOBAL_POS, pos))
                .isEqualTo(VanillaCodecsTest.bytes(0x13, "minecraft:overworld", 0x00, 0x00, 0x00, 0x40, 0x00, 0x00, 0x30, 0x02));
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.GLOBAL_POS, GlobalPos.CODEC, GlobalPos.STREAM_CODEC, pos);
        VanillaCodecsTest.assertRoundTrips(VanillaCodecs.GLOBAL_POS, pos);
    }
    
    @Test
    public void vec3IsAListOfDoublesAndThreeDoubles() {
        Vec3 vec = new Vec3(1.5D, -2.0D, 0.25D);
        
        Assertions.assertThat(VanillaCodecs.VEC3.encode(VanillaCodecsTest.JSON, vec)).hasToString("[1.5,-2.0,0.25]");
        Assertions.assertThat(VanillaCodecsTest.written(VanillaCodecs.VEC3, vec)).isEqualTo(VanillaCodecsTest.bytes(
                0x3F, 0xF8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xC0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x3F, 0xD0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        ));
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.VEC3, Vec3.CODEC, Vec3.STREAM_CODEC, vec);
        VanillaCodecsTest.assertRoundTrips(VanillaCodecs.VEC3, vec);
    }
    
    @Test
    public void vec3iIsAnIntArrayAndThreeVarInts() {
        Vec3i vec = new Vec3i(1, 2, 300);
        
        Assertions.assertThat(VanillaCodecs.VEC3I.encode(VanillaCodecsTest.JSON, vec)).hasToString("[1,2,300]");
        Assertions.assertThat(VanillaCodecs.VEC3I.encode(VanillaCodecsTest.NBT, vec)).isEqualTo(new IntArrayTag(new int[]{ 1, 2, 300 }));
        Assertions.assertThat(VanillaCodecsTest.written(VanillaCodecs.VEC3I, vec)).isEqualTo(VanillaCodecsTest.bytes(0x01, 0x02, 0xAC, 0x02));
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.VEC3I, Vec3i.CODEC, Vec3i.STREAM_CODEC, vec);
        VanillaCodecsTest.assertRoundTrips(VanillaCodecs.VEC3I, vec);
    }
    
    @Test
    public void directionIsItsNameAndItsOrdinal() {
        Assertions.assertThat(VanillaCodecs.DIRECTION.encode(VanillaCodecsTest.JSON, Direction.NORTH)).hasToString("\"north\"");
        Assertions.assertThat(VanillaCodecsTest.written(VanillaCodecs.DIRECTION, Direction.NORTH)).isEqualTo(VanillaCodecsTest.bytes(0x02));
        for (Direction direction : Direction.values()) {
            VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.DIRECTION, Direction.CODEC, Direction.STREAM_CODEC, direction);
            VanillaCodecsTest.assertRoundTrips(VanillaCodecs.DIRECTION, direction);
        }
    }
    
    @Test
    public void compoundTagIsAJsonObjectACopyAndTheTagBytes() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("a", (byte) 1);
        
        Assertions.assertThat(VanillaCodecs.COMPOUND_TAG.encode(VanillaCodecsTest.JSON, tag)).hasToString("{\"a\":1}");
        Assertions.assertThat(VanillaCodecs.COMPOUND_TAG.encode(VanillaCodecsTest.NBT, tag)).isEqualTo(tag).isNotSameAs(tag);
        Assertions.assertThat(VanillaCodecsTest.written(VanillaCodecs.COMPOUND_TAG, tag)).isEqualTo(VanillaCodecsTest.bytes(0x0A, 0x01, 0x00, 0x01, "a", 0x01, 0x00));
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.COMPOUND_TAG, CompoundTag.CODEC, ByteBufCodecs.COMPOUND_TAG, tag);
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.COMPOUND_TAG, CompoundTag.CODEC, ByteBufCodecs.COMPOUND_TAG, ParityCases.compoundTag());
        VanillaCodecsTest.assertRoundTrips(VanillaCodecs.COMPOUND_TAG, tag);
    }
    
    @Test
    public void blockStateIsItsNameAndPropertiesAndItsId() {
        BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP);
        StreamCodec<? super RegistryFriendlyByteBuf, BlockState> stream = ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY);
        
        Assertions.assertThat(VanillaCodecs.BLOCK_STATE.encode(VanillaCodecsTest.JSON, log)).hasToString("{\"Properties\":{\"axis\":\"x\"},\"Name\":\"minecraft:oak_log\"}");
        Assertions.assertThat(VanillaCodecs.BLOCK_STATE.encode(VanillaCodecsTest.JSON, Blocks.STONE.defaultBlockState())).hasToString("{\"Name\":\"minecraft:stone\"}");
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.BLOCK_STATE, BlockState.CODEC, stream, log);
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.BLOCK_STATE, BlockState.CODEC, stream, stairs);
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.BLOCK_STATE, BlockState.CODEC, stream, Blocks.STONE.defaultBlockState());
        VanillaCodecsTest.assertRoundTrips(VanillaCodecs.BLOCK_STATE, log);
        VanillaCodecsTest.assertRoundTrips(VanillaCodecs.BLOCK_STATE, stairs);
    }
    
    @Test
    public void blockStateReportsEveryErrorOnAStrictFormat() {
        CodecFormat<JsonElement, Access.Registries> strict = VanillaCodecsTest.JSON.strict();
        JsonElement badValueAndProperty = JsonParser.parseString("{\"Name\":\"minecraft:oak_stairs\",\"Properties\":{\"facing\":\"up\",\"bogus\":\"x\"}}");
        JsonElement badValueAndKey = JsonParser.parseString("{\"Name\":\"minecraft:oak_stairs\",\"Properties\":{\"facing\":\"up\"},\"extra\":1}");
        JsonElement badBlockAndKey = JsonParser.parseString("{\"Name\":\"minecraft:nope\",\"extra\":1}");
        
        Assertions.assertThat(VanillaCodecsTest.errors(() -> VanillaCodecs.BLOCK_STATE.decode(strict, badValueAndProperty)))
                .extracting(CodecError::path)
                .containsExactly("Properties.facing", "Properties.bogus");
        Assertions.assertThat(VanillaCodecsTest.errors(() -> VanillaCodecs.BLOCK_STATE.decode(strict, badValueAndKey)))
                .extracting(CodecError::path)
                .containsExactly("Properties.facing", "extra");
        Assertions.assertThat(VanillaCodecsTest.errors(() -> VanillaCodecs.BLOCK_STATE.decode(strict, badBlockAndKey)))
                .extracting(CodecError::path)
                .containsExactly("Name", "extra");
    }
    
    @Test
    public void fluidStackIsItsFluidAndAmount() {
        FluidStack water = new FluidStack(Fluids.WATER, 1000);
        
        Assertions.assertThat(VanillaCodecs.FLUID_STACK.encode(VanillaCodecsTest.JSON, water)).hasToString("{\"id\":\"minecraft:water\",\"amount\":1000}");
        Assertions.assertThat(VanillaCodecs.FLUID_STACK.encode(VanillaCodecsTest.JSON, FluidStack.EMPTY)).hasToString("{}");
        Assertions.assertThat(VanillaCodecsTest.written(VanillaCodecs.FLUID_STACK, FluidStack.EMPTY)).isEqualTo(VanillaCodecsTest.bytes(0x00));
        for (FluidStack stack : List.of(water, ParityCases.namedLava(), FluidStack.EMPTY)) {
            VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.FLUID_STACK, FluidStack.OPTIONAL_CODEC, FluidStack.OPTIONAL_STREAM_CODEC, stack);
            VanillaCodecsTest.assertRoundTrips(VanillaCodecsTest.FLUIDS, new ParityCases.MatchedFluid(stack));
        }
    }
    
    @Test
    public void componentIsVanillasText() {
        Component text = Component.literal("hello");
        
        Assertions.assertThat(VanillaCodecs.COMPONENT.encode(VanillaCodecsTest.JSON, text)).hasToString("\"hello\"");
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.COMPONENT, ComponentSerialization.CODEC, ComponentSerialization.STREAM_CODEC, text);
        VanillaCodecsTest.assertRoundTrips(VanillaCodecs.COMPONENT, text);
    }
    
    @Test
    public void resourceKeyIsItsIdentifier() {
        ResourceKey<Item> stone = ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("stone"));
        NexusCodec<ResourceKey<Item>, Access.Plain> codec = VanillaCodecs.resourceKey(Registries.ITEM);
        
        Assertions.assertThat(codec.encode(VanillaCodecsTest.JSON, stone)).hasToString("\"minecraft:stone\"");
        Assertions.assertThat(VanillaCodecsTest.written(codec, stone)).isEqualTo(VanillaCodecsTest.bytes(0x0F, "minecraft:stone"));
        VanillaCodecsTest.assertMatchesVanilla(codec, ResourceKey.codec(Registries.ITEM), ResourceKey.streamCodec(Registries.ITEM), stone);
        VanillaCodecsTest.assertRoundTrips(codec, stone);
    }
    
    @Test
    public void tagKeyIsItsIdentifierAfterAHashAndItsIdentifierOnNetty() {
        NexusCodec<TagKey<Item>, Access.Plain> codec = VanillaCodecs.tagKey(Registries.ITEM);
        
        Assertions.assertThat(codec.encode(VanillaCodecsTest.JSON, ItemTags.LOGS)).hasToString("\"#minecraft:logs\"");
        Assertions.assertThat(VanillaCodecsTest.written(codec, ItemTags.LOGS)).isEqualTo(VanillaCodecsTest.bytes(0x0E, "minecraft:logs"));
        Assertions.assertThatThrownBy(() -> codec.decode(VanillaCodecsTest.JSON, JsonParser.parseString("\"minecraft:logs\""))).isInstanceOf(NexusCodecException.class);
        VanillaCodecsTest.assertMatchesVanilla(codec, TagKey.hashedCodec(Registries.ITEM), TagKey.streamCodec(Registries.ITEM), ItemTags.LOGS);
        VanillaCodecsTest.assertRoundTrips(codec, ItemTags.LOGS);
    }
    
    @Test
    public void itemStackWritesVanillaComponentsWithTheCodecsOfTheirTypes() {
        ItemStack sword = ParityCases.namedSword();
        
        Assertions.assertThat(VanillaCodecs.ITEM_STACK.encode(VanillaCodecsTest.JSON, sword))
                .hasToString("{\"id\":\"minecraft:diamond_sword\",\"count\":1,\"components\":{\"minecraft:damage\":5,\"minecraft:custom_name\":\"Edge\"}}");
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.ITEM_STACK, ItemStack.OPTIONAL_CODEC, ItemStack.OPTIONAL_STREAM_CODEC, sword);
        VanillaCodecsTest.assertRoundTrips(VanillaCodecsTest.ITEMS, new ParityCases.MatchedItem(sword));
    }
    
    @Test
    public void itemStackWritesComponentsOfTypesBuiltFromNexusCodecs() {
        ItemStack stone = new ItemStack(Items.STONE);
        stone.set(Labels.TYPE, new Label("shiny", 3));
        
        Assertions.assertThat(VanillaCodecs.ITEM_STACK.encode(VanillaCodecsTest.JSON, stone))
                .hasToString("{\"id\":\"minecraft:stone\",\"count\":1,\"components\":{\"nexus:test_label\":{\"text\":\"shiny\",\"weight\":3}}}");
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.ITEM_STACK, ItemStack.OPTIONAL_CODEC, ItemStack.OPTIONAL_STREAM_CODEC, stone);
        VanillaCodecsTest.assertRoundTrips(VanillaCodecsTest.ITEMS, new ParityCases.MatchedItem(stone));
    }
    
    @Test
    public void emptyItemStackIsAnEmptyObjectAndACountOfZero() {
        Assertions.assertThat(VanillaCodecs.ITEM_STACK.encode(VanillaCodecsTest.JSON, ItemStack.EMPTY)).hasToString("{}");
        Assertions.assertThat(VanillaCodecs.ITEM_STACK.encode(VanillaCodecsTest.NBT, ItemStack.EMPTY)).isEqualTo(new CompoundTag());
        Assertions.assertThat(VanillaCodecsTest.written(VanillaCodecs.ITEM_STACK, ItemStack.EMPTY)).isEqualTo(VanillaCodecsTest.bytes(0x00));
        Assertions.assertThat(VanillaCodecs.ITEM_STACK.decode(VanillaCodecsTest.JSON, new JsonObject()).isEmpty()).isTrue();
        Assertions.assertThat(VanillaCodecs.ITEM_STACK.decode(VanillaCodecsTest.NBT, new CompoundTag()).isEmpty()).isTrue();
        Assertions.assertThat(VanillaCodecs.ITEM_STACK.decode(CodecFormat.netty(VanillaCodecsTest.readable(0x00))).isEmpty()).isTrue();
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.ITEM_STACK, ItemStack.OPTIONAL_CODEC, ItemStack.OPTIONAL_STREAM_CODEC, ItemStack.EMPTY);
    }
    
    @Test
    public void removedComponentIsWrittenUnderItsIdentifierAfterAnExclamationMark() {
        ItemStack apple = ParityCases.inedibleApple();
        
        Assertions.assertThat(VanillaCodecs.ITEM_STACK.encode(VanillaCodecsTest.JSON, apple))
                .hasToString("{\"id\":\"minecraft:apple\",\"count\":1,\"components\":{\"!minecraft:food\":{}}}");
        VanillaCodecsTest.assertMatchesVanilla(VanillaCodecs.ITEM_STACK, ItemStack.OPTIONAL_CODEC, ItemStack.OPTIONAL_STREAM_CODEC, apple);
        VanillaCodecsTest.assertRoundTrips(VanillaCodecsTest.ITEMS, new ParityCases.MatchedItem(apple));
    }
    
    @Test
    public void itemStackFailsAtCountOnABadCount() {
        for (String count : List.of("100", "0", "\"many\"")) {
            JsonElement input = JsonParser.parseString("{\"id\":\"minecraft:stone\",\"count\":" + count + "}");
            
            Assertions.assertThat(VanillaCodecsTest.errors(() -> VanillaCodecs.ITEM_STACK.decode(VanillaCodecsTest.JSON, input))).extracting(CodecError::path).containsExactly("count");
        }
        
        Assertions.assertThat(VanillaCodecsTest.errors(() -> VanillaCodecs.ITEM_STACK.encode(VanillaCodecsTest.NBT, new ItemStack(Items.STONE, 100))))
                .extracting(CodecError::path)
                .containsExactly("count");
    }
    
    @Test
    public void itemStackFailsAtThePathOfABadComponentValue() {
        JsonElement input = JsonParser.parseString("{\"id\":\"minecraft:diamond_sword\",\"count\":1,\"components\":{\"minecraft:damage\":-1}}");
        
        Assertions.assertThat(VanillaCodecsTest.errors(() -> VanillaCodecs.ITEM_STACK.decode(VanillaCodecsTest.JSON, input)))
                .extracting(CodecError::path)
                .containsExactly("components['minecraft:damage']");
    }
    
    @Test
    public void itemStackReportsABadCountAndABadComponentAtOnce() {
        JsonElement input = JsonParser.parseString("{\"id\":\"minecraft:diamond_sword\",\"count\":100,\"components\":{\"minecraft:damage\":-1}}");
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD, 100);
        sword.set(DataComponents.DAMAGE, -1);
        
        Assertions.assertThat(VanillaCodecsTest.errors(() -> VanillaCodecs.ITEM_STACK.decode(VanillaCodecsTest.JSON, input)))
                .extracting(CodecError::path)
                .containsExactly("count", "components['minecraft:damage']");
        Assertions.assertThat(VanillaCodecsTest.errors(() -> VanillaCodecs.ITEM_STACK.encode(VanillaCodecsTest.NBT, sword)))
                .extracting(CodecError::path)
                .containsExactly("count", "components['minecraft:damage']");
    }
    
    @Test
    public void itemStackReportsAnUnregisteredComponentTypeAndKeepsGoing() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(NexusCodec.INT.asDataComponentType(), 1);
        sword.set(DataComponents.DAMAGE, -1);
        
        Assertions.assertThat(VanillaCodecsTest.errors(() -> VanillaCodecs.ITEM_STACK.encode(VanillaCodecsTest.JSON, sword)))
                .extracting(CodecError::path)
                .containsExactly("components", "components['minecraft:damage']");
    }
    
    private static <T> void assertMatchesVanilla(NexusCodec<T, ? super Access.Registries> codec, Codec<T> dfu, StreamCodec<? super RegistryFriendlyByteBuf, T> stream, T value) {
        RegistryFriendlyByteBuf vanilla = VanillaCodecsTest.writable();
        stream.encode(vanilla, value);
        
        Assertions.assertThat(codec.encode(VanillaCodecsTest.JSON, value)).hasToString(dfu.encodeStart(VanillaCodecsTest.JSON_OPS, value).getOrThrow().toString());
        Assertions.assertThat(codec.encode(VanillaCodecsTest.NBT, value)).isEqualTo(dfu.encodeStart(VanillaCodecsTest.NBT_OPS, value).getOrThrow());
        Assertions.assertThat(VanillaCodecsTest.written(codec, value)).isEqualTo(ByteBufUtil.getBytes(vanilla));
    }
    
    private static <T> void assertRoundTrips(NexusCodec<T, ? super Access.Registries> codec, T value) {
        RegistryFriendlyByteBuf buffer = VanillaCodecsTest.writable();
        codec.encode(CodecFormat.netty(buffer), value);
        
        Assertions.assertThat(codec.decode(VanillaCodecsTest.JSON, codec.encode(VanillaCodecsTest.JSON, value))).isEqualTo(value);
        Assertions.assertThat(codec.decode(VanillaCodecsTest.NBT, codec.encode(VanillaCodecsTest.NBT, value))).isEqualTo(value);
        Assertions.assertThat(codec.decode(CodecFormat.netty(buffer))).isEqualTo(value);
        Assertions.assertThat(buffer.readableBytes()).isZero();
    }
    
    private static <T> byte[] written(NexusCodec<T, ? super Access.Registries> codec, T value) {
        RegistryFriendlyByteBuf buffer = VanillaCodecsTest.writable();
        codec.encode(CodecFormat.netty(buffer), value);
        return ByteBufUtil.getBytes(buffer);
    }
    
    private static List<CodecError> errors(ThrowableAssert.ThrowingCallable call) {
        return Assertions.catchThrowableOfType(NexusCodecException.class, call).errors();
    }
    
    private static RegistryFriendlyByteBuf writable() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), VanillaCodecsTest.REGISTRIES);
    }
    
    private static RegistryFriendlyByteBuf readable(Object... bytes) {
        return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(VanillaCodecsTest.bytes(bytes)), VanillaCodecsTest.REGISTRIES);
    }
    
    private static byte[] bytes(Object... parts) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (Object part : parts) {
            switch (part) {
                case String text -> bytes.writeBytes(text.getBytes(StandardCharsets.UTF_8));
                case Integer value -> bytes.write(value);
                default -> throw new IllegalArgumentException("Could not write '" + part + "' as bytes");
            }
        }
        
        return bytes.toByteArray();
    }
    
    public record Label(String text, int weight) { }
    
    @EventBusSubscriber(modid = Nexus.MOD_ID)
    public static final class Labels {
        
        public static final DataComponentType<Label> TYPE = NexusCodec.struct(
                "label",
                NexusCodec.STRING.field("text", Label::text),
                NexusCodec.INT.field("weight", Label::weight),
                Label::new
        ).asDataComponentType();
        
        @SubscribeEvent
        public static void onRegister(RegisterEvent event) {
            event.register(Registries.DATA_COMPONENT_TYPE, Identifier.fromNamespaceAndPath(Nexus.MOD_ID, "test_label"), () -> Labels.TYPE);
        }
    }
}
