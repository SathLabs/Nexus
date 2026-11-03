package dev.satherov.nexus.internal.render.shader.syntax;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import dev.satherov.zelqro.utils.StringUtils;

import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

///
/// A recursive descent parser for the file-scope items of a program file.
///
@ApiStatus.Internal
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Parser {
    
    ///
    /// The identifier of the program file, used in the failure messages.
    ///
    private final Identifier program;
    
    ///
    /// The text of the program file.
    ///
    private final String text;
    
    ///
    /// The tokens of the text.
    ///
    private final List<Token> tokens;
    
    ///
    /// The index of the next token to read.
    ///
    private int position;
    
    ///
    /// Gets the name of the given directive token, or an empty string if the token is a bare `#` or no directive at all.
    ///
    /// The name is the run of letters, digits, and `_` after the `#` and any whitespace.
    ///
    /// @param token The token.
    ///
    /// @return The name of the directive, or an empty string.
    ///
    private static String directive(Token token) {
        if (token.kind() != Token.Kind.DIRECTIVE) {
            return "";
        }
        
        String rest = token.text().substring(1).stripLeading();
        int end = 0;
        while (end < rest.length() && (Character.isLetterOrDigit(rest.charAt(end)) || rest.charAt(end) == '_')) {
            end++;
        }
        
        return rest.substring(0, end);
    }
    
    ///
    /// Gets the index of the first of the given tokens with the given text, or `-1` if none exists.
    ///
    /// @param tokens The tokens.
    /// @param text   The text of the token to find.
    ///
    /// @return The index, or `-1`.
    ///
    private static int indexOf(List<Token> tokens, String text) {
        return tokens.stream().map(Token::text).toList().indexOf(text);
    }
    
    ///
    /// Gets the index of the first token after the layout qualifier that the given declaration starts with, or `0` if it doesn't start with one.
    ///
    /// @param declaration The tokens of the declaration.
    ///
    /// @return The index of the first token after the layout qualifier, or `0`.
    ///
    private static int afterLayout(List<Token> declaration) {
        if (!declaration.getFirst().text().equals("layout") || !declaration.get(1).text().equals("(")) {
            return 0;
        }
        
        return Parser.indexOf(declaration, ")") + 1;
    }
    
    ///
    /// Gets the name of the function whose head the given tokens are, or `null` if they are not a function head.
    ///
    /// A function head ends in `)` and has at least two tokens before its first `(`, all of them words, numbers, `[`, or `]` and the last one a word.
    ///
    /// @param head The tokens.
    ///
    /// @return The name of the function, or `null`.
    ///
    private static @Nullable String functionName(List<Token> head) {
        int parenthesis = Parser.indexOf(head, "(");
        if (parenthesis < 2 || !head.getLast().text().equals(")")) {
            return null;
        }
        
        Token name = head.get(parenthesis - 1);
        boolean typed = head.subList(0, parenthesis)
                .stream()
                .allMatch(token -> token.kind() == Token.Kind.WORD || token.kind() == Token.Kind.NUMBER || token.text().equals("[") || token.text().equals("]"));
        return typed && name.kind() == Token.Kind.WORD ? name.text() : null;
    }
    
    ///
    /// If the given type is a sampler type.
    ///
    /// @param type The GLSL type.
    ///
    /// @return `true` if the type starts with `sampler`, `isampler`, or `usampler`.
    ///
    private static boolean isSamplerType(String type) {
        return type.startsWith("sampler") || type.startsWith("isampler") || type.startsWith("usampler");
    }
    
    ///
    /// Parses the given text of a program file into its file-scope items.
    ///
    /// @param program The identifier of the program file, used in the failure messages.
    /// @param text    The text of the program file.
    ///
    /// @return The items.
    ///
    /// @throws IllegalArgumentException - If [Lexer#lex(Identifier, String)] could not read the text.
    /// - If a `(` or `{` is not closed.
    /// - If a `)` or `}` does not match the opener of its group.
    /// - If a conditional has no `#endif`.
    /// - If an `#endif` has no opener.
    /// - If an item ends neither in `;` nor in a function body.
    /// - If a declaration starts with `in` after its layout qualifier and is not `in <type> <name>;`.
    ///
    public static @Unmodifiable List<Item> parse(Identifier program, String text) {
        Parser parser = new Parser(program, text, Lexer.lex(program, text));
        List<Item> items = new ArrayList<>();
        while (parser.position < parser.tokens.size()) {
            items.add(parser.item());
        }
        
        return List.copyOf(items);
    }
    
    ///
    /// Reads the item that starts at the current token.
    ///
    /// An `#if`, `#ifdef`, or `#ifndef` starts a conditional and any other directive is a directive item.
    ///
    /// @return The item.
    ///
    /// @throws IllegalArgumentException If the current token is an `#endif`.
    ///
    private Item item() {
        Token first = this.tokens.get(this.position);
        if (first.kind() != Token.Kind.DIRECTIVE) {
            return this.declaration();
        }
        
        this.position++;
        String name = Parser.directive(first);
        return switch (name) {
            case "if", "ifdef", "ifndef" -> this.conditional(first);
            case "endif" -> throw new IllegalArgumentException(StringUtils.format("The program '%s' has an '#endif' without its opener on line '%d'", this.program, first.line()));
            default -> new Item.Directive(name, first.text(), first.line());
        };
    }
    
    ///
    /// Reads the items of the conditional that the given directive opens, up to and including its `#endif`.
    ///
    /// @param opener The `#if`, `#ifdef`, or `#ifndef` token.
    ///
    /// @return The conditional.
    ///
    /// @throws IllegalArgumentException If the tokens end before the `#endif`.
    ///
    private Item.Conditional conditional(Token opener) {
        List<Item> items = new ArrayList<>();
        while (this.position < this.tokens.size() && !Parser.directive(this.tokens.get(this.position)).equals("endif")) {
            items.add(this.item());
        }
        
        if (this.position == this.tokens.size()) {
            throw new IllegalArgumentException(StringUtils.format("The program '%s' has a conditional on line '%d' without its '#endif'", this.program, opener.line()));
        }
        
        this.position++;
        return new Item.Conditional(List.copyOf(items), this.textFrom(opener), opener.line());
    }
    
    ///
    /// Reads the declaration or function that starts at the current token, up to its `;` or the end of its function body.
    ///
    /// A brace block ends the item only if a function head stands before it. Directive tokens inside the item are part of it.
    ///
    /// @return The item.
    ///
    /// @throws IllegalArgumentException If a `)` or `}` closes no group, or if the tokens end before the item does.
    ///
    private Item declaration() {
        int start = this.position;
        Token first = this.tokens.get(start);
        while (this.position < this.tokens.size()) {
            Token token = this.tokens.get(this.position);
            switch (token.text()) {
                case ";" -> {
                    this.position++;
                    return this.classify(start);
                }
                case "{" -> {
                    String function = Parser.functionName(this.tokens.subList(start, this.position));
                    this.group();
                    if (function != null) {
                        return new Item.Function(function, this.textFrom(first), first.line());
                    }
                }
                case "(" -> this.group();
                case ")", "}" -> throw this.unmatched(token);
                default -> this.position++;
            }
        }
        
        throw new IllegalArgumentException(StringUtils.format("The program '%s' has an item on line '%d' that ends neither in ';' nor in a function body", this.program, first.line()));
    }
    
    ///
    /// Reads the parenthesis or brace group that opens at the current token, nested groups included, up to and including its closer.
    ///
    /// @throws IllegalArgumentException If the group is not closed, or if a `)` or `}` does not match the opener of its group.
    ///
    private void group() {
        Token opener = this.tokens.get(this.position);
        String closer = opener.text().equals("(") ? ")" : "}";
        this.position++;
        while (this.position < this.tokens.size()) {
            Token token = this.tokens.get(this.position);
            switch (token.text()) {
                case "(", "{" -> this.group();
                case ")", "}" -> {
                    if (!token.text().equals(closer)) throw this.unmatched(token);
                    this.position++;
                    return;
                }
                default -> this.position++;
            }
        }
        
        throw new IllegalArgumentException(StringUtils.format("The program '%s' has an unclosed '%s' on line '%d'", this.program, opener.text(), opener.line()));
    }
    
    ///
    /// Creates the failure for the given `)` or `}` that matches no opener.
    ///
    /// @param closer The closer token.
    ///
    /// @return The failure.
    ///
    private IllegalArgumentException unmatched(Token closer) {
        return new IllegalArgumentException(StringUtils.format("The program '%s' has an unmatched '%s' on line '%d'", this.program, closer.text(), closer.line()));
    }
    
    ///
    /// Creates the item of the declaration from the given token up to the `;` that was read last.
    ///
    /// The item is the first of these that fits the declaration after its layout qualifier, if it has one:
    /// - [Item.Input]: `in <type> <name>;`.
    /// - [Item.Function]: A function head followed by `;`.
    /// - [Item.Output]: An `out` among the leading words.
    /// - [Item.Block]: `uniform <name> {`.
    /// - [Item.Sampler]: `uniform <sampler type> <name>;` without a layout qualifier.
    /// - [Item.Declaration]: Anything else.
    ///
    /// @param start The index of the first token of the declaration.
    ///
    /// @return The item.
    ///
    /// @throws IllegalArgumentException If the declaration starts with `in` and is not `in <type> <name>;`.
    ///
    private Item classify(int start) {
        List<Token> declaration = this.tokens.subList(start, this.position);
        Token first = declaration.getFirst();
        String text = this.textFrom(first);
        List<Token> withoutLayout = declaration.subList(Parser.afterLayout(declaration), declaration.size());
        if (withoutLayout.getFirst().text().equals("in")) {
            if (withoutLayout.size() != 4 || withoutLayout.get(1).kind() != Token.Kind.WORD || withoutLayout.get(2).kind() != Token.Kind.WORD) {
                throw new IllegalArgumentException(StringUtils.format("Could not read the input '%s' on line '%d' of the program '%s'", text, first.line(), this.program));
            }
            
            return new Item.Input(withoutLayout.get(1).text(), withoutLayout.get(2).text(), text, first.line());
        }
        
        String function = Parser.functionName(declaration.subList(0, declaration.size() - 1));
        if (function != null) {
            return new Item.Function(function, text, first.line());
        }
        
        Token out = withoutLayout.stream()
                .takeWhile(token -> token.kind() == Token.Kind.WORD)
                .filter(token -> token.text().equals("out"))
                .findFirst()
                .orElse(null);
        if (out != null) {
            String fragmentText = this.text.substring(first.start(), out.start()) + "in" + this.text.substring(out.end(), declaration.getLast().end());
            return new Item.Output(text, fragmentText, first.line());
        }
        
        if (
                withoutLayout.size() > 3 &&
                withoutLayout.getFirst().text().equals("uniform") &&
                withoutLayout.get(1).kind() == Token.Kind.WORD &&
                withoutLayout.get(2).text().equals("{")
        ) {
            return new Item.Block(withoutLayout.get(1).text(), text, first.line());
        }
        
        if (
                declaration.size() == 4 &&
                declaration.getFirst().text().equals("uniform") &&
                Parser.isSamplerType(declaration.get(1).text()) &&
                declaration.get(2).kind() == Token.Kind.WORD
        ) {
            return new Item.Sampler(declaration.get(2).text(), text, first.line());
        }
        
        return new Item.Declaration(text, first.line());
    }
    
    ///
    /// Gets the text of the file from the start of the given token to the end of the token that was read last.
    ///
    /// @param first The first token.
    ///
    /// @return The text.
    ///
    private String textFrom(Token first) {
        return this.text.substring(first.start(), this.tokens.get(this.position - 1).end());
    }
}
