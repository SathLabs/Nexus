package dev.satherov.nexus.internal.render.shader.syntax;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

///
/// A file-scope item of a program file.
///
@ApiStatus.Internal
public sealed interface Item {
    
    ///
    /// The text of the item from its first token to its last, comments between them included.
    ///
    /// @return The text.
    ///
    String text();
    
    ///
    /// The line the item starts on, starting from 1.
    ///
    /// @return The line.
    ///
    int line();
    
    ///
    /// A preprocessor line that neither opens nor closes a conditional.
    ///
    /// @param name The name of the directive after the `#`, or an empty string for a bare `#`.
    /// @param text The text of the directive.
    /// @param line The line the directive is on.
    ///
    record Directive(String name, String text, int line) implements Item { }
    
    ///
    /// A conditional from its `#if`, `#ifdef`, or `#ifndef` to its `#endif`.
    ///
    /// @param items The items of every branch, the `#elif` and `#else` directives among them.
    /// @param text  The text from the `#` of the opener to the end of the `#endif`.
    /// @param line  The line the opener is on.
    ///
    record Conditional(List<Item> items, String text, int line) implements Item { }
    
    ///
    /// A function definition or prototype.
    ///
    /// @param name The name of the function.
    /// @param text The text of the function.
    /// @param line The line the function starts on.
    ///
    record Function(String name, String text, int line) implements Item { }
    
    ///
    /// A vertex input declared as `in <type> <name>;`, which may have a layout qualifier in front.
    ///
    /// @param type The GLSL type of the input.
    /// @param name The name of the input.
    /// @param text The text of the declaration.
    /// @param line The line the declaration starts on.
    ///
    record Input(String type, String name, String text, int line) implements Item { }
    
    ///
    /// A varying declared with `out`.
    ///
    /// @param text         The text of the declaration.
    /// @param fragmentText The same declaration with `in` in place of the `out`.
    /// @param line         The line the declaration starts on.
    ///
    record Output(String text, String fragmentText, int line) implements Item { }
    
    ///
    /// A uniform block declaration.
    ///
    /// @param name The name of the block.
    /// @param text The text of the declaration.
    /// @param line The line the declaration starts on.
    ///
    record Block(String name, String text, int line) implements Item { }
    
    ///
    /// A sampler uniform declaration.
    ///
    /// @param name The name of the sampler.
    /// @param text The text of the declaration.
    /// @param line The line the declaration is on.
    ///
    record Sampler(String name, String text, int line) implements Item { }
    
    ///
    /// Any other declaration, such as a struct, a constant, a plain uniform, or a global variable.
    ///
    /// @param text The text of the declaration.
    /// @param line The line the declaration starts on.
    ///
    record Declaration(String text, int line) implements Item { }
}
