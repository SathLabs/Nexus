package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.Access;
import dev.satherov.nexus.api.codec.NexusCodec;
import dev.satherov.nexus.api.codec.format.CodecFormat;
import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.api.codec.struct.StructCodec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

///
/// Checks how either, one or many, the short form, dispatch, recursion, and the mappings write and read their values, and how they report their errors.
///
public class CombinatorsTest {
    
    private static final NexusCodec<Either<Integer, String>, Access.Plain> NUMBER_OR_NAME = NexusCodec.either(NexusCodec.INT, NexusCodec.STRING);
    
    private static final NexusCodec<List<String>, Access.Plain> NAMES = NexusCodec.STRING.oneOrMany();
    
    private static final NexusCodec<Item, Access.Plain> ITEM = NexusCodec.struct(
            "item",
            NexusCodec.STRING.field("id", Item::id),
            NexusCodec.INT.optionalField("count", 1, Item::count),
            Item::new
    ).orShort(NexusCodec.STRING, id -> new Item(id, 1), item -> item.count() == 1 ? Optional.of(item.id()) : Optional.empty());
    
    private static final StructCodec<ParityCases.Circle, Access.Plain> CIRCLE = NexusCodec.struct(
            "circle",
            NexusCodec.INT.field("radius", ParityCases.Circle::radius),
            ParityCases.Circle::new
    );
    
    private static final StructCodec<ParityCases.Square, Access.Plain> SQUARE = NexusCodec.struct(
            "square",
            NexusCodec.INT.field("side", ParityCases.Square::side),
            ParityCases.Square::new
    );
    
    private static final StructCodec<ParityCases.Shape, Access.Plain> SHAPE = NexusCodec.dispatch(
            "type",
            ParityCases::typeOf,
            Map.of(ParityCases.CIRCLE, CombinatorsTest.CIRCLE, ParityCases.SQUARE, CombinatorsTest.SQUARE)
    );
    
    private static final StructCodec<ParityCases.Named, Access.Plain> NAMED = NexusCodec.struct(
            "named",
            NexusCodec.STRING.field("name", ParityCases.Named::name),
            CombinatorsTest.SHAPE.field("shape", ParityCases.Named::shape),
            ParityCases.Named::new
    );
    
    private static final StructCodec<ParityCases.Named, Access.Plain> INLINE_NAMED = NexusCodec.struct(
            "named",
            NexusCodec.STRING.field("name", ParityCases.Named::name),
            CombinatorsTest.SHAPE.inline(ParityCases.Named::shape),
            ParityCases.Named::new
    );
    
    private static final NexusCodec<ParityCases.Node, Access.Plain> TREE = NexusCodec.recursive(
            "node",
            self -> NexusCodec.struct(
                    "branch",
                    NexusCodec.INT.field("value", ParityCases.Node::value),
                    self.list().field("children", ParityCases.Node::children),
                    ParityCases.Node::new
            )
    );
    
    private static final ParityCases.Node DEPTH_THREE = new ParityCases.Node(
            1,
            List.of(new ParityCases.Node(2, List.of()), new ParityCases.Node(3, List.of(new ParityCases.Node(4, List.of()))))
    );
    
    private static final NexusCodec<ParityCases.Amount, Access.Plain> POSITIVE = NexusCodec.INT.flatXmap(ParityCases::positiveAmount, ParityCases.Amount::value);
    
    private static final NexusCodec<Integer, Access.Plain> NON_NEGATIVE = NexusCodec.INT.validate(value -> value < 0 ? "negative" : null);
    
    @Test
    public void eitherWritesTheSideItHolds() {
        CombinatorsTest.assertJson(CombinatorsTest.NUMBER_OR_NAME, Either.left(5), "5");
        CombinatorsTest.assertJson(CombinatorsTest.NUMBER_OR_NAME, Either.right("five"), "\"five\"");
        CombinatorsTest.assertNbt(CombinatorsTest.NUMBER_OR_NAME, Either.left(5), IntTag.valueOf(5));
        CombinatorsTest.assertNbt(CombinatorsTest.NUMBER_OR_NAME, Either.right("five"), StringTag.valueOf("five"));
    }
    
    @Test
    public void eitherWritesTheSideAndThenTheValueOnNetty() {
        CombinatorsTest.assertNetty(CombinatorsTest.NUMBER_OR_NAME, Either.left(5), 0x01, 0x00, 0x00, 0x00, 0x05);
        CombinatorsTest.assertNetty(CombinatorsTest.NUMBER_OR_NAME, Either.right("five"), 0x00, 0x04, 'f', 'i', 'v', 'e');
    }
    
    @Test
    public void eitherReadsTheLeftCodecAndThenTheRight() {
        NexusCodec<Either<Integer, Long>, Access.Plain> numbers = NexusCodec.either(NexusCodec.INT, NexusCodec.LONG);
        
        Assertions.assertThat(numbers.decode(CodecFormat.JSON, JsonParser.parseString("5"))).isEqualTo(Either.left(5));
        Assertions.assertThat(numbers.decode(CodecFormat.JSON, JsonParser.parseString("5000000000"))).isEqualTo(Either.right(5_000_000_000L));
    }
    
    @Test
    public void eitherHoldsTheErrorsOfBothSidesIfNeitherCanRead() {
        JsonElement json = JsonParser.parseString("true");
        CompoundTag nbt = new CompoundTag();
        
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.NUMBER_OR_NAME.decode(CodecFormat.JSON, json))).containsExactly(
                CombinatorsTest.errors(() -> NexusCodec.INT.decode(CodecFormat.JSON, json)).getFirst(),
                CombinatorsTest.errors(() -> NexusCodec.STRING.decode(CodecFormat.JSON, json)).getFirst()
        );
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.NUMBER_OR_NAME.decode(CodecFormat.NBT, nbt))).containsExactly(
                CombinatorsTest.errors(() -> NexusCodec.INT.decode(CodecFormat.NBT, nbt)).getFirst(),
                CombinatorsTest.errors(() -> NexusCodec.STRING.decode(CodecFormat.NBT, nbt)).getFirst()
        );
    }
    
    @Test
    public void oneOrManyWritesASingleElementBare() {
        CombinatorsTest.assertJson(CombinatorsTest.NAMES, List.of("stone"), "\"stone\"");
        CombinatorsTest.assertNbt(CombinatorsTest.NAMES, List.of("stone"), StringTag.valueOf("stone"));
    }
    
    @Test
    public void oneOrManyWritesAnyOtherNumberOfElementsAsAList() {
        CombinatorsTest.assertJson(CombinatorsTest.NAMES, List.of("stone", "dirt"), "[\"stone\",\"dirt\"]");
        CombinatorsTest.assertJson(CombinatorsTest.NAMES, List.of(), "[]");
        CombinatorsTest.assertNbt(CombinatorsTest.NAMES, List.of("stone", "dirt"), CombinatorsTest.list(StringTag.valueOf("stone"), StringTag.valueOf("dirt")));
        CombinatorsTest.assertNbt(CombinatorsTest.NAMES, List.of(), new ListTag());
    }
    
    @Test
    public void oneOrManyReadsAListOfOne() {
        Assertions.assertThat(CombinatorsTest.NAMES.decode(CodecFormat.JSON, JsonParser.parseString("[\"stone\"]"))).containsExactly("stone");
        Assertions.assertThat(CombinatorsTest.NAMES.decode(CodecFormat.NBT, CombinatorsTest.list(StringTag.valueOf("stone")))).containsExactly("stone");
    }
    
    @Test
    public void oneOrManyAlwaysWritesTheListOnNetty() {
        CombinatorsTest.assertNetty(CombinatorsTest.NAMES, List.of("stone"), 0x01, 0x05, 's', 't', 'o', 'n', 'e');
        CombinatorsTest.assertNetty(CombinatorsTest.NAMES, List.of("a", "b"), 0x02, 0x01, 'a', 0x01, 'b');
    }
    
    @Test
    public void oneOrManyHoldsTheErrorsOfBothFormsIfNeitherCanRead() {
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.NAMES.decode(CodecFormat.JSON, JsonParser.parseString("5"))))
                .hasSize(2)
                .allSatisfy(error -> Assertions.assertThat(error.path()).isEmpty());
    }
    
    @Test
    public void orShortWritesTheShortFormIfThereIsOne() {
        CombinatorsTest.assertJson(CombinatorsTest.ITEM, new Item("stone", 1), "\"stone\"");
        CombinatorsTest.assertNbt(CombinatorsTest.ITEM, new Item("stone", 1), StringTag.valueOf("stone"));
    }
    
    @Test
    public void orShortWritesTheStructIfThereIsNoShortForm() {
        CombinatorsTest.assertJson(CombinatorsTest.ITEM, new Item("stone", 3), "{\"id\":\"stone\",\"count\":3}");
        CombinatorsTest.assertNbt(CombinatorsTest.ITEM, new Item("stone", 3), CombinatorsTest.compound(Map.of("id", StringTag.valueOf("stone"), "count", IntTag.valueOf(3))));
    }
    
    @Test
    public void orShortReadsTheStructOfAValueThatHasAShortForm() {
        Assertions.assertThat(CombinatorsTest.ITEM.decode(CodecFormat.JSON, JsonParser.parseString("{\"id\":\"stone\"}"))).isEqualTo(new Item("stone", 1));
    }
    
    @Test
    public void orShortWritesTheSideAndThenTheValueOnNetty() {
        CombinatorsTest.assertNetty(CombinatorsTest.ITEM, new Item("stone", 1), 0x01, 0x05, 's', 't', 'o', 'n', 'e');
        CombinatorsTest.assertNetty(CombinatorsTest.ITEM, new Item("stone", 3), 0x00, 0x05, 's', 't', 'o', 'n', 'e', 0x00, 0x00, 0x00, 0x03);
    }
    
    @Test
    public void dispatchWritesTheFieldsOfTheSubtypeBesideTheKey() {
        CombinatorsTest.assertJson(CombinatorsTest.SHAPE, new ParityCases.Circle(2), "{\"radius\":2,\"type\":\"nexus:circle\"}");
        CombinatorsTest.assertJson(CombinatorsTest.SHAPE, new ParityCases.Square(4), "{\"side\":4,\"type\":\"nexus:square\"}");
        CombinatorsTest.assertNbt(
                CombinatorsTest.SHAPE,
                new ParityCases.Circle(2),
                CombinatorsTest.compound(Map.of("radius", IntTag.valueOf(2), "type", StringTag.valueOf("nexus:circle")))
        );
    }
    
    @Test
    public void dispatchReadsTheKeyAtAnyPosition() {
        Assertions.assertThat(CombinatorsTest.SHAPE.decode(CodecFormat.JSON, JsonParser.parseString("{\"type\":\"nexus:square\",\"side\":4}")))
                .isEqualTo(new ParityCases.Square(4));
    }
    
    @Test
    public void dispatchWritesTheIdentifierAndThenTheFieldsOnNetty() {
        CombinatorsTest.assertNetty(
                CombinatorsTest.SHAPE,
                new ParityCases.Circle(2),
                0x0C, 'n', 'e', 'x', 'u', 's', ':', 'c', 'i', 'r', 'c', 'l', 'e', 0x00, 0x00, 0x00, 0x02
        );
    }
    
    @Test
    public void dispatchRefusesUnknownIdentifierListingTheKnownOnes() {
        JsonElement input = JsonParser.parseString("{\"type\":\"nexus:hexagon\",\"radius\":2}");
        FriendlyByteBuf buffer = CombinatorsTest.buffer(0x0D, 'n', 'e', 'x', 'u', 's', ':', 'h', 'e', 'x', 'a', 'g', 'o', 'n');
        
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.SHAPE.decode(CodecFormat.JSON, input)))
                .singleElement()
                .satisfies(error -> {
                    Assertions.assertThat(error.path()).isEqualTo("type");
                    Assertions.assertThat(error.message()).contains("nexus:circle", "nexus:square", "nexus:hexagon");
                });
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.SHAPE.decode(CodecFormat.netty(buffer))))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.message()).contains("nexus:circle", "nexus:square", "nexus:hexagon"));
    }
    
    @Test
    public void dispatchRefusesMissingKey() {
        CompoundTag tag = CombinatorsTest.compound(Map.of("radius", IntTag.valueOf(2)));
        
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.SHAPE.decode(CodecFormat.JSON, JsonParser.parseString("{\"radius\":2}"))))
                .containsExactly(new CodecError("type", "Missing"));
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.SHAPE.decode(CodecFormat.NBT, tag))).containsExactly(new CodecError("type", "Missing"));
    }
    
    @Test
    public void dispatchRefusesToEncodeUnregisteredSubtype() {
        StructCodec<ParityCases.Shape, Access.Plain> circles = NexusCodec.dispatch("type", ParityCases::typeOf, Map.of(ParityCases.CIRCLE, CombinatorsTest.CIRCLE));
        ParityCases.Square square = new ParityCases.Square(4);
        
        Assertions.assertThat(CombinatorsTest.errors(() -> circles.encode(CodecFormat.JSON, square)))
                .singleElement()
                .satisfies(error -> {
                    Assertions.assertThat(error.path()).isEqualTo("type");
                    Assertions.assertThat(error.message()).contains("nexus:circle", "nexus:square");
                });
        Assertions.assertThatThrownBy(() -> circles.encode(CodecFormat.netty(CombinatorsTest.buffer()), square)).isInstanceOf(NexusCodecException.class);
    }
    
    @Test
    public void dispatchRefusesSubtypeThatHasTheKey() {
        StructCodec<ParityCases.Circle, Access.Plain> typed = NexusCodec.struct("typed", NexusCodec.INT.field("type", ParityCases.Circle::radius), ParityCases.Circle::new);
        StructCodec<ParityCases.Named, Access.Plain> inlined = NexusCodec.struct(
                "inlined",
                NexusCodec.STRING.field("name", ParityCases.Named::name),
                CombinatorsTest.SHAPE.inline(ParityCases.Named::shape),
                ParityCases.Named::new
        );
        
        Assertions.assertThatThrownBy(() -> NexusCodec.dispatch("type", ParityCases::typeOf, Map.of(ParityCases.CIRCLE, typed)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nexus:circle");
        Assertions.assertThatThrownBy(() -> NexusCodec.dispatch("type", _ -> ParityCases.CIRCLE, Map.of(ParityCases.CIRCLE, inlined)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nexus:circle");
    }
    
    @Test
    public void dispatchReportsTheErrorsOfTheSubtypeAtTheirOwnKeys() {
        JsonElement nested = JsonParser.parseString("{\"name\":\"wheel\",\"shape\":{\"type\":\"nexus:circle\",\"radius\":\"big\"}}");
        JsonElement inline = JsonParser.parseString("{\"name\":\"wheel\",\"type\":\"nexus:circle\",\"radius\":\"big\"}");
        
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.NAMED.decode(CodecFormat.JSON, nested)))
                .extracting(CodecError::path)
                .containsExactly("shape.radius");
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.INLINE_NAMED.decode(CodecFormat.JSON, inline)))
                .extracting(CodecError::path)
                .containsExactly("radius");
    }
    
    @Test
    public void inlineDispatchMergesItsKeysIntoTheOwner() {
        ParityCases.Named wheel = new ParityCases.Named("wheel", new ParityCases.Circle(2));
        
        CombinatorsTest.assertJson(CombinatorsTest.INLINE_NAMED, wheel, "{\"name\":\"wheel\",\"radius\":2,\"type\":\"nexus:circle\"}");
        CombinatorsTest.assertNbt(
                CombinatorsTest.INLINE_NAMED,
                wheel,
                CombinatorsTest.compound(Map.of("name", StringTag.valueOf("wheel"), "radius", IntTag.valueOf(2), "type", StringTag.valueOf("nexus:circle")))
        );
        CombinatorsTest.assertNetty(
                CombinatorsTest.INLINE_NAMED,
                wheel,
                0x05, 'w', 'h', 'e', 'e', 'l', 0x0C, 'n', 'e', 'x', 'u', 's', ':', 'c', 'i', 'r', 'c', 'l', 'e', 0x00, 0x00, 0x00, 0x02
        );
    }
    
    @Test
    public void strictFormatKnowsTheKeyAndTheKeysOfThePickedSubtype() {
        JsonElement known = JsonParser.parseString("{\"type\":\"nexus:circle\",\"radius\":2}");
        JsonElement other = JsonParser.parseString("{\"type\":\"nexus:circle\",\"radius\":2,\"side\":4}");
        
        Assertions.assertThat(CombinatorsTest.SHAPE.decode(CodecFormat.JSON.strict(), known)).isEqualTo(new ParityCases.Circle(2));
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.SHAPE.decode(CodecFormat.JSON.strict(), other)))
                .containsExactly(new CodecError("side", "Unknown key"));
    }
    
    @Test
    public void strictFormatKnowsTheKeysOfInlineDispatch() {
        CompoundTag known = CombinatorsTest.compound(Map.of("name", StringTag.valueOf("wheel"), "type", StringTag.valueOf("nexus:circle"), "radius", IntTag.valueOf(2)));
        JsonElement unknown = JsonParser.parseString("{\"name\":\"wheel\",\"type\":\"nexus:circle\",\"radius\":2,\"side\":4,\"extra\":1}");
        
        Assertions.assertThat(CombinatorsTest.INLINE_NAMED.decode(CodecFormat.NBT.strict(), known))
                .isEqualTo(new ParityCases.Named("wheel", new ParityCases.Circle(2)));
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.INLINE_NAMED.decode(CodecFormat.JSON.strict(), unknown)))
                .containsExactlyInAnyOrder(new CodecError("side", "Unknown key"), new CodecError("extra", "Unknown key"));
    }
    
    @Test
    public void recursiveCodecRoundTripsATreeOfDepthThree() {
        CombinatorsTest.assertJson(
                CombinatorsTest.TREE,
                CombinatorsTest.DEPTH_THREE,
                "{\"value\":1,\"children\":[{\"value\":2,\"children\":[]},{\"value\":3,\"children\":[{\"value\":4,\"children\":[]}]}]}"
        );
        CombinatorsTest.assertNbt(
                CombinatorsTest.TREE,
                CombinatorsTest.DEPTH_THREE,
                CombinatorsTest.node(1, CombinatorsTest.node(2), CombinatorsTest.node(3, CombinatorsTest.node(4)))
        );
        CombinatorsTest.assertNetty(
                CombinatorsTest.TREE,
                CombinatorsTest.DEPTH_THREE,
                0x00, 0x00, 0x00, 0x01, 0x02, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x03, 0x01, 0x00, 0x00, 0x00, 0x04, 0x00
        );
    }
    
    @Test
    public void recursiveCodecReportsThePathThroughEveryLevel() {
        JsonElement input = JsonParser.parseString("{\"value\":1,\"children\":[{\"value\":2,\"children\":[]},{\"value\":3,\"children\":[{\"value\":\"four\",\"children\":[]}]}]}");
        NexusCodecException failure = Assertions.catchThrowableOfType(NexusCodecException.class, () -> CombinatorsTest.TREE.decode(CodecFormat.JSON, input));
        
        Assertions.assertThat(failure.errors()).extracting(CodecError::path).containsExactly("children[1].children[0].value");
        Assertions.assertThat(failure.getMessage().lines().findFirst())
                .hasValueSatisfying(line -> Assertions.assertThat(line).contains("'node'").doesNotContain("branch"));
    }
    
    @Test
    public void xmapMapsBothWays() {
        NexusCodec<ParityCases.Amount, Access.Plain> amount = NexusCodec.INT.xmap(ParityCases.Amount::new, ParityCases.Amount::value);
        
        CombinatorsTest.assertJson(amount, new ParityCases.Amount(7), "7");
        CombinatorsTest.assertNbt(amount, new ParityCases.Amount(7), IntTag.valueOf(7));
        CombinatorsTest.assertNetty(amount, new ParityCases.Amount(7), 0x00, 0x00, 0x00, 0x07);
    }
    
    @Test
    public void flatXmapMapsTheValuesItAccepts() {
        CombinatorsTest.assertJson(CombinatorsTest.POSITIVE, new ParityCases.Amount(7), "7");
        CombinatorsTest.assertNbt(CombinatorsTest.POSITIVE, new ParityCases.Amount(7), IntTag.valueOf(7));
        CombinatorsTest.assertNetty(CombinatorsTest.POSITIVE, new ParityCases.Amount(7), 0x00, 0x00, 0x00, 0x07);
    }
    
    @Test
    public void flatXmapRefusesAtThePathOfTheValue() {
        CodecError refusal = new CodecError("", "expected a positive amount, found -1");
        
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.POSITIVE.decode(CodecFormat.JSON, JsonParser.parseString("-1")))).containsExactly(refusal);
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.POSITIVE.decode(CodecFormat.netty(CombinatorsTest.buffer(0xFF, 0xFF, 0xFF, 0xFF)))))
                .containsExactly(refusal);
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.POSITIVE.list().decode(CodecFormat.NBT, CombinatorsTest.list(IntTag.valueOf(1), IntTag.valueOf(-1)))))
                .containsExactly(new CodecError("[1]", refusal.message()));
    }
    
    @Test
    public void flatXmapRefusesToEncode() {
        NexusCodec<ParityCases.Amount, Access.Plain> readOnly = NexusCodec.INT.flatXmap(ParityCases.Amount::new, amount -> {
            throw new NexusCodecException("read only");
        });
        
        Assertions.assertThat(CombinatorsTest.errors(() -> readOnly.encode(CodecFormat.JSON, new ParityCases.Amount(1)))).containsExactly(new CodecError("", "read only"));
    }
    
    @Test
    public void flatXmapTurnsAnyOtherExceptionIntoAFailure() {
        NexusCodec<Identifier, Access.Plain> parsed = NexusCodec.STRING.flatXmap(Identifier::parse, Identifier::toString);
        
        Assertions.assertThat(CombinatorsTest.errors(() -> parsed.decode(CodecFormat.JSON, JsonParser.parseString("\"Not An Identifier\""))))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.path()).isEmpty());
    }
    
    @Test
    public void validateKeepsTheValuesItAccepts() {
        CombinatorsTest.assertJson(CombinatorsTest.NON_NEGATIVE, 5, "5");
        CombinatorsTest.assertNbt(CombinatorsTest.NON_NEGATIVE, 5, IntTag.valueOf(5));
        CombinatorsTest.assertNetty(CombinatorsTest.NON_NEGATIVE, 5, 0x00, 0x00, 0x00, 0x05);
    }
    
    @Test
    public void validateRefusesOnReadAndOnWrite() {
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.NON_NEGATIVE.decode(CodecFormat.JSON, JsonParser.parseString("-1"))))
                .containsExactly(new CodecError("", "negative"));
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.NON_NEGATIVE.encode(CodecFormat.NBT, -1))).containsExactly(new CodecError("", "negative"));
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.NON_NEGATIVE.decode(CodecFormat.netty(CombinatorsTest.buffer(0xFF, 0xFF, 0xFF, 0xFF)))))
                .containsExactly(new CodecError("", "negative"));
        Assertions.assertThat(CombinatorsTest.errors(() -> CombinatorsTest.NON_NEGATIVE.list().decode(CodecFormat.JSON, JsonParser.parseString("[0,-1]"))))
                .containsExactly(new CodecError("[1]", "negative"));
    }
    
    @Test
    public void validateTurnsAnyOtherExceptionIntoAFailure() {
        NexusCodec<Integer, Access.Plain> broken = NexusCodec.INT.validate(value -> {
            throw new IllegalStateException("broken");
        });
        
        Assertions.assertThat(CombinatorsTest.errors(() -> broken.decode(CodecFormat.JSON, JsonParser.parseString("1"))))
                .singleElement()
                .satisfies(error -> Assertions.assertThat(error.path()).isEmpty());
        Assertions.assertThat(CombinatorsTest.errors(() -> broken.encode(CodecFormat.netty(CombinatorsTest.buffer()), 1))).hasSize(1);
    }
    
    private static List<CodecError> errors(ThrowableAssert.ThrowingCallable call) {
        return Assertions.catchThrowableOfType(NexusCodecException.class, call).errors();
    }
    
    private static CompoundTag compound(Map<String, Tag> entries) {
        CompoundTag tag = new CompoundTag();
        entries.forEach(tag::put);
        return tag;
    }
    
    private static ListTag list(Tag... elements) {
        ListTag tag = new ListTag();
        tag.addAll(List.of(elements));
        return tag;
    }
    
    private static CompoundTag node(int value, Tag... children) {
        return CombinatorsTest.compound(Map.of("value", IntTag.valueOf(value), "children", CombinatorsTest.list(children)));
    }
    
    private static <T> void assertJson(NexusCodec<T, Access.Plain> codec, T value, String json) {
        Assertions.assertThat(codec.encode(CodecFormat.JSON, value)).hasToString(json);
        Assertions.assertThat(codec.decode(CodecFormat.JSON, JsonParser.parseString(json))).isEqualTo(value);
    }
    
    private static <T> void assertNbt(NexusCodec<T, Access.Plain> codec, T value, Tag tag) {
        Assertions.assertThat(codec.encode(CodecFormat.NBT, value)).isEqualTo(tag);
        Assertions.assertThat(codec.decode(CodecFormat.NBT, tag)).isEqualTo(value);
    }
    
    private static <T> void assertNetty(NexusCodec<T, Access.Plain> codec, T value, int... bytes) {
        FriendlyByteBuf written = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(CodecFormat.netty(written), value);
        FriendlyByteBuf read = CombinatorsTest.buffer(bytes);
        
        Assertions.assertThat(ByteBufUtil.getBytes(written)).isEqualTo(ByteBufUtil.getBytes(CombinatorsTest.buffer(bytes)));
        Assertions.assertThat(codec.decode(CodecFormat.netty(read))).isEqualTo(value);
        Assertions.assertThat(read.readableBytes()).isZero();
    }
    
    private static FriendlyByteBuf buffer(int... bytes) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        for (int value : bytes) {
            buffer.writeByte(value);
        }
        
        return buffer;
    }
    
    public record Item(String id, int count) { }
}
