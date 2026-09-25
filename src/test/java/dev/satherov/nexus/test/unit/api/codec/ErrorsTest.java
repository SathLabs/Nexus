package dev.satherov.nexus.test.unit.api.codec;

import dev.satherov.nexus.api.codec.CodecError;
import dev.satherov.nexus.api.codec.NexusCodecException;
import dev.satherov.nexus.internal.codec.Errors;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

///
/// Checks how a [NexusCodecException] renders its errors and how [Errors] builds failures and prefixes their paths.
///
public class ErrorsTest {

    private static final CodecError NUMBER = new CodecError("count", "expected a number, found \"many\"");
    private static final CodecError UNKNOWN = new CodecError("entries[2].extra", "unknown key");

    @Test
    public void messageConstructorGivesOneErrorAtEmptyPath() {
        Assertions.assertThat(new NexusCodecException("missing").errors()).containsExactly(new CodecError("", "missing"));
    }

    @Test
    public void errorsKeepTheGivenOrder() {
        Assertions.assertThat(new NexusCodecException(List.of(ErrorsTest.NUMBER, ErrorsTest.UNKNOWN)).errors()).containsExactly(ErrorsTest.NUMBER, ErrorsTest.UNKNOWN);
        Assertions.assertThat(new NexusCodecException("header", List.of(ErrorsTest.UNKNOWN, ErrorsTest.NUMBER)).errors()).containsExactly(ErrorsTest.UNKNOWN, ErrorsTest.NUMBER);
    }

    @Test
    public void messageRendersOneError() {
        List<String> lines = new NexusCodecException(List.of(ErrorsTest.NUMBER)).getMessage().lines().toList();

        Assertions.assertThat(lines).singleElement().asString().contains("count", "expected a number, found \"many\"");
    }

    @Test
    public void messageRendersTopLevelErrorAsItsMessage() {
        List<String> lines = new NexusCodecException("expected a number, found \"many\"").getMessage().lines().toList();

        Assertions.assertThat(lines).singleElement().asString().contains("expected a number, found \"many\"");
    }

    @Test
    public void messageRendersOneLinePerError() {
        List<String> lines = new NexusCodecException(List.of(ErrorsTest.NUMBER, ErrorsTest.UNKNOWN)).getMessage().lines().toList();

        Assertions.assertThat(lines).hasSize(2);
        Assertions.assertThat(lines.get(0)).contains("count", "expected a number, found \"many\"");
        Assertions.assertThat(lines.get(1)).contains("entries[2].extra", "unknown key");
    }

    @Test
    public void decodeFailureFirstLineHasCodecDirectionAndFormat() {
        NexusCodecException failure = Errors.decodeFailure("recipe", "JSON", new NexusCodecException(List.of(ErrorsTest.NUMBER, ErrorsTest.UNKNOWN)));
        List<String> lines = failure.getMessage().lines().toList();

        Assertions.assertThat(lines).hasSize(3);
        Assertions.assertThat(lines.get(0)).contains("recipe", "decode", "JSON").doesNotContain("encode", "count", "unknown key");
        Assertions.assertThat(lines.get(1)).contains("count", "expected a number, found \"many\"");
        Assertions.assertThat(lines.get(2)).contains("entries[2].extra", "unknown key");
    }

    @Test
    public void encodeFailureFirstLineHasCodecDirectionAndFormat() {
        NexusCodecException failure = Errors.encodeFailure("recipe", "netty", new NexusCodecException(List.of(ErrorsTest.NUMBER)));
        List<String> lines = failure.getMessage().lines().toList();

        Assertions.assertThat(lines).hasSize(2);
        Assertions.assertThat(lines.get(0)).contains("recipe", "encode", "netty").doesNotContain("decode", "count");
        Assertions.assertThat(lines.get(1)).contains("count", "expected a number, found \"many\"");
    }

    @Test
    public void entryFailuresKeepErrorsWithoutCodecInPath() {
        NexusCodecException inner = new NexusCodecException(List.of(ErrorsTest.NUMBER, ErrorsTest.UNKNOWN));

        Assertions.assertThat(Errors.encodeFailure("recipe", "NBT", inner).errors()).containsExactly(ErrorsTest.NUMBER, ErrorsTest.UNKNOWN);
        Assertions.assertThat(Errors.decodeFailure("recipe", "NBT", inner).errors()).containsExactly(ErrorsTest.NUMBER, ErrorsTest.UNKNOWN);
    }

    @Test
    public void prefixKeyJoinsNamesWithDotAndBracketsDirectly() {
        Assertions.assertThat(ErrorsTest.path(Errors.prefixKey(ErrorsTest.at(""), "cost"))).isEqualTo("cost");
        Assertions.assertThat(ErrorsTest.path(Errors.prefixKey(ErrorsTest.at("cost"), "entry"))).isEqualTo("entry.cost");
        Assertions.assertThat(ErrorsTest.path(Errors.prefixKey(ErrorsTest.at("[2]"), "entries"))).isEqualTo("entries[2]");
        Assertions.assertThat(ErrorsTest.path(Errors.prefixKey(ErrorsTest.at("['stone']"), "prices"))).isEqualTo("prices['stone']");
    }

    @Test
    public void prefixIndexJoinsNamesWithDotAndBracketsDirectly() {
        Assertions.assertThat(ErrorsTest.path(Errors.prefixIndex(ErrorsTest.at(""), 2))).isEqualTo("[2]");
        Assertions.assertThat(ErrorsTest.path(Errors.prefixIndex(ErrorsTest.at("cost"), 2))).isEqualTo("[2].cost");
        Assertions.assertThat(ErrorsTest.path(Errors.prefixIndex(ErrorsTest.at("[0]"), 2))).isEqualTo("[2][0]");
        Assertions.assertThat(ErrorsTest.path(Errors.prefixIndex(ErrorsTest.at("['stone']"), 2))).isEqualTo("[2]['stone']");
    }

    @Test
    public void prefixMapKeyJoinsNamesWithDotAndBracketsDirectly() {
        Assertions.assertThat(ErrorsTest.path(Errors.prefixMapKey(ErrorsTest.at(""), "stone"))).isEqualTo("['stone']");
        Assertions.assertThat(ErrorsTest.path(Errors.prefixMapKey(ErrorsTest.at("cost"), "stone"))).isEqualTo("['stone'].cost");
        Assertions.assertThat(ErrorsTest.path(Errors.prefixMapKey(ErrorsTest.at("[2]"), "stone"))).isEqualTo("['stone'][2]");
        Assertions.assertThat(ErrorsTest.path(Errors.prefixMapKey(ErrorsTest.at("['dirt']"), "stone"))).isEqualTo("['stone']['dirt']");
    }

    @Test
    public void nestedPrefixesBuildFullPath() {
        NexusCodecException leaf = new NexusCodecException("expected a number, found \"many\"");
        NexusCodecException entries = Errors.prefixKey(Errors.prefixIndex(Errors.prefixKey(leaf, "cost"), 2), "entries");
        NexusCodecException prices = Errors.prefixKey(Errors.prefixMapKey(leaf, "stone"), "prices");
        NexusCodecException deep = Errors.prefixKey(Errors.prefixMapKey(entries, "stone"), "recipes");

        Assertions.assertThat(ErrorsTest.path(entries)).isEqualTo("entries[2].cost");
        Assertions.assertThat(ErrorsTest.path(prices)).isEqualTo("prices['stone']");
        Assertions.assertThat(ErrorsTest.path(deep)).isEqualTo("recipes['stone'].entries[2].cost");
    }

    @Test
    public void prefixReachesEveryErrorAndKeepsMessages() {
        NexusCodecException prefixed = Errors.prefixIndex(new NexusCodecException(List.of(ErrorsTest.NUMBER, ErrorsTest.UNKNOWN)), 4);

        Assertions.assertThat(prefixed.errors()).containsExactly(
                new CodecError("[4].count", "expected a number, found \"many\""),
                new CodecError("[4].entries[2].extra", "unknown key")
        );
    }

    @Test
    public void prefixLeavesArgumentUnchanged() {
        NexusCodecException original = new NexusCodecException(List.of(ErrorsTest.NUMBER));
        Errors.prefixKey(original, "entry");
        Errors.prefixIndex(original, 1);
        Errors.prefixMapKey(original, "stone");

        Assertions.assertThat(original.errors()).containsExactly(ErrorsTest.NUMBER);
    }

    @Test
    public void mismatchQuotesFoundValue() {
        Assertions.assertThat(Errors.mismatch("a number", "many").errors()).containsExactly(new CodecError("", "expected a number, found \"many\""));
    }

    @Test
    public void unknownNameListsKnownNames() {
        NexusCodecException failure = Errors.unknownName(List.of("fire", "ice"), "water");

        Assertions.assertThat(failure.errors()).containsExactly(new CodecError("", "expected one of [fire, ice], found \"water\""));
    }

    @Test
    public void outOfRangeHasRangeAndValue() {
        Assertions.assertThat(Errors.outOfRange(0, 255, 300).errors()).singleElement().satisfies(error -> {
            Assertions.assertThat(error.path()).isEmpty();
            Assertions.assertThat(error.message()).contains("0", "255", "300");
        });
    }

    @Test
    public void tooLongHasLimitAndLength() {
        Assertions.assertThat(Errors.tooLong(32, 40).errors()).singleElement().satisfies(error -> {
            Assertions.assertThat(error.path()).isEmpty();
            Assertions.assertThat(error.message()).contains("32", "40");
        });
    }

    @Test
    public void describeQuotesStringsAndLeavesNumbersAndBooleansBare() {
        Assertions.assertThat(Errors.describe("many")).isEqualTo("\"many\"");
        Assertions.assertThat(Errors.describe(5)).isEqualTo("5");
        Assertions.assertThat(Errors.describe(true)).isEqualTo("true");
        Assertions.assertThat(Errors.describe(null)).isEqualTo("null");
    }

    @Test
    public void describeGivesJsonPrimitivesByValueAndStructuresByKind() {
        Assertions.assertThat(Errors.describe(new JsonPrimitive("many"))).isEqualTo("\"many\"");
        Assertions.assertThat(Errors.describe(new JsonPrimitive(5))).isEqualTo("5");
        Assertions.assertThat(Errors.describe(new JsonPrimitive(false))).isEqualTo("false");
        Assertions.assertThat(Errors.describe(new JsonObject())).isEqualTo("an object");
        Assertions.assertThat(Errors.describe(new JsonArray())).isEqualTo("a list");
    }

    @Test
    public void describeGivesTagPrimitivesByValueAndStructuresByKind() {
        Assertions.assertThat(Errors.describe(StringTag.valueOf("many"))).isEqualTo("\"many\"");
        Assertions.assertThat(Errors.describe(IntTag.valueOf(5))).isEqualTo("5");
        Assertions.assertThat(Errors.describe(new CompoundTag())).isEqualTo("an object");
        Assertions.assertThat(Errors.describe(new ListTag())).isEqualTo("a list");
    }

    @Test
    public void describeEscapesQuotedText() {
        Assertions.assertThat(Errors.describe("a\nb\r\tc")).isEqualTo("\"a\\nb\\r\\tc\"");
        Assertions.assertThat(Errors.describe("say \"hi\" \\ it's")).isEqualTo("\"say \\\"hi\\\" \\\\ it's\"");
        Assertions.assertThat(Errors.describe("bell\u0007")).isEqualTo("\"bell\\u0007\"");
        Assertions.assertThat(Errors.describe(new JsonPrimitive("a\nb"))).isEqualTo("\"a\\nb\"");
        Assertions.assertThat(Errors.describe(StringTag.valueOf("a\nb"))).isEqualTo("\"a\\nb\"");
    }

    @Test
    public void prefixMapKeyEscapesKey() {
        Assertions.assertThat(ErrorsTest.path(Errors.prefixMapKey(ErrorsTest.at(""), "it's"))).isEqualTo("['it\\'s']");
        Assertions.assertThat(ErrorsTest.path(Errors.prefixMapKey(ErrorsTest.at(""), "x\ny"))).isEqualTo("['x\\ny']");
        Assertions.assertThat(ErrorsTest.path(Errors.prefixMapKey(ErrorsTest.at(""), "a\\b \"c\""))).isEqualTo("['a\\\\b \"c\"']");
    }

    @Test
    public void lineBreaksInValuesAndKeysKeepOneLinePerError() {
        NexusCodecException inner = new NexusCodecException(List.of(
                Errors.prefixMapKey(Errors.unknownName(List.of("fire", "ice"), "wa\nter"), "x\ny").errors().getFirst(),
                ErrorsTest.NUMBER
        ));

        Assertions.assertThat(inner.getMessage().lines()).hasSize(2);
        Assertions.assertThat(Errors.decodeFailure("recipe", "JSON", inner).getMessage().lines()).hasSize(3);
    }

    private static NexusCodecException at(String path) {
        return new NexusCodecException(List.of(new CodecError(path, "missing")));
    }

    private static String path(NexusCodecException failure) {
        return failure.errors().getFirst().path();
    }
}
