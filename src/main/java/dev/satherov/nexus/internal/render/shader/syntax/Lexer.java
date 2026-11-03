package dev.satherov.nexus.internal.render.shader.syntax;

import lombok.experimental.UtilityClass;

import dev.satherov.zelqro.utils.StringUtils;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

///
/// Splits the text of a program file into tokens, dropping comments and whitespace.
///
@UtilityClass
@ApiStatus.Internal
public class Lexer {
    
    ///
    /// The operators of more than one character, longest first.
    ///
    private static final List<String> OPERATORS = List.of("<<=", ">>=", "++", "--", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "^^", "+=", "-=", "*=", "/=", "%=", "&=", "^=", "|=");
    
    ///
    /// The characters that a symbol token starts with.
    ///
    private static final String SYMBOLS = "()[]{}.,;:?+-*/%=<>!&|^~";
    
    ///
    /// If the given offset holds a backslash directly before a line break.
    ///
    /// @param text  The text of the program file.
    /// @param index The offset.
    ///
    /// @return `true` if the offset holds a backslash followed by `\n` or `\r\n`.
    ///
    private static boolean isContinuation(String text, int index) {
        return text.startsWith("\\\n", index) || text.startsWith("\\\r\n", index);
    }
    
    ///
    /// Gets the offset of the line break that ends the `//` comment starting at the given offset, or the length of the text if there is none.
    ///
    /// @param text  The text of the program file.
    /// @param start The offset of the comment.
    ///
    /// @return The offset of the line break, or the length of the text.
    ///
    private static int lineEnd(String text, int start) {
        int end = text.indexOf('\n', start);
        while (end != -1 && (text.startsWith("\\\n", end - 1) || text.startsWith("\\\r\n", end - 2))) {
            end = text.indexOf('\n', end + 1);
        }
        
        return end == -1 ? text.length() : end;
    }
    
    ///
    /// Gets the offset after the block comment that starts at the given offset.
    ///
    /// @param program The identifier of the program file, used in the failure message.
    /// @param text    The text of the program file.
    /// @param start   The offset of the `/*`.
    /// @param line    The line of the `/*`, used in the failure message.
    ///
    /// @return The offset after the `*/`.
    ///
    /// @throws IllegalArgumentException If the comment has no `*/`.
    ///
    private static int commentEnd(Identifier program, String text, int start, int line) {
        int close = text.indexOf("*/", start + 2);
        if (close == -1) {
            throw new IllegalArgumentException(StringUtils.format("The program '%s' has an unterminated block comment on line '%d'", program, line));
        }
        
        return close + 2;
    }
    
    ///
    /// Gets the offset after the last character of the directive that starts at the given offset, leaving out the comment and whitespace at its end.
    ///
    /// @param program The identifier of the program file, used in the failure message.
    /// @param text    The text of the program file.
    /// @param start   The offset of the `#`.
    /// @param line    The line of the `#`, used in the failure message.
    ///
    /// @return The offset after the directive.
    ///
    private static int directiveEnd(Identifier program, String text, int start, int line) {
        int end = start + 1;
        int index = start + 1;
        while (index < text.length() && text.charAt(index) != '\n' && !text.startsWith("//", index)) {
            if (text.startsWith("/*", index)) {
                index = Lexer.commentEnd(program, text, index, line + Lexer.lineBreaks(text, start, index));
            } else if (Lexer.isContinuation(text, index)) {
                index = text.indexOf('\n', index) + 1;
            } else if (Character.isWhitespace(text.charAt(index))) {
                index++;
            } else {
                index++;
                end = index;
            }
        }
        
        return end;
    }
    
    ///
    /// Gets the offset after the word that starts at the given offset.
    ///
    /// @param text  The text of the program file.
    /// @param start The offset of the first character of the word.
    ///
    /// @return The offset after the word.
    ///
    private static int wordEnd(String text, int start) {
        int end = start;
        while (end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_')) {
            end++;
        }
        
        return end;
    }
    
    ///
    /// Gets the offset after the number literal that starts at the given offset.
    ///
    /// @param text  The text of the program file.
    /// @param start The offset of the first character of the literal.
    ///
    /// @return The offset after the literal.
    ///
    private static int numberEnd(String text, int start) {
        boolean hex = text.startsWith("0x", start) || text.startsWith("0X", start);
        int end = start;
        while (end < text.length()) {
            char c = text.charAt(end);
            boolean exponentSign = !hex && (c == '+' || c == '-') && (text.charAt(end - 1) == 'e' || text.charAt(end - 1) == 'E');
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '.' && !exponentSign) {
                return end;
            }
            
            end++;
        }
        
        return end;
    }
    
    ///
    /// Gets the offset after the longest operator of [#OPERATORS] at the given offset, or after the single character there if none fits.
    ///
    /// @param text  The text of the program file.
    /// @param start The offset of the first character of the symbol.
    ///
    /// @return The offset after the symbol.
    ///
    private static int symbolEnd(String text, int start) {
        return Lexer.OPERATORS.stream()
                .filter(operator -> text.startsWith(operator, start))
                .findFirst()
                .map(operator -> start + operator.length())
                .orElse(start + 1);
    }
    
    ///
    /// The number of line breaks between the given offsets.
    ///
    /// @param text The text of the program file.
    /// @param from The offset to count from.
    /// @param to   The offset to count up to.
    ///
    /// @return The number of line breaks.
    ///
    private static int lineBreaks(String text, int from, int to) {
        return (int) text.substring(from, to).chars().filter(c -> c == '\n').count();
    }
    
    ///
    /// Splits the given text of a program file into its tokens.
    ///
    /// A `//` comment ends at the first line break that is not directly preceded by a backslash. For a `\r\n` break the backslash has to sit directly before the `\r`.
    /// A backslash directly before a line break outside of a directive is whitespace.
    ///
    /// @param program The identifier of the program file, used in the failure messages.
    /// @param text    The text of the program file.
    ///
    /// @return The tokens.
    ///
    /// @throws IllegalArgumentException If the text has a block comment without its `*/` or a character that no token starts with.
    ///
    public static @Unmodifiable List<Token> lex(Identifier program, String text) {
        List<Token> tokens = new ArrayList<>();
        int line = 1;
        int start = 0;
        while (start < text.length()) {
            char c = text.charAt(start);
            Token.Kind kind = null;
            int end;
            if (Character.isWhitespace(c) || Lexer.isContinuation(text, start)) {
                end = start + 1;
            } else if (text.startsWith("//", start)) {
                end = Lexer.lineEnd(text, start);
            } else if (text.startsWith("/*", start)) {
                end = Lexer.commentEnd(program, text, start, line);
            } else if (c == '#') {
                kind = Token.Kind.DIRECTIVE;
                end = Lexer.directiveEnd(program, text, start, line);
            } else if (Character.isLetter(c) || c == '_') {
                kind = Token.Kind.WORD;
                end = Lexer.wordEnd(text, start);
            } else if (Character.isDigit(c) || (c == '.' && start + 1 < text.length() && Character.isDigit(text.charAt(start + 1)))) {
                kind = Token.Kind.NUMBER;
                end = Lexer.numberEnd(text, start);
            } else if (Lexer.SYMBOLS.indexOf(c) != -1) {
                kind = Token.Kind.SYMBOL;
                end = Lexer.symbolEnd(text, start);
            } else {
                throw new IllegalArgumentException(StringUtils.format("The program '%s' has the unexpected character '%s' on line '%d'", program, c, line));
            }
            
            if (kind != null) {
                tokens.add(new Token(kind, text.substring(start, end), start, end, line));
            }
            
            line += Lexer.lineBreaks(text, start, end);
            start = end;
        }
        
        return List.copyOf(tokens);
    }
}
