package dev.satherov.nexus.test.unit.internal.render.shader.syntax;

import dev.satherov.nexus.internal.render.shader.syntax.Item;
import dev.satherov.nexus.internal.render.shader.syntax.Parser;

import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

public class ParserTest {
    
    private static final Identifier ID = Identifier.fromNamespaceAndPath("nexus", "parser_test");
    
    private static List<Item> parse(String... lines) {
        return Parser.parse(ParserTest.ID, String.join("\n", lines));
    }
    
    @Test
    public void readsAFunctionWithNestedBlocksAsOneItem() {
        String function = String.join(
                "\n",
                "void parserTestNested(float value) {",
                "    if (value > 0.0) {",
                "        for (int i = 0; i < 2; i++) {",
                "            value -= 1.0;",
                "        }",
                "    }",
                "}"
        );
        Assertions.assertThat(ParserTest.parse(function, "float parserTestAfter;")).containsExactly(
                new Item.Function("parserTestNested", function, 1),
                new Item.Declaration("float parserTestAfter;", 8)
        );
    }
    
    @Test
    public void keepsADirectiveInsideAFunctionBody() {
        String function = String.join("\n", "void parserTestA() {", "#ifdef PARSER_TEST_X", "    parserTestB();", "#endif", "}");
        Assertions.assertThat(ParserTest.parse(function)).containsExactly(new Item.Function("parserTestA", function, 1));
    }
    
    @Test
    public void readsAStructAsOneDeclaration() {
        String struct = String.join("\n", "struct ParserTestLight {", "    vec3 color;", "    float strength;", "};");
        Assertions.assertThat(ParserTest.parse(struct, "ParserTestLight parserTestLight;")).containsExactly(
                new Item.Declaration(struct, 1),
                new Item.Declaration("ParserTestLight parserTestLight;", 5)
        );
    }
    
    @Test
    public void readsAFunctionPrototype() {
        Assertions.assertThat(ParserTest.parse("vec4 parserTestShade(vec4 color, float weights[2]);"))
                .containsExactly(new Item.Function("parserTestShade", "vec4 parserTestShade(vec4 color, float weights[2]);", 1));
    }
    
    @Test
    public void readsADeclarationWithAnInitializerCall() {
        Assertions.assertThat(ParserTest.parse("const vec3 PARSER_TEST_UP = normalize(vec3(0.0, 1.0, 0.0));"))
                .containsExactly(new Item.Declaration("const vec3 PARSER_TEST_UP = normalize(vec3(0.0, 1.0, 0.0));", 1));
    }
    
    @Test
    public void readsTheDeclarationsThePipelineHasToKnow() {
        Assertions.assertThat(ParserTest.parse(
                "layout(location = 3) in vec2 ParserTestA;",
                "flat out int parserTestB;",
                "layout(std140) uniform ParserTestC {",
                "    vec4 ParserTestColor;",
                "};",
                "uniform sampler2D ParserTestD;"
        )).containsExactly(
                new Item.Input("vec2", "ParserTestA", "layout(location = 3) in vec2 ParserTestA;", 1),
                new Item.Output("flat out int parserTestB;", "flat in int parserTestB;", 2),
                new Item.Block("ParserTestC", "layout(std140) uniform ParserTestC {\n    vec4 ParserTestColor;\n};", 3),
                new Item.Sampler("ParserTestD", "uniform sampler2D ParserTestD;", 6)
        );
    }
    
    @Test
    public void readsDirectivesWithTheirNames() {
        Assertions.assertThat(ParserTest.parse("#define PARSER_TEST_X 1 // one", "#  moj_import <nexus:parser_test.glsl>")).containsExactly(
                new Item.Directive("define", "#define PARSER_TEST_X 1", 1),
                new Item.Directive("moj_import", "#  moj_import <nexus:parser_test.glsl>", 2)
        );
    }
    
    @Test
    public void keepsCommentsInsideAnItemOnly() {
        Assertions.assertThat(ParserTest.parse(
                "// before the item",
                "float /* inside */ parserTestA; // after the item",
                "void parserTestB() {",
                "    // inside the body",
                "}"
        )).containsExactly(
                new Item.Declaration("float /* inside */ parserTestA;", 2),
                new Item.Function("parserTestB", "void parserTestB() {\n    // inside the body\n}", 3)
        );
    }
    
    @Test
    public void readsAConditionalAroundItems() {
        String inner = String.join("\n", "#if PARSER_TEST_LEVEL > 1", "void parserTestB() {", "}", "#endif");
        String outer = String.join("\n", "#ifdef NEXUS_FRAGMENT", "float parserTestA;", inner, "#else", "float parserTestC;", "#endif");
        Assertions.assertThat(ParserTest.parse(outer, "float parserTestD;")).containsExactly(
                new Item.Conditional(
                        List.of(
                                new Item.Declaration("float parserTestA;", 2),
                                new Item.Conditional(List.of(new Item.Function("parserTestB", "void parserTestB() {\n}", 4)), inner, 3),
                                new Item.Directive("else", "#else", 7),
                                new Item.Declaration("float parserTestC;", 8)
                        ),
                        outer,
                        1
                ),
                new Item.Declaration("float parserTestD;", 10)
        );
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
            "float parserTestA;\nvoid parserTestB() {\n    float parserTestC;",
            "float parserTestA;\n}",
            "float parserTestA;\nfloat parserTestB = (1.0;",
            "float parserTestA;\nfloat parserTestB = 1.0);",
            "float parserTestA;\n#ifdef PARSER_TEST_X\nfloat parserTestB;",
            "float parserTestA;\n#endif",
            "float parserTestA;\nfloat parserTestB",
            "float parserTestA;\nin vec4 ParserTestB, ParserTestC;",
            "float parserTestA;\nlayout(location = 0) in highp vec4 ParserTestB;"
    })
    public void throwsNamingTheLine(String text) {
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> Parser.parse(ParserTest.ID, text))
                .withMessageContaining("line '2'");
    }
}
