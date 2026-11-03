package dev.satherov.nexus.test.unit.internal.render.shader.syntax;

import dev.satherov.nexus.internal.render.shader.syntax.Lexer;
import dev.satherov.nexus.internal.render.shader.syntax.Token;

import net.minecraft.resources.Identifier;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class LexerTest {
    
    private static final Identifier ID = Identifier.fromNamespaceAndPath("nexus", "lexer_test");
    
    private static List<Token> lex(String... lines) {
        return Lexer.lex(LexerTest.ID, String.join("\n", lines));
    }
    
    @Test
    public void dropsCommentsWithBracesAndSemicolons() {
        List<Token> tokens = LexerTest.lex(
                "float lexerTestA; // } ; {",
                "/* { ;",
                "   } */ float /* ; */ lexerTestB;"
        );
        Assertions.assertThat(tokens).extracting(Token::text).containsExactly("float", "lexerTestA", ";", "float", "lexerTestB", ";");
    }
    
    @Test
    public void readsADirectiveWithItsContinuationLines() {
        List<Token> tokens = LexerTest.lex(
                "#define LEXER_TEST_SUM(a, b) \\",
                "    ((a) + \\",
                "    (b)) // the sum",
                "#ifdef LEXER_TEST_SUM",
                "float lexerTestC;"
        );
        Assertions.assertThat(tokens)
                .extracting(Token::kind, Token::text, Token::line)
                .containsExactly(
                        Assertions.tuple(Token.Kind.DIRECTIVE, "#define LEXER_TEST_SUM(a, b) \\\n    ((a) + \\\n    (b))", 1),
                        Assertions.tuple(Token.Kind.DIRECTIVE, "#ifdef LEXER_TEST_SUM", 4),
                        Assertions.tuple(Token.Kind.WORD, "float", 5),
                        Assertions.tuple(Token.Kind.WORD, "lexerTestC", 5),
                        Assertions.tuple(Token.Kind.SYMBOL, ";", 5)
                );
    }
    
    @Test
    public void endsAContinuedLineAtTheNextUnescapedLineBreak() {
        Assertions.assertThat(LexerTest.lex("// lexer test \\", "", "float lexerTestA;", "#define LEXER_TEST_B \\", "", "float lexerTestC;"))
                .extracting(Token::kind, Token::text, Token::line)
                .containsExactly(
                        Assertions.tuple(Token.Kind.WORD, "float", 3),
                        Assertions.tuple(Token.Kind.WORD, "lexerTestA", 3),
                        Assertions.tuple(Token.Kind.SYMBOL, ";", 3),
                        Assertions.tuple(Token.Kind.DIRECTIVE, "#define LEXER_TEST_B", 4),
                        Assertions.tuple(Token.Kind.WORD, "float", 6),
                        Assertions.tuple(Token.Kind.WORD, "lexerTestC", 6),
                        Assertions.tuple(Token.Kind.SYMBOL, ";", 6)
                );
        Assertions.assertThat(Lexer.lex(LexerTest.ID, "// lexer test \\\r\n\r\nfloat lexerTestD;"))
                .extracting(Token::text, Token::line)
                .containsExactly(Assertions.tuple("float", 3), Assertions.tuple("lexerTestD", 3), Assertions.tuple(";", 3));
    }
    
    @Test
    public void keepsABlockCommentInsideADirective() {
        Assertions.assertThat(LexerTest.lex("#define LEXER_TEST_A /* one */ 1 /* two", "*/", "float"))
                .extracting(Token::kind, Token::text)
                .containsExactly(
                        Assertions.tuple(Token.Kind.DIRECTIVE, "#define LEXER_TEST_A /* one */ 1"),
                        Assertions.tuple(Token.Kind.WORD, "float")
                );
    }
    
    @Test
    public void readsNumbersAndSymbols() {
        Assertions.assertThat(LexerTest.lex("x += 1.0e-4 * .5f << 0x1Fu - 0xE-1 >= y.z && !w[2]--;"))
                .extracting(Token::kind, Token::text)
                .containsExactly(
                        Assertions.tuple(Token.Kind.WORD, "x"),
                        Assertions.tuple(Token.Kind.SYMBOL, "+="),
                        Assertions.tuple(Token.Kind.NUMBER, "1.0e-4"),
                        Assertions.tuple(Token.Kind.SYMBOL, "*"),
                        Assertions.tuple(Token.Kind.NUMBER, ".5f"),
                        Assertions.tuple(Token.Kind.SYMBOL, "<<"),
                        Assertions.tuple(Token.Kind.NUMBER, "0x1Fu"),
                        Assertions.tuple(Token.Kind.SYMBOL, "-"),
                        Assertions.tuple(Token.Kind.NUMBER, "0xE"),
                        Assertions.tuple(Token.Kind.SYMBOL, "-"),
                        Assertions.tuple(Token.Kind.NUMBER, "1"),
                        Assertions.tuple(Token.Kind.SYMBOL, ">="),
                        Assertions.tuple(Token.Kind.WORD, "y"),
                        Assertions.tuple(Token.Kind.SYMBOL, "."),
                        Assertions.tuple(Token.Kind.WORD, "z"),
                        Assertions.tuple(Token.Kind.SYMBOL, "&&"),
                        Assertions.tuple(Token.Kind.SYMBOL, "!"),
                        Assertions.tuple(Token.Kind.WORD, "w"),
                        Assertions.tuple(Token.Kind.SYMBOL, "["),
                        Assertions.tuple(Token.Kind.NUMBER, "2"),
                        Assertions.tuple(Token.Kind.SYMBOL, "]"),
                        Assertions.tuple(Token.Kind.SYMBOL, "--"),
                        Assertions.tuple(Token.Kind.SYMBOL, ";")
                );
    }
    
    @Test
    public void readsTheLineAndPlaceOfEveryToken() {
        String text = String.join("\n", "float lexerTestA;", "", "/* one", "   two */ float lexerTestB; // three", "  lexerTestC");
        List<Token> tokens = Lexer.lex(LexerTest.ID, text);
        Assertions.assertThat(tokens).extracting(Token::line).containsExactly(1, 1, 1, 4, 4, 4, 5);
        for (Token token : tokens) {
            Assertions.assertThat(text.substring(token.start(), token.end())).isEqualTo(token.text());
        }
    }
    
    @Test
    public void throwsOnUnterminatedBlockComment() {
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> LexerTest.lex("float lexerTestA;", "/* never closed", "float lexerTestB;"))
                .withMessageContaining("line '2'");
    }
    
    @Test
    public void throwsOnCharacterNoTokenStartsWith() {
        Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> LexerTest.lex("float lexerTestA;", "float lexerTestB = @;"))
                .withMessageContaining("line '2'");
    }
}
