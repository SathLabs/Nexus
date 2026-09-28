package dev.satherov.nexus.test.unit.api.codec.result;

import dev.satherov.nexus.api.codec.result.CodecError;
import dev.satherov.nexus.api.codec.result.NexusCodecException;
import dev.satherov.nexus.internal.codec.CodecErrors;

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
/// Checks how a [NexusCodecException] renders its errors and how [CodecErrors] builds failures and prefixes their paths.
///
public class CodecErrorsTest {

    private static final CodecError NUMBER = new CodecError("count", "expected a number, found \"many\"");
    private static final CodecError UNKNOWN = new CodecError("entries[2].extra", "unknown key");

    @Test
    public void messageConstructorGivesOneErrorAtEmptyPath() {
        Assertions.assertThat(new NexusCodecException("missing").errors()).containsExactly(new CodecError("", "missing"));
    }

    @Test
    public void errorsKeepTheGivenOrder() {
        Assertions.assertThat(new NexusCodecException(List.of(CodecErrorsTest.NUMBER, CodecErrorsTest.UNKNOWN)).errors()).containsExactly(CodecErrorsTest.NUMBER, CodecErrorsTest.UNKNOWN);
        Assertions.assertThat(new NexusCodecException("header", List.of(CodecErrorsTest.UNKNOWN, CodecErrorsTest.NUMBER)).errors()).containsExactly(CodecErrorsTest.UNKNOWN, CodecErrorsTest.NUMBER);
    }

    @Test
    public void messageRendersOneError() {
        List<String> lines = new NexusCodecException(List.of(CodecErrorsTest.NUMBER)).getMessage().lines().toList();

        Assertions.assertThat(lines).singleElement().asString().contains("count", "expected a number, found \"many\"");
    }

    @Test
    public void messageRendersTopLevelErrorAsItsMessage() {
        List<String> lines = new NexusCodecException("expected a number, found \"many\"").getMessage().lines().toList();

        Assertions.assertThat(lines).singleElement().asString().contains("expected a number, found \"many\"");
    }

    @Test
    public void messageRendersOneLinePerError() {
        List<String> lines = new NexusCodecException(List.of(CodecErrorsTest.NUMBER, CodecErrorsTest.UNKNOWN)).getMessage().lines().toList();

        Assertions.assertThat(lines).hasSize(2);
        Assertions.assertThat(lines.get(0)).contains("count", "expected a number, found \"many\"");
        Assertions.assertThat(lines.get(1)).contains("entries[2].extra", "unknown key");
    }

    @Test
    public void decodeFailureFirstLineHasCodecDirectionAndFormat() {
        NexusCodecException failure = CodecErrors.decodeFailure("recipe", "JSON", new NexusCodecException(List.of(CodecErrorsTest.NUMBER, CodecErrorsTest.UNKNOWN)));
        List<String> lines = failure.getMessage().lines().toList();

        Assertions.assertThat(lines).hasSize(3);
        Assertions.assertThat(lines.get(0)).contains("recipe", "decode", "JSON").doesNotContain("encode", "count", "unknown key");
        Assertions.assertThat(lines.get(1)).contains("count", "expected a number, found \"many\"");
        Assertions.assertThat(lines.get(2)).contains("entries[2].extra", "unknown key");
    }

    @Test
    public void encodeFailureFirstLineHasCodecDirectionAndFormat() {
        NexusCodecException failure = CodecErrors.encodeFailure("recipe", "netty", new NexusCodecException(List.of(CodecErrorsTest.NUMBER)));
        List<String> lines = failure.getMessage().lines().toList();

        Assertions.assertThat(lines).hasSize(2);
        Assertions.assertThat(lines.get(0)).contains("recipe", "encode", "netty").doesNotContain("decode", "count");
        Assertions.assertThat(lines.get(1)).contains("count", "expected a number, found \"many\"");
    }

    @Test
    public void entryFailuresKeepErrorsWithoutCodecInPath() {
        NexusCodecException inner = new NexusCodecException(List.of(CodecErrorsTest.NUMBER, CodecErrorsTest.UNKNOWN));

        Assertions.assertThat(CodecErrors.encodeFailure("recipe", "NBT", inner).errors()).containsExactly(CodecErrorsTest.NUMBER, CodecErrorsTest.UNKNOWN);
        Assertions.assertThat(CodecErrors.decodeFailure("recipe", "NBT", inner).errors()).containsExactly(CodecErrorsTest.NUMBER, CodecErrorsTest.UNKNOWN);
    }

    @Test
    public void prefixKeyJoinsNamesWithDotAndBracketsDirectly() {
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixKey(CodecErrorsTest.at(""), "cost"))).isEqualTo("cost");
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixKey(CodecErrorsTest.at("cost"), "entry"))).isEqualTo("entry.cost");
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixKey(CodecErrorsTest.at("[2]"), "entries"))).isEqualTo("entries[2]");
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixKey(CodecErrorsTest.at("['stone']"), "prices"))).isEqualTo("prices['stone']");
    }

    @Test
    public void prefixIndexJoinsNamesWithDotAndBracketsDirectly() {
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixIndex(CodecErrorsTest.at(""), 2))).isEqualTo("[2]");
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixIndex(CodecErrorsTest.at("cost"), 2))).isEqualTo("[2].cost");
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixIndex(CodecErrorsTest.at("[0]"), 2))).isEqualTo("[2][0]");
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixIndex(CodecErrorsTest.at("['stone']"), 2))).isEqualTo("[2]['stone']");
    }

    @Test
    public void prefixMapKeyJoinsNamesWithDotAndBracketsDirectly() {
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixMapKey(CodecErrorsTest.at(""), "stone"))).isEqualTo("['stone']");
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixMapKey(CodecErrorsTest.at("cost"), "stone"))).isEqualTo("['stone'].cost");
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixMapKey(CodecErrorsTest.at("[2]"), "stone"))).isEqualTo("['stone'][2]");
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixMapKey(CodecErrorsTest.at("['dirt']"), "stone"))).isEqualTo("['stone']['dirt']");
    }

    @Test
    public void nestedPrefixesBuildFullPath() {
        NexusCodecException leaf = new NexusCodecException("expected a number, found \"many\"");
        NexusCodecException entries = CodecErrors.prefixKey(CodecErrors.prefixIndex(CodecErrors.prefixKey(leaf, "cost"), 2), "entries");
        NexusCodecException prices = CodecErrors.prefixKey(CodecErrors.prefixMapKey(leaf, "stone"), "prices");
        NexusCodecException deep = CodecErrors.prefixKey(CodecErrors.prefixMapKey(entries, "stone"), "recipes");

        Assertions.assertThat(CodecErrorsTest.path(entries)).isEqualTo("entries[2].cost");
        Assertions.assertThat(CodecErrorsTest.path(prices)).isEqualTo("prices['stone']");
        Assertions.assertThat(CodecErrorsTest.path(deep)).isEqualTo("recipes['stone'].entries[2].cost");
    }

    @Test
    public void prefixReachesEveryErrorAndKeepsMessages() {
        NexusCodecException prefixed = CodecErrors.prefixIndex(new NexusCodecException(List.of(CodecErrorsTest.NUMBER, CodecErrorsTest.UNKNOWN)), 4);

        Assertions.assertThat(prefixed.errors()).containsExactly(
                new CodecError("[4].count", "expected a number, found \"many\""),
                new CodecError("[4].entries[2].extra", "unknown key")
        );
    }

    @Test
    public void prefixLeavesArgumentUnchanged() {
        NexusCodecException original = new NexusCodecException(List.of(CodecErrorsTest.NUMBER));
        CodecErrors.prefixKey(original, "entry");
        CodecErrors.prefixIndex(original, 1);
        CodecErrors.prefixMapKey(original, "stone");

        Assertions.assertThat(original.errors()).containsExactly(CodecErrorsTest.NUMBER);
    }

    @Test
    public void mismatchQuotesFoundValue() {
        Assertions.assertThat(CodecErrors.mismatch("a number", "many").errors()).containsExactly(new CodecError("", "Expected a number, found \"many\""));
    }

    @Test
    public void unknownNameListsKnownNames() {
        NexusCodecException failure = CodecErrors.unknownName(List.of("fire", "ice"), "water");

        Assertions.assertThat(failure.errors()).containsExactly(new CodecError("", "Expected one of [fire, ice], found \"water\""));
    }

    @Test
    public void outOfRangeHasRangeAndValue() {
        Assertions.assertThat(CodecErrors.outOfRange(0, 255, 300).errors()).singleElement().satisfies(error -> {
            Assertions.assertThat(error.path()).isEmpty();
            Assertions.assertThat(error.message()).contains("0", "255", "300");
        });
    }

    @Test
    public void tooLongHasLimitAndLength() {
        Assertions.assertThat(CodecErrors.tooLong(32, 40).errors()).singleElement().satisfies(error -> {
            Assertions.assertThat(error.path()).isEmpty();
            Assertions.assertThat(error.message()).contains("32", "40");
        });
    }

    @Test
    public void describeQuotesStringsAndLeavesNumbersAndBooleansBare() {
        Assertions.assertThat(CodecErrors.describe("many")).isEqualTo("\"many\"");
        Assertions.assertThat(CodecErrors.describe(5)).isEqualTo("5");
        Assertions.assertThat(CodecErrors.describe(true)).isEqualTo("true");
        Assertions.assertThat(CodecErrors.describe(null)).isEqualTo("null");
    }

    @Test
    public void describeGivesJsonPrimitivesByValueAndStructuresByKind() {
        Assertions.assertThat(CodecErrors.describe(new JsonPrimitive("many"))).isEqualTo("\"many\"");
        Assertions.assertThat(CodecErrors.describe(new JsonPrimitive(5))).isEqualTo("5");
        Assertions.assertThat(CodecErrors.describe(new JsonPrimitive(false))).isEqualTo("false");
        Assertions.assertThat(CodecErrors.describe(new JsonObject())).isEqualTo("an object");
        Assertions.assertThat(CodecErrors.describe(new JsonArray())).isEqualTo("a list");
    }

    @Test
    public void describeGivesTagPrimitivesByValueAndStructuresByKind() {
        Assertions.assertThat(CodecErrors.describe(StringTag.valueOf("many"))).isEqualTo("\"many\"");
        Assertions.assertThat(CodecErrors.describe(IntTag.valueOf(5))).isEqualTo("5");
        Assertions.assertThat(CodecErrors.describe(new CompoundTag())).isEqualTo("an object");
        Assertions.assertThat(CodecErrors.describe(new ListTag())).isEqualTo("a list");
    }

    @Test
    public void describeEscapesQuotedText() {
        Assertions.assertThat(CodecErrors.describe("a\nb\r\tc")).isEqualTo("\"a\\nb\\r\\tc\"");
        Assertions.assertThat(CodecErrors.describe("say \"hi\" \\ it's")).isEqualTo("\"say \\\"hi\\\" \\\\ it's\"");
        Assertions.assertThat(CodecErrors.describe("bell\u0007")).isEqualTo("\"bell\\u0007\"");
        Assertions.assertThat(CodecErrors.describe(new JsonPrimitive("a\nb"))).isEqualTo("\"a\\nb\"");
        Assertions.assertThat(CodecErrors.describe(StringTag.valueOf("a\nb"))).isEqualTo("\"a\\nb\"");
    }

    @Test
    public void prefixMapKeyEscapesKey() {
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixMapKey(CodecErrorsTest.at(""), "it's"))).isEqualTo("['it\\'s']");
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixMapKey(CodecErrorsTest.at(""), "x\ny"))).isEqualTo("['x\\ny']");
        Assertions.assertThat(CodecErrorsTest.path(CodecErrors.prefixMapKey(CodecErrorsTest.at(""), "a\\b \"c\""))).isEqualTo("['a\\\\b \"c\"']");
    }

    @Test
    public void lineBreaksInValuesAndKeysKeepOneLinePerError() {
        NexusCodecException inner = new NexusCodecException(List.of(
                CodecErrors.prefixMapKey(CodecErrors.unknownName(List.of("fire", "ice"), "wa\nter"), "x\ny").errors().getFirst(),
                CodecErrorsTest.NUMBER
        ));

        Assertions.assertThat(inner.getMessage().lines()).hasSize(2);
        Assertions.assertThat(CodecErrors.decodeFailure("recipe", "JSON", inner).getMessage().lines()).hasSize(3);
    }

    private static NexusCodecException at(String path) {
        return new NexusCodecException(List.of(new CodecError(path, "missing")));
    }

    private static String path(NexusCodecException failure) {
        return failure.errors().getFirst().path();
    }
}
