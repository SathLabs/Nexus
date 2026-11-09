package dev.satherov.nexus.test.unit.api.component;

import dev.satherov.nexus.api.component.Comparison;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

///
/// Checks every operator on every kind of literal, and how numbers, text, flags, tags, and identifiers compare.
///
public class ComparisonTest {
    
    @ParameterizedTest
    @MethodSource("orders")
    public void comparesNumbers(Comparison.Operator operator, boolean below, boolean equal, boolean above) {
        Comparison comparison = new Comparison(operator, new Comparison.Literal.Numeric(3.0D));
        Assertions.assertThat(comparison.test(2.0D)).isEqualTo(below);
        Assertions.assertThat(comparison.test(3.0D)).isEqualTo(equal);
        Assertions.assertThat(comparison.test(4.0D)).isEqualTo(above);
    }
    
    @ParameterizedTest
    @MethodSource("orders")
    public void comparesText(Comparison.Operator operator, boolean below, boolean equal, boolean above) {
        Comparison comparison = new Comparison(operator, new Comparison.Literal.Text("b"));
        Assertions.assertThat(comparison.test("a")).isEqualTo(below);
        Assertions.assertThat(comparison.test("b")).isEqualTo(equal);
        Assertions.assertThat(comparison.test("c")).isEqualTo(above);
    }
    
    @ParameterizedTest
    @MethodSource("orders")
    public void comparesIdentifiers(Comparison.Operator operator, boolean below, boolean equal, boolean above) {
        Comparison comparison = new Comparison(operator, new Comparison.Literal.Text("minecraft:b"));
        Assertions.assertThat(comparison.test(Identifier.withDefaultNamespace("a"))).isEqualTo(below);
        Assertions.assertThat(comparison.test(Identifier.withDefaultNamespace("b"))).isEqualTo(equal);
        Assertions.assertThat(comparison.test(Identifier.withDefaultNamespace("c"))).isEqualTo(above);
    }
    
    private static Stream<Arguments> orders() {
        return Stream.of(
                Arguments.of(Comparison.Operator.EQUAL, false, true, false),
                Arguments.of(Comparison.Operator.NOT_EQUAL, true, false, true),
                Arguments.of(Comparison.Operator.LESS, true, false, false),
                Arguments.of(Comparison.Operator.LESS_OR_EQUAL, true, true, false),
                Arguments.of(Comparison.Operator.GREATER, false, false, true),
                Arguments.of(Comparison.Operator.GREATER_OR_EQUAL, false, true, true)
        );
    }
    
    @ParameterizedTest
    @MethodSource("symbols")
    public void hasTheSymbolAndTheOrderOfEachOperator(Comparison.Operator operator, String symbol, boolean ordered) {
        Assertions.assertThat(operator.getSymbol()).isEqualTo(symbol);
        Assertions.assertThat(operator.isOrdered()).isEqualTo(ordered);
    }
    
    private static Stream<Arguments> symbols() {
        return Stream.of(
                Arguments.of(Comparison.Operator.EQUAL, "==", false),
                Arguments.of(Comparison.Operator.NOT_EQUAL, "!=", false),
                Arguments.of(Comparison.Operator.LESS, "<", true),
                Arguments.of(Comparison.Operator.LESS_OR_EQUAL, "<=", true),
                Arguments.of(Comparison.Operator.GREATER, ">", true),
                Arguments.of(Comparison.Operator.GREATER_OR_EQUAL, ">=", true)
        );
    }
    
    @Test
    public void treatsNegativeZeroAsZero() {
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(0.0D)).test(-0.0D)).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(-0.0D)).test(0.0D)).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Numeric(0.0D)).test(-0.0D)).isFalse();
        Assertions.assertThat(new Comparison(Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Numeric(0.0D)).test(-0.0D)).isFalse();
    }
    
    @ParameterizedTest
    @EnumSource(Comparison.Operator.class)
    public void passesNotANumberOnlyWithNotEqual(Comparison.Operator operator) {
        boolean passes = operator == Comparison.Operator.NOT_EQUAL;
        Assertions.assertThat(new Comparison(operator, new Comparison.Literal.Numeric(1.0D)).test(Double.NaN)).isEqualTo(passes);
        Assertions.assertThat(new Comparison(operator, new Comparison.Literal.Numeric(Double.NaN)).test(1.0D)).isEqualTo(passes);
        Assertions.assertThat(new Comparison(operator, new Comparison.Literal.Numeric(Double.NaN)).test(Double.NaN)).isEqualTo(passes);
    }
    
    @Test
    public void comparesTextByUtf16CodeUnits() {
        Assertions.assertThat(new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Text("｡")).test("😀")).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.GREATER, new Comparison.Literal.Text("ab")).test("abc")).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Text("a")).test("B")).isTrue();
    }
    
    @ParameterizedTest
    @EnumSource(Comparison.Operator.class)
    public void comparesIdentifiersLikeTheirTextForm(Comparison.Operator operator) {
        Identifier id = Identifier.fromNamespaceAndPath("a", "b");
        for (String text : new String[]{ "a:b", "a:bc", "a:a", "a:", "a", "a-:b", "ab:c", "a:b:", "" }) {
            Comparison comparison = new Comparison(operator, new Comparison.Literal.Text(text));
            Assertions.assertThat(comparison.test(id)).as("'a:b' %s '%s'", operator.getSymbol(), text).isEqualTo(comparison.test(id.toString()));
        }
    }
    
    @Test
    public void comparesFlags() {
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true)).test(true)).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true)).test(false)).isFalse();
        Assertions.assertThat(new Comparison(Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Flag(false)).test(true)).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Flag(false)).test(false)).isFalse();
    }
    
    @Test
    public void comparesAFlagWithANumberAsOneOrZero() {
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(1.0D)).test(true)).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(0.0D)).test(false)).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(1.0D)).test(false)).isFalse();
        Assertions.assertThat(new Comparison(Comparison.Operator.GREATER, new Comparison.Literal.Numeric(0.0D)).test(true)).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Numeric(1.0D)).test(false)).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(2.0D)).test(true)).isFalse();
    }
    
    @ParameterizedTest
    @EnumSource(Comparison.Operator.class)
    public void failsAValueOfAnotherKindThanTheLiteral(Comparison.Operator operator) {
        Assertions.assertThat(new Comparison(operator, new Comparison.Literal.Numeric(3.0D)).test("3")).isFalse();
        Assertions.assertThat(new Comparison(operator, new Comparison.Literal.Numeric(3.0D)).test(Identifier.withDefaultNamespace("stone"))).isFalse();
        Assertions.assertThat(new Comparison(operator, new Comparison.Literal.Text("3")).test(3.0D)).isFalse();
        Assertions.assertThat(new Comparison(operator, new Comparison.Literal.Text("true")).test(true)).isFalse();
        Assertions.assertThat(new Comparison(operator, new Comparison.Literal.Text("true")).test(false)).isFalse();
    }
    
    @ParameterizedTest
    @EnumSource(value = Comparison.Operator.class, names = { "EQUAL", "NOT_EQUAL" })
    public void failsANumberOrTextAgainstAFlag(Comparison.Operator operator) {
        Comparison comparison = new Comparison(operator, new Comparison.Literal.Flag(true));
        Assertions.assertThat(comparison.test(1.0D)).isFalse();
        Assertions.assertThat(comparison.test(0.0D)).isFalse();
        Assertions.assertThat(comparison.test("true")).isFalse();
        Assertions.assertThat(comparison.test(Identifier.withDefaultNamespace("true"))).isFalse();
    }
    
    @ParameterizedTest
    @EnumSource(value = Comparison.Operator.class, names = { "LESS", "LESS_OR_EQUAL", "GREATER", "GREATER_OR_EQUAL" })
    public void refusesAnOrderedOperatorWithAFlag(Comparison.Operator operator) {
        Assertions.assertThatThrownBy(() -> new Comparison(operator, new Comparison.Literal.Flag(true))).isInstanceOf(IllegalArgumentException.class);
    }
    
    @Test
    public void comparesNumberTagsAsNumbers() {
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(3.0D)).test(IntTag.valueOf(3))).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(1.0D)).test(ByteTag.valueOf((byte) 1))).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Numeric(3.0D)).test(DoubleTag.valueOf(2.5D))).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(Long.MAX_VALUE)).test(LongTag.valueOf(Long.MAX_VALUE - 1L))).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Text("3")).test(IntTag.valueOf(3))).isFalse();
    }
    
    @Test
    public void comparesStringTagsAsText() {
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Text("stone")).test(StringTag.valueOf("stone"))).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.LESS, new Comparison.Literal.Text("stone")).test(StringTag.valueOf("dirt"))).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Numeric(3.0D)).test(StringTag.valueOf("3"))).isFalse();
    }
    
    @Test
    public void comparesByteTagsAsFlags() {
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true)).test(ByteTag.valueOf((byte) 1))).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true)).test(ByteTag.valueOf((byte) 2))).isTrue();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true)).test(ByteTag.valueOf((byte) 0))).isFalse();
        Assertions.assertThat(new Comparison(Comparison.Operator.NOT_EQUAL, new Comparison.Literal.Flag(true)).test(ByteTag.valueOf((byte) 0))).isTrue();
    }
    
    @Test
    public void comparesOnlyByteTagsAsFlags() {
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true)).test(IntTag.valueOf(1))).isFalse();
        Assertions.assertThat(new Comparison(Comparison.Operator.EQUAL, new Comparison.Literal.Flag(true)).test(StringTag.valueOf("true"))).isFalse();
    }
    
    @ParameterizedTest
    @EnumSource(value = Comparison.Operator.class, names = { "EQUAL", "NOT_EQUAL" })
    public void failsCompoundsListsAndTheEndTag(Comparison.Operator operator) {
        for (Comparison.Literal literal : new Comparison.Literal[]{ new Comparison.Literal.Numeric(0.0D), new Comparison.Literal.Text(""), new Comparison.Literal.Flag(false) }) {
            Comparison comparison = new Comparison(operator, literal);
            Assertions.assertThat(comparison.test(new CompoundTag())).isFalse();
            Assertions.assertThat(comparison.test(new ListTag())).isFalse();
            Assertions.assertThat(comparison.test(EndTag.INSTANCE)).isFalse();
        }
    }
}
