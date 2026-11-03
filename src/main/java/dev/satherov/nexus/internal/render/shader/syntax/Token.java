package dev.satherov.nexus.internal.render.shader.syntax;

import org.jetbrains.annotations.ApiStatus;

///
/// A token of a program file with its place in the text.
///
/// @param kind  The kind of the token.
/// @param text  The text of the token as written.
/// @param start The offset of its first character in the file text.
/// @param end   The offset after its last character.
/// @param line  The line it starts on, starting from 1.
///
@ApiStatus.Internal
public record Token(Kind kind, String text, int start, int end, int line) {
    
    ///
    /// The kind of a token.
    ///
    public enum Kind {
        
        ///
        /// A preprocessor line from its `#` to the end of the line, without the comment and whitespace at its end.
        ///
        /// A backslash directly before the line break continues the line. A block comment in the middle of the line is part of the token.
        ///
        DIRECTIVE,
        
        ///
        /// An identifier or a keyword.
        ///
        WORD,
        
        ///
        /// A number literal, which runs over letters, digits, `_`, and `.`.
        ///
        /// A `+` or `-` directly after an `e` or `E` is part of the literal unless it starts with `0x` or `0X`.
        ///
        NUMBER,
        
        ///
        /// The longest operator of more than one character at its offset, or a single punctuation or operator character.
        ///
        SYMBOL
    }
}
